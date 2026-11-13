package net.time_cloud.mixin;
import com.interstellaris.mod.ship.ShipGravity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.time_cloud.HabitatRuntime;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=ShipGravity.class,remap=false)
public abstract class ShipGravityMixin {
 @WrapMethod(method="travel") private static Vec3 potg$stationGravity(Entity entity,Vec3 velocity,float friction,Operation<Vec3> original) {
  return HabitatRuntime.shipTravel(entity,original.call(entity,velocity,friction));
 }
}
