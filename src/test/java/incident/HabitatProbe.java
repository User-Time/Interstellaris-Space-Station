package incident;
import java.util.*;
import java.nio.file.*;
import java.net.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.Unpooled;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.core.*;
import net.minecraft.resources.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import com.interstellaris.mod.environment.*;
import com.interstellaris.mod.block.entity.AirSeparationUnitBlockEntity;
import net.time_cloud.*;

@Mod("potg_habitat_probe")
public class HabitatProbe {
 MinecraftServer server;int ticks,checks;boolean done;ServerLevel solar;ServerPlayer player;
 dev.ryanhcode.sable.sublevel.ServerSubLevel ship;
 void log(String x){String s="tick="+ticks+" "+x;System.out.println("POTG_HABITAT "+s);try{Files.writeString(Path.of("habitat-results.txt"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
 void ok(boolean b,String x){if(!b)throw new IllegalStateException(x);checks++;log("PASS "+x);}
 void equal(double actual,double expected,String x){ok(Math.abs(actual-expected)<1e-7,x+" actual="+actual);}
 public HabitatProbe(){if(Boolean.getBoolean("habitat.probe.navigation")||Boolean.getBoolean("habitat.probe.floor")||Boolean.getBoolean("habitat.probe.station"))return;NeoForge.EVENT_BUS.addListener(this::start);NeoForge.EVENT_BUS.addListener(this::tick);}
 void start(ServerStartedEvent e){server=e.getServer();log("START phase="+System.getProperty("habitat.probe.phase","1")+" synthetic ServerPlayer; not a real client");}
 ServerLevel world(String id){return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse(id)));}
 static class MemoryConnection extends Connection {
  MemoryConnection(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new InetSocketAddress("127.0.0.1",10000);}
  @Override public boolean isConnected(){return true;}
  @Override public boolean isMemoryConnection(){return true;}
  @Override public SocketAddress getRemoteAddress(){return address;}
  @Override public void send(Packet<?> p){}
  @Override public void send(Packet<?> p,PacketSendListener l){}
  @Override public void send(Packet<?> p,PacketSendListener l,boolean f){}
 }
 void command(String command){server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(solar).withPosition(new Vec3(100.5,65,100.5)),command);}
 void position(double x,double y,double z){player.moveTo(x,y,z,0,0);player.tickCount=20;}
 void run()throws Exception {
  solar=world("interstellaris:solar_system");ok(solar!=null,"solar dimension loaded");
  var profile=new GameProfile(UUID.randomUUID(),"HabitatProbe");player=new ServerPlayer(server,solar,profile,ClientInformation.createDefault());
  player.connection=new ServerGamePacketListenerImpl(server,new MemoryConnection(),player,CommonListenerCookie.createInitial(profile,false));player.setGameMode(GameType.SURVIVAL);
  position(100.5,65,100.5);
  if(System.getProperty("habitat.probe.phase","1").equals("2")) {
   solar.setBlockAndUpdate(new BlockPos(100,64,100),Blocks.STONE.defaultBlockState());
   ok(HabitatRuntime.serverSnapshot().named("lab")!=null,"config persisted across normal restart");
   equal(player.getGravity(),.08,"restored sphere gravity");equal(EnvironmentManager.of(player).temperature(solar),20,"restored temperature");
   log("PERSISTENCE_TESTS_PASSED checks="+checks);
   BlockPos root=new BlockPos(100,63,100);var blocks=new ArrayList<BlockPos>();
   for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){BlockPos b=root.offset(x,0,z);solar.setBlockAndUpdate(b,Blocks.STONE.defaultBlockState());blocks.add(b);}
   ship=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(solar,root,blocks,new dev.ryanhcode.sable.companion.math.BoundingBox3i(root.offset(-3,-1,-3),root.offset(3,2,3)));
   var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(solar);
   container.addForceLoadTicket(ship,dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,net.minecraft.util.Unit.INSTANCE);
   var feet=ship.logicalPose().transformPosition(Vec3.atCenterOf(ship.getPlot().getCenterBlock()).add(0,.5,0));position(feet.x,feet.y,feet.z);
   log("SHIP_SETUP ship="+ship+" feet="+feet+" plot="+ship.getPlot().getCenterBlock());return;
  }
  ok(HabitatRuntime.serverSnapshot().habitats().isEmpty(),"initial config empty");
  equal(player.getGravity(),0,"outside original space gravity");
  var outside=EnvironmentManager.of(player);ok(outside!=null&&!outside.breathable()&&outside.pressure()<.1,"outside vacuum environment");
  command("habitat create lab 10");ok(HabitatRuntime.serverSnapshot().named("lab")!=null,"create actual command");
  equal(player.getGravity(),0,"empty habitat has no artificial gravity");
  solar.setBlockAndUpdate(new BlockPos(100,64,100),Blocks.STONE.defaultBlockState());
  equal(player.getGravity(),.08,"sphere overrides original space zero gravity");
  var earth=EnvironmentManager.of(player);ok(earth.breathable(),"breathable earth air");equal(earth.pressure(),1,"one atmosphere");equal(earth.temperature(solar),20,"20 Celsius");equal(earth.radiation(),0,"zero radiation");
  ok(!HabitatRuntime.ours(EnvironmentManager.of(solar)),"world environment remains vacuum");
  for(double[] d:new double[][]{{10,0,0},{-10,0,0},{0,10,0},{0,-10,0},{0,0,10},{0,0,-10}}){position(100.5+d[0],65+d[1],100.5+d[2]);solar.setBlockAndUpdate(BlockPos.containing(player.position().add(0,-1,0)),Blocks.STONE.defaultBlockState());equal(player.getGravity(),.08,"inclusive sphere boundary with floor "+Arrays.toString(d));}
  position(110.501,65,100.5);equal(player.getGravity(),0,"outside boundary restores gravity");ok(!EnvironmentManager.of(player).breathable(),"outside boundary restores air");
  position(100.5,65,100.5);player.getAttribute(Attributes.GRAVITY).setBaseValue(.04);equal(player.getGravity(),.04,"preserves player gravity attribute");player.getAttribute(Attributes.GRAVITY).setBaseValue(.08);
  player.setGameMode(GameType.SPECTATOR);equal(player.getGravity(),0,"spectator preserved");player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;equal(player.getGravity(),0,"creative flight preserved");player.getAbilities().flying=false;player.setGameMode(GameType.SURVIVAL);
  player.setAirSupply(300);float health=player.getHealth();EnvironmentHazards.onPlayerTick(new PlayerTickEvent.Post(player));equal(player.getAirSupply(),300,"bare player keeps air inside");equal(player.getHealth(),health,"bare player no environment damage inside");
  BlockPos in=new BlockPos(100,64,100),out=new BlockPos(130,64,100);
  ok(Combustion.canBurn(solar,in),"combustion permitted inside");ok(!Combustion.canBurn(solar,out),"combustion prohibited outside");
  log("water phases inside="+WaterPhase.at(solar,in)+" outside="+WaterPhase.at(solar,out));ok(WaterPhase.at(solar,in).toString().equals("LIQUID"),"water liquid inside");ok(!WaterPhase.at(solar,out).toString().equals("LIQUID"),"water original space phase outside");
  var machineField=Class.forName("com.interstellaris.mod.init.ModBlocks").getField("AIR_SEPARATION_UNIT");var machineBlock=(Block)((java.util.function.Supplier<?>)machineField.get(null)).get();
  long insideGas=machineGas(new AirSeparationUnitBlockEntity(in,machineBlock.defaultBlockState()));long outsideGas=machineGas(new AirSeparationUnitBlockEntity(out,machineBlock.defaultBlockState()));
  ok(insideGas>0,"air separator produces earth gas inside amount="+insideGas);equal(outsideGas,0,"air separator cannot extract outside vacuum");
  command("habitat temperature lab 25");equal(EnvironmentManager.of(player).temperature(solar),25,"temperature command applies immediately");command("habitat gravity lab 0.5");equal(player.getGravity(),.04,"gravity command applies immediately");
  command("habitat create overlap 5");ok(HabitatRuntime.serverSnapshot().named("overlap")==null,"overlap rejected");
  command("habitat disable lab");equal(player.getGravity(),0,"disable restores gravity");ok(!EnvironmentManager.of(player).breathable(),"disable restores vacuum");command("habitat enable lab");equal(player.getGravity(),.04,"enable restores sphere");
  command("habitat temperature lab 20");command("habitat gravity lab 1");command("habitat reload");equal(player.getGravity(),.08,"reload applies valid config");
  var snapshot=HabitatRuntime.serverSnapshot();var payload=new HabitatNetwork.SnapshotPayload(snapshot.revision(),snapshot.habitats(),HabitatRuntime.backgrounds());
  FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer());try{HabitatNetwork.SnapshotPayload.CODEC.encode(buf,payload);var decoded=HabitatNetwork.SnapshotPayload.CODEC.decode(buf);ok(new HabitatSnapshot(decoded.habitats()).revision().equals(snapshot.revision()),"network codec sphere roundtrip");ok(decoded.backgrounds().equals(payload.backgrounds()),"network codec background roundtrip");}finally{buf.release();}
  buf=new FriendlyByteBuf(Unpooled.buffer());try{buf.writeUtf(snapshot.revision(),64);buf.writeVarInt(513);try{HabitatNetwork.SnapshotPayload.CODEC.decode(buf);throw new IllegalStateException("oversized payload accepted");}catch(IllegalArgumentException expected){ok(true,"network oversized snapshot rejected");}}finally{buf.release();}
  position(130.5,65,100.5);player.setAirSupply(300);EnvironmentHazards.onPlayerTick(new PlayerTickEvent.Post(player));ok(player.getAirSupply()<300,"vacuum hazard restored on exit");
  var normal=world("minecraft:overworld");player.setServerLevel(normal);equal(player.getGravity(),.08,"overworld gravity unchanged");
  log("FUNCTIONAL_TESTS_PASSED checks="+checks+"; real client prediction/HUD and full modpack player acceptance pending");done=true;server.halt(false);
 }
 void shipTests()throws Exception {
  player.tickCount++;ok(HabitatRuntime.at(player)!=null,"physical ship position remains inside habitat");
  ok(com.interstellaris.mod.ship.ShipGravity.ship(player)!=null,"native ship floor detected");
  equal(player.getGravity(),0,"habitat defers world gravity to native ship gravity");
  ok(com.interstellaris.mod.ship.ShipGravity.travel(player,Vec3.ZERO,.91f).y<0,"native ship local downward gravity remains active");
  BlockPos plot=ship.getPlot().getCenterBlock();ok(HabitatRuntime.at(solar,plot.getCenter())!=null,"Sable plot coordinates project to physical habitat");
  ok(EnvironmentManager.of(player).breathable(),"ship inside sphere retains breathable air");
  log("SHIP_COMPATIBILITY_TESTS_PASSED checks="+checks);done=true;server.halt(false);
 }
 long machineGas(AirSeparationUnitBlockEntity be)throws Exception {
  be.setLevel(solar);var energyField=AirSeparationUnitBlockEntity.class.getDeclaredField("energy");energyField.setAccessible(true);
  Object energy=energyField.get(be);var receive=energy.getClass().getMethod("receiveEnergy",int.class,boolean.class);receive.setAccessible(true);receive.invoke(energy,200000,false);
  for(int i=0;i<40;i++)be.serverTick();var tanksField=AirSeparationUnitBlockEntity.class.getDeclaredField("tanks");tanksField.setAccessible(true);
  long total=0;for(var tank:(mekanism.api.chemical.IChemicalTank[])tanksField.get(be))total+=tank.getStored();return total;
 }
 void tick(ServerTickEvent.Post e){if(server==null||done)return;ticks++;try{if(ticks==40)run();if(ticks==60&&ship!=null)shipTests();if(ticks>300)throw new IllegalStateException("Timeout");}catch(Throwable t){done=true;log("TEST_FAILURE "+t);t.printStackTrace();server.halt(false);}}
}
