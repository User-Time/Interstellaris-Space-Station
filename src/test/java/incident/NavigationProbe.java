package incident;

import java.nio.file.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import com.interstellaris.mod.ship.*;
import com.interstellaris.mod.space.CelestialBody;
import com.interstellaris.mod.block.entity.ShipInformationCenterBlockEntity;
import com.interstellaris.mod.network.SicPacket;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3d;
import net.time_cloud.*;
import net.time_cloud.navigation.*;

/** Actual server classes and mixins; synthetic players/views, not a graphical client or flying ship. */
@Mod("potg_navigation_probe")
public final class NavigationProbe {
    final UUID ownerId=UUID.fromString("11111111-1111-4111-8111-111111111111"),otherId=UUID.fromString("22222222-2222-4222-8222-222222222222");
    final BlockPos anchor=new BlockPos(5000,96,6000);
    MinecraftServer server;ServerLevel level;ServerPlayer owner,other;Habitat h;ResourceLocation target;int ticks,checks;boolean done;
    final List<net.minecraft.network.protocol.Packet<?>> outgoing=new ArrayList<>();
    Navigator.Stock stock=new Navigator.Stock(0,List.of(),List.of(),List.of());
    public NavigationProbe(){if(!Boolean.getBoolean("habitat.probe.navigation"))return;NeoForge.EVENT_BUS.addListener(this::start);NeoForge.EVENT_BUS.addListener(this::tick);}
    void start(ServerStartedEvent e){server=e.getServer();}
    void log(String s){System.out.println("POTG_NAV "+s);try{Files.writeString(Path.of("navigation-results.txt"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
    void ok(boolean result,String label){if(!result)throw new AssertionError(label);checks++;log("PASS "+label);}
    ServerPlayer player(UUID id,String name){var profile=new GameProfile(id,name);var p=new ServerPlayer(server,level,profile,ClientInformation.createDefault());p.connection=new ServerGamePacketListenerImpl(server,new HabitatProbe.MemoryConnection(),p,CommonListenerCookie.createInitial(profile,false)){@Override public void send(net.minecraft.network.protocol.Packet<?> packet){outgoing.add(packet);}};return p;}
    Habitat publicity(Habitat v,boolean visible){return new Habitat(v.id(),v.dimension(),v.x(),v.y(),v.z(),v.radius(),v.temperatureC(),v.gravityMultiplier(),v.enabled(),v.floorDistance(),v.gravityRadius(),v.gravityMode(),v.core().display(v.core().name(),visible));}
    void put(Habitat next)throws Exception{h=next;HabitatRuntime.store().put(h,true);((StationCoreBlockEntity)level.getBlockEntity(anchor)).accept(h);}
    Navigator.View view(double x,double y,double z){return new Navigator.View(new Vector3d(x,y,z),new Vector3d(1,0,0),0,1,List.of(),List.of());}
    void run()throws Exception{
        level=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse("interstellaris:solar_system")));ok(level!=null,"native solar system present");owner=player(ownerId,"NavOwner");other=player(otherId,"NavVisitor");
        if(System.getProperty("habitat.probe.phase","1").equals("2")){
            String id=Files.readString(Path.of("expected-navigation-id.txt"));h=HabitatRuntime.serverSnapshot().named(id);target=StationDestinations.target(id);
            ok(h!=null,"station settings survive normal stop/start");ok(StationVisits.get(server).contains(ownerId,id),"owner visit survives normal stop/start");ok(StationVisits.get(server).contains(otherId,id),"visitor visit survives normal stop/start");
            ok(level.getBlockEntity(anchor) instanceof StationCoreBlockEntity core&&id.equals(core.key()),"existing core block entity retains its station UUID after package migration");
            ok(StationDestinations.issue(level,target,otherId)==null,"restored visit authorizes navigation");ok(Navigator.body(level,target)!=null,"station holder rebuilt after restart");
            ok(!level.registryAccess().registryOrThrow(CelestialBody.REGISTRY).containsKey(target),"restarted station still absent from native celestial registry");finish("NAVIGATION_PERSISTENCE_TESTS_PASSED");return;
        }
        ok(HabitatRuntime.serverSnapshot().habitats().isEmpty(),"isolated world starts without production stations");
        level.setBlockAndUpdate(anchor,StationContent.CORE.get().defaultBlockState());var core=(StationCoreBlockEntity)level.getBlockEntity(anchor);ok(core!=null,"test core exists in blank test world");
        var info=new CoreInfo(UUID.randomUUID(),ownerId,"NavOwner","导航测试站",false,anchor.getX(),anchor.getY(),anchor.getZ());
        h=new Habitat(info.key(),level.dimension().location().toString(),anchor.getX()+.5,anchor.getY()+.5,anchor.getZ()+.5,32,20,1,true,4,32,GravityMode.ADHESION,info);
        HabitatRuntime.store().put(h,false);core.accept(h);target=StationDestinations.target(h.id());Files.writeString(Path.of("expected-navigation-id.txt"),h.id());
        var holder=Navigator.body(level,target);ok(holder!=null&&holder.isBound(),"native Navigator resolves bound ephemeral station holder");
        ok(StationDestinations.station(target).equals(h.id()),"station target identity roundtrip");ok(((StationBody)(Object)holder.value()).potg$stationId().equals(h.id()),"mixin tags final CelestialBody record");ok(CelestialBody.name(holder).getString().equals("导航测试站"),"native destination name is station name");
        ok(!level.registryAccess().registryOrThrow(CelestialBody.REGISTRY).containsKey(target),"station not inserted into native planet registry");
        ok(Navigator.body(level,CelestialBody.MOON.location())!=null,"original Moon destination unchanged");
        ok("station_locked".equals(StationDestinations.issue(level,target,ownerId)),"owner also needs first visit");ok("station_unavailable".equals(StationDestinations.issue(level,target,otherId)),"private station denied to visitor");
        var origin=new Vector3d(-100000,100,0);
        for(NavMode mode:NavMode.values())ok("station_locked".equals(Navigator.plan(level,origin,holder,mode,stock,ownerId).issue()),"server rejects locked station in native "+mode+" plan");
        ok(StationVisits.get(server).add(ownerId,h.id()),"owner discovery recorded once");ok(!StationVisits.get(server).add(ownerId,h.id()),"duplicate discovery does not dirty again");
        for(NavMode mode:NavMode.values()){
            String expected=switch(mode){case THRUST->"no_rocket";case PULSE->"no_pulse_drive";case WARP->"no_warp_drive";};
            String actual=Navigator.plan(level,origin,holder,mode,stock,ownerId).issue();ok(expected.equals(actual),"native equipment check retained "+mode+" actual="+actual);
        }
        var rocketOnly=new Navigator.Stock(1,List.of(),List.of(),List.of());ok("fuel".equals(Navigator.plan(level,origin,holder,NavMode.THRUST,rocketOnly,ownerId).issue()),"native propellant requirement retained");
        double range=Navigator.distance(new Vector3d(h.x()+h.extent()+1000,h.y(),h.z()),holder.value());ok(Math.abs(range-(1000-Navigator.stopBlocks()))<1e-6,"native arrival distance retained");
        var nav=new Navigator();nav.start(holder.key(),NavMode.THRUST,ownerId);var sic=new ShipInformationCenterBlockEntity(BlockPos.ZERO,net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.parse("interstellaris:ship_information_center")).defaultBlockState());
        nav.tick(null,level,null,sic,stock,view(h.x()-5000,h.y(),h.z()));ok(nav.phase()==Navigator.TRAVEL,"native THRUST align transitions to cruise with station target");
        nav.tick(null,level,null,sic,stock,view(h.x()+100,h.y(),h.z()));ok(!nav.active(),"native arrival clears station navigation");
        nav.start(holder.key(),NavMode.THRUST,otherId);nav.tick(null,level,null,null,stock,view(0,0,0));ok(!nav.active(),"unauthorized route aborted on native tick");
        IPayloadContext context=(IPayloadContext)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{IPayloadContext.class},(proxy,method,args)->{if(method.getName().equals("player"))return other;throw new AssertionError("denied configure reached native packet body: "+method.getName());});
        int messages=outgoing.size();new SicPacket(1,BlockPos.ZERO,target.toString(),0).handle(context);ok(outgoing.size()==messages+1,"forged private station configure rejected before native SIC lookup");
        put(h.enabled(false));ok(Navigator.body(level,target)==null,"disabled station no longer resolves");nav.start(holder.key(),NavMode.THRUST,ownerId);nav.tick(null,level,null,null,stock,view(0,0,0));ok(!nav.active(),"in-flight disabled target aborts through native finish");
        put(publicity(h.enabled(true),true));
        ok("station_locked".equals(StationDestinations.issue(level,target,otherId)),"public station visible but visitor remains locked");ok(StationDirectory.visible(HabitatRuntime.serverSnapshot(),otherId).size()==1,"public station directory includes target");
        ok(StationUnlockNetwork.visibleVisited(other).isEmpty(),"unlock packet omits unvisited public target");
        var payload=new StationUnlockNetwork.Payload(Set.of(h.id()));FriendlyByteBuf b=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{StationUnlockNetwork.Payload.CODEC.encode(b,payload);ok(StationUnlockNetwork.Payload.CODEC.decode(b).ids().equals(payload.ids()),"unlock payload roundtrip");}finally{b.release();}
        b=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{b.writeVarInt(513);try{StationUnlockNetwork.Payload.CODEC.decode(b);throw new AssertionError("oversized packet accepted");}catch(IllegalArgumentException expected){ok(true,"oversized unlock payload rejected");}}finally{b.release();}
        other.moveTo(h.x(),h.y(),h.z(),0,0);owner.moveTo(h.x(),h.y(),h.z(),0,0);StationDiscovery.tick(other);
        log("Waiting for real server tick dwell with synthetic ServerPlayer (not FakePlayer or real client)");
    }
    void afterDwell()throws Exception{
        ok(StationVisits.get(server).contains(otherId,h.id()),"40-tick actual server dwell records first visit");ok(StationDestinations.issue(level,target,otherId)==null,"visited public station authorized");ok(StationUnlockNetwork.visibleVisited(other).equals(Set.of(h.id())),"unlock directory includes only visible visited station");
        put(publicity(h,false));ok(StationUnlockNetwork.visibleVisited(other).isEmpty(),"privacy revocation removes previously visited target from unlock packet");
        var nav=new Navigator();nav.start(StationDestinations.resolve(target).key(),NavMode.PULSE,otherId);nav.tick(null,level,null,null,stock,view(0,0,0));ok(!nav.active(),"privacy revoked mid-route aborts native navigator");put(publicity(h,true));
        var tag=StationVisits.get(server).save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());ok(tag.getByteArray("ledger").length>0,"discovery SavedData serialized as bounded byte array");
        finish("NAVIGATION_COMPONENT_TESTS_PASSED");
    }
    void finish(String label){log(label+" checks="+checks+"; synthetic players and native navigator component calls only; graphical client and real ship travel pending");done=true;server.halt(false);}
    void tick(ServerTickEvent.Post event){if(server==null||done)return;ticks++;try{if(ticks==40)run();if(done)return;if(other!=null&&ticks>40&&ticks<=110)StationDiscovery.tick(other);if(ticks==110)afterDwell();if(ticks>400)throw new AssertionError("timeout");}catch(Throwable t){done=true;log("TEST_FAILURE "+t);t.printStackTrace();server.halt(false);}}
}
