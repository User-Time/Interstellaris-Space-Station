package net.time_cloud.mixin;
import com.interstellaris.mod.environment.*;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.time_cloud.HabitatRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(value=EnvironmentHazards.class,remap=false)
public abstract class MobEnvironmentMixin {
    @WrapOperation(method="onEntityTick",at=@At(value="INVOKE",target="Lcom/interstellaris/mod/environment/EnvironmentManager;of(Lnet/minecraft/world/level/Level;)Lcom/interstellaris/mod/environment/Environment;"))
    private static Environment potg$mob(Level level,Operation<Environment> original,@Local(argsOnly=true) EntityTickEvent.Post event) {
        return HabitatRuntime.environmentAt(level,event.getEntity().position(),original.call(level));
    }
}
