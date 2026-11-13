package net.time_cloud.mixin;

import com.interstellaris.mod.ship.*;
import com.interstellaris.mod.space.CelestialBody;
import com.interstellaris.mod.block.entity.*;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3dc;
import net.time_cloud.navigation.StationDestinations;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=Navigator.class,remap=false)
public abstract class StationNavigatorMixin {
    @Shadow private ResourceKey<CelestialBody> target;
    @Shadow private UUID engager;
    @Shadow protected abstract void finish(Navigator.View view,String message);
    @Inject(method="body",at=@At("HEAD"),cancellable=true)
    private static void potg$body(ServerLevel level,ResourceLocation id,CallbackInfoReturnable<Holder.Reference<CelestialBody>> ci){
        if(StationDestinations.ours(id))ci.setReturnValue(StationDestinations.resolve(id));
    }
    @Inject(method="plan",at=@At("HEAD"),cancellable=true)
    private static void potg$plan(ServerLevel level,Vector3dc position,Holder<CelestialBody> body,NavMode mode,Navigator.Stock stock,UUID player,CallbackInfoReturnable<Navigator.Plan> ci){
        ResourceLocation id=body==null?null:body.unwrapKey().map(ResourceKey::location).orElse(null);
        if(StationDestinations.ours(id)){
            String issue=StationDestinations.issue(level,id,player);
            if(issue!=null)ci.setReturnValue(new Navigator.Plan(-1,0,-1,"",0,issue));
        }
    }
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void potg$flightAccess(CockpitBlockEntity cockpit,ServerLevel level,ServerSubLevel ship,ShipInformationCenterBlockEntity sic,Navigator.Stock stock,Navigator.View view,CallbackInfo ci){
        if(target!=null&&StationDestinations.ours(target.location())){
            String issue=StationDestinations.issue(level,target.location(),engager);
            if(issue!=null){finish(view,"message.interstellaris.sic.issue."+issue);ci.cancel();}
        }
    }
}
