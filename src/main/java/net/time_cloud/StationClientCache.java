package net.time_cloud;
import java.util.*;
public final class StationClientCache {
    private static volatile List<StationMarker> markers=List.of();
    private StationClientCache(){}
    public static List<StationMarker> markers(){return markers;}
    public static void accept(List<StationMarker> next){markers=List.copyOf(next);}
    public static void clear(){markers=List.of();}
}
