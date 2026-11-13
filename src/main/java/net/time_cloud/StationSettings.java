package net.time_cloud;

import com.google.gson.JsonObject;

/** Only these fields may be supplied by a client; identity, owner and position are immutable. */
public record StationSettings(String name,boolean publicStation,boolean enabled,double oxygenRadius,double gravityRadius,
                              GravityMode gravityMode,double gravityMultiplier,double adhesionDistance,double temperatureC) {
    public StationSettings {
        name=CoreInfo.validName(name);
        for(double v:new double[]{oxygenRadius,gravityRadius,gravityMultiplier,adhesionDistance,temperatureC})if(!Double.isFinite(v))throw new IllegalArgumentException("数值必须有限");
        if(oxygenRadius<0||oxygenRadius>2048||gravityRadius<0||gravityRadius>2048)throw new IllegalArgumentException("半径须为0–2048格");
        if(gravityMode==null||gravityMultiplier<0||gravityMultiplier>4)throw new IllegalArgumentException("重力倍率须为0–4");
        if(adhesionDistance<0||adhesionDistance>32)throw new IllegalArgumentException("吸附距离须为0–32格");
        if(temperatureC< -100||temperatureC>120)throw new IllegalArgumentException("核心温度须为-100～120°C");
    }
    public static StationSettings from(Habitat h){
        if(h.core()==null)throw new IllegalArgumentException("不是核心区域");
        return new StationSettings(h.core().name(),h.core().publicStation(),h.enabled(),h.radius(),h.gravityRadius(),h.gravityMode(),h.gravityMultiplier(),h.floorDistance(),h.temperatureC());
    }
    public Habitat apply(Habitat h){
        if(h.core()==null)throw new IllegalArgumentException("不是核心区域");
        return new Habitat(h.id(),h.dimension(),h.x(),h.y(),h.z(),oxygenRadius,temperatureC,gravityMultiplier,enabled,adhesionDistance,gravityRadius,gravityMode,h.core().display(name,publicStation));
    }
    public JsonObject json(){
        var v=new JsonObject();v.addProperty("name",name);v.addProperty("public",publicStation);v.addProperty("enabled",enabled);
        v.addProperty("oxygen_radius",oxygenRadius);v.addProperty("gravity_radius",gravityRadius);v.addProperty("gravity_mode",gravityMode.name());
        v.addProperty("gravity_multiplier",gravityMultiplier);v.addProperty("adhesion_distance",adhesionDistance);v.addProperty("temperature_c",temperatureC);return v;
    }
    private static double number(JsonObject v,String key){if(!v.get(key).getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("数值格式无效："+key);return v.get(key).getAsDouble();}
    private static boolean bool(JsonObject v,String key){if(!v.get(key).getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("布尔值格式无效："+key);return v.get(key).getAsBoolean();}
    public static StationSettings parse(JsonObject v){return new StationSettings(v.get("name").getAsString(),bool(v,"public"),bool(v,"enabled"),number(v,"oxygen_radius"),number(v,"gravity_radius"),GravityMode.valueOf(v.get("gravity_mode").getAsString()),number(v,"gravity_multiplier"),number(v,"adhesion_distance"),number(v,"temperature_c"));}
}
