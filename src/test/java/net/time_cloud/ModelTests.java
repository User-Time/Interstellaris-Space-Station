package net.time_cloud;
import java.nio.file.*;
import java.util.*;
public final class ModelTests {
    static int checks;
    static void ok(boolean value,String name) { if(!value)throw new AssertionError(name);checks++; }
    static void reject(Runnable action,String name) { try {action.run();}catch(Exception expected){checks++;return;}throw new AssertionError(name); }
    public static void main(String[] args)throws Exception {
        Habitat a=new Habitat("alpha","interstellaris:solar_system",-100,64,200,10,20,1,true);
        HabitatSnapshot snapshot=new HabitatSnapshot(List.of(a));
        ok(snapshot.at(a.dimension(),-100,64,200)==a,"center");
        for(double[] d:new double[][]{{10,0,0},{-10,0,0},{0,10,0},{0,-10,0},{0,0,10},{0,0,-10}})
            ok(snapshot.at(a.dimension(),a.x()+d[0],a.y()+d[1],a.z()+d[2])==a,"sphere boundary");
        ok(snapshot.at(a.dimension(),-90,74,200)==null,"not a cube");
        ok(snapshot.at(a.dimension(),-89.999,64,200)==null,"outside");
        ok(snapshot.at("minecraft:overworld",-100,64,200)==null,"dimension isolation");
        ok(new HabitatSnapshot(List.of(a.enabled(false))).at(a.dimension(),-100,64,200)==null,"disabled");
        reject(()->new HabitatSnapshot(List.of(a,a)),"duplicate");
        reject(()->new HabitatSnapshot(List.of(a,new Habitat("beta",a.dimension(),-80,64,200,10,20,1,true))),"touching overlap");
        ok(new HabitatSnapshot(List.of(a,new Habitat("beta",a.dimension(),-80,64,200,10,20,1,false))).habitats().size()==2,"disabled overlap allowed");
        reject(()->a.radius(Double.NaN),"nan");ok(!a.radius(0).contains(a.x(),a.y(),a.z()),"zero disables radius");reject(()->a.gravity(-1),"negative gravity");
        reject(()->a.temperature(-274),"absolute zero");reject(()->a.move("BAD DIM",0,0,0),"invalid dimension");
        ok(a.floorDistance()==4,"default floor distance");
        var legacy=a.json();legacy.remove("floor_distance");ok(Habitat.parse(legacy).floorDistance()==4,"legacy config defaults to four");
        var custom=a.floorDistance(2);ok(Habitat.parse(custom.json()).equals(custom),"custom floor distance roundtrip");
        ok(custom.temperature(21).gravity(.5).radius(20).enabled(false).move(a.dimension(),0,0,0).floorDistance()==2,"edits preserve floor distance");
        reject(()->a.floorDistance(-1),"negative floor distance");reject(()->a.floorDistance(33),"excess floor distance");reject(()->a.floorDistance(Double.NaN),"nan floor distance");
        var json=a.json();json.addProperty("enabled","true");reject(()->Habitat.parse(json),"boolean string");
        Path dir=Files.createTempDirectory("habitat-model-");Path file=dir.resolve("habitats.json");
        var store=new HabitatStore(file);store.load();ok(store.snapshot().habitats().isEmpty(),"new store empty");
        store.put(a,false);String saved=Files.readString(file);
        var reload=new HabitatStore(file);reload.load();ok(reload.snapshot().revision().equals(snapshot.revision()),"persisted digest");
        store.put(a.temperature(25),true);ok(Files.readString(file.resolveSibling("habitats.json.previous")).equals(saved),"previous config");
        String active=store.snapshot().revision();Files.writeString(file,"broken");
        try {store.load();throw new AssertionError("invalid reload accepted");}catch(com.google.gson.JsonParseException|IllegalStateException expected){checks++;}
        ok(store.snapshot().revision().equals(active),"failed reload preserves active");
        Path blocked=dir.resolve("not-a-directory");Files.writeString(blocked,"x");var fail=new HabitatStore(blocked.resolve("cfg.json"));
        try {fail.put(a,false);throw new AssertionError("write should fail");}catch(java.io.IOException expected){checks++;}
        ok(fail.snapshot().habitats().isEmpty(),"failed write not published");
        var restricted=new HabitatStore(dir.resolve("restricted.json"),h->false);
        try {restricted.put(a,false);throw new AssertionError("dimension validator");}catch(IllegalArgumentException expected){checks++;}
        ok(!Files.exists(restricted.path()),"invalid dimension never written");
        store.delete("alpha");ok(store.snapshot().habitats().isEmpty(),"delete");
        ok(a.gravityMode()==GravityMode.ADHESION&&a.gravityRadius()==a.radius(),"legacy gravity defaults");
        var old=a.json();old.remove("gravity_radius");old.remove("gravity_mode");ok(Habitat.parse(old).equals(a),"legacy JSON compatibility");
        CoreInfo core=new CoreInfo(UUID.randomUUID(),UUID.randomUUID(),"Owner","空间站甲",false,100,64,100);
        Habitat station=new Habitat(core.key(),a.dimension(),100.5,64.5,100.5,16,20,1,true,4,32,GravityMode.FULL,core);
        ok(Habitat.parse(station.json()).equals(station),"core persistence roundtrip");
        var fields=new HabitatSnapshot(List.of(station));ok(fields.at(a.dimension(),125,64.5,100.5)==null&&fields.gravityAt(a.dimension(),125,64.5,100.5)==station,"independent radii");
        ok(StationDirectory.visible(fields,core.owner()).size()==1,"owner sees private marker");
        ok(StationDirectory.visible(fields,UUID.randomUUID()).isEmpty(),"outsider private directory omitted");
        var settings=new StationSettings("公共空间站",true,true,10,30,GravityMode.NONE,.5,2,-37.5);
        Habitat edited=settings.apply(station);
        ok(edited.core().owner().equals(core.owner())&&edited.core().stationId().equals(core.stationId())&&edited.x()==station.x(),"settings cannot change owner identity or anchor");
        ok(StationDirectory.visible(new HabitatSnapshot(List.of(edited)),UUID.randomUUID()).size()==1,"public marker visible");
        ok(StationSettings.parse(settings.json()).equals(settings),"arbitrary temperature settings roundtrip");
        var forged=settings.json();forged.addProperty("owner",UUID.randomUUID().toString());forged.addProperty("x",99999);ok(StationSettings.parse(forged).apply(station).equals(edited),"unknown identity inputs cannot alter core");
        for(double temp:new double[]{-100,-77.125,0,20,119.99,120})ok(settings.apply(station).temperature(temp).temperatureC()==temp,"allowed core temperature "+temp);
        reject(()->edited.temperature(-100.001),"core minimum temperature");reject(()->edited.temperature(120.001),"core maximum temperature");
        reject(()->new StationSettings("a",false,true,1,1,GravityMode.FULL,Double.NaN,4,20),"nan settings");
        reject(()->new StationSettings("a",false,true,1,1,GravityMode.FULL,1,4,121),"settings temperature cap");
        reject(()->station.move(a.dimension(),200,64,100),"core anchor fixed");
        var renamed=StationSettings.from(station).json();renamed.addProperty("name","更名");renamed.addProperty("public",true);
        var renamedSnapshot=new HabitatSnapshot(List.of(StationSettings.parse(renamed).apply(station)));
        ok(renamedSnapshot.revision().equals(fields.revision()),"private metadata excluded from physics digest");
        ok(!fields.physicsJson().toString().contains("owner"),"physics payload has no owner metadata");
        var coreStore=new HabitatStore(dir.resolve("core.json"));coreStore.put(station,false);var coreReload=new HabitatStore(coreStore.path());coreReload.load();
        ok(coreReload.snapshot().named(core.key()).equals(station),"core store survives reload");
        String valid=Files.readString(coreStore.path());ok(valid.contains("\"schema_version\": 2"),"new schema prevents silent old-version downgrade");
        coreStore.lockWrites("bad config");try{coreStore.delete(core.key());throw new AssertionError("locked write accepted");}catch(java.io.IOException expected){checks++;}
        ok(Files.readString(coreStore.path()).equals(valid),"write lock preserves disk");coreStore.load();ok(coreStore.writable(),"valid reload clears write lock");
        var legacyFile=dir.resolve("legacy.json");var legacyDoc=snapshot.json();legacyDoc.addProperty("schema_version",1);Files.writeString(legacyFile,legacyDoc.toString());
        var legacyStore=new HabitatStore(legacyFile);legacyStore.load();ok(legacyStore.snapshot().named("alpha").equals(a),"schema one is readable");
        System.out.println("MODEL_TESTS_PASSED checks="+checks);
    }
}
