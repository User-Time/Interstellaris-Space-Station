package net.time_cloud.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Two-sided transparency with depth testing but no depth writes. */
final class DomeRenderType extends RenderType {
    static final RenderType SHELL=new DomeRenderType();
    private DomeRenderType() {
        super("potg_station_habitat_shell",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.TRIANGLES,
                1048576,false,false,DomeRenderType::begin,DomeRenderType::finish);
    }
    private static void begin() {
        POSITION_COLOR_SHADER.setupRenderState();TRANSLUCENT_TRANSPARENCY.setupRenderState();
        LEQUAL_DEPTH_TEST.setupRenderState();NO_CULL.setupRenderState();COLOR_WRITE.setupRenderState();
        PARTICLES_TARGET.setupRenderState();
    }
    private static void finish() {
        PARTICLES_TARGET.clearRenderState();COLOR_WRITE.clearRenderState();NO_CULL.clearRenderState();
        LEQUAL_DEPTH_TEST.clearRenderState();TRANSLUCENT_TRANSPARENCY.clearRenderState();POSITION_COLOR_SHADER.clearRenderState();
    }
}
