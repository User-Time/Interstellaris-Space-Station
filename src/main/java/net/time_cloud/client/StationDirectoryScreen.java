package net.time_cloud.client;

import com.interstellaris.mod.client.SystemMapScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class StationDirectoryScreen extends Screen {
    private final SystemMapScreen parent;
    private int page;
    private int perPage;
    public StationDirectoryScreen(SystemMapScreen parent){super(Component.literal("当前星系空间站"));this.parent=parent;}
    @Override protected void init(){
        var list=StationMapOverlay.current();perPage=Math.max(1,(height-90)/28);int pages=Math.max(1,(list.size()+perPage-1)/perPage);page=Math.min(page,pages-1);
        for(int i=page*perPage;i<Math.min(list.size(),(page+1)*perPage);i++){
            var station=list.get(i);int y=40+(i-page*perPage)*28;
            String text=station.name()+" · "+(station.publicStation()?"公共":"私人")+(station.enabled()?"":" · 未启用");
            addRenderableWidget(Button.builder(Component.literal(text),button->{StationMapOverlay.focus(parent,station);minecraft.setScreen(parent);}).bounds(width/2-145,y,290,22).build());
        }
        addRenderableWidget(Button.builder(Component.literal("上一页"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(width/2-145,height-34,90,20).build());
        addRenderableWidget(Button.builder(Component.literal("返回地图"),b->onClose()).bounds(width/2-45,height-34,90,20).build());
        addRenderableWidget(Button.builder(Component.literal("下一页"),b->{page=Math.min(pages-1,page+1);rebuildWidgets();}).bounds(width/2+55,height-34,90,20).build());
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        g.fill(0,0,width,height,0xF00A1420);g.drawCenteredString(font,title,width/2,14,0xFFE3F6FF);
        if(StationMapOverlay.current().isEmpty())g.drawCenteredString(font,"当前星系没有你可见的空间站",width/2,60,0xFF98AABB);
        super.render(g,mouseX,mouseY,partial);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
