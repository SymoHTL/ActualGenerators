package dev.symo.actualgenerators.registry;

import dev.symo.actualgenerators.generator.GeothermalTapBlockEntity;
import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.generator.CorrosionCellBlockEntity;
import dev.symo.actualgenerators.generator.EnchantmentCombustorBlockEntity;
import dev.symo.actualgenerators.generator.HydrostaticGeneratorBlockEntity;
import dev.symo.actualgenerators.generator.ImpactDynamoBlockEntity;
import dev.symo.actualgenerators.generator.PhotovoreBlockEntity;
import dev.symo.actualgenerators.generator.SpawnerSiphonBlockEntity;
import dev.symo.actualgenerators.logistics.EnergyInjectorBlockEntity;
import dev.symo.actualgenerators.logistics.LinkPortBlockEntity;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.processing.ResonanceCrusherBlockEntity;
import dev.symo.actualgenerators.storage.CrystalChargerBlockEntity;
import dev.symo.actualgenerators.storage.SurgeBankBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import dev.symo.actualgenerators.generator.AnnihilationFurnaceBlockEntity;
import dev.symo.actualgenerators.machine.multiblock.HatchBlockEntity;

/**
 * Block entity registration.
 *
 * <p>Machines are registered through {@link #registerMachine} rather than directly, so that
 * every machine automatically gets its energy capability wired to its per-face configuration
 * without each one repeating the boilerplate.
 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ActualGenerators.MODID);

    private static final List<Supplier<? extends BlockEntityType<? extends MachineBlockEntity>>> MACHINE_TYPES =
            new ArrayList<>();

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CorrosionCellBlockEntity>> CORROSION_CELL =
            registerMachine("corrosion_cell", CorrosionCellBlockEntity::new, ModBlocks.CORROSION_CELL);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HydrostaticGeneratorBlockEntity>>
            HYDROSTATIC_GENERATOR = registerMachine(
                    "hydrostatic_generator", HydrostaticGeneratorBlockEntity::new, ModBlocks.HYDROSTATIC_GENERATOR);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeothermalTapBlockEntity>> GEOTHERMAL_TAP =
            registerMachine("geothermal_tap", GeothermalTapBlockEntity::new, ModBlocks.GEOTHERMAL_TAP);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PhotovoreBlockEntity>> PHOTOVORE =
            registerMachine("photovore", PhotovoreBlockEntity::new, ModBlocks.PHOTOVORE);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ImpactDynamoBlockEntity>> IMPACT_DYNAMO =
            registerMachine("impact_dynamo", ImpactDynamoBlockEntity::new, ModBlocks.IMPACT_DYNAMO);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpawnerSiphonBlockEntity>> SPAWNER_SIPHON =
            registerMachine("spawner_siphon", SpawnerSiphonBlockEntity::new, ModBlocks.SPAWNER_SIPHON);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnchantmentCombustorBlockEntity>>
            ENCHANTMENT_COMBUSTOR = registerMachine(
                    "enchantment_combustor", EnchantmentCombustorBlockEntity::new, ModBlocks.ENCHANTMENT_COMBUSTOR);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyInjectorBlockEntity>>
            ENERGY_INJECTOR = registerMachine("energy_injector", EnergyInjectorBlockEntity::new,
            ModBlocks.ENERGY_INJECTOR);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SurgeBankBlockEntity>> SURGE_BANK =
            registerMachine("surge_bank", SurgeBankBlockEntity::new, ModBlocks.SURGE_BANK);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrystalChargerBlockEntity>>
            CRYSTAL_CHARGER = registerMachine(
                    "crystal_charger", CrystalChargerBlockEntity::new, ModBlocks.CRYSTAL_CHARGER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResonanceCrusherBlockEntity>>
            RESONANCE_CRUSHER = registerMachine(
                    "resonance_crusher", ResonanceCrusherBlockEntity::new, ModBlocks.RESONANCE_CRUSHER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AnnihilationFurnaceBlockEntity>>
            ANNIHILATION_FURNACE = registerMachine(
                    "annihilation_furnace", AnnihilationFurnaceBlockEntity::new, ModBlocks.ANNIHILATION_FURNACE);

    /** One type for every hatch: the block says what kind, the entity only remembers its controller. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HatchBlockEntity>> HATCH =
            BLOCK_ENTITIES.register("hatch",
                    () -> BlockEntityType.Builder.of(HatchBlockEntity::new,
                            ModBlocks.ITEM_HATCH.get(), ModBlocks.ENERGY_HATCH.get(), ModBlocks.REDSTONE_HATCH.get(),
                            ModBlocks.FLUID_HATCH.get())
                            .build(null));

    /**
     * The Logic Port is not a machine: it has no buffer, no upgrades of the machine kind and no
     * tick, so it is registered plainly rather than through {@link #registerMachine}.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LinkPortBlockEntity>> LOGIC_PORT =
            BLOCK_ENTITIES.register("logic_port",
                    () -> BlockEntityType.Builder.of(LinkPortBlockEntity::new, ModBlocks.LOGIC_PORT.get()).build(null));

    private ModBlockEntities() {
    }

    /** Registers a machine block entity and remembers it for capability registration. */
    public static <T extends MachineBlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> registerMachine(
            String name,
            BlockEntityType.BlockEntitySupplier<T> factory,
            Supplier<? extends Block> block) {
        DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> holder = BLOCK_ENTITIES.register(
                name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
        MACHINE_TYPES.add(holder);
        return holder;
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }

    /** Exposes every machine's energy and inventory, filtered by each face's configured mode. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type : MACHINE_TYPES) {
            registerMachineCapabilities(event, type.get());
        }
        // A pad is a door for whatever the block behind it pushes: no inventory, straight onto the network.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LOGIC_PORT.get(), LinkPortBlockEntity::passthrough);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, LOGIC_PORT.get(), LinkPortBlockEntity::passthrough);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, LOGIC_PORT.get(), LinkPortBlockEntity::passthrough);
        // A hatch is a proxy for its controller: present whatever the structure is doing, empty
        // until it is formed, so nothing ever has to be invalidated.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, HATCH.get(), HatchBlockEntity::items);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, HATCH.get(), HatchBlockEntity::energy);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, HATCH.get(), HatchBlockEntity::fluids);
    }

    private static <T extends MachineBlockEntity> void registerMachineCapabilities(RegisterCapabilitiesEvent event,
                                                                                   BlockEntityType<T> type) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                type,
                (machine, side) -> machine.energyForSide(side));
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                type,
                (machine, side) -> machine.fluidsForSide(side));
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                type,
                (machine, side) -> machine.itemsForSide(side));
    }
}
