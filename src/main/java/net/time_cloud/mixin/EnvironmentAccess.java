package net.time_cloud.mixin;
import com.interstellaris.mod.environment.*;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=EnvironmentManager.class,remap=false)
public interface EnvironmentAccess {
    @Accessor("byDimension") static Map<ResourceLocation,Environment> potg$byDimension() { throw new AssertionError(); }
}
