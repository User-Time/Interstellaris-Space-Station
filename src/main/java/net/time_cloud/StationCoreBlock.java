package net.time_cloud;

import com.interstellaris.mod.worldgen.ModDimensions;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class StationCoreBlock extends BaseEntityBlock {
    public static final MapCodec<StationCoreBlock> CODEC=simpleCodec(StationCoreBlock::new);
    public StationCoreBlock(Properties properties){super(properties);}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new StationCoreBlockEntity(pos,state);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        if(!ModDimensions.isSpace(context.getLevel()))return null;
        var p=context.getClickedPos().getCenter();var global=HabitatRuntime.worldPosition(context.getLevel(),p);
        if(global.distanceSquared(p.x,p.y,p.z)>1e-4)return null;
        return super.getStateForPlacement(context);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);
        if(placer instanceof ServerPlayer player&&level.getBlockEntity(pos) instanceof StationCoreBlockEntity core)StationRuntime.claim(core,player);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player instanceof ServerPlayer serverPlayer&&level.getBlockEntity(pos) instanceof StationCoreBlockEntity core)StationRuntime.open(core,serverPlayer);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!level.isClientSide&&!state.is(next.getBlock())&&level.getBlockEntity(pos) instanceof StationCoreBlockEntity core)StationRuntime.removed(core);
        super.onRemove(state,level,pos,next,moving);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        return level.isClientSide?null:createTickerHelper(type,StationContent.CORE_ENTITY.get(),(l,p,s,core)->core.serverTick());
    }
    @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("tooltip.potg_station_habitat.core"));
        lines.add(Component.translatable("tooltip.potg_station_habitat.fixed"));
    }
}
