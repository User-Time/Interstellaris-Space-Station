package net.time_cloud.navigation;

import com.google.gson.*;
import java.util.*;

/** Durable per-player discovery only; no native research, island or ship data is changed. */
public final class VisitLedger {
    private final Map<UUID,Set<String>> visits=new HashMap<>();
    private int count;
    public static boolean validId(String id){return id!=null&&id.matches("core_[0-9a-f]{32}");}
    public boolean contains(UUID player,String station){return visits.getOrDefault(player,Set.of()).contains(station);}
    public boolean add(UUID player,String station){
        Objects.requireNonNull(player);if(!validId(station))throw new IllegalArgumentException("invalid station identity");
        if(contains(player,station))return false;
        if(count>=100000||visits.getOrDefault(player,Set.of()).size()>=512)throw new IllegalStateException("空间站访问记录超过上限");
        visits.computeIfAbsent(player,k->new HashSet<>()).add(station);count++;return true;
    }
    public Set<String> forPlayer(UUID player){return Set.copyOf(visits.getOrDefault(player,Set.of()));}
    public JsonObject json(){
        JsonObject root=new JsonObject();root.addProperty("schema",1);JsonArray players=new JsonArray();
        visits.keySet().stream().sorted().forEach(id->{JsonObject row=new JsonObject();row.addProperty("player",id.toString());JsonArray list=new JsonArray();visits.get(id).stream().sorted().forEach(list::add);row.add("stations",list);players.add(row);});
        root.add("players",players);return root;
    }
    public static VisitLedger parse(JsonObject root){
        if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("invalid discovery schema");
        VisitLedger result=new VisitLedger();Set<UUID> players=new HashSet<>();JsonArray rows=root.getAsJsonArray("players");
        if(rows.size()>100000)throw new IllegalArgumentException("too many discovery players");
        for(JsonElement element:rows){JsonObject row=element.getAsJsonObject();UUID player=UUID.fromString(row.get("player").getAsString());if(!players.add(player))throw new IllegalArgumentException("duplicate discovery player");
            for(JsonElement id:row.getAsJsonArray("stations"))if(!result.add(player,id.getAsString()))throw new IllegalArgumentException("duplicate discovery station");
        }return result;
    }
}
