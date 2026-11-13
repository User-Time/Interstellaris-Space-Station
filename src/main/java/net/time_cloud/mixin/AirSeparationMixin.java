package net.time_cloud.mixin;
import com.interstellaris.mod.block.entity.AirSeparationUnitBlockEntity;
import com.interstellaris.mod.environment.AtmosphereManager;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.time_cloud.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(value=AirSeparationUnitBlockEntity.class,remap=false)
public abstract class AirSeparationMixin {
    @WrapOperation(method="serverTick",at=@At(value="INVOKE",target="Lcom/interstellaris/mod/block/entity/AirSeparationUnitBlockEntity;composition(Lnet/minecraft/world/level/Level;)Ljava/util/List;"))
    private List<AirSeparationUnitBlockEntity.Share> potg$composition(Level level,Operation<List<AirSeparationUnitBlockEntity.Share>> original) {
        BlockEntity self=(BlockEntity)(Object)this;
        return HabitatRuntime.at(level,self.getBlockPos().getCenter())!=null
                ?AtmosphereManager.of(ResourceLocation.fromNamespaceAndPath("minecraft","overworld")):original.call(level);
    }
    @WrapOperation(method="serverTick",at=@At(value="INVOKE",target="Lcom/interstellaris/mod/block/entity/AirSeparationUnitBlockEntity;pressureFactor(Lnet/minecraft/world/level/Level;)D"))
    private double potg$pressure(Level level,Operation<Double> original) {
        return HabitatRuntime.at(level,((BlockEntity)(Object)this).getBlockPos().getCenter())!=null?1.0:original.call(level);
    }
}
