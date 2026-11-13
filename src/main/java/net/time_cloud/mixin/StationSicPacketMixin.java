package net.time_cloud.mixin;

import com.interstellaris.mod.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.time_cloud.navigation.StationDestinations;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=SicPacket.class,remap=false)
public abstract class StationSicPacketMixin {
    @Shadow @Final private int action;
    @Shadow @Final private String target;
    @Inject(method="handle",at=@At("HEAD"),cancellable=true)
    private void potg$configureAccess(IPayloadContext context,CallbackInfo ci){
        if(action!=1)return;
        ResourceLocation id=target==null?null:ResourceLocation.tryParse(target);
        if(action==1&&StationDestinations.ours(id)&&context.player() instanceof ServerPlayer player){
            String issue=StationDestinations.issue(player.serverLevel(),id,player.getUUID());
            if(issue!=null){ModNetwork.sendTo(player,new SicNoticePacket(Component.translatable("message.interstellaris.sic.issue."+issue)));ci.cancel();}
        }
    }
}
