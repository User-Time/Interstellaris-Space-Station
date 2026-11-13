package net.time_cloud;

/** Directory information for one already-authorized recipient; never used for server permissions. */
public record StationMarker(String id,String name,String ownerName,String dimension,double x,double y,double z,
                            boolean publicStation,boolean enabled,double oxygenRadius,double gravityRadius,
                            GravityMode gravityMode,double gravityMultiplier,double temperatureC) {
    public StationMarker {
        if(id==null||!id.matches("core_[0-9a-f]{32}"))throw new IllegalArgumentException("invalid station id");
        name=CoreInfo.validName(name);
        if(ownerName==null||ownerName.length()>32||dimension==null||dimension.length()>128||!dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))throw new IllegalArgumentException("invalid station marker");
        for(double v:new double[]{x,y,z,oxygenRadius,gravityRadius,gravityMultiplier,temperatureC})if(!Double.isFinite(v))throw new IllegalArgumentException("invalid station coordinate");
        if(oxygenRadius<0||oxygenRadius>2048||gravityRadius<0||gravityRadius>2048||gravityMultiplier<0||gravityMultiplier>4||temperatureC< -100||temperatureC>120||gravityMode==null)throw new IllegalArgumentException("invalid station settings");
    }
    public static StationMarker of(Habitat h){var c=h.core();return new StationMarker(h.id(),c.name(),c.ownerName(),h.dimension(),h.x(),h.y(),h.z(),c.publicStation(),h.enabled(),h.radius(),h.gravityRadius(),h.gravityMode(),h.gravityMultiplier(),h.temperatureC());}
}
