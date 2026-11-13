package net.time_cloud.navigation;

import com.interstellaris.mod.space.CelestialBody;
import com.mojang.serialization.Lifecycle;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.time_cloud.*;

/** A private, ephemeral holder is sufficient for the original Navigator. Never mutates a world registry. */
public final class StationDestinations {
    private static final String PREFIX="station/";
    private record Key(String id,String name,String dim,double x,double y,double z,double radius){}
    private static final Map<Key,Holder.Reference<CelestialBody>> CACHE=new LinkedHashMap<>(32,.75f,true){protected boolean removeEldestEntry(Map.Entry<Key,Holder.Reference<CelestialBody>> e){return size()>1024;}};
    private StationDestinations(){}
    public static boolean ours(ResourceLocation id){return id!=null&&id.getNamespace().equals(HabitatRuntime.ID)&&id.getPath().startsWith(PREFIX);}
    public static ResourceLocation target(String id){if(!VisitLedger.validId(id))throw new IllegalArgumentException("invalid station identity");return ResourceLocation.fromNamespaceAndPath(HabitatRuntime.ID,PREFIX+id.substring(5));}
    public static String station(ResourceLocation id){if(!ours(id))return null;String key="core_"+id.getPath().substring(PREFIX.length());return VisitLedger.validId(key)?key:null;}
    public static Habitat server(ResourceLocation id){String key=station(id);return key==null?null:HabitatRuntime.serverSnapshot().named(key);}
    public static String issue(ServerLevel level,ResourceLocation id,UUID player){
        Habitat h=server(id);
        if(h==null||!h.enabled()||!StationDirectory.canView(h,player))return "station_unavailable";
        return StationVisits.get(level.getServer()).contains(player,h.id())?null:"station_locked";
    }
    public static Holder.Reference<CelestialBody> resolve(ResourceLocation id){Habitat h=server(id);return h!=null&&h.core()!=null&&h.enabled()?holder(StationMarker.of(h)):null;}
    public static synchronized Holder.Reference<CelestialBody> holder(StationMarker station){
        Key spec=new Key(station.id(),station.name(),station.dimension(),station.x(),station.y(),station.z(),Math.max(8,Math.max(station.oxygenRadius(),station.gravityRadius())));
        return CACHE.computeIfAbsent(spec,k->{
            var system=ResourceKey.<Level>create(Registries.DIMENSION,ResourceLocation.parse(k.dim()));
            var body=new CelestialBody(Optional.empty(),system,new Vec3(k.x(),k.y(),k.z()),k.radius(),0,false);
            ((StationBody)(Object)body).potg$station(k.id(),k.name());
            var registry=new MappedRegistry<CelestialBody>(CelestialBody.REGISTRY,Lifecycle.stable());
            var holder=registry.register(ResourceKey.create(CelestialBody.REGISTRY,target(k.id())),body,RegistrationInfo.BUILT_IN);
            registry.freeze();return holder;
        });
    }
    public static synchronized void reset(){CACHE.clear();}
}
