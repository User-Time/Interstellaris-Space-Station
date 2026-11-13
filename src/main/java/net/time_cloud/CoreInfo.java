package net.time_cloud;

import com.google.gson.JsonObject;
import java.util.UUID;

/** Server-only ownership and directory data. */
public record CoreInfo(UUID stationId,UUID owner,String ownerName,String name,boolean publicStation,int blockX,int blockY,int blockZ) {
    public CoreInfo {
        if(stationId==null||owner==null)throw new IllegalArgumentException("缺少核心身份");
        if(ownerName==null||ownerName.length()>32)throw new IllegalArgumentException("无效站主名称");
        name=validName(name);
    }
    public static String validName(String name) {
        if(name==null)throw new IllegalArgumentException("缺少空间站名称");name=name.strip();
        if(name.isEmpty()||name.length()>48||name.codePoints().anyMatch(c->Character.isISOControl(c)||c==0x00a7))throw new IllegalArgumentException("站名须为1–48个字符，不能含控制符或颜色码");
        return name;
    }
    public String key(){return "core_"+stationId.toString().replace("-","");}
    public CoreInfo display(String value,boolean visible){return new CoreInfo(stationId,owner,ownerName,value,visible,blockX,blockY,blockZ);}
    public JsonObject json(){
        var v=new JsonObject();v.addProperty("station_id",stationId.toString());v.addProperty("owner",owner.toString());v.addProperty("owner_name",ownerName);
        v.addProperty("name",name);v.addProperty("public",publicStation);v.addProperty("block_x",blockX);v.addProperty("block_y",blockY);v.addProperty("block_z",blockZ);return v;
    }
    public static CoreInfo parse(JsonObject v){
        if(!v.get("public").getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("public必须为布尔值");
        return new CoreInfo(UUID.fromString(v.get("station_id").getAsString()),UUID.fromString(v.get("owner").getAsString()),v.get("owner_name").getAsString(),v.get("name").getAsString(),v.get("public").getAsBoolean(),v.get("block_x").getAsInt(),v.get("block_y").getAsInt(),v.get("block_z").getAsInt());
    }
}
