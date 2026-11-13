package net.time_cloud.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.time_cloud.*;

public final class DomeRenderer {
    private static final DomeMesh MESH=new DomeMesh(96,48);
    private static ByteBufferBuilder memory;
    private static MultiBufferSource.BufferSource buffers;
    private DomeRenderer() {}
    public static void clear() {
        buffers=null;if(memory!=null){memory.close();memory=null;}
    }
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||!DomeConfig.ENABLED.get()||DomeConfig.OPACITY.get()<=0)return;
        var level=Minecraft.getInstance().level;if(level==null)return;
        var camera=event.getCamera().getPosition();
        var visible=DomeVisibility.select(HabitatNetwork.clientSnapshot(),level.dimension().location().toString(),
                camera.x,camera.y,camera.z,DomeConfig.RENDER_DISTANCE.get(),DomeConfig.MAX_SPHERES.get());
        if(visible.isEmpty())return;
        var poses=event.getPoseStack();boolean drew=false;
        for(Habitat habitat:visible) {
            double r=habitat.radius();
            if(r<=0)continue;
            if(event.getFrustum()!=null&&!event.getFrustum().isVisible(new AABB(habitat.x()-r,habitat.y()-r,habitat.z()-r,
                    habitat.x()+r,habitat.y()+r,habitat.z()+r)))continue;
            if(buffers==null){memory=new ByteBufferBuilder(1048576);buffers=MultiBufferSource.immediate(memory);}
            poses.pushPose();
            try {
                // Subtract as doubles before converting to GPU floats, including distant space stations.
                poses.translate(habitat.x()-camera.x,habitat.y()-camera.y,habitat.z()-camera.z);
                VertexConsumer vertices=buffers.getBuffer(DomeRenderType.SHELL);var matrix=poses.last().pose();
                double ex=camera.x-habitat.x(),ey=camera.y-habitat.y(),ez=camera.z-habitat.z(),opacity=DomeConfig.OPACITY.get();
                for(int vertex=0;vertex<MESH.vertexCount();vertex++) {
                    float x=MESH.x(vertex),y=MESH.y(vertex),z=MESH.z(vertex);
                    float alpha=DomeMesh.alpha(x,y,z,r,ex,ey,ez,opacity);
                    vertices.addVertex(matrix,(float)(x*r),(float)(y*r),(float)(z*r)).setColor(1f,1f,1f,alpha);
                }
                drew=true;
            } finally { poses.popPose(); }
        }
        if(drew)buffers.endBatch(DomeRenderType.SHELL);
    }
}
