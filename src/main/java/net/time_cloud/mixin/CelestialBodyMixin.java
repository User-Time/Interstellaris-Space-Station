package net.time_cloud.mixin;

import com.interstellaris.mod.space.CelestialBody;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.time_cloud.navigation.StationBody;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=CelestialBody.class,remap=false)
public abstract class CelestialBodyMixin implements StationBody {
    @Unique private String potg$stationId,potg$stationName;
    public void potg$station(String id,String name){potg$stationId=id;potg$stationName=name;}
    public String potg$stationId(){return potg$stationId;}
    public String potg$stationName(){return potg$stationName;}
    @Inject(method="name",at=@At("HEAD"),cancellable=true)
    private static void potg$name(Holder<CelestialBody> holder,CallbackInfoReturnable<Component> ci){
        if(holder!=null&&((StationBody)(Object)holder.value()).potg$stationId()!=null)
            ci.setReturnValue(Component.literal(((StationBody)(Object)holder.value()).potg$stationName()));
    }
}
