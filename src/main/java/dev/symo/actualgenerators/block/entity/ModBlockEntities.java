package dev.symo.actualgenerators.block.entity;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.block.ModBlocks;
import dev.symo.actualgenerators.block.entity.pipe.ItemPipeBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ActualGenerators.MOD_ID);

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }    public static final RegistryObject<BlockEntityType<ItemPipeBlockEntity>> ITEM_PIPE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("item_pipe_block_entity", () ->
                    BlockEntityType.Builder.of(ItemPipeBlockEntity::new,
                            ModBlocks.ITEM_PIPE_BLOCK.get()).build(null));



}
