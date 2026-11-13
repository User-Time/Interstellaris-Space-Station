package net.time_cloud;

import java.util.*;

public final class DomeVisibility {
    private DomeVisibility() {}
    public static double surfaceDistance(Habitat habitat,double x,double y,double z) {
        double dx=x-habitat.x(),dy=y-habitat.y(),dz=z-habitat.z();
        return Math.max(0,Math.sqrt(dx*dx+dy*dy+dz*dz)-habitat.radius());
    }
    public static List<Habitat> select(HabitatSnapshot snapshot,String dimension,double x,double y,double z,double distance,int limit) {
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Double.isFinite(distance)||distance<0||limit<1)return List.of();
        return snapshot.habitats().stream().filter(h->h.enabled()&&h.dimension().equals(dimension))
                .filter(h->surfaceDistance(h,x,y,z)<=distance)
                .sorted(Comparator.comparingDouble((Habitat h)->surfaceDistance(h,x,y,z)).thenComparing(Habitat::id))
                .limit(Math.min(8,limit)).toList();
    }
}
