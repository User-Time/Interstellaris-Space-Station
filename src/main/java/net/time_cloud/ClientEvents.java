package net.time_cloud;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.time_cloud.client.DomeRenderer;
import net.time_cloud.client.StationMapOverlay;
import com.interstellaris.mod.client.SystemMapScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
@EventBusSubscriber(modid=HabitatRuntime.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.GAME)
public final class ClientEvents {
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { HabitatNetwork.resetClient();DomeRenderer.clear();StationMapOverlay.clear(); }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) { DomeRenderer.render(event); }
    @SubscribeEvent public static void screen(ScreenEvent.Render.Post event){if(event.getScreen() instanceof SystemMapScreen map)StationMapOverlay.render(map,event.getGuiGraphics(),event.getMouseX(),event.getMouseY());}
    @SubscribeEvent public static void click(ScreenEvent.MouseButtonPressed.Pre event){if(event.getScreen() instanceof SystemMapScreen map&&StationMapOverlay.click(map,event.getMouseX(),event.getMouseY(),event.getButton()))event.setCanceled(true);}
}
