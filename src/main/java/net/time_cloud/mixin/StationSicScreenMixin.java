package net.time_cloud.mixin;

import com.interstellaris.mod.client.ShipInformationCenterScreen;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import net.time_cloud.client.StationNavigationEntries;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=ShipInformationCenterScreen.class,remap=false)
public abstract class StationSicScreenMixin {
    @Shadow protected abstract ResourceKey<Level> system();
    @Shadow protected abstract Vector3d shipPosition(ClientLevel level);
    @Inject(method="entries",at=@At("RETURN"),cancellable=true)
    private void potg$entries(CallbackInfoReturnable<List<?>> ci){
        ClientLevel level=Minecraft.getInstance().level;
        if(level!=null)ci.setReturnValue(StationNavigationEntries.append(ci.getReturnValue(),system(),level.dimension(),shipPosition(level)));
    }
}
