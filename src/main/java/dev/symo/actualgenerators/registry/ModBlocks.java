package dev.symo.actualgenerators.registry;

import dev.symo.actualgenerators.machine.multiblock.GeothermalCasingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import dev.symo.actualgenerators.generator.GeothermalTapBlock;
import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.generator.CorrosionCellBlock;
import dev.symo.actualgenerators.generator.EnchantmentCombustorBlock;
import dev.symo.actualgenerators.generator.HydrostaticGeneratorBlock;
import dev.symo.actualgenerators.generator.ImpactDynamoBlock;
import dev.symo.actualgenerators.generator.PhotovoreBlock;
import dev.symo.actualgenerators.generator.SpawnerSiphonBlock;
import dev.symo.actualgenerators.logistics.EnergyInjectorBlock;
import dev.symo.actualgenerators.logistics.LinkPortBlock;
import dev.symo.actualgenerators.processing.ResonanceCrusherBlock;
import dev.symo.actualgenerators.storage.CrystalChargerBlock;
import dev.symo.actualgenerators.storage.SurgeBankBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import dev.symo.actualgenerators.generator.AnnihilationFurnaceBlock;
import dev.symo.actualgenerators.machine.multiblock.HatchBlock;
import dev.symo.actualgenerators.machine.multiblock.HatchKind;
import dev.symo.actualgenerators.machine.multiblock.MultiblockCasingBlock;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ActualGenerators.MODID);

    /** Oxidises copper for FE. */
    public static final DeferredBlock<CorrosionCellBlock> CORROSION_CELL = BLOCKS.registerBlock(
            "corrosion_cell",
            CorrosionCellBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER));

    /** Turns the pressure of a flooded shaft into FE. */
    public static final DeferredBlock<HydrostaticGeneratorBlock> HYDROSTATIC_GENERATOR = BLOCKS.registerBlock(
            "hydrostatic_generator",
            HydrostaticGeneratorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** The wellhead on a plate of Geothermal Casing over the heat pocket under its chunk; leaves Corium behind. */
    public static final DeferredBlock<GeothermalTapBlock> GEOTHERMAL_TAP = BLOCKS.registerBlock(
            "geothermal_tap",
            GeothermalTapBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** Corium in the world: lava that never spreads its source, with the furnace as its only use. */
    public static final DeferredBlock<LiquidBlock> CORIUM = BLOCKS.registerBlock(
            "corium",
            properties -> new LiquidBlock(ModFluids.CORIUM.get(), properties),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .replaceable()
                    .noCollission()
                    .randomTicks()
                    .strength(100.0F)
                    .lightLevel(state -> 12)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .sound(SoundType.EMPTY));

    /** Eats the lighting around it. */
    public static final DeferredBlock<PhotovoreBlock> PHOTOVORE = BLOCKS.registerBlock(
            "photovore",
            PhotovoreBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(2.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** Catches falling blocks and keeps both the impact and the block. */
    public static final DeferredBlock<ImpactDynamoBlock> IMPACT_DYNAMO = BLOCKS.registerBlock(
            "impact_dynamo",
            ImpactDynamoBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.ANVIL));

    /** Holds a mob spawner shut and banks what it would have spawned. */
    public static final DeferredBlock<SpawnerSiphonBlock> SPAWNER_SIPHON = BLOCKS.registerBlock(
            "spawner_siphon",
            SpawnerSiphonBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(4.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** Burns the enchantments off gear and hands the gear back. */
    public static final DeferredBlock<EnchantmentCombustorBlock> ENCHANTMENT_COMBUSTOR = BLOCKS.registerBlock(
            "enchantment_combustor",
            EnchantmentCombustorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_MAGENTA)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** Holds a very large charge, hands it back very fast, and bleeds it if left alone. */
    public static final DeferredBlock<SurgeBankBlock> SURGE_BANK = BLOCKS.registerBlock(
            "surge_bank",
            SurgeBankBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(4.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** Charges and discharges flux crystals a whole stack at a time. */
    public static final DeferredBlock<CrystalChargerBlock> CRYSTAL_CHARGER = BLOCKS.registerBlock(
            "crystal_charger",
            CrystalChargerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** Shakes ore apart at the frequency it rings at, once it has worked out what that is. */
    public static final DeferredBlock<ResonanceCrusherBlock> RESONANCE_CRUSHER = BLOCKS.registerBlock(
            "resonance_crusher",
            ResonanceCrusherBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** One end of a wireless network, stuck flat to the face of whatever it moves things for. */
    public static final DeferredBlock<LinkPortBlock> LOGIC_PORT = BLOCKS.registerBlock(
            "logic_port",
            LinkPortBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY));

    /** What pays for a wireless network: link one on, keep it fed, and the pads work. */
    public static final DeferredBlock<EnergyInjectorBlock> ENERGY_INJECTOR = BLOCKS.registerBlock(
            "energy_injector",
            EnergyInjectorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    // ---------------------------------------------------------------- multiblocks

    /** The wall block of every multiblock. No block entity, no tick. */
    public static final DeferredBlock<MultiblockCasingBlock> MACHINE_CASING = BLOCKS.registerBlock(
            "machine_casing",
            MultiblockCasingBlock::new,
            shell());

    /** The plate under a Geothermal Fissure Tap: the bore, laid on the ground. Same block, different look. */
    public static final DeferredBlock<GeothermalCasingBlock> GEOTHERMAL_CASING = BLOCKS.registerBlock(
            "geothermal_casing",
            GeothermalCasingBlock::new,
            shell());

    /** Items in and out of the controller, through the shell. */
    public static final DeferredBlock<HatchBlock> ITEM_HATCH = BLOCKS.registerBlock(
            "item_hatch",
            properties -> new HatchBlock(HatchKind.ITEM, properties),
            shell());

    /** The controller's FE buffer, through the shell. */
    public static final DeferredBlock<HatchBlock> ENERGY_HATCH = BLOCKS.registerBlock(
            "energy_hatch",
            properties -> new HatchBlock(HatchKind.ENERGY, properties),
            shell());

    /** A redstone signal for the controller, read where the hatch is. */
    public static final DeferredBlock<HatchBlock> REDSTONE_HATCH = BLOCKS.registerBlock(
            "redstone_hatch",
            properties -> new HatchBlock(HatchKind.REDSTONE, properties),
            shell());

    /** The controller's tank, through the shell, for the machines that have one. */
    public static final DeferredBlock<HatchBlock> FLUID_HATCH = BLOCKS.registerBlock(
            "fluid_hatch",
            properties -> new HatchBlock(HatchKind.FLUID, properties),
            shell());

    /** Mass into energy: the controller of a box of casing that eats blocks by their hardness. */
    public static final DeferredBlock<AnnihilationFurnaceBlock> ANNIHILATION_FURNACE = BLOCKS.registerBlock(
            "annihilation_furnace",
            AnnihilationFurnaceBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(5.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .pushReaction(PushReaction.BLOCK));

    /**
     * Shell blocks: heavy, and never moved by a piston. A piston that could push a casing out of
     * a wall would be a way to put a block inside a sealed box without the controller hearing.
     */
    private static BlockBehaviour.Properties shell() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(4.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                .pushReaction(PushReaction.BLOCK);
    }

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
