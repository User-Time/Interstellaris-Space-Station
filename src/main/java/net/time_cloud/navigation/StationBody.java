package net.time_cloud.navigation;
/** Tags only our ephemeral navigation targets; real celestial registry values stay unmarked. */
public interface StationBody {
    void potg$station(String id,String name);
    String potg$stationId();
    String potg$stationName();
}
