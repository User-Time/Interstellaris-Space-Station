package net.time_cloud;

import com.interstellaris.mod.environment.EnvironmentManager;
import com.interstellaris.mod.worldgen.ModDimensions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import static net.minecraft.commands.Commands.*;

public final class HabitatCommands {
    private HabitatCommands() {}
    @FunctionalInterface interface Action { int run() throws Exception; }
    private static void message(CommandSourceStack source,String text) { source.sendSuccess(()->Component.literal("[空间站环境] "+text),false); }
    private static int guarded(CommandSourceStack source,Action action) {
        try { return action.run(); }
        catch(Exception exception) {
            source.sendFailure(Component.literal("[空间站环境] 操作未生效："+exception.getMessage()));
            HabitatRuntime.LOG.warn("Habitat command failed for {}: {}",source.getTextName(),exception.toString());return 0;
        }
    }
    private static String id(CommandContext<CommandSourceStack> context) { return Habitat.normalizeId(StringArgumentType.getString(context,"id")); }
    private static Habitat required(CommandContext<CommandSourceStack> context) {
        Habitat value=HabitatRuntime.store().snapshot().named(id(context));
        if(value==null)throw new IllegalArgumentException("环境球不存在："+id(context));return value;
    }
    private static void validateSpace(CommandSourceStack source) {
        if(!ModDimensions.isSpace(source.getLevel()))throw new IllegalArgumentException("请在太阳系等太空维度使用此操作");
    }
    private static int update(CommandContext<CommandSourceStack> context,Habitat value,boolean replace,String action) throws Exception {
        HabitatRuntime.store().put(value,replace);
        HabitatRuntime.changed(context.getSource().getServer(),action+":"+value.id(),context.getSource().getTextName());
        describe(context.getSource(),value);return 1;
    }
    private static void describe(CommandSourceStack source,Habitat value) {
        message(source,value.id()+" | "+value.dimension()+" | "+value.bounds()+" | "+value.temperatureC()+"°C | "+value.gravityMultiplier()+"倍重力 | 地板距离 "+value.floorDistance()+"格 | 1atm可呼吸空气 | "+(value.enabled()?"启用":"停用"));
    }
    private static int create(CommandContext<CommandSourceStack> context) throws Exception {
        validateSpace(context.getSource());Vec3 point=context.getSource().getPosition();
        return update(context,new Habitat(id(context),context.getSource().getLevel().dimension().location().toString(),
                point.x,point.y,point.z,DoubleArgumentType.getDouble(context,"radius"),20,1,true),false,"create");
    }
    private static int check(CommandSourceStack source,ServerPlayer player) {
        Habitat value=HabitatRuntime.at(player);var environment=EnvironmentManager.of(player);
        message(source,player.getGameProfile().getName()+"：环境球="+(value==null?"无":value.id())+"，实际重力="+player.getGravity()
                +"，气压="+(environment==null?"默认":environment.pressure()+"atm")+"，可呼吸="+(environment==null||environment.breathable())
                +"，温度="+(environment==null?"默认":environment.temperature(player.level())+"°C")
                +"，固定地板="+(value==null?"区域外":FloorSupport.hasFloor(player,value.floorDistance())?"有":"无")
                +"，客户端配置="+(HabitatNetwork.acknowledged(player)?"已确认同步":"尚未确认"));return 1;
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root=literal("habitat").requires(source->source.hasPermission(2))
            .executes(c->{message(c.getSource(),"创建：/habitat create 名称 半径；其他命令：list / info / move / radius / temperature / gravity / floor-distance / enable / disable / delete / reload / check / resync");return 1;});
        root.then(literal("create").then(argument("id",StringArgumentType.word()).then(argument("radius",DoubleArgumentType.doubleArg(1,2048))
                .executes(c->guarded(c.getSource(),()->create(c))))));
        root.then(literal("list").executes(c->guarded(c.getSource(),()->list(c.getSource(),1)))
                .then(argument("page",IntegerArgumentType.integer(1)).executes(c->guarded(c.getSource(),()->list(c.getSource(),IntegerArgumentType.getInteger(c,"page"))))));
        root.then(literal("info").then(argument("id",StringArgumentType.word()).executes(c->guarded(c.getSource(),()->{describe(c.getSource(),required(c));return 1;}))));
        root.then(literal("move").then(argument("id",StringArgumentType.word()).executes(c->guarded(c.getSource(),()->{
            validateSpace(c.getSource());Vec3 point=c.getSource().getPosition();return update(c,required(c).move(c.getSource().getLevel().dimension().location().toString(),point.x,point.y,point.z),true,"move");
        }))));
        root.then(literal("radius").then(argument("id",StringArgumentType.word()).then(argument("value",DoubleArgumentType.doubleArg(1,2048))
                .executes(c->guarded(c.getSource(),()->update(c,required(c).radius(DoubleArgumentType.getDouble(c,"value")),true,"radius"))))));
        root.then(literal("temperature").then(argument("id",StringArgumentType.word()).then(argument("value",DoubleArgumentType.doubleArg(-273.15,1000))
                .executes(c->guarded(c.getSource(),()->update(c,required(c).temperature(DoubleArgumentType.getDouble(c,"value")),true,"temperature"))))));
        root.then(literal("gravity").then(argument("id",StringArgumentType.word()).then(argument("value",DoubleArgumentType.doubleArg(0,4))
                .executes(c->guarded(c.getSource(),()->update(c,required(c).gravity(DoubleArgumentType.getDouble(c,"value")),true,"gravity"))))));
        root.then(literal("floor-distance").then(argument("id",StringArgumentType.word()).then(argument("value",DoubleArgumentType.doubleArg(0,32))
                .executes(c->guarded(c.getSource(),()->update(c,required(c).floorDistance(DoubleArgumentType.getDouble(c,"value")),true,"floor-distance"))))));
        for(boolean enabled:new boolean[]{true,false})root.then(literal(enabled?"enable":"disable").then(argument("id",StringArgumentType.word())
                .executes(c->guarded(c.getSource(),()->update(c,required(c).enabled(enabled),true,enabled?"enable":"disable")))));
        root.then(literal("delete").then(argument("id",StringArgumentType.word()).executes(c->guarded(c.getSource(),()->{
            if(required(c).core()!=null)throw new IllegalArgumentException("核心区域请拆除核心方块，或在界面中关闭环境");
            HabitatRuntime.store().delete(id(c));HabitatRuntime.changed(c.getSource().getServer(),"delete:"+id(c),c.getSource().getTextName());
            message(c.getSource(),"已删除环境球："+id(c));return 1;
        }))));
        root.then(literal("reload").executes(c->guarded(c.getSource(),()->{
            HabitatRuntime.store().load();HabitatRuntime.changed(c.getSource().getServer(),"reload",c.getSource().getTextName());
            message(c.getSource(),"环境球配置已重载");return 1;
        })));
        root.then(literal("resync").executes(c->{HabitatRuntime.changed(c.getSource().getServer(),"resync",c.getSource().getTextName());message(c.getSource(),"已重新发送环境球配置");return 1;}));
        root.then(literal("check").executes(c->guarded(c.getSource(),()->check(c.getSource(),c.getSource().getPlayerOrException())))
                .then(argument("player",EntityArgument.player()).executes(c->guarded(c.getSource(),()->check(c.getSource(),EntityArgument.getPlayer(c,"player"))))));
        dispatcher.register(root);
    }
    private static int list(CommandSourceStack source,int page) {
        var values=HabitatRuntime.store().snapshot().habitats();int pages=Math.max(1,(values.size()+7)/8);
        if(page>pages)throw new IllegalArgumentException("页数超出范围，总页数："+pages);
        message(source,"共"+values.size()+"个环境球，第"+page+"/"+pages+"页");
        for(int i=(page-1)*8;i<Math.min(page*8,values.size());i++)describe(source,values.get(i));return 1;
    }
}
