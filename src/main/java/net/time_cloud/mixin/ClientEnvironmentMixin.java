package net.time_cloud.mixin;
import com.interstellaris.mod.client.ClientEnvironment;
import com.interstellaris.mod.environment.*;
import com.interstellaris.mod.ship.ShipCabin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.time_cloud.HabitatRuntime;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=ClientEnvironment.class,remap=false)
public abstract class ClientEnvironmentMixin {
    @WrapMethod(method="get") private static Environment potg$environment(Operation<Environment> original) {
        return HabitatRuntime.clientEnvironment(original.call());
    }
    @WrapMethod(method="noFire") private static boolean potg$fire(Operation<Boolean> original) {
        Player player=HabitatRuntime.clientPlayer();
        if(player==null)return original.call();
        Environment environment=HabitatRuntime.clientEnvironment(ClientEnvironment.get());
        return environment!=null&&!environment.breathable()&&!ShipCabin.isSealed(player);
    }
    @WrapMethod(method="vacuum") private static boolean potg$vacuum(Level level,Operation<Boolean> original) {
        Player player=HabitatRuntime.clientPlayer();
        return player!=null&&player.level()==level&&HabitatRuntime.at(player)!=null?false:original.call(level);
    }
}
