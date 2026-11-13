package net.time_cloud;

import com.interstellaris.mod.environment.*;
import com.interstellaris.mod.ship.ShipGravity;
import com.interstellaris.mod.worldgen.ModDimensions;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.Sable;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3d;
import net.time_cloud.mixin.EnvironmentAccess;
import org.slf4j.Logger;
import net.time_cloud.navigation.*;

@Mod(HabitatRuntime.ID)
public final class HabitatRuntime {
    public static final String ID="potg_station_habitat";
    public static final Logger LOG=LogUtils.getLogger();
    private static volatile HabitatStore store;
    private static volatile WeakReference<Player> localPlayer=new WeakReference<>(null);
    private static final Map<Habitat,Environment> ENVIRONMENTS=new ConcurrentHashMap<>();
    private static final Map<UUID,String> MEMBERSHIP=new HashMap<>();

    public HabitatRuntime(IEventBus bus,net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,DomeConfig.SPEC,"potgcraft-station-habitat-client.toml");
        bus.addListener(HabitatNetwork::register);
        StationContent.register(bus);
        bus.addListener(StationNetwork::register);
        bus.addListener(StationUnlockNetwork::register);
        StationRuntime.register();
        NeoForge.EVENT_BUS.addListener(this::commands);
        NeoForge.EVENT_BUS.addListener(this::start);
        NeoForge.EVENT_BUS.addListener(this::stop);
        NeoForge.EVENT_BUS.addListener(this::login);
        NeoForge.EVENT_BUS.addListener(this::logout);
        NeoForge.EVENT_BUS.addListener(this::prePlayer);
        NeoForge.EVENT_BUS.addListener(this::postPlayer);
        NeoForge.EVENT_BUS.addListener(this::datapacks);
    }
    private void commands(RegisterCommandsEvent event) { HabitatCommands.register(event.getDispatcher()); }
    private void start(ServerStartedEvent event) {
        HabitatStore next=new HabitatStore(FMLPaths.CONFIGDIR.get().resolve("potgcraft-station-habitats.json"),
                habitat->ModDimensions.isSystem(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(habitat.dimension()))));
        try { next.load(); LOG.info("Station habitats ready: {} spheres",next.snapshot().habitats().size()); }
        catch(Exception exception) { next.lockWrites("原环境配置无法解析，修复文件并重载前禁止写入");LOG.error("Invalid habitat config; starting with empty spheres, preserving the original file",exception); }
        store=next;
    }
    private void stop(ServerStoppedEvent event) { store=null;MEMBERSHIP.clear();clearEnvironmentCache();HabitatNetwork.resetServer();StationNetwork.resetServer();StationRuntime.reset();StationDiscovery.reset(); }
    private void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) {
            HabitatNetwork.send(player,serverSnapshot()); MEMBERSHIP.put(player.getUUID(),membership(player));
            StationNetwork.sendDirectory(player);
        }
    }
    private void logout(PlayerEvent.PlayerLoggedOutEvent event) { MEMBERSHIP.remove(event.getEntity().getUUID());HabitatNetwork.remove(event.getEntity().getUUID());StationNetwork.logout(event.getEntity().getUUID());StationDiscovery.logout(event.getEntity().getUUID()); }
    private void datapacks(OnDatapackSyncEvent event) {
        if(event.getPlayer()!=null){HabitatNetwork.send(event.getPlayer(),serverSnapshot());StationNetwork.sendDirectory(event.getPlayer());}
        else {HabitatNetwork.broadcast(serverSnapshot());for(var player:event.getPlayerList().getPlayers())StationNetwork.sendDirectory(player);}
    }
    private void prePlayer(PlayerTickEvent.Pre event) { trackClient(event.getEntity()); }
    private void postPlayer(PlayerTickEvent.Post event) {
        Player player=event.getEntity();trackClient(player);
        if(player instanceof ServerPlayer serverPlayer) {
            String next=membership(player),previous=MEMBERSHIP.put(player.getUUID(),next);
            if(!Objects.equals(previous,next)) EnvironmentHazards.sync(serverPlayer);
            StationDiscovery.tick(serverPlayer);
        }
    }
    private static void trackClient(Player player) {
        if(player.level().isClientSide && player.isLocalPlayer()) localPlayer=new WeakReference<>(player);
    }
    public static Player clientPlayer() { return localPlayer.get(); }
    public static void clearClient() { localPlayer=new WeakReference<>(null);clearEnvironmentCache(); }
    public static void clearEnvironmentCache() { ENVIRONMENTS.clear(); }
    public static HabitatStore store() {
        HabitatStore result=store;if(result==null)throw new IllegalStateException("环境球尚未就绪");return result;
    }
    public static HabitatSnapshot serverSnapshot() { HabitatStore value=store;return value==null?HabitatSnapshot.EMPTY:value.snapshot(); }
    public static HabitatSnapshot snapshot(Level level) { return level.isClientSide?HabitatNetwork.clientSnapshot():serverSnapshot(); }

    /** Delegate plot-to-world conversion to Sable, for both players and block positions. */
    public static Vector3d worldPosition(Level level,Vec3 point) {
        return Sable.HELPER.projectOutOfSubLevel(level,new Vector3d(point.x,point.y,point.z));
    }

    public static Habitat at(Level level,Vec3 point) {
        if(!ModDimensions.isSpace(level))return null;
        // Block machines aboard a Sable structure use plot coordinates; spheres use physical world coordinates.
        Vector3d global=worldPosition(level,point);
        return snapshot(level).at(level.dimension().location().toString(),global.x,global.y,global.z);
    }
    public static Habitat at(Entity entity) { return at(entity.level(),entity.position()); }
    public static Habitat gravityAt(Entity entity) {
        Level level=entity.level();if(!ModDimensions.isSpace(level))return null;var p=entity.position();
        Vector3d global=worldPosition(level,p);
        return snapshot(level).gravityAt(level.dimension().location().toString(),global.x,global.y,global.z);
    }
    private static String membership(Player player) { Habitat value=at(player);return value==null?"":value.id(); }
    public static boolean ours(Environment environment) { return environment!=null && environment.id().getNamespace().equals(ID); }
    public static Environment baseEnvironment(Level level) {
        Environment base=EnvironmentAccess.potg$byDimension().get(level.dimension().location());
        return base!=null?base:level.isClientSide?HabitatNetwork.background(level.dimension().location()):null;
    }
    public static List<Environment> backgrounds() { return List.copyOf(EnvironmentAccess.potg$byDimension().values()); }
    public static Environment environment(Habitat habitat) {
        return ENVIRONMENTS.computeIfAbsent(habitat,key->new Environment(ResourceLocation.fromNamespaceAndPath(ID,key.id()),
                ResourceLocation.parse(key.dimension()),true,1.0,key.temperatureC(),key.temperatureC(),0.0));
    }
    public static Environment environmentAt(Level level,Vec3 point,Environment original) {
        Habitat found=at(level,point);
        return found!=null?environment(found):ours(original)?baseEnvironment(level):original;
    }
    public static Environment clientEnvironment(Environment original) {
        Player player=clientPlayer();
        return player==null?(ours(original)?null:original):environmentAt(player.level(),player.position(),original);
    }
    public static double gravity(Entity entity,double original) {
        if(!(entity instanceof Player player)||player.isSpectator()||player.isPassenger()||player.isNoGravity()||player.getAbilities().flying)
            return original;
        Habitat found=gravityAt(player);
        if(found==null || ShipGravity.ship(player)!=null)return original;
        if(found.gravityMode()==GravityMode.NONE)return 0;
        if(found.gravityMode()==GravityMode.ADHESION&&!FloorSupport.hasFloor(player,found.floorDistance()))return original;
        double gravity=player.getAttributeValue(Attributes.GRAVITY)*found.gravityMultiplier();
        return Double.isFinite(gravity)&&gravity>=0?gravity:original;
    }
    public static Vec3 shipTravel(Entity entity,Vec3 original) {
        if(original==null||!(entity instanceof Player player)||player.isSpectator()||player.isPassenger()||player.isNoGravity()||player.getAbilities().flying)return original;
        Habitat h=gravityAt(player);if(h==null)return original;
        var ship=ShipGravity.ship(player);var orientation=ShipGravity.orientation(player,1f);if(ship==null||orientation==null)return original;
        double desired=h.gravityMode()==GravityMode.NONE?0:player.getAttributeValue(Attributes.GRAVITY)*h.gravityMultiplier();
        if(h.gravityMode()==GravityMode.ADHESION&&!FloorSupport.hasShipFloor(player,ship,h.floorDistance()))desired=0;
        if(!Double.isFinite(desired)||desired<0)return original;
        Vector3d adjustment=orientation.transform(new Vector3d(0,(.08-desired)*.98,0));
        return original.add(adjustment.x,adjustment.y,adjustment.z);
    }
    public static void changed(MinecraftServer server,String action,String actor) {
        clearEnvironmentCache();HabitatNetwork.broadcast(serverSnapshot());
        for(ServerPlayer player:server.getPlayerList().getPlayers()) {
            EnvironmentHazards.sync(player);MEMBERSHIP.put(player.getUUID(),membership(player));
            StationNetwork.sendDirectory(player);
        }
        LOG.info("Station habitat {} by {}: {} spheres, revision {}",action,actor,serverSnapshot().habitats().size(),serverSnapshot().revision());
    }
}
