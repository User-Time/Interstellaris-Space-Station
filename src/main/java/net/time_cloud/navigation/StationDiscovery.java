package net.time_cloud.navigation;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.time_cloud.*;

public final class StationDiscovery {
    private static final VisitProgress PROGRESS=new VisitProgress();
    private StationDiscovery(){}
    public static void reset(){PROGRESS.clear();StationDestinations.reset();}
    public static void logout(UUID player){PROGRESS.remove(player);}
    public static void tick(ServerPlayer player){
        if(player instanceof FakePlayer||player.isSpectator()){PROGRESS.remove(player.getUUID());return;}
        if(player.server.getTickCount()%10!=0)return;
        var physical=HabitatRuntime.worldPosition(player.level(),player.position());
        String dimension=player.level().dimension().location().toString();Habitat found=null;
        for(Habitat h:HabitatRuntime.serverSnapshot().habitats())if(h.enabled()&&h.dimension().equals(dimension)&&StationDirectory.canView(h,player.getUUID())){
            double radius=Math.max(8,h.extent());double dx=physical.x-h.x(),dy=physical.y-h.y(),dz=physical.z-h.z();if(dx*dx+dy*dy+dz*dz<=radius*radius){found=h;break;}
        }
        if(!PROGRESS.observe(player.getUUID(),found==null?null:found.id(),player.server.getTickCount())||found==null)return;
        try{if(StationVisits.get(player.server).add(player.getUUID(),found.id())){
            StationUnlockNetwork.send(player);player.displayClientMessage(Component.literal("已解锁空间站导航："+found.core().name()),false);
        }}catch(RuntimeException bad){PROGRESS.remove(player.getUUID());HabitatRuntime.LOG.error("Unable to record station discovery for {}",player.getGameProfile().getName(),bad);}
    }
}
