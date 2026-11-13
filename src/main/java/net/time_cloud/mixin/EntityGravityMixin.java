package net.time_cloud.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.time_cloud.HabitatRuntime;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=Entity.class,priority=900,remap=false)
public abstract class EntityGravityMixin {
    @WrapMethod(method="getGravity") private double potg$habitat(Operation<Double> original) {
        return HabitatRuntime.gravity((Entity)(Object)this,original.call());
    }
}
