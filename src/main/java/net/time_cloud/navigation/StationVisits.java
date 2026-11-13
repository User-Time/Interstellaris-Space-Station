package net.time_cloud.navigation;

import com.google.gson.JsonParser;
import java.util.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.time_cloud.HabitatRuntime;

public final class StationVisits extends SavedData {
    public static final String KEY="potg_station_visits";
    public static final Factory<StationVisits> FACTORY=new Factory<>(StationVisits::new,StationVisits::load,null);
    private VisitLedger ledger=new VisitLedger();
    private boolean locked;
    public static StationVisits get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(FACTORY,KEY);}
    public boolean contains(UUID player,String id){return ledger.contains(player,id);}
    public Set<String> forPlayer(UUID player){return ledger.forPlayer(player);}
    public boolean add(UUID player,String id){if(locked)throw new IllegalStateException("访问记录损坏，已保护原文件");boolean added=ledger.add(player,id);if(added)setDirty();return added;}
    private static StationVisits load(CompoundTag tag,HolderLookup.Provider registries){
        StationVisits data=new StationVisits();
        try{byte[] bytes=tag.getByteArray("ledger");if(bytes.length==0||bytes.length>8*1024*1024)throw new IllegalArgumentException("discovery data size");data.ledger=VisitLedger.parse(JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject());}
        catch(RuntimeException bad){data.locked=true;HabitatRuntime.LOG.error("Station discovery data invalid; preserving file and refusing writes",bad);}
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){if(locked)throw new IllegalStateException("discovery data locked");tag.putByteArray("ledger",ledger.json().toString().getBytes(StandardCharsets.UTF_8));return tag;}
}
