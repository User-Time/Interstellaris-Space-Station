package net.time_cloud;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class StationRuntime {
    private static long nextCleanup;
    private StationRuntime(){}
    public static void reset(){nextCleanup=0;}
    public static void register(){NeoForge.EVENT_BUS.addListener(StationRuntime::breaking);NeoForge.EVENT_BUS.addListener(StationRuntime::tick);}
    public static boolean ready(){try{HabitatRuntime.store();return true;}catch(IllegalStateException e){return false;}}
    public static boolean matches(Habitat h,StationCoreBlockEntity be){
        return h!=null&&h.core()!=null&&be.getLevel()!=null&&h.id().equals(be.key())&&h.dimension().equals(be.getLevel().dimension().location().toString())
                &&new BlockPos(h.core().blockX(),h.core().blockY(),h.core().blockZ()).equals(be.getBlockPos());
    }
    public static boolean canManage(ServerPlayer player,Habitat h){return h!=null&&h.core()!=null&&(h.core().owner().equals(player.getUUID())||player.hasPermissions(2));}
    public static void error(ServerPlayer player,String text){player.displayClientMessage(Component.literal("[空间站] "+text),false);}
    private static void publish(StationCoreBlockEntity be,Habitat h,String action){
        be.accept(h);HabitatRuntime.changed(((ServerLevel)be.getLevel()).getServer(),action+":"+h.id(),h.core().ownerName());
    }
    public static void claim(StationCoreBlockEntity be,ServerPlayer player){
        if(!ready())return;
        try {
            Habitat old=be.key()==null?null:HabitatRuntime.serverSnapshot().named(be.key());
            if(old!=null){if(matches(old,be))be.accept(old);return;}
            Habitat previous=be.backup();
            if(previous!=null){
                if(!canManage(player,previous)){error(player,"这个核心仍归原站主所有");return;}
                if(matches(previous,be)){restore(be,previous);return;}
                error(player,"核心身份与位置不一致，请原站主拆下后重新放置");return;
            }
            BlockPos pos=be.getBlockPos();var info=new CoreInfo(UUID.randomUUID(),player.getUUID(),player.getGameProfile().getName(),player.getGameProfile().getName()+"的空间站",false,pos.getX(),pos.getY(),pos.getZ());
            Habitat h=new Habitat(info.key(),player.level().dimension().location().toString(),pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,32,20,1,true,4,32,GravityMode.ADHESION,info);
            Habitat candidate=h;
            if(HabitatRuntime.serverSnapshot().habitats().stream().anyMatch(existing->existing.enabled()&&existing.overlaps(candidate))){
                h=h.enabled(false);error(player,"与已有环境区域重叠，新核心暂未启用；请先调整范围或停用原区域");
            }
            HabitatRuntime.store().put(h,false);publish(be,h,"core-place");
        }catch(Exception failure){error(player,"核心登记失败："+failure.getMessage());HabitatRuntime.LOG.warn("Station core registration failed at {}",be.getBlockPos(),failure);}
    }
    public static void open(StationCoreBlockEntity be,ServerPlayer player){
        if(!ready()){error(player,"服务端配置尚未就绪");return;}
        reconcile(be);if(be.key()==null)claim(be,player);
        Habitat h=be.key()==null?null:HabitatRuntime.serverSnapshot().named(be.key());
        if(!matches(h,be)){error(player,"核心登记缺失或身份重复，请站主检查配置");return;}
        if(!canManage(player,h)){error(player,"只有站主和管理员可以设置核心");return;}
        StationNetwork.open(player,h);
    }
    private static void restore(StationCoreBlockEntity be,Habitat h)throws Exception {
        if(!matches(h,be))return;
        Habitat restored=h;
        if(HabitatRuntime.serverSnapshot().habitats().stream().anyMatch(existing->existing.enabled()&&existing.overlaps(h)))restored=h.enabled(false);
        HabitatRuntime.store().put(restored,false);publish(be,restored,"core-restore");
    }
    public static void reconcile(StationCoreBlockEntity be){
        if(!ready()||!HabitatRuntime.store().writable()||be.key()==null||!(be.getLevel() instanceof ServerLevel))return;
        Habitat h=HabitatRuntime.serverSnapshot().named(be.key());
        if(h!=null){if(matches(h,be))be.accept(h);return;}
        h=be.backup();if(h==null)return;
        try{restore(be,h);}catch(Exception failure){HabitatRuntime.LOG.warn("Could not restore core {}: {}",be.getBlockPos(),failure.toString());}
    }
    public static void removed(StationCoreBlockEntity be){
        if(!ready()||be.key()==null||!(be.getLevel() instanceof ServerLevel level))return;
        Habitat h=HabitatRuntime.serverSnapshot().named(be.key());if(!matches(h,be))return;
        try{HabitatRuntime.store().delete(h.id());HabitatRuntime.changed(level.getServer(),"core-remove:"+h.id(),"block removal");}
        catch(Exception failure){HabitatRuntime.LOG.error("Could not unregister removed core {}",h.id(),failure);}
    }
    public static StationCoreBlockEntity loadedCore(ServerPlayer player,Habitat h){
        if(h.core()==null||!h.dimension().equals(player.level().dimension().location().toString())||player.position().distanceToSqr(h.x(),h.y(),h.z())>64)return null;
        var c=h.core();LevelChunk chunk=player.serverLevel().getChunkSource().getChunkNow(c.blockX()>>4,c.blockZ()>>4);
        if(chunk==null)return null;BlockEntity be=chunk.getBlockEntity(new BlockPos(c.blockX(),c.blockY(),c.blockZ()),LevelChunk.EntityCreationType.CHECK);
        return be instanceof StationCoreBlockEntity core&&matches(h,core)?core:null;
    }
    public static String etag(Habitat h){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(h.json().toString().getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new AssertionError(e);}
    }
    private static void breaking(BlockEvent.BreakEvent event){
        if(!(event.getPlayer() instanceof ServerPlayer player)||!event.getState().is(StationContent.CORE.get())||!ready())return;
        if(event.getLevel().getBlockEntity(event.getPos()) instanceof StationCoreBlockEntity be){
            Habitat h=be.key()==null?be.backup():HabitatRuntime.serverSnapshot().named(be.key());
            if(h==null)h=be.backup();
            if(h!=null&&!canManage(player,h)){event.setCanceled(true);error(player,"只有站主或管理员可以拆除这个核心");}
        }
    }
    private static void tick(ServerTickEvent.Post event){
        if(!ready()||!HabitatRuntime.store().writable())return;MinecraftServer server=event.getServer();long now=server.getTickCount();
        if(now<nextCleanup)return;nextCleanup=now+100;
        StationNetwork.expire(server);
        List<String> removed=new ArrayList<>();
        for(Habitat h:HabitatRuntime.serverSnapshot().habitats())if(h.core()!=null){
            ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(h.dimension())));if(level==null)continue;
            var c=h.core();LevelChunk chunk=level.getChunkSource().getChunkNow(c.blockX()>>4,c.blockZ()>>4);
            if(chunk!=null&&!chunk.getBlockState(new BlockPos(c.blockX(),c.blockY(),c.blockZ())).is(StationContent.CORE.get()))removed.add(h.id());
        }
        for(String id:removed)try{HabitatRuntime.store().delete(id);}catch(Exception e){HabitatRuntime.LOG.error("Failed to remove stale station {}",id,e);}
        if(!removed.isEmpty())HabitatRuntime.changed(server,"core-reconcile", "loaded block check");
    }
}
