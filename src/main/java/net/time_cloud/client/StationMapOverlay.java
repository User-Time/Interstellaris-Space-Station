package net.time_cloud.client;

import com.interstellaris.mod.client.SystemMapScreen;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.time_cloud.*;
import net.time_cloud.mixin.SystemMapAccess;

public final class StationMapOverlay {
    private record Hit(StationMarker marker,int x,int y){}
    private static final List<Hit> HITS=new ArrayList<>();
    private static SystemMapScreen lastScreen;
    private StationMapOverlay(){}
    public static List<StationMarker> current(){
        var level=Minecraft.getInstance().level;if(level==null)return List.of();String dim=level.dimension().location().toString();
        return StationClientCache.markers().stream().filter(m->m.dimension().equals(dim)).toList();
    }
    public static void render(SystemMapScreen screen,GuiGraphics g,int mouseX,int mouseY){
        lastScreen=screen;HITS.clear();if(SystemMapAccess.potg$anchor()!=null)return;
        var minecraft=Minecraft.getInstance();var font=minecraft.font;var access=(SystemMapAccess)(Object)screen;
        var stations=current();StationMarker hovered=null;
        for(var station:stations){
            float[] point=access.potg$project(access.potg$local(new Vec3(station.x(),station.y(),station.z())));
            if(point==null||!Float.isFinite(point[0])||!Float.isFinite(point[1]))continue;
            int x=Math.round(point[0]),y=Math.round(point[1]);if(x<6||x>screen.width-6||y<28||y>screen.height-24)continue;
            int color=!station.enabled()?0xFF74818C:station.publicStation()?0xFF83DFEA:0xFFFFE0AA;
            g.fill(x-5,y-5,x+6,y+6,0xE0101B27);g.fill(x-4,y-4,x+5,y-3,color);g.fill(x-4,y+4,x+5,y+5,color);
            g.fill(x-4,y-3,x-3,y+4,color);g.fill(x+4,y-3,x+5,y+4,color);g.fill(x-1,y-2,x+2,y+3,color);
            HITS.add(new Hit(station,x,y));if(Math.abs(mouseX-x)<=7&&Math.abs(mouseY-y)<=7)hovered=station;
        }
        int buttonX=screen.width-150;g.fill(buttonX,6,screen.width-8,25,0xE01A2A39);
        g.drawCenteredString(font,"空间站 ("+stations.size()+")",buttonX+71,12,0xFFE9F6FF);
        if(hovered!=null)g.renderTooltip(font,List.of(Component.literal(hovered.name()+" · "+(hovered.publicStation()?"公共":"私人")),
                Component.literal("站主："+hovered.ownerName()),Component.literal(String.format(java.util.Locale.ROOT,"X %.0f  Y %.0f  Z %.0f",hovered.x(),hovered.y(),hovered.z())),
                Component.literal("氧气半径 "+hovered.oxygenRadius()+" | "+hovered.gravityMode().label()+" | "+hovered.temperatureC()+"°C"),Component.literal("左键聚焦；不会传送玩家")),Optional.empty(),mouseX,mouseY);
    }
    public static boolean click(SystemMapScreen screen,double x,double y,int button){
        if(button!=0||SystemMapAccess.potg$anchor()!=null)return false;
        if(x>=screen.width-150&&x<=screen.width-8&&y>=6&&y<=25){Minecraft.getInstance().setScreen(new StationDirectoryScreen(screen));return true;}
        if(screen==lastScreen)for(Hit hit:HITS)if(Math.abs(x-hit.x())<=7&&Math.abs(y-hit.y())<=7){focus(screen,hit.marker());return true;}
        return false;
    }
    public static void focus(SystemMapScreen screen,StationMarker station){
        var map=(SystemMapAccess)(Object)screen;map.potg$target(new Vec3(station.x(),station.y(),station.z()));
        map.potg$distance(Math.max(128,Math.max(station.oxygenRadius(),station.gravityRadius())*4));
    }
    public static void clear(){HITS.clear();lastScreen=null;}
}
