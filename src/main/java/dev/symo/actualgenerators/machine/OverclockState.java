package dev.symo.actualgenerators.machine;

/**
 * The overclock ramp of a single machine.
 *
 * <p>It climbs while the machine is working and powered, and — the point of the whole
 * mechanic — it does <em>not</em> reset between recipes. It only falls when the machine
 * runs out of work or out of power, so a well fed production line stays hot.
 *
 * <p>State is the number of ticks of work banked, so reaching full overclock takes exactly
 * {@code overclockRampTicks} ticks of work. Decay is scaled so a fully ramped machine cools
 * down in at most {@code overclockDecayTicks}.
 */
public final class OverclockState {
    private int workedTicks;

    /** How far up the ramp the machine is, from 0 to 1. */
    public double progress(MachineTuning tuning) {
        return Math.clamp(workedTicks / (double) tuning.overclockRampTicks(), 0.0, 1.0);
    }

    public int workedTicks() {
        return workedTicks;
    }

    public void setWorkedTicks(int value) {
        this.workedTicks = Math.max(0, value);
    }

    public boolean isRampedUp(MachineTuning tuning) {
        return workedTicks >= tuning.overclockRampTicks();
    }

    public boolean isCold() {
        return workedTicks == 0;
    }

    /**
     * Advances the ramp by one tick.
     *
     * @param working whether the machine did useful, powered work this tick
     * @return true if the ramp changed, so callers can skip saving when it did not
     */
    public boolean tick(boolean working, MachineTuning tuning) {
        int ramp = tuning.overclockRampTicks();
        int next;
        if (working) {
            // Not min(ramp, workedTicks + 1): a banked value near Integer.MAX_VALUE — from a
            // corrupt save, or from a test priming the ramp — would overflow to a negative one.
            next = workedTicks >= ramp ? ramp : workedTicks + 1;
        } else {
            int decayPerTick = Math.max(1, ceilDiv(ramp, tuning.overclockDecayTicks()));
            next = Math.max(0, Math.min(workedTicks, ramp) - decayPerTick);
        }
        if (next == workedTicks) {
            return false;
        }
        workedTicks = next;
        return true;
    }

    public void reset() {
        workedTicks = 0;
    }

    private static int ceilDiv(int dividend, int divisor) {
        return (dividend + divisor - 1) / divisor;
    }
}
