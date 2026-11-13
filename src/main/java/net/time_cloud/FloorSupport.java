package net.time_cloud;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Bounded, vertical support check against already loaded, ordinary world blocks. */
public final class FloorSupport {
    private static final double EPS=1.0e-6;
    private FloorSupport() {}

    public static boolean hasFloor(Player player,double distance) {
        if(!Double.isFinite(distance)||distance<0||distance>32)return false;
        Level level=player.level();AABB feet=player.getBoundingBox();
        return scan(player,distance,feet,(x,z)->level instanceof ServerLevel server?server.getChunkSource().getChunkNow(x,z):
                level.isClientSide?level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false):null);
    }
    public static boolean hasShipFloor(Player player,dev.ryanhcode.sable.sublevel.SubLevel ship,double distance) {
        if(!Double.isFinite(distance)||distance<0||distance>32)return false;
        var center=net.time_cloud.mixin.ShipGravityAccess.potg$centre(player,ship);
        var local=ship.logicalPose().transformPositionInverse(center);
        double half=player.getBbWidth()/2.0,foot=local.y-player.getBbHeight()/2.0;
        var feet=new AABB(local.x-half,foot,local.z-half,local.x+half,foot+player.getBbHeight(),local.z+half);
        var plot=ship.getPlot();
        return scan(player,distance,feet,(x,z)->plot.getChunk(plot.toLocal(new net.minecraft.world.level.ChunkPos(x,z))));
    }
    private interface Chunks {ChunkAccess get(int x,int z);}
    private static boolean scan(Player player,double distance,AABB feet,Chunks chunks) {
        Level level=player.level();double low=feet.minY-distance;
        // Include bases of fences/walls whose collision extends above their own cell.
        int minY=Math.max(level.getMinBuildHeight(),Mth.floor(low)-1);
        int maxY=Math.min(level.getMaxBuildHeight()-1,Mth.floor(feet.minY-EPS));
        int minX=Mth.floor(feet.minX+EPS),maxX=Mth.floor(feet.maxX-EPS);
        int minZ=Mth.floor(feet.minZ+EPS),maxZ=Mth.floor(feet.maxZ-EPS);
        CollisionContext context=CollisionContext.of(player);
        var pos=new BlockPos.MutableBlockPos();
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
            // ServerChunkCache.getChunk(..., false) can still wait for a future: use getChunkNow.
            ChunkAccess chunk=chunks.get(x>>4,z>>4);
            if(chunk==null)continue;
            for(int y=maxY;y>=minY;y--) {
                pos.set(x,y,z);var state=chunk.getBlockState(pos);
                if(state.isAir())continue;
                var shape=state.getCollisionShape(level,pos,context);
                for(AABB box:shape.toAabbs()) {
                    double top=y+box.maxY;
                    if(top<=feet.minY+EPS&&top>=low-EPS
                            &&x+box.maxX>feet.minX+EPS&&x+box.minX<feet.maxX-EPS
                            &&z+box.maxZ>feet.minZ+EPS&&z+box.minZ<feet.maxZ-EPS)return true;
                }
            }
        }
        return false;
    }
}
