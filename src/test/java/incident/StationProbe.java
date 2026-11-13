package incident;

import java.nio.file.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import com.interstellaris.mod.environment.EnvironmentManager;
import com.interstellaris.mod.ship.ShipGravity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.*;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraft.tags.TagKey;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.time_cloud.*;

@Mod("potg_station_probe")
public final class StationProbe {
 MinecraftServer server;ServerLevel level;ServerPlayer owner,other;int ticks,checks;boolean done;ServerSubLevel ship;
 final BlockPos anchor=new BlockPos(100,64,100);String id;StationCoreBlockEntity core;
 final UUID ownerId=UUID.fromString("11111111-1111-4111-8111-111111111111");
 public StationProbe(){if(!Boolean.getBoolean("habitat.probe.station"))return;NeoForge.EVENT_BUS.addListener(this::start);NeoForge.EVENT_BUS.addListener(this::tick);}
 void start(ServerStartedEvent e){server=e.getServer();}
 void log(String s){System.out.println("POTG_STATION "+s);try{Files.writeString(Path.of("station-results.txt"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
 void ok(boolean b,String s){if(!b)throw new AssertionError(s);checks++;log("PASS "+s);}
 void eq(double a,double b,String s){ok(Math.abs(a-b)<1e-6,s+" actual="+a);}
 ServerPlayer player(UUID uuid,String name){var profile=new GameProfile(uuid,name);var p=new ServerPlayer(server,level,profile,ClientInformation.createDefault());p.connection=new ServerGamePacketListenerImpl(server,new HabitatProbe.MemoryConnection(),p,CommonListenerCookie.createInitial(profile,false)){@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}};p.setGameMode(GameType.SURVIVAL);return p;}
 Habitat current(){return HabitatRuntime.serverSnapshot().named(id);}
 void pos(ServerPlayer player,double x,double y,double z){player.moveTo(x,y,z,0,0);player.tickCount++;}
 Habitat settings(double air,double gr,GravityMode mode,double strength,double distance,double temp,boolean pub,boolean active){return new StationSettings("轨道站",pub,active,air,gr,mode,strength,distance,temp).apply(current());}
 void direct(Habitat h)throws Exception{HabitatRuntime.store().put(h,true);core.accept(h);HabitatRuntime.clearEnvironmentCache();}
 boolean request(ServerPlayer p,Habitat h){long token=StationNetwork.open(p,current());return StationNetwork.apply(p,new StationNetwork.UpdatePayload(token,id,StationRuntime.etag(current()),StationSettings.from(h).json().toString()));}
 StationCoreBlockEntity place(BlockPos pos,ServerPlayer player){level.setBlockAndUpdate(pos,StationContent.CORE.get().defaultBlockState());var be=(StationCoreBlockEntity)level.getBlockEntity(pos);StationContent.CORE.get().setPlacedBy(level,pos,be.getBlockState(),player,new ItemStack(StationContent.CORE_ITEM.get()));return be;}
 void setup(){
  level=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse("interstellaris:solar_system")));ok(level!=null,"space dimension available");
  owner=player(ownerId,"StationOwner");other=player(UUID.fromString("22222222-2222-4222-8222-222222222222"),"StationOther");pos(owner,100.5,65,100.5);pos(other,100.5,65,101.5);
 }
 void run()throws Exception{
  setup();
  if(System.getProperty("habitat.probe.phase","1").equals("2")){persistence();return;}
  ok(HabitatRuntime.serverSnapshot().habitats().isEmpty(),"independent blank registry baseline");
  // Actual registered block and ordinary placement callback, not a production world.
  core=place(anchor,owner);id=core.key();ok(id!=null&&current()!=null,"placement registered station");
  ok(current().core().owner().equals(ownerId),"owner recorded from actual placer");ok(!current().core().publicStation(),"new station private by default");
  ok(StationDirectory.visible(HabitatRuntime.serverSnapshot(),ownerId).size()==1,"owner receives private marker");ok(StationDirectory.visible(HabitatRuntime.serverSnapshot(),other.getUUID()).isEmpty(),"other player private marker omitted");
  ok(StationRuntime.canManage(owner,current())&&!StationRuntime.canManage(other,current()),"owner-only management");
  var denied=new BlockEvent.BreakEvent(level,anchor,core.getBlockState(),other);NeoForge.EVENT_BUS.post(denied);ok(denied.isCanceled(),"foreign player cannot break core");
  var allowed=new BlockEvent.BreakEvent(level,anchor,core.getBlockState(),owner);NeoForge.EVENT_BUS.post(allowed);ok(!allowed.isCanceled(),"owner break allowed");
  var tag=TagKey.create(Registries.BLOCK,ResourceLocation.parse("interstellaris:ship_assembly_excluded"));ok(core.getBlockState().is(tag),"fixed core excluded from ship assembly");
  ok(server.getRecipeManager().byKey(ResourceLocation.parse("potg_station_habitat:station_core")).isPresent(),"survival crafting recipe loaded");
  ok(StationContent.CORE.get().getStateForPlacement(new BlockPlaceContext(owner,InteractionHand.MAIN_HAND,new ItemStack(StationContent.CORE_ITEM.get()),new BlockHitResult(new Vec3(110,65,110),Direction.UP,new BlockPos(110,64,110),false)))!=null,"normal space core placement permitted");
  var normal=server.overworld();owner.setServerLevel(normal);
  ok(StationContent.CORE.get().getStateForPlacement(new BlockPlaceContext(owner,InteractionHand.MAIN_HAND,new ItemStack(StationContent.CORE_ITEM.get()),new BlockHitResult(new Vec3(110,65,110),Direction.UP,new BlockPos(110,64,110),false)))==null,"ordinary overworld placement rejected");owner.setServerLevel(level);
  ok(request(owner,settings(16,32,GravityMode.FULL,.5,4,-37.5,true,true)),"actual packet mutation path accepts owner settings");
  eq(current().temperatureC(),-37.5,"arbitrary fractional temperature saved");ok(current().gravityMode()==GravityMode.FULL&&current().radius()==16&&current().gravityRadius()==32,"independent fields saved");
  ok(StationDirectory.visible(HabitatRuntime.serverSnapshot(),other.getUUID()).size()==1,"public marker visible to other");
  eq(EnvironmentManager.of(owner).temperature(level),-37.5,"native environment receives fractional temperature");ok(EnvironmentManager.of(other).breathable(),"private/public does not restrict environment entry");
  var before=current();long token=StationNetwork.open(other,before);ok(!StationNetwork.apply(other,new StationNetwork.UpdatePayload(token,id,StationRuntime.etag(before),StationSettings.from(before).json().toString())),"foreign player edit rejected even with an issued session");ok(current().equals(before),"foreign edit leaves settings unchanged");
  token=StationNetwork.open(owner,before);ok(!StationNetwork.apply(owner,new StationNetwork.UpdatePayload(token+123,id,StationRuntime.etag(before),StationSettings.from(before).json().toString())),"forged session rejected");
  token=StationNetwork.open(owner,before);ok(!StationNetwork.apply(owner,new StationNetwork.UpdatePayload(token,id,"stale",StationSettings.from(before).json().toString())),"stale revision rejected");
  token=StationNetwork.open(owner,before);var bad=StationSettings.from(before).json();bad.addProperty("temperature_c",121);ok(!StationNetwork.apply(owner,new StationNetwork.UpdatePayload(token,id,StationRuntime.etag(before),bad.toString())),"out-of-range packet rejected");
  ok(current().equals(before),"rejected packet does not mutate durable snapshot");
  token=StationNetwork.open(owner,before);var forged=StationSettings.from(before).json();forged.addProperty("owner",other.getUUID().toString());forged.addProperty("x",-99999);ok(StationNetwork.apply(owner,new StationNetwork.UpdatePayload(token,id,StationRuntime.etag(before),forged.toString())),"unknown identity fields ignored");ok(current().core().owner().equals(ownerId)&&current().x()==100.5,"packet cannot change ownership or location");
  token=StationNetwork.open(owner,current());ok(StationNetwork.apply(owner,new StationNetwork.UpdatePayload(token,id,StationRuntime.etag(current()),StationSettings.from(current()).json().toString())),"first request accepted");ok(!StationNetwork.apply(owner,new StationNetwork.UpdatePayload(token,id,StationRuntime.etag(current()),StationSettings.from(current()).json().toString())),"same-tick repeated request rate limited");
  pos(owner,140.5,65,100.5);ok(!request(owner,current()),"remote core settings rejected");pos(owner,100.5,65,100.5);
  owner.setServerLevel(normal);ok(!request(owner,current()),"cross-dimension edit rejected");owner.setServerLevel(level);
  direct(settings(16,32,GravityMode.FULL,.5,4,20,true,true));pos(owner,125.5,65,100.5);
  eq(owner.getGravity(),.04,"gravity operates beyond oxygen radius");ok(!EnvironmentManager.of(owner).breathable(),"oxygen stays outside smaller radius");
  pos(owner,100.5,65,100.5);eq(owner.getGravity(),.04,"full gravity works without floor");
  level.setBlockAndUpdate(anchor.below(),Blocks.STONE.defaultBlockState());direct(settings(16,32,GravityMode.NONE,1,4,20,true,true));eq(owner.getGravity(),0,"no-gravity mode overrides supporting floor");
  direct(settings(16,32,GravityMode.ADHESION,1,4,20,true,true));pos(owner,102.5,65,100.5);eq(owner.getGravity(),0,"adhesion without floor remains weightless");
  var floor=new BlockPos(102,64,100);level.setBlockAndUpdate(floor,Blocks.STONE.defaultBlockState());eq(owner.getGravity(),.08,"adhesion sees loaded floor");pos(owner,102.5,69,100.5);eq(owner.getGravity(),.08,"four-block adhesion boundary");pos(owner,102.5,69.01,100.5);eq(owner.getGravity(),0,"beyond adhesion distance weightless");
  pos(owner,100.5,65,100.5);direct(settings(16,32,GravityMode.FULL,1,4,-100,true,true));eq(EnvironmentManager.of(owner).temperature(level),-100,"minimum temperature runtime");direct(settings(16,32,GravityMode.FULL,1,4,120,true,true));eq(EnvironmentManager.of(owner).temperature(level),120,"maximum temperature runtime");
  direct(settings(0,32,GravityMode.FULL,1,4,20,true,true));eq(owner.getGravity(),.08,"gravity independent of disabled air");ok(!EnvironmentManager.of(owner).breathable(),"zero air radius restores vacuum");
  direct(settings(16,0,GravityMode.FULL,1,4,20,true,true));eq(owner.getGravity(),0,"zero gravity radius restores native space gravity");ok(EnvironmentManager.of(owner).breathable(),"air independent of disabled gravity field");
  direct(settings(16,32,GravityMode.FULL,1,4,20,true,false));eq(owner.getGravity(),0,"disabled core restores gravity");ok(!EnvironmentManager.of(owner).breathable(),"disabled core restores vacuum");
  direct(settings(16,32,GravityMode.FULL,1,4,20,false,true));
  var overlap=place(anchor.offset(4,0,0),other);var extra=HabitatRuntime.serverSnapshot().named(overlap.key());ok(!extra.enabled(),"overlapping placement is disabled instead of changing active environment");
  level.setBlockAndUpdate(overlap.getBlockPos(),Blocks.AIR.defaultBlockState());ok(HabitatRuntime.serverSnapshot().named(extra.id())==null,"core break removes only its field");ok(current()!=null,"other core remains intact");
  // Core backup restores only this anchor; a copied identity at a different block cannot register.
  HabitatRuntime.store().delete(id);StationRuntime.reconcile(core);ok(current()!=null,"matching block-entity backup can restore missing registry entry");
  var duplicate=place(anchor.offset(80,0,0),other);String duplicateId=duplicate.key();level.setBlockAndUpdate(duplicate.getBlockPos(),Blocks.AIR.defaultBlockState());
  ok(HabitatRuntime.serverSnapshot().named(duplicateId)==null,"second independent core removed cleanly");
  var copyPos=anchor.offset(80,0,0);level.setBlockAndUpdate(copyPos,StationContent.CORE.get().defaultBlockState());var copied=(StationCoreBlockEntity)level.getBlockEntity(copyPos);copied.accept(current());StationRuntime.reconcile(copied);ok(!StationRuntime.matches(current(),copied),"copied core identity rejected at different anchor");level.setBlockAndUpdate(copyPos,Blocks.AIR.defaultBlockState());ok(current()!=null,"removing duplicated core cannot unregister original");
  var snapshot=HabitatRuntime.serverSnapshot();var payload=new HabitatNetwork.SnapshotPayload(snapshot.revision(),snapshot.habitats(),HabitatRuntime.backgrounds());
  var buf=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{HabitatNetwork.SnapshotPayload.CODEC.encode(buf,payload);var decoded=HabitatNetwork.SnapshotPayload.CODEC.decode(buf);ok(decoded.habitats().stream().allMatch(h->h.core()==null),"physics codec strips owner metadata");ok(new HabitatSnapshot(decoded.habitats()).revision().equals(snapshot.revision()),"expanded physics codec digest roundtrip");}finally{buf.release();}
  buf=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{var directory=new StationNetwork.DirectoryPayload(StationDirectory.visible(snapshot,ownerId));StationNetwork.DirectoryPayload.CODEC.encode(buf,directory);ok(StationNetwork.DirectoryPayload.CODEC.decode(buf).equals(directory),"directory codec roundtrip");}finally{buf.release();}
  direct(settings(16,32,GravityMode.ADHESION,1,4,-37.5,false,true));Files.writeString(Path.of("expected-station-id.txt"),id);
  var nbt=core.saveWithFullMetadata(level.registryAccess());ok(nbt.hasUUID("StationId")&&nbt.getString("StationBackup").contains(id),"block entity stores recovery identity and settings");
  log("CORE_TESTS_PASSED checks="+checks+"; synthetic ServerPlayer, actual GUI and M rendering pending");done=true;server.halt(false);
 }
 void persistence()throws Exception{
  id=Files.readString(Path.of("expected-station-id.txt"));core=(StationCoreBlockEntity)level.getBlockEntity(anchor);ok(core!=null&&core.key().equals(id),"block entity identity persists across normal shutdown");
  ok(current()!=null&&current().core().owner().equals(ownerId),"registry ownership persists across restart");ok(current().equals(core.backup()),"block entity backup matches durable registry");eq(current().temperatureC(),-37.5,"fractional temperature persists");ok(!current().core().publicStation(),"private visibility persists");
  // Station remains effective without any per-station force-load ticket.
  eq(EnvironmentManager.of(owner).temperature(level),-37.5,"restored station environment active");
  var root=new BlockPos(102,63,100);var blocks=new ArrayList<BlockPos>();for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){var b=root.offset(x,0,z);level.setBlockAndUpdate(b,Blocks.STONE.defaultBlockState());blocks.add(b);}
  ship=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level,root,blocks,new dev.ryanhcode.sable.companion.math.BoundingBox3i(root.offset(-3,-1,-3),root.offset(3,2,3)));
  dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).addForceLoadTicket(ship,dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,net.minecraft.util.Unit.INSTANCE);
  var feet=ship.logicalPose().transformPosition(Vec3.atCenterOf(ship.getPlot().getCenterBlock()).add(0,.5,0));pos(owner,feet.x,feet.y,feet.z);log("SHIP_SETUP feet="+feet);
 }
 void shipTests()throws Exception{
  owner.tickCount++;ok(ShipGravity.ship(owner)!=null,"native ship floor detected");ok(HabitatRuntime.gravityAt(owner)!=null,"ship physical position inside station gravity field");
  ok(FloorSupport.hasShipFloor(owner,ship,4),"ship-local collision floor detected without world chunk request");
  direct(settings(16,32,GravityMode.NONE,1,4,20,false,true));var motion=ShipGravity.travel(owner,Vec3.ZERO,.91f);eq(motion.length(),0,"station NONE suppresses native ship acceleration");
  direct(settings(16,32,GravityMode.FULL,.5,4,20,false,true));motion=ShipGravity.travel(owner,Vec3.ZERO,.91f);eq(motion.length(),.04*.98,"station FULL multiplier applied on ship");
  direct(settings(16,32,GravityMode.ADHESION,1,4,-37.5,false,true));motion=ShipGravity.travel(owner,Vec3.ZERO,.91f);eq(motion.length(),.08*.98,"ship adhesion floor preserves normal acceleration");eq(owner.getGravity(),0,"no double world gravity while aboard ship");
  dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).removeForceLoadTicket(ship,dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,net.minecraft.util.Unit.INSTANCE);
  log("CORE_PERSISTENCE_SHIP_TESTS_PASSED checks="+checks+"; real client aboard rotating ships pending");done=true;server.halt(false);
 }
 void tick(ServerTickEvent.Post event){if(server==null||done)return;ticks++;try{if(ticks==40)run();if(ticks==60&&ship!=null)shipTests();if(ticks>400)throw new IllegalStateException("timeout");}catch(Throwable failure){done=true;log("TEST_FAILURE "+failure);failure.printStackTrace();server.halt(false);}}
}
