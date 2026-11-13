package net.time_cloud.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.time_cloud.*;

public final class StationClientHooks {
    private StationClientHooks(){}
    public static void open(StationNetwork.OpenPayload payload){
        var minecraft=Minecraft.getInstance();if(minecraft.level==null||minecraft.player==null)return;
        if(payload.initial())minecraft.setScreen(new StationCoreScreen(payload));
        else if(minecraft.screen instanceof StationCoreScreen screen&&screen.token()==payload.token())screen.accept(payload);
    }
    public static void error(StationNetwork.ErrorPayload payload){
        var minecraft=Minecraft.getInstance();
        if(minecraft.screen instanceof StationCoreScreen screen&&screen.token()==payload.token())screen.error(payload.message());
        else if(minecraft.player!=null)minecraft.player.displayClientMessage(Component.literal("[空间站] "+payload.message()),false);
    }
}
