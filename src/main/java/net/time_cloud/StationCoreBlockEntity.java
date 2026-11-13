package net.time_cloud;

import com.google.gson.JsonParser;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class StationCoreBlockEntity extends BlockEntity {
    private UUID stationId;
    private String backup="";
    public StationCoreBlockEntity(BlockPos pos,BlockState state){super(StationContent.CORE_ENTITY.get(),pos,state);}
    public UUID stationId(){return stationId;}
    public String key(){return stationId==null?null:"core_"+stationId.toString().replace("-","");}
    public Habitat backup(){
        if(backup.isEmpty())return null;
        try{return Habitat.parse(JsonParser.parseString(backup).getAsJsonObject());}catch(RuntimeException invalid){return null;}
    }
    public void accept(Habitat habitat){
        if(habitat.core()==null)throw new IllegalArgumentException("missing core metadata");
        String next=habitat.json().toString();
        if(!next.equals(backup)||!habitat.core().stationId().equals(stationId)){stationId=habitat.core().stationId();backup=next;setChanged();}
    }
    public void serverTick(){if(level!=null&&level.getGameTime()%100==0)StationRuntime.reconcile(this);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);if(stationId!=null)tag.putUUID("StationId",stationId);if(!backup.isEmpty())tag.putString("StationBackup",backup);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);stationId=tag.hasUUID("StationId")?tag.getUUID("StationId"):null;
        backup=tag.getString("StationBackup");if(backup.length()>8192)backup="";
    }
}
