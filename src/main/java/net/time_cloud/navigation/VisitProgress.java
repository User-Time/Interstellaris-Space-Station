package net.time_cloud.navigation;
import java.util.*;
/** A brief fly-by is not a visit; this transient dwell state is never persisted. */
public final class VisitProgress {
    private record Pending(String id,long since){}
    private final Map<UUID,Pending> pending=new HashMap<>();
    public boolean observe(UUID player,String id,long tick){
        if(id==null){pending.remove(player);return false;}
        Pending before=pending.get(player);
        if(before==null||!before.id().equals(id)||tick<before.since()){pending.put(player,new Pending(id,tick));return false;}
        return tick-before.since()>=40;
    }
    public void remove(UUID player){pending.remove(player);}
    public void clear(){pending.clear();}
}
