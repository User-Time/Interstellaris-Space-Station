package net.time_cloud.mixin;
import com.interstellaris.mod.ship.ShipGravity;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(value=ShipGravity.class,remap=false)
public interface ShipGravityAccess {
 @Invoker("centre") static Vec3 potg$centre(Entity entity,SubLevel ship){throw new AssertionError();}
}
