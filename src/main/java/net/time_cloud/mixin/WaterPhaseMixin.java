package net.time_cloud.mixin;
import com.interstellaris.mod.environment.*;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.time_cloud.HabitatRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(value=WaterPhase.class,remap=false)
public abstract class WaterPhaseMixin {
    @WrapOperation(method="at",at=@At(value="INVOKE",target="Lcom/interstellaris/mod/environment/EnvironmentManager;of(Lnet/minecraft/world/level/Level;)Lcom/interstellaris/mod/environment/Environment;"))
    private static Environment potg$water(Level level,Operation<Environment> original,@Local(argsOnly=true) BlockPos pos) {
        return HabitatRuntime.environmentAt(level,pos.getCenter(),original.call(level));
    }
}
