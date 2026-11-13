package net.time_cloud;

import com.google.gson.JsonParser;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class StationNetwork {
    private record Session(long token,String stationId,int opened,int lastRequest){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final AtomicLong SEQUENCE=new AtomicLong(System.nanoTime());
    private StationNetwork(){}
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name){return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HabitatRuntime.ID,name));}
    public static void resetServer(){SESSIONS.clear();}
    public static void logout(UUID id){SESSIONS.remove(id);}
    public static void expire(MinecraftServer server){SESSIONS.entrySet().removeIf(e->server.getTickCount()-e.getValue().opened()>12000);}
    public static void sendDirectory(ServerPlayer player){PacketDistributor.sendToPlayer(player,new DirectoryPayload(StationDirectory.visible(HabitatRuntime.serverSnapshot(),player.getUUID())));net.time_cloud.navigation.StationUnlockNetwork.send(player);}
    public static long open(ServerPlayer player,Habitat h){
        long token=SEQUENCE.incrementAndGet();SESSIONS.put(player.getUUID(),new Session(token,h.id(),player.server.getTickCount(),Integer.MIN_VALUE));
        sendState(player,h,token,true,"");return token;
    }
    private static void sendState(ServerPlayer player,Habitat h,long token,boolean initial,String message){
        PacketDistributor.sendToPlayer(player,new OpenPayload(token,initial,h.id(),StationRuntime.etag(h),h.json().toString(),message));
    }
    private static void error(ServerPlayer player,long token,String text){if(text.length()>240)text=text.substring(0,240);PacketDistributor.sendToPlayer(player,new ErrorPayload(token,text));}
    /** Same mutation path used by the actual packet handler and isolated authorization tests. */
    public static boolean apply(ServerPlayer player,UpdatePayload payload){
        Session session=SESSIONS.get(player.getUUID());int tick=player.server.getTickCount();
        if(session==null||session.token()!=payload.token()||!session.stationId().equals(payload.id())||tick-session.opened()>12000){error(player,payload.token(),"设置会话已失效，请重新右键核心");return false;}
        if(session.lastRequest()!=Integer.MIN_VALUE&&tick-session.lastRequest()<5){error(player,payload.token(),"操作过快，请稍后重试");return false;}
        SESSIONS.put(player.getUUID(),new Session(session.token(),session.stationId(),session.opened(),tick));
        Habitat h=HabitatRuntime.serverSnapshot().named(payload.id());
        if(!StationRuntime.canManage(player,h)){error(player,payload.token(),"没有这个核心的管理权限");return false;}
        StationCoreBlockEntity core=StationRuntime.loadedCore(player,h);
        if(core==null){error(player,payload.token(),"核心已移除、尚未加载或距离超过8格");return false;}
        if(!StationRuntime.etag(h).equals(payload.expected())){sendState(player,h,payload.token(),false,"配置已被其他操作更新，已重新加载");return false;}
        try {
            StationSettings settings=StationSettings.parse(JsonParser.parseString(payload.json()).getAsJsonObject());
            Habitat next=settings.apply(h);HabitatRuntime.store().put(next,true);core.accept(next);
            HabitatRuntime.changed(player.server,"core-settings:"+h.id(),player.getGameProfile().getName());
            sendState(player,next,payload.token(),false,"设置已保存");return true;
        }catch(Exception failure){error(player,payload.token(),"保存失败："+failure.getMessage());return false;}
    }
    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("4");
        registrar.playToClient(DirectoryPayload.TYPE,DirectoryPayload.CODEC,(payload,context)->context.enqueueWork(()->StationClientCache.accept(payload.markers())));
        registrar.playToClient(OpenPayload.TYPE,OpenPayload.CODEC,(payload,context)->context.enqueueWork(()->net.time_cloud.client.StationClientHooks.open(payload)));
        registrar.playToClient(ErrorPayload.TYPE,ErrorPayload.CODEC,(payload,context)->context.enqueueWork(()->net.time_cloud.client.StationClientHooks.error(payload)));
        registrar.playToServer(UpdatePayload.TYPE,UpdatePayload.CODEC,(payload,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,payload);}));
        registrar.playToServer(ClosePayload.TYPE,ClosePayload.CODEC,(payload,context)->context.enqueueWork(()->{
            if(context.player() instanceof ServerPlayer player){var session=SESSIONS.get(player.getUUID());if(session!=null&&session.token()==payload.token())SESSIONS.remove(player.getUUID());}
        }));
    }
    public record DirectoryPayload(List<StationMarker> markers) implements CustomPacketPayload {
        public static final Type<DirectoryPayload> TYPE=StationNetwork.type("station_directory");
        public static final StreamCodec<FriendlyByteBuf,DirectoryPayload> CODEC=new StreamCodec<>(){
            public DirectoryPayload decode(FriendlyByteBuf b){
                int n=b.readVarInt();if(n<0||n>512)throw new IllegalArgumentException("station count");List<StationMarker> list=new ArrayList<>(n);Set<String> ids=new HashSet<>();
                for(int i=0;i<n;i++){
                    var marker=new StationMarker(b.readUtf(48),b.readUtf(48),b.readUtf(32),b.readUtf(128),b.readDouble(),b.readDouble(),b.readDouble(),b.readBoolean(),b.readBoolean(),b.readDouble(),b.readDouble(),GravityMode.valueOf(b.readUtf(16)),b.readDouble(),b.readDouble());
                    if(!ids.add(marker.id()))throw new IllegalArgumentException("duplicate station");list.add(marker);
                }return new DirectoryPayload(List.copyOf(list));
            }
            public void encode(FriendlyByteBuf b,DirectoryPayload p){
                if(p.markers().size()>512)throw new IllegalArgumentException("station count");b.writeVarInt(p.markers().size());
                for(var m:p.markers()){b.writeUtf(m.id(),48);b.writeUtf(m.name(),48);b.writeUtf(m.ownerName(),32);b.writeUtf(m.dimension(),128);b.writeDouble(m.x());b.writeDouble(m.y());b.writeDouble(m.z());b.writeBoolean(m.publicStation());b.writeBoolean(m.enabled());b.writeDouble(m.oxygenRadius());b.writeDouble(m.gravityRadius());b.writeUtf(m.gravityMode().name(),16);b.writeDouble(m.gravityMultiplier());b.writeDouble(m.temperatureC());}
            }
        };
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record OpenPayload(long token,boolean initial,String id,String revision,String json,String message) implements CustomPacketPayload {
        public static final Type<OpenPayload> TYPE=StationNetwork.type("station_open");
        public static final StreamCodec<FriendlyByteBuf,OpenPayload> CODEC=new StreamCodec<>(){
            public OpenPayload decode(FriendlyByteBuf b){return new OpenPayload(b.readLong(),b.readBoolean(),b.readUtf(48),b.readUtf(64),b.readUtf(8192),b.readUtf(256));}
            public void encode(FriendlyByteBuf b,OpenPayload p){b.writeLong(p.token());b.writeBoolean(p.initial());b.writeUtf(p.id(),48);b.writeUtf(p.revision(),64);b.writeUtf(p.json(),8192);b.writeUtf(p.message(),256);}
        };
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record UpdatePayload(long token,String id,String expected,String json) implements CustomPacketPayload {
        public static final Type<UpdatePayload> TYPE=StationNetwork.type("station_update");
        public static final StreamCodec<FriendlyByteBuf,UpdatePayload> CODEC=new StreamCodec<>(){
            public UpdatePayload decode(FriendlyByteBuf b){return new UpdatePayload(b.readLong(),b.readUtf(48),b.readUtf(64),b.readUtf(4096));}
            public void encode(FriendlyByteBuf b,UpdatePayload p){b.writeLong(p.token());b.writeUtf(p.id(),48);b.writeUtf(p.expected(),64);b.writeUtf(p.json(),4096);}
        };
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record ErrorPayload(long token,String message) implements CustomPacketPayload {
        public static final Type<ErrorPayload> TYPE=StationNetwork.type("station_error");
        public static final StreamCodec<FriendlyByteBuf,ErrorPayload> CODEC=new StreamCodec<>(){
            public ErrorPayload decode(FriendlyByteBuf b){return new ErrorPayload(b.readLong(),b.readUtf(256));}
            public void encode(FriendlyByteBuf b,ErrorPayload p){b.writeLong(p.token());b.writeUtf(p.message(),256);}
        };
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record ClosePayload(long token) implements CustomPacketPayload {
        public static final Type<ClosePayload> TYPE=StationNetwork.type("station_close");
        public static final StreamCodec<FriendlyByteBuf,ClosePayload> CODEC=new StreamCodec<>(){
            public ClosePayload decode(FriendlyByteBuf b){return new ClosePayload(b.readLong());}
            public void encode(FriendlyByteBuf b,ClosePayload p){b.writeLong(p.token());}
        };
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
}
