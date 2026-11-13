package net.time_cloud;
public enum GravityMode {
    NONE("无重力"),FULL("完全重力"),ADHESION("吸附重力");
    private final String label;
    GravityMode(String label){this.label=label;}
    public String label(){return label;}
}
