package incident;

import java.nio.file.*;
import java.util.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.core.*;
import net.minecraft.resources.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.Vec3;
import com.mojang.authlib.GameProfile;
import com.interstellaris.mod.environment.EnvironmentManager;
import net.time_cloud.*;

@Mod("potg_floor_probe")
public class FloorProbe {
 MinecraftServer server;ServerLevel level;ServerPlayer player;int ticks,checks;boolean done;
 final BlockPos floor=new BlockPos(100,64,100);
 public FloorProbe(){if(!Boolean.getBoolean("habitat.probe.floor"))return;NeoForge.EVENT_BUS.addListener(this::start);NeoForge.EVENT_BUS.addListener(this::tick);}
 void start(ServerStartedEvent event){server=event.getServer();}
 void log(String s){System.out.println("POTG_FLOOR "+s);try{Files.writeString(Path.of("floor-results.txt"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
 void ok(boolean b,String name){if(!b)throw new AssertionError(name);checks++;log("PASS "+name);}
 void gravity(double expected,String name){double actual=player.getGravity();ok(Math.abs(actual-expected)<1e-7,name+" actual="+actual);}
 void pos(double x,double y,double z){player.moveTo(x,y,z,0,0);player.tickCount++;}
 void block(BlockState state){level.setBlockAndUpdate(floor,state);}
 void command(String cmd){server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withPosition(new Vec3(100.5,65,100.5)),cmd);}
 void run()throws Exception {
  level=server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse("interstellaris:solar_system")));
  var profile=new GameProfile(UUID.randomUUID(),"FloorProbe");player=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
  player.connection=new ServerGamePacketListenerImpl(server,new HabitatProbe.MemoryConnection(),player,CommonListenerCookie.createInitial(profile,false));player.setGameMode(GameType.SURVIVAL);
  command("habitat create floor_lab 20");ok(HabitatRuntime.serverSnapshot().named("floor_lab")!=null,"create sphere");pos(100.5,65,100.5);
  gravity(0,"empty space inside sphere remains weightless");var env=EnvironmentManager.of(player);ok(env.breathable()&&env.pressure()==1&&env.temperature(level)==20,"air temperature pressure independent of floor");
  block(Blocks.STONE.defaultBlockState());gravity(.08,"standing on full block");
  pos(100.5,66.25,100.5);gravity(.08,"normal jump apex above floor");
  pos(100.5,69,100.5);gravity(.08,"floor exactly four blocks below feet");
  pos(100.5,69.001,100.5);gravity(0,"floor beyond four blocks");
  pos(101.31,65,100.5);gravity(0,"walk beyond platform footprint");
  pos(101.1,65,100.5);gravity(.08,"partial footprint supported at edge");
  pos(100.5,65,100.5);block(Blocks.AIR.defaultBlockState());gravity(0,"removed floor takes effect immediately");
  level.setBlockAndUpdate(floor.above(2),Blocks.STONE.defaultBlockState());gravity(0,"ceiling does not count");level.setBlockAndUpdate(floor.above(2),Blocks.AIR.defaultBlockState());
  level.setBlockAndUpdate(floor.east(),Blocks.STONE.defaultBlockState());gravity(0,"adjacent wall does not count");level.setBlockAndUpdate(floor.east(),Blocks.AIR.defaultBlockState());
  block(Blocks.SHORT_GRASS.defaultBlockState());gravity(0,"non-colliding plant does not count");
  block(Blocks.WATER.defaultBlockState());gravity(0,"water alone does not count");
  block(Blocks.GLASS.defaultBlockState());gravity(.08,"transparent solid block counts");
  block(Blocks.OAK_SLAB.defaultBlockState());pos(100.5,64.5,100.5);gravity(.08,"bottom slab top surface");
  pos(100.5,68.5,100.5);gravity(.08,"slab four-block boundary uses real shape");pos(100.5,68.501,100.5);gravity(0,"slab beyond range uses real shape");
  block(Blocks.OAK_TRAPDOOR.defaultBlockState());pos(100.5,64.1875,100.5);gravity(.08,"closed trapdoor collision surface");
  block(Blocks.OAK_FENCE.defaultBlockState());pos(100.5,65.5,100.5);gravity(.08,"fence extends above block cell");
  block(Blocks.STONE.defaultBlockState());pos(100.5,66.25,100.5);command("habitat floor-distance floor_lab 1");gravity(0,"shorter configured floor distance takes effect");
  command("habitat floor-distance floor_lab 4");gravity(.08,"restored default floor distance");
  command("habitat temperature floor_lab 22");ok(HabitatRuntime.serverSnapshot().named("floor_lab").floorDistance()==4,"other edits preserve floor range");
  command("habitat reload");gravity(.08,"floor setting persists through reload");
  command("habitat disable floor_lab");gravity(0,"disabled sphere restores space gravity");command("habitat enable floor_lab");gravity(.08,"enabled sphere with floor");
  pos(140.5,65,100.5);level.setBlockAndUpdate(new BlockPos(140,64,100),Blocks.STONE.defaultBlockState());gravity(0,"floor outside sphere cannot enable gravity");
  pos(32000.5,100,32000.5);ok(level.getChunkSource().getChunkNow(2000,2000)==null,"remote chunk initially unloaded");
  ok(!FloorSupport.hasFloor(player,4),"unloaded space has no support");ok(level.getChunkSource().getChunkNow(2000,2000)==null,"floor detection did not load chunk");
  log("FLOOR_TESTS_PASSED checks="+checks+"; synthetic server player, real client movement pending");done=true;server.halt(false);
 }
 void tick(ServerTickEvent.Post e){if(server==null||done)return;ticks++;try{if(ticks==40)run();}catch(Throwable t){done=true;log("TEST_FAILURE "+t);t.printStackTrace();server.halt(false);}}
}
