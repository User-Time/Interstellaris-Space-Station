package net.time_cloud;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-local visual preferences; no gameplay or server configuration changes. */
public final class DomeConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue OPACITY;
    public static final ModConfigSpec.DoubleValue RENDER_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_SPHERES;
    static {
        var builder=new ModConfigSpec.Builder();builder.push("dome");
        ENABLED=builder.comment("Show a white translucent shell on enabled habitat boundaries.").define("enabled",true);
        OPACITY=builder.comment("Maximum shell opacity; 0 hides the shell. The middle is softer than the rim.").defineInRange("opacity",0.18,0.0,0.6);
        RENDER_DISTANCE=builder.comment("Maximum distance from the camera to a sphere surface, in blocks.").defineInRange("render_distance",512.0,16.0,2048.0);
        MAX_SPHERES=builder.comment("Maximum number of nearest shells rendered per frame.").defineInRange("max_spheres",4,1,8);
        builder.pop();SPEC=builder.build();
    }
    private DomeConfig() {}
}
