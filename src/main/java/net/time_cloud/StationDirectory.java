package net.time_cloud;
import java.util.*;
public final class StationDirectory {
    private StationDirectory(){}
    public static boolean canView(Habitat h,UUID player){
        return h!=null&&h.core()!=null&&player!=null&&(h.core().publicStation()||h.core().owner().equals(player));
    }
    public static List<StationMarker> visible(HabitatSnapshot snapshot,UUID player){
        return snapshot.habitats().stream().filter(h->canView(h,player))
                .map(StationMarker::of).sorted(Comparator.comparing(StationMarker::name).thenComparing(StationMarker::id)).toList();
    }
}
