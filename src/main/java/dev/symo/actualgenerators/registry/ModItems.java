package dev.symo.actualgenerators.registry;

import dev.symo.actualgenerators.machine.multiblock.HatchBlockItem;
import dev.symo.actualgenerators.item.ThermalProbeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BucketItem;
import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.item.ConfigCardItem;
import dev.symo.actualgenerators.item.FilterItem;
import dev.symo.actualgenerators.item.FluxCouplerItem;
import dev.symo.actualgenerators.item.FluxCrystalItem;
import dev.symo.actualgenerators.item.ItemEnergyStorage;
import dev.symo.actualgenerators.item.LinkUpgradeItem;
import dev.symo.actualgenerators.item.LinkingToolItem;
import dev.symo.actualgenerators.item.TierUpgradeItem;
import dev.symo.actualgenerators.item.UpgradeItem;
import dev.symo.actualgenerators.machine.MachineTier;
import dev.symo.actualgenerators.machine.UpgradeType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ActualGenerators.MODID);

    /** Raises FE throughput and buffer size. */
    public static final DeferredItem<UpgradeItem> ENERGY_UPGRADE =
            ITEMS.registerItem("energy_upgrade", properties -> new UpgradeItem(UpgradeType.ENERGY, properties));

    /** Raises base speed, up to the ceiling. */
    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE =
            ITEMS.registerItem("speed_upgrade", properties -> new UpgradeItem(UpgradeType.SPEED, properties));

    /** Lets a working machine ramp past the speed ceiling. */
    public static final DeferredItem<UpgradeItem> OVERCLOCK_UPGRADE =
            ITEMS.registerItem("overclock_upgrade", properties -> new UpgradeItem(UpgradeType.OVERCLOCK, properties));

    /** Processes more items per operation rather than more often. */
    public static final DeferredItem<UpgradeItem> STACK_UPGRADE =
            ITEMS.registerItem("stack_upgrade", properties -> new UpgradeItem(UpgradeType.STACK, properties));

    /** The grades of a block itself, one per machine or pad; see {@link TierUpgradeItem}. */
    public static final DeferredItem<TierUpgradeItem> IRON_TIER_UPGRADE =
            ITEMS.registerItem("iron_tier_upgrade", properties -> new TierUpgradeItem(MachineTier.IRON, properties));
    public static final DeferredItem<TierUpgradeItem> GOLD_TIER_UPGRADE =
            ITEMS.registerItem("gold_tier_upgrade", properties -> new TierUpgradeItem(MachineTier.GOLD, properties));
    public static final DeferredItem<TierUpgradeItem> DIAMOND_TIER_UPGRADE =
            ITEMS.registerItem("diamond_tier_upgrade", properties -> new TierUpgradeItem(MachineTier.DIAMOND, properties));
    public static final DeferredItem<TierUpgradeItem> NETHERITE_TIER_UPGRADE =
            ITEMS.registerItem("netherite_tier_upgrade", properties -> new TierUpgradeItem(MachineTier.NETHERITE, properties));

    /** Copies a machine's whole configuration onto another machine. */
    public static final DeferredItem<ConfigCardItem> CONFIG_CARD =
            ITEMS.registerItem("config_card", properties -> new ConfigCardItem(properties.stacksTo(1)));

    /** Reads the heat pocket under the chunk the player stands in, before a tap is built. */
    public static final DeferredItem<ThermalProbeItem> THERMAL_PROBE =
            ITEMS.registerItem("thermal_probe", properties -> new ThermalProbeItem(properties.stacksTo(1)));

    /**
     * Crushed metal, one step short of an ingot.
     *
     * <p>The dust tier is what lets the crusher pay out on raw ore without minting metal: raw iron
     * cannot crush into more raw iron, so it crushes into something that smelts back into one.
     * Registered under {@code c:dusts/<metal>}, so another mod's dust smelts the same way.
     */
    public static final DeferredItem<Item> IRON_DUST = ITEMS.registerSimpleItem("iron_dust");
    public static final DeferredItem<Item> COPPER_DUST = ITEMS.registerSimpleItem("copper_dust");
    public static final DeferredItem<Item> GOLD_DUST = ITEMS.registerSimpleItem("gold_dust");

    /** The casing every machine is built around: one recipe to rebalance, not nine. */
    public static final DeferredItem<Item> MACHINE_FRAME = ITEMS.registerSimpleItem("machine_frame");

    /** Puts pads and injectors on networks, and shows every network in view while held. */
    public static final DeferredItem<LinkingToolItem> LINKING_TOOL =
            ITEMS.registerItem("linking_tool", LinkingToolItem::new);

    /** Two lists of what a pad lets through on a channel. Opens on use; copies and clears in a crafting grid. */
    public static final DeferredItem<FilterItem> FILTER =
            ITEMS.registerItem("filter", FilterItem::new);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> CORROSION_CELL =
            ITEMS.registerSimpleBlockItem(ModBlocks.CORROSION_CELL);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> HYDROSTATIC_GENERATOR =
            ITEMS.registerSimpleBlockItem(ModBlocks.HYDROSTATIC_GENERATOR);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> GEOTHERMAL_TAP =
            ITEMS.registerSimpleBlockItem(ModBlocks.GEOTHERMAL_TAP);

    /** A bucket of Corium: the tap fills it, the furnace floor takes it. Never crafted. */
    public static final DeferredItem<BucketItem> CORIUM_BUCKET = ITEMS.registerItem("corium_bucket",
            properties -> new BucketItem(ModFluids.CORIUM.get(), properties.craftRemainder(Items.BUCKET).stacksTo(1)));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> PHOTOVORE =
            ITEMS.registerSimpleBlockItem(ModBlocks.PHOTOVORE);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> IMPACT_DYNAMO =
            ITEMS.registerSimpleBlockItem(ModBlocks.IMPACT_DYNAMO);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> SPAWNER_SIPHON =
            ITEMS.registerSimpleBlockItem(ModBlocks.SPAWNER_SIPHON);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> ENCHANTMENT_COMBUSTOR =
            ITEMS.registerSimpleBlockItem(ModBlocks.ENCHANTMENT_COMBUSTOR);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> ENERGY_INJECTOR =
            ITEMS.registerSimpleBlockItem(ModBlocks.ENERGY_INJECTOR);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> SURGE_BANK =
            ITEMS.registerSimpleBlockItem(ModBlocks.SURGE_BANK);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> RESONANCE_CRUSHER =
            ITEMS.registerSimpleBlockItem(ModBlocks.RESONANCE_CRUSHER);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> CRYSTAL_CHARGER =
            ITEMS.registerSimpleBlockItem(ModBlocks.CRYSTAL_CHARGER);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> LOGIC_PORT =
            ITEMS.registerSimpleBlockItem(ModBlocks.LOGIC_PORT);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> MACHINE_CASING =
            ITEMS.registerSimpleBlockItem(ModBlocks.MACHINE_CASING);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> GEOTHERMAL_CASING =
            ITEMS.registerSimpleBlockItem(ModBlocks.GEOTHERMAL_CASING);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> ITEM_HATCH =
            ITEMS.registerItem("item_hatch", properties -> new HatchBlockItem(ModBlocks.ITEM_HATCH.get(), properties));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> ENERGY_HATCH =
            ITEMS.registerItem("energy_hatch", properties -> new HatchBlockItem(ModBlocks.ENERGY_HATCH.get(), properties));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> REDSTONE_HATCH =
            ITEMS.registerItem("redstone_hatch", properties -> new HatchBlockItem(ModBlocks.REDSTONE_HATCH.get(), properties));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> FLUID_HATCH =
            ITEMS.registerItem("fluid_hatch", properties -> new HatchBlockItem(ModBlocks.FLUID_HATCH.get(), properties));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> ANNIHILATION_FURNACE =
            ITEMS.registerSimpleBlockItem(ModBlocks.ANNIHILATION_FURNACE);

    /** Reach, one tier at a time. The last one a port will hold also reaches into other dimensions. */
    public static final DeferredItem<LinkUpgradeItem> LINK_RANGE_UPGRADE =
            ITEMS.registerItem("link_range_upgrade", LinkUpgradeItem::new);

    /**
     * Unlimited reach inside one dimension, and a network tolerates exactly one.
     *
     * <p>That is the trade the card exists for: enormous power for a hard constraint, rather than
     * one more tier of the same thing.
     */
    public static final DeferredItem<LinkUpgradeItem> UNBOUND_LINK_CARD =
            ITEMS.registerItem("unbound_link_card", properties -> new LinkUpgradeItem(properties.stacksTo(1)));

    /** Energy as an item: a standard FE battery that stacks. */
    public static final DeferredItem<FluxCrystalItem> FLUX_CRYSTAL =
            ITEMS.registerItem("flux_crystal", FluxCrystalItem::new);

    /** Carries crystals and keeps the rest of the inventory charged from them. */
    public static final DeferredItem<FluxCouplerItem> FLUX_COUPLER =
            ITEMS.registerItem("flux_coupler", FluxCouplerItem::new);

    /** The item that installs a given upgrade type, so screens can name a slot's contents. */
    public static DeferredItem<UpgradeItem> upgradeItem(UpgradeType type) {
        return switch (type) {
            case ENERGY -> ENERGY_UPGRADE;
            case SPEED -> SPEED_UPGRADE;
            case OVERCLOCK -> OVERCLOCK_UPGRADE;
            case STACK -> STACK_UPGRADE;
        };
    }

    /**
     * Exposes a flux crystal's charge through the standard FE item capability.
     *
     * <p>Only at a stack size of one. That is the convention every battery item follows, and it is
     * what stops a charger from treating a stack of sixty-four as a single cell — the whole stack
     * shares one set of components, so filling "the stack" would fill all of them for the price of
     * one. The {@code Crystal Charger} is the block that does stacks, and it does the arithmetic
     * properly.
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(
                Capabilities.EnergyStorage.ITEM,
                (stack, context) -> stack.getCount() != 1
                        ? null
                        : new ItemEnergyStorage(stack, FluxCrystalItem.capacity(), FluxCrystalItem.transferRate()),
                FLUX_CRYSTAL.get());
    }

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
