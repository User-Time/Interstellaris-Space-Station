package net.time_cloud;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

public final class StationContent {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,HabitatRuntime.ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,HabitatRuntime.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,HabitatRuntime.ID);
    public static final DeferredHolder<Block,StationCoreBlock> CORE=BLOCKS.register("station_core",()->new StationCoreBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)));
    public static final DeferredHolder<Item,BlockItem> CORE_ITEM=ITEMS.register("station_core",()->new BlockItem(CORE.get(),new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<StationCoreBlockEntity>> CORE_ENTITY=ENTITIES.register("station_core",()->BlockEntityType.Builder.of(StationCoreBlockEntity::new,CORE.get()).build(null));
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);bus.addListener(StationContent::creative);}
    private static void creative(BuildCreativeModeTabContentsEvent event){if(event.getTabKey().location().toString().equals("minecraft:functional_blocks"))event.accept(CORE_ITEM.get());}
    private StationContent(){}
}
