package net.time_cloud.mixin;

import com.interstellaris.mod.client.SystemMapScreen;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value=SystemMapScreen.class,remap=false)
public interface SystemMapAccess {
    @Invoker("local") Vector3f potg$local(Vec3 point);
    @Invoker("project") float[] potg$project(Vector3f point);
    @Accessor("goalTarget") void potg$target(Vec3 point);
    @Accessor("goalDistance") void potg$distance(double distance);
    @Accessor("anchor") static Vec3 potg$anchor(){throw new AssertionError();}
}
