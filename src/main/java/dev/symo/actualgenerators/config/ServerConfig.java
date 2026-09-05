package dev.symo.actualgenerators.config;

import dev.symo.actualgenerators.machine.MachineTuning;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server-side balance configuration. Every number a pack maintainer might want to move lives
 * here rather than in code.
 *
 * <p>{@link #tuning()} caches the derived {@link MachineTuning} because machines ask for it
 * every tick; {@link #invalidate()} drops the cache when the config is loaded or reloaded.
 */
public final class ServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue SPEED_PER_UPGRADE;
    public static final ModConfigSpec.IntValue MAX_SPEED_UPGRADES;
    public static final ModConfigSpec.DoubleValue OVERCLOCK_PER_UPGRADE;
    public static final ModConfigSpec.IntValue MAX_OVERCLOCK_UPGRADES;
    public static final ModConfigSpec.DoubleValue ENERGY_EXPONENT;
    public static final ModConfigSpec.DoubleValue CAPACITY_PER_ENERGY_UPGRADE;
    public static final ModConfigSpec.DoubleValue TRANSFER_PER_ENERGY_UPGRADE;
    public static final ModConfigSpec.IntValue MAX_ENERGY_UPGRADES;
    public static final ModConfigSpec.IntValue BATCH_PER_STACK_UPGRADE;
    public static final ModConfigSpec.IntValue MAX_STACK_UPGRADES;
    public static final ModConfigSpec.IntValue SLOT_OPERATIONS;
    public static final ModConfigSpec.ConfigValue<List<? extends Number>> MACHINE_TIER_SPEED_MULTIPLIER;
    public static final ModConfigSpec.ConfigValue<List<? extends Number>> MACHINE_TIER_BATCH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue GENERATOR_SPEED_PENALTY;
    public static final ModConfigSpec.DoubleValue GENERATOR_WARMUP_FLOOR;
    public static final ModConfigSpec.IntValue OVERCLOCK_RAMP_TICKS;
    public static final ModConfigSpec.IntValue OVERCLOCK_DECAY_TICKS;
    public static final ModConfigSpec.IntValue AUTO_IO_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue IDLE_RECHECK_TICKS;

    public static final ModConfigSpec.IntValue CORROSION_CELL_FE_PER_TICK;
    public static final ModConfigSpec.IntValue CORROSION_CELL_TICKS_PER_STAGE;
    public static final ModConfigSpec.LongValue CORROSION_CELL_CAPACITY;
    public static final ModConfigSpec.IntValue CORROSION_CELL_TRANSFER;

    public static final ModConfigSpec.IntValue HYDROSTATIC_FE_PER_COLUMN_BLOCK;
    public static final ModConfigSpec.IntValue HYDROSTATIC_MAX_COLUMN;
    public static final ModConfigSpec.IntValue HYDROSTATIC_COLUMN_RECHECK_TICKS;
    public static final ModConfigSpec.LongValue HYDROSTATIC_CAPACITY;
    public static final ModConfigSpec.IntValue HYDROSTATIC_TRANSFER;

    public static final ModConfigSpec.IntValue PHOTOVORE_FE_PER_LIGHT_LEVEL;
    public static final ModConfigSpec.IntValue PHOTOVORE_RADIUS;
    public static final ModConfigSpec.IntValue PHOTOVORE_GRAZE_TICKS;
    public static final ModConfigSpec.IntValue PHOTOVORE_SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.LongValue PHOTOVORE_CAPACITY;
    public static final ModConfigSpec.IntValue PHOTOVORE_TRANSFER;

    public static final ModConfigSpec.IntValue IMPACT_DYNAMO_FE_PER_BLOCK_FALLEN;
    public static final ModConfigSpec.IntValue IMPACT_DYNAMO_MAX_FALL_DISTANCE;
    public static final ModConfigSpec.IntValue IMPACT_DYNAMO_WARM_TICKS;
    public static final ModConfigSpec.LongValue IMPACT_DYNAMO_CAPACITY;
    public static final ModConfigSpec.IntValue IMPACT_DYNAMO_TRANSFER;

    public static final ModConfigSpec.IntValue SPAWNER_SIPHON_FE_PER_SPAWN;
    public static final ModConfigSpec.IntValue SPAWNER_SIPHON_SAMPLE_INTERVAL_TICKS;
    public static final ModConfigSpec.LongValue SPAWNER_SIPHON_CAPACITY;
    public static final ModConfigSpec.IntValue SPAWNER_SIPHON_TRANSFER;

    public static final ModConfigSpec.IntValue COMBUSTOR_FE_PER_ENCHANTMENT_LEVEL;
    public static final ModConfigSpec.IntValue COMBUSTOR_TICKS_PER_ITEM;
    public static final ModConfigSpec.LongValue COMBUSTOR_CAPACITY;
    public static final ModConfigSpec.IntValue COMBUSTOR_TRANSFER;

    public static final ModConfigSpec.IntValue CRUSHER_FE_PER_TICK;
    public static final ModConfigSpec.IntValue CRUSHER_TICKS;
    public static final ModConfigSpec.IntValue CRUSHER_CALIBRATION_MULTIPLIER;
    public static final ModConfigSpec.IntValue CRUSHER_BONUS_PERMILLE;
    public static final ModConfigSpec.LongValue CRUSHER_CAPACITY;
    public static final ModConfigSpec.IntValue CRUSHER_TRANSFER;

    public static final ModConfigSpec.LongValue SURGE_BANK_CAPACITY;
    public static final ModConfigSpec.IntValue SURGE_BANK_TRANSFER;
    public static final ModConfigSpec.IntValue SURGE_BANK_LEAK_PERMILLE;
    public static final ModConfigSpec.IntValue SURGE_BANK_LEAK_IDLE_TICKS;

    public static final ModConfigSpec.LongValue FLUX_CRYSTAL_CAPACITY;
    public static final ModConfigSpec.IntValue FLUX_CRYSTAL_TRANSFER;
    public static final ModConfigSpec.LongValue CRYSTAL_CHARGER_CAPACITY;
    public static final ModConfigSpec.IntValue CRYSTAL_CHARGER_TRANSFER;

    public static final ModConfigSpec.IntValue FLUX_COUPLER_INTERVAL;

    public static final ModConfigSpec.IntValue LINK_IDLE_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue LINK_AMOUNT_ITEMS;
    public static final ModConfigSpec.IntValue LINK_AMOUNT_MILLIBUCKETS;
    public static final ModConfigSpec.IntValue LINK_AMOUNT_ENERGY;
    public static final ModConfigSpec.ConfigValue<List<? extends Number>> LINK_TIER_AMOUNT_MULTIPLIER;
    public static final ModConfigSpec.ConfigValue<List<? extends Number>> LINK_TIER_DELAY_TICKS;
    public static final ModConfigSpec.IntValue LINK_BASE_RANGE;
    public static final ModConfigSpec.IntValue LINK_RANGE_PER_UPGRADE;
    public static final ModConfigSpec.IntValue LINK_MAX_RANGE_UPGRADES;
    public static final ModConfigSpec.IntValue LINK_FE_PER_ITEM;
    public static final ModConfigSpec.IntValue LINK_FE_PER_BUCKET;
    public static final ModConfigSpec.IntValue LINK_FE_PERMILLE_OF_ENERGY;
    public static final ModConfigSpec.LongValue LINK_INJECTOR_CAPACITY;
    public static final ModConfigSpec.IntValue LINK_INJECTOR_TRANSFER;

    public static final ModConfigSpec SPEC;

    private static volatile MachineTuning cachedTuning;

    static {
        BUILDER.comment("Machine framework balance. These apply to every machine in the mod.")
                .push("machines");

        BUILDER.comment("Speed gained per speed upgrade, as a fraction of base speed.");
        SPEED_PER_UPGRADE = BUILDER.defineInRange("speedPerUpgrade", 0.5, 0.0, 16.0);

        BUILDER.comment("Speed upgrades counted before the ceiling is hit. Overclocks scale past this.");
        MAX_SPEED_UPGRADES = BUILDER.defineInRange("maxSpeedUpgrades", 4, 0, 64);

        BUILDER.comment("Extra speed a fully ramped-up machine gains per overclock upgrade.");
        OVERCLOCK_PER_UPGRADE = BUILDER.defineInRange("overclockPerUpgrade", 0.5, 0.0, 16.0);

        BUILDER.comment("Overclock upgrades counted.");
        MAX_OVERCLOCK_UPGRADES = BUILDER.defineInRange("maxOverclockUpgrades", 4, 0, 64);

        BUILDER.comment(
                "Exponent applied to the speed multiplier when computing FE/t.",
                "2.0 means running at 3x speed costs 9x the power. Values below 1 make speed free; don't.");
        ENERGY_EXPONENT = BUILDER.defineInRange("energyExponent", 2.0, 0.1, 8.0);

        BUILDER.comment("Extra buffer capacity per energy upgrade, as a fraction of base capacity.");
        CAPACITY_PER_ENERGY_UPGRADE = BUILDER.defineInRange("capacityPerEnergyUpgrade", 1.0, 0.0, 64.0);

        BUILDER.comment("Extra FE/t throughput per energy upgrade, as a fraction of the base rate.");
        TRANSFER_PER_ENERGY_UPGRADE = BUILDER.defineInRange("transferPerEnergyUpgrade", 1.0, 0.0, 64.0);

        BUILDER.comment("Energy upgrades counted.");
        MAX_ENERGY_UPGRADES = BUILDER.defineInRange("maxEnergyUpgrades", 4, 0, 64);

        BUILDER.comment(
                "Extra items processed per operation per stack upgrade.",
                "Batching is the cheap way to raise throughput: a machine resolves at most one",
                "operation per tick regardless of batch size, so a big batch costs the server no",
                "more than a small one. Energy cost scales linearly with batch size.");
        BATCH_PER_STACK_UPGRADE = BUILDER.defineInRange("batchPerStackUpgrade", 2, 1, 64);

        BUILDER.comment("Stack upgrades counted. This caps how much work one machine can do in a tick.");
        MAX_STACK_UPGRADES = BUILDER.defineInRange("maxStackUpgrades", 4, 0, 64);

        BUILDER.comment(
                "How many operations' worth a machine's input and output slots hold, never less",
                "than a stack. Slots grow with the batch, so a machine fed by a pattern provider is",
                "capped by its batch and nothing else; more here is more buffer, not more speed.");
        SLOT_OPERATIONS = BUILDER.defineInRange("slotOperations", 8, 2, 256);

        BUILDER.comment(
                "Speed multiplier per tier of the machine block itself: none, iron, gold, diamond,",
                "netherite. Unlike a speed upgrade this is free: FE per operation does not change,",
                "which is the whole reason to build the better machine.");
        MACHINE_TIER_SPEED_MULTIPLIER = BUILDER.<Number>defineList("tierSpeedMultiplier",
                List.of(1.0, 1.5, 2.0, 3.0, 4.0), entry -> entry instanceof Number);

        BUILDER.comment(
                "Batch multiplier per tier of the machine block itself. A tier makes one operation",
                "bigger; it never adds a second processing line, so the per-tick cost is unchanged.");
        MACHINE_TIER_BATCH_MULTIPLIER = BUILDER.<Number>defineList("tierBatchMultiplier",
                List.of(1, 1, 2, 2, 3), entry -> entry instanceof Number);

        BUILDER.comment(
                "How much of a generator's yield per item is lost to running it fast.",
                "Generators cannot pay superlinearly for speed or overclocking would create energy,",
                "so speed raises their output sublinearly instead: faster burn, less total energy",
                "per item. 0 = speed is free, 1 = speed buys nothing. At 0.5, 9x speed yields 3x",
                "power while consuming fuel 9x as fast.");
        GENERATOR_SPEED_PENALTY = BUILDER.defineInRange("generatorSpeedPenalty", 0.5, 0.0, 1.0);

        BUILDER.comment(
                "What a warm-up generator makes when cold, as a fraction of its rating.",
                "Generators paid by the world rather than by fuel have no fuel to trade for speed,",
                "so their ramp climbs towards the rating instead of past it: at 0.5 a cold one makes",
                "half of what it is worth and one kept running makes all of it. 1.0 turns it off.");
        GENERATOR_WARMUP_FLOOR = BUILDER.defineInRange("generatorWarmupFloor", 0.5, 0.0, 1.0);

        BUILDER.comment("Ticks of continuous work needed to reach full overclock.");
        OVERCLOCK_RAMP_TICKS = BUILDER.defineInRange("overclockRampTicks", 1200, 1, 432000);

        BUILDER.comment(
                "Ticks for a full overclock to decay away once the machine has no work or no power.",
                "The ramp never resets between recipes -- only idling or losing power cools a machine down.");
        OVERCLOCK_DECAY_TICKS = BUILDER.defineInRange("overclockDecayTicks", 600, 1, 432000);

        BUILDER.comment("Ticks between automatic push/pull passes on configured faces.");
        AUTO_IO_INTERVAL_TICKS = BUILDER.defineInRange("autoIoIntervalTicks", 10, 1, 200);

        BUILDER.comment("Ticks an idle machine waits before looking for work again.");
        IDLE_RECHECK_TICKS = BUILDER.defineInRange("idleRecheckTicks", 20, 1, 200);

        BUILDER.pop();

        BUILDER.comment("Per-machine balance.").push("corrosion_cell");

        BUILDER.comment(
                "FE/t produced while oxidising, before speed and batch scaling.",
                "A low trickle for a long time: the cell is a slow burner rather than a burst",
                "generator, so the copper it converts is worth a lot and the rate never is.",
                "Fully sped up and overclocked one still only makes about 33 x sqrt(9) = 99 FE/t,",
                "and only stack upgrades take it past that.");
        CORROSION_CELL_FE_PER_TICK = BUILDER.defineInRange("energyPerTick", 33, 0, 1_000_000);

        BUILDER.comment(
                "Ticks to advance one copper item by one oxidation stage, at base speed.",
                "This is what decides the value of the copper rather than the rate: at the defaults",
                "one stage is worth 33 x 1200 = 39,600 FE, and a block taken all the way from fresh",
                "to oxidised is worth three stages, 118,800 FE. Running the cell hot cuts that yield",
                "-- speed pays sublinearly for generators -- so patience is the efficient play.");
        CORROSION_CELL_TICKS_PER_STAGE = BUILDER.defineInRange("ticksPerStage", 1200, 1, 72000);

        BUILDER.comment("Internal FE buffer, before energy upgrades.");
        CORROSION_CELL_CAPACITY = BUILDER.defineInRange("capacity", 100_000L, 0L, Long.MAX_VALUE);

        BUILDER.comment("FE/t each face may move, before energy upgrades.");
        CORROSION_CELL_TRANSFER = BUILDER.defineInRange("transferRate", 1_000, 0, Integer.MAX_VALUE);

        BUILDER.pop();

        BUILDER.push("hydrostatic_generator");

        BUILDER.comment(
                "FE/t earned per water block in the column above the generator.",
                "This generator burns no fuel, so it takes no speed or overclock upgrades --",
                "its output is whatever the shaft you flooded is worth, and nothing else.");
        HYDROSTATIC_FE_PER_COLUMN_BLOCK = BUILDER.defineInRange("energyPerColumnBlock", 2, 0, 1_000_000);

        BUILDER.comment("Water blocks counted above the generator. Deeper than this pays nothing more.");
        HYDROSTATIC_MAX_COLUMN = BUILDER.defineInRange("maxColumn", 16, 1, 384);

        BUILDER.comment(
                "Ticks between re-measurements of the water column.",
                "Breaking or placing right above the generator is noticed immediately; this only",
                "bounds how long a change further up the shaft can go unnoticed.");
        HYDROSTATIC_COLUMN_RECHECK_TICKS = BUILDER.defineInRange("columnRecheckTicks", 40, 1, 6000);

        BUILDER.comment("Internal FE buffer, before energy upgrades.");
        HYDROSTATIC_CAPACITY = BUILDER.defineInRange("capacity", 20_000L, 0L, Long.MAX_VALUE);

        BUILDER.comment("FE/t each face may move, before energy upgrades.");
        HYDROSTATIC_TRANSFER = BUILDER.defineInRange("transferRate", 200, 0, Integer.MAX_VALUE);

        BUILDER.pop();

        BUILDER.push("photovore");

        BUILDER.comment(
                "FE earned per point of light in a block it eats.",
                "At the defaults a torch (light 14) is worth 4,200 FE and glowstone (15) 4,500.");
        PHOTOVORE_FE_PER_LIGHT_LEVEL = BUILDER.defineInRange("energyPerLightLevel", 300, 0, 1_000_000);

        BUILDER.comment(
                "How far it reaches for light, in blocks. Raising this widens the periodic scan",
                "cubically, so it is the one number here with a real server cost.");
        PHOTOVORE_RADIUS = BUILDER.defineInRange("radius", 5, 1, 16);

        BUILDER.comment("Ticks to finish one meal, at base speed.");
        PHOTOVORE_GRAZE_TICKS = BUILDER.defineInRange("grazeTicks", 200, 1, 72000);

        BUILDER.comment(
                "Minimum ticks between searches for new light. Nothing tells a block that a torch",
                "went up six blocks away, so this is a poll -- but it only runs when the machine",
                "has run out of food and has room for power.");
        PHOTOVORE_SCAN_INTERVAL_TICKS = BUILDER.defineInRange("scanIntervalTicks", 200, 20, 72000);

        BUILDER.comment("Internal FE buffer, before energy upgrades.");
        PHOTOVORE_CAPACITY = BUILDER.defineInRange("capacity", 50_000L, 0L, Long.MAX_VALUE);

        BUILDER.comment("FE/t each face may move, before energy upgrades.");
        PHOTOVORE_TRANSFER = BUILDER.defineInRange("transferRate", 500, 0, Integer.MAX_VALUE);

        BUILDER.pop();

        BUILDER.push("impact_dynamo");

        BUILDER.comment(
                "FE earned per block of the drop, for anything that falls onto the dynamo.",
                "At the defaults a sand block dropped down a full 64-block shaft is worth 2,560 FE,",
                "and the sand itself lands in the dynamo's buffer rather than being destroyed.");
        IMPACT_DYNAMO_FE_PER_BLOCK_FALLEN = BUILDER.defineInRange("energyPerBlockFallen", 40, 0, 1_000_000);

        BUILDER.comment("The longest drop that still pays. Anything further is free height, not free power.");
        IMPACT_DYNAMO_MAX_FALL_DISTANCE = BUILDER.defineInRange("maxFallDistance", 64, 1, 384);

        BUILDER.comment(
                "How long one landing counts as work, for the warm-up ramp.",
                "A dynamo fed more often than this stays warm and climbs towards its full rate;",
                "one that is fed slower cools back down between blocks.");
        IMPACT_DYNAMO_WARM_TICKS = BUILDER.defineInRange("warmTicksPerImpact", 40, 1, 72000);

        BUILDER.comment("Internal FE buffer, before energy upgrades.");
        IMPACT_DYNAMO_CAPACITY = BUILDER.defineInRange("capacity", 30_000L, 0L, Long.MAX_VALUE);

        BUILDER.comment("FE/t each face may move, before energy upgrades.");
        IMPACT_DYNAMO_TRANSFER = BUILDER.defineInRange("transferRate", 400, 0, Integer.MAX_VALUE);

        BUILDER.pop();

        BUILDER.push("spawner_siphon");

        BUILDER.comment(
                "FE earned for each spawn the siphon holds back.",
                "The rate is read from the spawner itself -- spawn count divided by its average",
                "delay -- so a vanilla spawner (4 mobs every 200-800 ticks) is worth 40 FE/t and a",
                "spawner another mod has upgraded pays more without any code here knowing about it.");
        SPAWNER_SIPHON_FE_PER_SPAWN = BUILDER.defineInRange("energyPerSpawn", 5_000, 0, 1_000_000);

        BUILDER.comment(
                "Ticks between readings of the attached spawner.",
                "Each reading also pushes the spawner's countdown back out of reach, so this doubles",
                "as how long a spawner would have to slip if the siphon stopped.");
        SPAWNER_SIPHON_SAMPLE_INTERVAL_TICKS = BUILDER.defineInRange("sampleIntervalTicks", 40, 1, 6000);

        BUILDER.comment("Internal FE buffer, before energy upgrades.");
        SPAWNER_SIPHON_CAPACITY = BUILDER.defineInRange("capacity", 100_000L, 0L, Long.MAX_VALUE);

        BUILDER.comment("FE/t each face may move, before energy upgrades.");
        SPAWNER_SIPHON_TRANSFER = BUILDER.defineInRange("transferRate", 1_000, 0, Integer.MAX_VALUE);

        BUILDER.pop();

        BUILDER.push("enchantment_combustor");

        BUILDER.comment(
                "FE earned per level of enchantment burned off an item.",
                "At the defaults a Sharpness V book is worth 10,000 FE and the book comes back out",
                "blank. Levels are summed across every enchantment on the item, so a fully kitted",
                "tool pays far more than the sum of its parts suggests.");
        COMBUSTOR_FE_PER_ENCHANTMENT_LEVEL = BUILDER.defineInRange("energyPerEnchantmentLevel", 2_000, 0, 1_000_000);

        BUILDER.comment("Ticks to strip one item, at base speed.");
        COMBUSTOR_TICKS_PER_ITEM = BUILDER.defineInRange("ticksPerItem", 100, 1, 72000);

        BUILDER.comment("Internal FE buffer, before energy upgrades.");
        COMBUSTOR_CAPACITY = BUILDER.defineInRange("capacity", 100_000L, 0L, Long.MAX_VALUE);

        BUILDER.comment("FE/t each face may move, before energy upgrades.");
        COMBUSTOR_TRANSFER = BUILDER.defineInRange("transferRate", 1_000, 0, Integer.MAX_VALUE);

        BUILDER.pop();

        BUILDER.push("resonance_crusher");

        BUILDER.comment(
                "FE/t drawn while crushing, before speed and batch scaling.",
                "Speed costs power superlinearly and batching costs it linearly, so a crusher run",
                "wide is far cheaper per item than one run fast -- which is the whole trade.");
        CRUSHER_FE_PER_TICK = BUILDER.defineInRange("energyPerTick", 120, 0, 1_000_000);

        BUILDER.comment("Ticks one operation takes at base speed, for recipes that do not set their own.");
        CRUSHER_TICKS = BUILDER.defineInRange("ticksPerOperation", 100, 1, 72000);

        BUILDER.comment(
                "How much longer the first run on an unfamiliar material takes.",
                "That run is the calibration: the crusher is working out what frequency the material",
                "rings at. It pays no bonus, and it happens exactly once per material -- after that",
                "the frequency is written down, and a config card copies it to the next crusher.");
        CRUSHER_CALIBRATION_MULTIPLIER = BUILDER.defineInRange("calibrationMultiplier", 4, 1, 64);

        BUILDER.comment(
                "Permille chance, per item crushed, of shaking an extra one loose once tuned.",
                "This is what calibrating buys: at 250 a tuned crusher averages a quarter more than",
                "an untuned one. 0 makes tuning worth only the time it saves.");
        CRUSHER_BONUS_PERMILLE = BUILDER.defineInRange("tunedBonusPermille", 250, 0, 1000);

        BUILDER.comment("Internal FE buffer, before energy upgrades.");
        CRUSHER_CAPACITY = BUILDER.defineInRange("capacity", 100_000L, 0L, Long.MAX_VALUE);

        BUILDER.comment("FE/t each face may move, before energy upgrades.");
        CRUSHER_TRANSFER = BUILDER.defineInRange("transferRate", 1_000, 0, Integer.MAX_VALUE);

        BUILDER.pop();

        BUILDER.push("surge_bank");

        BUILDER.comment(
                "FE one bank holds, before energy upgrades.",
                "Banks that touch pool their charge, so a wall of them is worth this much each.");
        SURGE_BANK_CAPACITY = BUILDER.defineInRange("capacity", 4_000_000L, 0L, Long.MAX_VALUE);

        BUILDER.comment(
                "FE/t each face may move, before energy upgrades.",
                "Deliberately enormous next to a machine's own throughput: absorbing and covering",
                "a spike is the entire point of the block.");
        SURGE_BANK_TRANSFER = BUILDER.defineInRange("transferRate", 20_000, 0, Integer.MAX_VALUE);

        BUILDER.comment(
                "Permille of the stored charge a quiet bank loses each second.",
                "This is the trade for the size and the speed: a bank being used loses nothing, and",
                "one sitting on a full charge bleeds it away. At 2 a full default bank loses 8,000",
                "FE/s and halves in about six minutes. 0 turns the leak off entirely.");
        SURGE_BANK_LEAK_PERMILLE = BUILDER.defineInRange("leakPermillePerSecond", 2, 0, 1000);

        BUILDER.comment("Ticks a bank must go untouched before it starts leaking.");
        SURGE_BANK_LEAK_IDLE_TICKS = BUILDER.defineInRange("leakIdleTicks", 100, 1, 72000);

        BUILDER.pop();

        BUILDER.push("flux_crystal");

        BUILDER.comment(
                "FE a single flux crystal holds when full.",
                "This is the size of one 'unit' of craftable energy, so it sets what a recipe that",
                "asks for a charged crystal is really asking a player to produce.");
        FLUX_CRYSTAL_CAPACITY = BUILDER.defineInRange("capacity", 200_000L, 1L, Long.MAX_VALUE);

        BUILDER.comment(
                "FE/t a crystal takes or gives through the standard item energy capability --",
                "that is, in another mod's charger or battery slot. The Crystal Charger ignores",
                "this and uses its own throughput, which is what makes it the fast way.");
        FLUX_CRYSTAL_TRANSFER = BUILDER.defineInRange("transferRate", 2_000, 0, Integer.MAX_VALUE);

        BUILDER.pop();

        BUILDER.push("crystal_charger");

        BUILDER.comment("FE buffer, before energy upgrades. Only has to cover a tick's transfer.");
        CRYSTAL_CHARGER_CAPACITY = BUILDER.defineInRange("capacity", 400_000L, 0L, Long.MAX_VALUE);

        BUILDER.comment(
                "FE/t it moves in or out of crystals, before energy upgrades.",
                "This is the machine's whole speed: it neither converts nor loses anything, so the",
                "only thing upgrades buy here is throughput. At 5,000 a default crystal fills in",
                "40 ticks, and a stack of 64 in about 43 minutes.");
        CRYSTAL_CHARGER_TRANSFER = BUILDER.defineInRange("transferRate", 5_000, 0, Integer.MAX_VALUE);

        BUILDER.pop();

        BUILDER.push("flux_coupler");

        BUILDER.comment(
                "Ticks between passes. It moves a whole interval's worth each time, so this is",
                "how choppy the charging looks, not how fast it is. Raise it on a busy server.");
        FLUX_COUPLER_INTERVAL = BUILDER.defineInRange("intervalTicks", 20, 1, 1200);

        BUILDER.pop();

        BUILDER.comment("Logic Ports: the wireless logistics network.").push("logic_ports");

        BUILDER.comment(
                "Ticks between passes on a network that moved nothing last time.",
                "Nothing tells a port that somebody put an item in a chest six hundred blocks away,",
                "so a network with a source has to look. This is how slowly it looks while there is",
                "nothing to find; the first thing it does move puts it back on the fast interval.");
        LINK_IDLE_INTERVAL_TICKS = BUILDER.defineInRange("idleIntervalTicks", 40, 1, 1200);

        BUILDER.comment(
                "Items one send of an untiered port carries. A port's rate is what it carries per",
                "send divided by the ticks it waits between sends, and both of those are the",
                "player's to set in the pad's window; these are the ceilings a tierless pad has.");
        LINK_AMOUNT_ITEMS = BUILDER.defineInRange("amountItems", 32, 1, 40_000);

        BUILDER.comment("Millibuckets one send of an untiered port carries.");
        LINK_AMOUNT_MILLIBUCKETS = BUILDER.defineInRange("amountMillibuckets", 1_000, 1, 1_000_000);

        BUILDER.comment("FE one send of an untiered port carries.");
        LINK_AMOUNT_ENERGY = BUILDER.defineInRange("amountEnergy", 20_000, 1, Integer.MAX_VALUE);

        BUILDER.comment(
                "Multiplier on the amounts above per tier of the pad: none, iron, gold, diamond,",
                "netherite. A tier upgrade in a pad raises what one send may carry.");
        LINK_TIER_AMOUNT_MULTIPLIER = BUILDER.<Number>defineList("tierAmountMultiplier",
                List.of(1, 2, 4, 8, 16), entry -> entry instanceof Number);

        BUILDER.comment(
                "Shortest wait between sends, in ticks, per tier of the pad. This is the floor a",
                "player may set the pad's delay to; a tier buys a shorter one.");
        LINK_TIER_DELAY_TICKS = BUILDER.<Number>defineList("tierDelayTicks",
                List.of(10, 8, 5, 2, 1), entry -> entry instanceof Number);

        BUILDER.comment("How far an unupgraded port reaches, in blocks.");
        LINK_BASE_RANGE = BUILDER.defineInRange("baseRange", 16, 0, 30_000_000);

        BUILDER.comment("Extra blocks of reach per range upgrade in the port.");
        LINK_RANGE_PER_UPGRADE = BUILDER.defineInRange("rangePerUpgrade", 16, 0, 30_000_000);

        BUILDER.comment(
                "Range upgrades a port counts. A port holding this many also reaches across",
                "dimensions -- that is what the top tier buys, on top of the distance.");
        LINK_MAX_RANGE_UPGRADES = BUILDER.defineInRange("maxRangeUpgrades", 4, 1, 64);

        BUILDER.comment(
                "FE an Energy Injector spends to move one item across a network.",
                "Wireless logistics is not free: every pass is paid for out of the injectors linked",
                "to that network, and a network with no injector on it moves nothing at all.",
                "Zero makes item transfer free.");
        LINK_FE_PER_ITEM = BUILDER.defineInRange("energyPerItem", 10, 0, 1_000_000);

        BUILDER.comment("FE spent per bucket of fluid moved. Charged per millibucket, so a trickle still pays.");
        LINK_FE_PER_BUCKET = BUILDER.defineInRange("energyPerBucket", 10, 0, 1_000_000);

        BUILDER.comment(
                "Cut taken out of FE moved across a network, in permille. 20 means moving 1000 FE",
                "costs 20 FE out of the injectors, so a wireless power link is lossy on purpose.");
        LINK_FE_PERMILLE_OF_ENERGY = BUILDER.defineInRange("energyTollPermille", 20, 0, 1000);

        BUILDER.comment("FE an Energy Injector holds.");
        LINK_INJECTOR_CAPACITY = BUILDER.defineInRange("injectorCapacity", 200_000L, 0L, Long.MAX_VALUE);

        BUILDER.comment("FE/t an Energy Injector takes in.");
        LINK_INJECTOR_TRANSFER = BUILDER.defineInRange("injectorTransferRate", 5_000, 0, Integer.MAX_VALUE);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private ServerConfig() {
    }

    /**
     * The current machine tuning. Falls back to {@link MachineTuning#DEFAULT} when the config
     * has not loaded yet, which happens during datagen and early startup.
     */
    public static MachineTuning tuning() {
        MachineTuning tuning = cachedTuning;
        if (tuning == null) {
            tuning = read();
            cachedTuning = tuning;
        }
        return tuning;
    }

    public static void invalidate() {
        cachedTuning = null;
    }

    private static MachineTuning read() {
        try {
            return new MachineTuning(
                    SPEED_PER_UPGRADE.get(),
                    MAX_SPEED_UPGRADES.get(),
                    OVERCLOCK_PER_UPGRADE.get(),
                    MAX_OVERCLOCK_UPGRADES.get(),
                    ENERGY_EXPONENT.get(),
                    CAPACITY_PER_ENERGY_UPGRADE.get(),
                    TRANSFER_PER_ENERGY_UPGRADE.get(),
                    MAX_ENERGY_UPGRADES.get(),
                    BATCH_PER_STACK_UPGRADE.get(),
                    MAX_STACK_UPGRADES.get(),
                    GENERATOR_SPEED_PENALTY.get(),
                    GENERATOR_WARMUP_FLOOR.get(),
                    OVERCLOCK_RAMP_TICKS.get(),
                    OVERCLOCK_DECAY_TICKS.get());
        } catch (IllegalStateException notLoadedYet) {
            return MachineTuning.DEFAULT;
        }
    }

    public static int autoIoIntervalTicks() {
        try {
            return AUTO_IO_INTERVAL_TICKS.get();
        } catch (IllegalStateException notLoadedYet) {
            return 10;
        }
    }

    public static int idleRecheckTicks() {
        try {
            return IDLE_RECHECK_TICKS.get();
        } catch (IllegalStateException notLoadedYet) {
            return 20;
        }
    }

    /** Reads a machine value, falling back to its default before the config has loaded. */
    public static int valueOr(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }

    /**
     * One tier's entry from a per-tier list, falling back before the config has loaded and clamping
     * to the last entry so a shortened list still answers for every tier.
     */
    public static int tierValue(ModConfigSpec.ConfigValue<List<? extends Number>> value, int tier, int[] fallback) {
        Number found = tierEntry(value, tier);
        return found != null ? found.intValue() : fallback[Math.clamp(tier, 0, fallback.length - 1)];
    }

    public static double tierValue(ModConfigSpec.ConfigValue<List<? extends Number>> value, int tier, double[] fallback) {
        Number found = tierEntry(value, tier);
        return found != null ? found.doubleValue() : fallback[Math.clamp(tier, 0, fallback.length - 1)];
    }

    private static @Nullable Number tierEntry(ModConfigSpec.ConfigValue<List<? extends Number>> value, int tier) {
        try {
            List<? extends Number> list = value.get();
            return list.isEmpty() ? null : list.get(Math.clamp(tier, 0, list.size() - 1));
        } catch (IllegalStateException notLoadedYet) {
            return null;
        }
    }

    /** The same, for the amounts that are counted in longs rather than ints. */
    public static long valueOr(ModConfigSpec.LongValue value, long fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }
}
