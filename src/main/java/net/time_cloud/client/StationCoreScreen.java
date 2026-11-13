package net.time_cloud.client;

import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.time_cloud.*;

/** No inventory slots: all changes require an authenticated, range-checked server save. */
public final class StationCoreScreen extends Screen {
    private final long token;
    private Habitat habitat;
    private String revision;
    private String status="";
    private boolean publicStation,enabled,pending,closed;
    private GravityMode mode;
    private EditBox name,oxygen,gravityRadius,strength,adhesion,temperature;
    private Button mapButton,enabledButton,modeButton,saveButton;
    private int left,top,panelWidth,column,right;
    public StationCoreScreen(StationNetwork.OpenPayload payload){super(Component.literal("空间站核心"));token=payload.token();acceptData(payload);}
    public long token(){return token;}
    private void acceptData(StationNetwork.OpenPayload payload){
        habitat=Habitat.parse(JsonParser.parseString(payload.json()).getAsJsonObject());
        if(habitat.core()==null||!habitat.id().equals(payload.id()))throw new IllegalArgumentException("invalid core response");
        revision=payload.revision();publicStation=habitat.core().publicStation();enabled=habitat.enabled();mode=habitat.gravityMode();status=payload.message();pending=false;
    }
    public void accept(StationNetwork.OpenPayload payload){acceptData(payload);name=oxygen=gravityRadius=strength=adhesion=temperature=null;rebuildWidgets();}
    public void error(String message){status=message;pending=false;if(saveButton!=null)saveButton.active=true;}
    private EditBox field(EditBox old,String fallback,int x,int y,int width,int max){
        var box=new EditBox(font,left+x,top+y,width,18,Component.literal("设置"));box.setMaxLength(max);box.setValue(old==null?fallback:old.getValue());addRenderableWidget(box);return box;
    }
    private Button button(String label,int x,int y,int width,Runnable action){return addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(left+x,top+y,width,19).build());}
    private String number(double n){return n==Math.rint(n)?Long.toString((long)n):Double.toString(n);}
    @Override protected void init(){
        panelWidth=Math.min(420,width-12);left=(width-panelWidth)/2;top=Math.max(2,(height-230)/2);column=(panelWidth-42)/2;right=panelWidth/2+7;
        name=field(name,habitat.core().name(),72,29,panelWidth-86,48);
        mapButton=button(publicStation?"地图：公共":"地图：私人",14,53,column,()->{publicStation=!publicStation;mapButton.setMessage(Component.literal(publicStation?"地图：公共":"地图：私人"));});
        enabledButton=button(enabled?"环境：开启":"环境：关闭",right,53,column,()->{enabled=!enabled;enabledButton.setMessage(Component.literal(enabled?"环境：开启":"环境：关闭"));});
        oxygen=field(oxygen,number(habitat.radius()),14,89,column,20);
        gravityRadius=field(gravityRadius,number(habitat.gravityRadius()),right,89,column,20);
        modeButton=button("模式："+mode.label(),14,116,column,()->{mode=GravityMode.values()[(mode.ordinal()+1)%GravityMode.values().length];modeButton.setMessage(Component.literal("模式："+mode.label()));updateMode();});
        strength=field(strength,number(habitat.gravityMultiplier()),right+58,116,column-58,20);
        adhesion=field(adhesion,number(habitat.floorDistance()),14+64,142,column-64,20);
        temperature=field(temperature,number(habitat.temperatureC()),right+58,142,column-58,20);
        int preset=(column-4)/3;
        for(int i=0;i<3;i++){int value=new int[]{-100,20,120}[i];button(value+"°C",right+i*(preset+2),167,preset,()->temperature.setValue(Integer.toString(value)));}
        saveButton=button("保存设置",14,205,column,this::save);saveButton.active=!pending;
        button("关闭",right,205,column,this::onClose);updateMode();
        oxygen.setTooltip(Tooltip.create(Component.literal("氧气与温度球半径，0–2048格；0表示不提供此环境")));
        gravityRadius.setTooltip(Tooltip.create(Component.literal("重力球半径，独立于氧气范围；0表示不覆盖原重力")));
        adhesion.setTooltip(Tooltip.create(Component.literal("只有吸附重力使用此距离，范围0–32格")));
        strength.setTooltip(Tooltip.create(Component.literal("重力倍率0–4，1为正常地球重力")));
        setInitialFocus(name);
    }
    private void updateMode(){if(adhesion!=null)adhesion.active=mode==GravityMode.ADHESION;if(strength!=null)strength.active=mode!=GravityMode.NONE;}
    private void save(){
        try{
            var settings=new StationSettings(name.getValue(),publicStation,enabled,Double.parseDouble(oxygen.getValue()),Double.parseDouble(gravityRadius.getValue()),mode,
                    Double.parseDouble(strength.getValue()),Double.parseDouble(adhesion.getValue()),Double.parseDouble(temperature.getValue()));
            PacketDistributor.sendToServer(new StationNetwork.UpdatePayload(token,habitat.id(),revision,settings.json().toString()));pending=true;saveButton.active=false;status="正在等待服务器保存…";
        }catch(Exception invalid){error(invalid.getMessage()==null?"请输入有效数值":invalid.getMessage());}
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        g.fill(0,0,width,height,0x88000000);g.fill(left,top,left+panelWidth,top+230,0xF20B1420);
        g.fill(left,top,left+panelWidth,top+2,0xFF89D9E8);
        g.drawString(font,title,left+14,top+11,0xFFE8F7FF,false);
        String owner="站主："+habitat.core().ownerName();g.drawString(font,owner,left+panelWidth-14-font.width(owner),top+11,0xFF92AABC,false);
        label(g,"名称",14,34);label(g,"氧气/温度半径",14,78);label(g,"重力半径",right,78);
        label(g,"重力倍率",right,121);label(g,"吸附距离",14,147);label(g,"温度 °C",right,147);
        label(g,"温度 −100～120°C",14,172);
        String message=status.isBlank()?"私人仅站主在M地图可见；半径0可关闭对应效果":status;
        g.drawString(font,font.plainSubstrByWidth(message,panelWidth-28),left+14,top+191,status.startsWith("设置已保存")?0xFF88E3AE:0xFFC2D4E2,false);
        super.render(g,mouseX,mouseY,partial);
    }
    private void label(GuiGraphics g,String text,int x,int y){g.drawString(font,text,left+x,top+y,0xFFB6C9D9,false);}
    @Override public boolean isPauseScreen(){return false;}
    private void closeSession(){if(!closed&&minecraft!=null&&minecraft.getConnection()!=null){closed=true;PacketDistributor.sendToServer(new StationNetwork.ClosePayload(token));}}
    @Override public void onClose(){closeSession();super.onClose();}
    @Override public void removed(){closeSession();super.removed();}
}
