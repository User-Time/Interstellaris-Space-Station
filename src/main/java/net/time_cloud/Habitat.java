package net.time_cloud;

import com.google.gson.JsonObject;
import java.util.Locale;

/** Physics fields plus optional authoritative core metadata; metadata is never broadcast in effect snapshots. */
public record Habitat(String id, String dimension, double x, double y, double z, double radius,
                      double temperatureC, double gravityMultiplier, boolean enabled, double floorDistance,
                      double gravityRadius, GravityMode gravityMode, CoreInfo core) {
    public static final double DEFAULT_FLOOR_DISTANCE=4.0;
    public Habitat(String id,String dimension,double x,double y,double z,double radius,double temperatureC,double gravityMultiplier,boolean enabled) {
        this(id,dimension,x,y,z,radius,temperatureC,gravityMultiplier,enabled,DEFAULT_FLOOR_DISTANCE);
    }
    public Habitat(String id,String dimension,double x,double y,double z,double radius,double temperatureC,double gravityMultiplier,boolean enabled,double floorDistance) {
        this(id,dimension,x,y,z,radius,temperatureC,gravityMultiplier,enabled,floorDistance,radius,GravityMode.ADHESION,null);
    }
    public Habitat {
        if(id==null||!id.matches("[a-z0-9_-]{1,48}"))throw new IllegalArgumentException("名称须为1–48个小写字母、数字、下划线或短横线");
        if(dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")||dimension.length()>128)throw new IllegalArgumentException("维度标识无效");
        for(double v:new double[]{x,y,z,radius,temperatureC,gravityMultiplier,floorDistance,gravityRadius})if(!Double.isFinite(v))throw new IllegalArgumentException("参数必须为有限数值");
        if(Math.abs(x)>29999984||Math.abs(z)>29999984||Math.abs(y)>30000)throw new IllegalArgumentException("球心坐标超出范围");
        if(radius<0||radius>2048||gravityRadius<0||gravityRadius>2048)throw new IllegalArgumentException("半径须为0–2048格");
        if(temperatureC< -273.15||temperatureC>1000)throw new IllegalArgumentException("温度须为-273.15至1000°C");
        if(gravityMultiplier<0||gravityMultiplier>4)throw new IllegalArgumentException("重力倍率须为0–4");
        if(floorDistance<0||floorDistance>32)throw new IllegalArgumentException("吸附距离须为0–32格");
        if(gravityMode==null)throw new IllegalArgumentException("缺少重力模式");
        if(core!=null&&(!id.equals(core.key())||Math.abs(x-core.blockX()-.5)>1e-8||Math.abs(y-core.blockY()-.5)>1e-8||Math.abs(z-core.blockZ()-.5)>1e-8))
            throw new IllegalArgumentException("核心身份与位置不匹配");
        if(core!=null&&(temperatureC< -100||temperatureC>120))throw new IllegalArgumentException("核心温度须为-100～120°C");
    }
    public static String normalizeId(String value) { return value.toLowerCase(Locale.ROOT); }
    private boolean within(double px,double py,double pz,double r) {
        double dx=px-x,dy=py-y,dz=pz-z;
        return r>0&&Double.isFinite(px)&&Double.isFinite(py)&&Double.isFinite(pz)&&dx*dx+dy*dy+dz*dz<=r*r;
    }
    public boolean contains(double px,double py,double pz) { return within(px,py,pz,radius); }
    public boolean containsGravity(double px,double py,double pz) { return within(px,py,pz,gravityRadius); }
    public double extent() { return Math.max(radius,gravityRadius); }
    public boolean overlaps(Habitat other) {
        double dx=x-other.x,dy=y-other.y,dz=z-other.z,r=extent()+other.extent();
        return extent()>0&&other.extent()>0&&dimension.equals(other.dimension)&&dx*dx+dy*dy+dz*dz<=r*r;
    }
    private Habitat copy(double r,double temperature,double gravity,boolean active,double floor,double gr,GravityMode mode,CoreInfo info) {
        return new Habitat(id,dimension,x,y,z,r,temperature,gravity,active,floor,gr,mode,info);
    }
    public Habitat enabled(boolean value) { return copy(radius,temperatureC,gravityMultiplier,value,floorDistance,gravityRadius,gravityMode,core); }
    /** Legacy command radius continues to resize both fields together. */
    public Habitat radius(double value) { return copy(value,temperatureC,gravityMultiplier,enabled,floorDistance,value,gravityMode,core); }
    public Habitat temperature(double value) { return copy(radius,value,gravityMultiplier,enabled,floorDistance,gravityRadius,gravityMode,core); }
    public Habitat gravity(double value) { return copy(radius,temperatureC,value,enabled,floorDistance,gravityRadius,gravityMode,core); }
    public Habitat floorDistance(double value) { return copy(radius,temperatureC,gravityMultiplier,enabled,value,gravityRadius,gravityMode,core); }
    public Habitat gravityRadius(double value) { return copy(radius,temperatureC,gravityMultiplier,enabled,floorDistance,value,gravityMode,core); }
    public Habitat gravityMode(GravityMode value) { return copy(radius,temperatureC,gravityMultiplier,enabled,floorDistance,gravityRadius,value,core); }
    public Habitat withoutCore() { return copy(radius,temperatureC,gravityMultiplier,enabled,floorDistance,gravityRadius,gravityMode,null); }
    public Habitat move(String world,double px,double py,double pz) {
        if(core!=null)throw new IllegalArgumentException("核心区域请通过移动核心方块重建，不支持命令移动");
        return new Habitat(id,world,px,py,pz,radius,temperatureC,gravityMultiplier,enabled,floorDistance,gravityRadius,gravityMode,null);
    }
    public JsonObject json() {
        JsonObject v=new JsonObject();v.addProperty("id",id);v.addProperty("dimension",dimension);v.addProperty("x",x);v.addProperty("y",y);v.addProperty("z",z);
        v.addProperty("radius",radius);v.addProperty("temperature_c",temperatureC);v.addProperty("gravity_multiplier",gravityMultiplier);v.addProperty("enabled",enabled);
        v.addProperty("floor_distance",floorDistance);v.addProperty("gravity_radius",gravityRadius);v.addProperty("gravity_mode",gravityMode.name());
        if(core!=null)v.add("core",core.json());return v;
    }
    public static Habitat parse(JsonObject v) {
        if(!v.get("enabled").isJsonPrimitive()||!v.get("enabled").getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("enabled必须是true或false");
        double r=v.get("radius").getAsDouble();
        return new Habitat(v.get("id").getAsString(),v.get("dimension").getAsString(),v.get("x").getAsDouble(),v.get("y").getAsDouble(),v.get("z").getAsDouble(),r,
                v.get("temperature_c").getAsDouble(),v.get("gravity_multiplier").getAsDouble(),v.get("enabled").getAsBoolean(),
                v.has("floor_distance")?v.get("floor_distance").getAsDouble():DEFAULT_FLOOR_DISTANCE,
                v.has("gravity_radius")?v.get("gravity_radius").getAsDouble():r,
                v.has("gravity_mode")?GravityMode.valueOf(v.get("gravity_mode").getAsString()):GravityMode.ADHESION,
                v.has("core")?CoreInfo.parse(v.getAsJsonObject("core")):null);
    }
    public String bounds() { return "球心("+x+","+y+","+z+")，氧气半径 "+radius+"，重力半径 "+gravityRadius; }
}
