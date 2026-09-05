package dev.symo.actualgenerators.machine;

/**
 * The scaling maths shared by every machine, kept free of Minecraft types so it can be
 * exercised directly by tests.
 *
 * <p>Speed upgrades raise throughput up to a ceiling; overclock upgrades raise the ceiling
 * itself, but only as far as the machine's current overclock ramp has climbed. Energy cost
 * rises superlinearly with the combined multiplier, so running hot is deliberately expensive.
 *
 * <p>Instances are derived from the server config via {@code ServerConfig#tuning()}; tests
 * build them directly.
 */
public record MachineTuning(
        double speedPerUpgrade,
        int maxSpeedUpgrades,
        double overclockPerUpgrade,
        int maxOverclockUpgrades,
        double energyExponent,
        double capacityPerEnergyUpgrade,
        double transferPerEnergyUpgrade,
        int maxEnergyUpgrades,
        int batchPerStackUpgrade,
        int maxStackUpgrades,
        double generatorSpeedPenalty,
        double generatorWarmupFloor,
        int overclockRampTicks,
        int overclockDecayTicks) {

    /** Used by tests and as a fallback before the server config has loaded. */
    public static final MachineTuning DEFAULT = new MachineTuning(
            0.5, 4,
            0.5, 4,
            2.0,
            1.0, 1.0, 4,
            2, 4,
            0.5, 0.5,
            1200, 600);

    public MachineTuning {
        if (maxSpeedUpgrades < 0 || maxOverclockUpgrades < 0 || maxEnergyUpgrades < 0 || maxStackUpgrades < 0) {
            throw new IllegalArgumentException("upgrade limits must not be negative");
        }
        if (overclockRampTicks < 1 || overclockDecayTicks < 1) {
            throw new IllegalArgumentException("overclock ramp and decay must be at least 1 tick");
        }
    }

    /** How many upgrades of a type are counted before the rest are ignored. */
    public int maxUpgrades(UpgradeType type) {
        return switch (type) {
            case ENERGY -> maxEnergyUpgrades;
            case SPEED -> maxSpeedUpgrades;
            case OVERCLOCK -> maxOverclockUpgrades;
            case STACK -> maxStackUpgrades;
        };
    }

    /** Speed from speed upgrades alone. Capped — this is the ceiling overclocks push past. */
    public double speedMultiplier(int speedUpgrades) {
        return 1.0 + speedPerUpgrade * clampUpgrades(speedUpgrades, maxSpeedUpgrades);
    }

    /** The multiplier a fully ramped-up machine reaches on top of its speed multiplier. */
    public double peakOverclockMultiplier(int overclockUpgrades) {
        return 1.0 + overclockPerUpgrade * clampUpgrades(overclockUpgrades, maxOverclockUpgrades);
    }

    /** The overclock multiplier at the current ramp progress (0..1). */
    public double overclockMultiplier(int overclockUpgrades, double rampProgress) {
        double progress = Math.clamp(rampProgress, 0.0, 1.0);
        return 1.0 + (peakOverclockMultiplier(overclockUpgrades) - 1.0) * progress;
    }

    /** Combined throughput multiplier: speed ceiling times however far the ramp has climbed. */
    public double totalSpeedMultiplier(int speedUpgrades, int overclockUpgrades, double rampProgress) {
        return speedMultiplier(speedUpgrades) * overclockMultiplier(overclockUpgrades, rampProgress);
    }

    /**
     * How many items one operation may process at once.
     *
     * <p>This is the throughput lever that costs nothing extra to run: a machine resolves at
     * most one operation per tick no matter how large the batch, so the per-tick cost of a
     * batch of 9 is the same as a batch of 1.
     */
    public int maxBatch(int stackUpgrades) {
        return 1 + batchPerStackUpgrade * clampUpgrades(stackUpgrades, maxStackUpgrades);
    }

    /** FE/t at the given multiplier for a batch of one. */
    public int energyPerTick(int baseEnergyPerTick, double totalSpeedMultiplier) {
        return energyPerTick(baseEnergyPerTick, totalSpeedMultiplier, 1);
    }

    /**
     * FE/t for a machine running at the given multiplier and batch size.
     *
     * <p>Speed is superlinear — doubling it costs far more than double the power — while batch
     * size is strictly linear, because processing eight items really is eight times the work
     * and nothing more. That is what makes stack upgrades the efficient path and overclocking
     * the expensive one.
     */
    public int energyPerTick(int baseEnergyPerTick, double totalSpeedMultiplier, int batchSize) {
        if (baseEnergyPerTick <= 0) {
            return 0;
        }
        double scaled = baseEnergyPerTick
                * Math.pow(Math.max(totalSpeedMultiplier, 1.0), energyExponent)
                * Math.max(1, batchSize);
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(scaled));
    }

    /**
     * How many ticks one operation takes at the given multiplier. Never less than one tick —
     * a machine resolves at most one operation per tick, which is what bounds its cost to the
     * server. Speed beyond one operation per tick is wasted; batching is what scales past it.
     */
    public int ticksForOperation(int baseTicks, double totalSpeedMultiplier) {
        if (baseTicks <= 0) {
            return 1;
        }
        double scaled = baseTicks / Math.max(totalSpeedMultiplier, 0.01);
        return (int) Math.max(1, Math.ceil(scaled));
    }

    /**
     * True once the machine is fast enough to finish an operation every tick, so any further
     * speed is burned for nothing and only stack upgrades raise throughput.
     */
    public boolean isSpeedSaturated(int baseTicks, double totalSpeedMultiplier) {
        return baseTicks > 0 && totalSpeedMultiplier >= baseTicks;
    }

    /**
     * FE/t produced by a generator running at the given multiplier and batch size.
     *
     * <p>Consumers pay superlinearly for speed; generators cannot simply be paid superlinearly
     * in return, or overclocking one would conjure energy. Instead speed raises output
     * <em>sublinearly</em>: a generator burns fuel N times faster but yields less total energy
     * per item. Overclocking a generator buys burst power at the cost of efficiency.
     *
     * <p>{@code generatorSpeedPenalty} controls the trade — 0 means speed is free (output rises
     * linearly, total yield per item unchanged), 1 means speed buys nothing at all. The default
     * of 0.5 makes 9x speed yield 3x power for 9x the fuel.
     */
    public int generatorEnergyPerTick(int baseEnergyPerTick, double totalSpeedMultiplier, int batchSize) {
        if (baseEnergyPerTick <= 0) {
            return 0;
        }
        double speed = Math.max(totalSpeedMultiplier, 1.0);
        double scaled = baseEnergyPerTick
                * Math.max(1, batchSize)
                * Math.pow(speed, 1.0 - Math.clamp(generatorSpeedPenalty, 0.0, 1.0));
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(scaled));
    }

    /**
     * Output scale for a generator whose ramp is a warm-up rather than an overclock.
     *
     * <p>A generator the world pays — depth, a spawner next door, a shaft full of falling rock —
     * has no fuel to trade for speed, so its ramp cannot push output past its rating without
     * conjuring energy. Instead it starts <em>below</em> the rating and climbs to it: cold it
     * makes {@code generatorWarmupFloor} of what it is worth, and at the top of the ramp it makes
     * all of it. Keeping one running is the reward, and there is nothing above the rating to reach.
     */
    public double warmupMultiplier(double rampProgress) {
        double floor = Math.clamp(generatorWarmupFloor, 0.0, 1.0);
        return floor + (1.0 - floor) * Math.clamp(rampProgress, 0.0, 1.0);
    }

    /** How much more power a consuming machine draws at the given multiplier than at rest. */
    public double energyCostMultiplier(double totalSpeedMultiplier) {
        return Math.pow(Math.max(totalSpeedMultiplier, 1.0), energyExponent);
    }

    /** The fraction of a generator's baseline yield per item that survives running it hot. */
    public double generatorEfficiency(double totalSpeedMultiplier) {
        double speed = Math.max(totalSpeedMultiplier, 1.0);
        return Math.pow(speed, -Math.clamp(generatorSpeedPenalty, 0.0, 1.0));
    }

    /** Buffer size after upgrades. A long, because a late-game buffer outgrows an int. */
    public long capacity(long baseCapacity, int energyUpgrades) {
        if (baseCapacity <= 0) {
            return 0;
        }
        double scaled = baseCapacity * energyFactor(energyUpgrades, capacityPerEnergyUpgrade);
        return (long) Math.min(Long.MAX_VALUE, Math.round(scaled));
    }

    /** Throughput after upgrades. Stays an int: FE/t past two billion is not a real balance. */
    public int transferRate(int baseTransferRate, int energyUpgrades) {
        if (baseTransferRate <= 0) {
            return 0;
        }
        double scaled = baseTransferRate * energyFactor(energyUpgrades, transferPerEnergyUpgrade);
        return (int) Math.min(Integer.MAX_VALUE, Math.round(scaled));
    }

    private double energyFactor(int energyUpgrades, double perUpgrade) {
        return 1.0 + perUpgrade * clampUpgrades(energyUpgrades, maxEnergyUpgrades);
    }

    private static int clampUpgrades(int count, int max) {
        return Math.clamp(count, 0, max);
    }
}
