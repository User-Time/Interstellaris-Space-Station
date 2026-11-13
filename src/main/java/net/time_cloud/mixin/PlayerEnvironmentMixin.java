package net.time_cloud.mixin;
import com.interstellaris.mod.environment.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.time_cloud.HabitatRuntime;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=EnvironmentManager.class,remap=false)
public abstract class PlayerEnvironmentMixin {
    @WrapMethod(method="of(Lnet/minecraft/world/entity/player/Player;)Lcom/interstellaris/mod/environment/Environment;")
    private static Environment potg$player(Player player,Operation<Environment> original) {
        return HabitatRuntime.environmentAt(player.level(),player.position(),original.call(player));
    }
    @WrapMethod(method="of(Lnet/minecraft/world/level/Level;)Lcom/interstellaris/mod/environment/Environment;")
    private static Environment potg$level(Level level,Operation<Environment> original) {
        Environment value=original.call(level);
        // An initial server packet may describe a habitat: never treat that as the whole dimension.
        return HabitatRuntime.ours(value)?HabitatRuntime.baseEnvironment(level):value;
    }
}
