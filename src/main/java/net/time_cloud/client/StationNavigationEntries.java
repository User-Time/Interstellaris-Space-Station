package net.time_cloud.client;

import com.interstellaris.mod.space.CelestialBody;
import java.lang.reflect.Constructor;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.resources.*;
import net.minecraft.world.level.Level;
import org.joml.Vector3dc;
import net.time_cloud.*;
import net.time_cloud.navigation.*;

/** A small adapter for Interstellaris 0.6.3a's private UI entry record; no new UI or controls. */
public final class StationNavigationEntries {
    private static final Constructor<?> ENTRY=entryConstructor();
    private static Constructor<?> entryConstructor(){
        try{
            Constructor<?> c=Class.forName("com.interstellaris.mod.client.ShipInformationCenterScreen$Entry").getDeclaredConstructor(Holder.Reference.class,ResourceLocation.class,String.class,boolean.class,double.class);
            c.setAccessible(true);return c;
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Unsupported Interstellaris SIC entry layout",e);}
    }
    private StationNavigationEntries(){}
    public static List<?> append(List<?> original,ResourceKey<Level> system,ResourceKey<Level> current,Vector3dc ship){
        List<Object> result=new ArrayList<>(original);
        StationClientCache.markers().stream().filter(m->m.dimension().equals(system.location().toString())).sorted(Comparator.comparing(StationMarker::name).thenComparing(StationMarker::id)).forEach(m->{
            var holder=StationDestinations.holder(m);
            double km=system.equals(current)?CelestialBody.toKm(Math.max(0,ship.distance(m.x(),m.y(),m.z())-holder.value().radius())):-2;
            try{result.add(ENTRY.newInstance(holder,StationDestinations.target(m.id()),"空间站 · "+m.name(),m.enabled()&&StationUnlockNetwork.visited(m.id()),km));}
            catch(ReflectiveOperationException e){throw new IllegalStateException("Unable to build native SIC station entry",e);}
        });return result;
    }
}
