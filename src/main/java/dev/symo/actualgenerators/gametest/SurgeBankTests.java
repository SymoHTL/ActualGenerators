package dev.symo.actualgenerators.gametest;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.MachineBlock;
import dev.symo.actualgenerators.machine.RelativeSide;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.menu.SurgeBankMenu;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModItems;
import dev.symo.actualgenerators.storage.SurgeBankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Tests for the battery.
 *
 * <p>The two things that could quietly go wrong here are both about arithmetic rather than
 * behaviour: banks sharing a charge must never create or destroy any of it, and the leak must
 * only bite a bank that is genuinely idle. Both are checked against exact totals.
 */
@GameTestHolder(ActualGenerators.MODID)
@PrefixGameTestTemplate(false)
public final class SurgeBankTests {
    private static final String PLATFORM = "platform";
    private static final BlockPos BANK = new BlockPos(2, 1, 2);
    private static final BlockPos NEIGHBOUR = new BlockPos(3, 1, 2);

    /** Matches the config defaults. */
    private static final int LEAK_IDLE_TICKS = 100;

    private SurgeBankTests() {
    }

    @GameTest(template = PLATFORM)
    public static void handsPowerBackInOneEnormousGulp(GameTestHelper helper) {
        helper.setBlock(BANK, ModBlocks.SURGE_BANK.get());
        SurgeBankBlockEntity bank = bank(helper);
        bank.energyStorage().setEnergy(bank.energyStorage().getMaxEnergyStored());

        helper.assertTrue(bank.energyStorage().getMaxEnergyStored() >= 1_000_000,
                "a bank is meant to be big, got " + bank.energyStorage().getMaxEnergyStored());

        Direction front = helper.getBlockState(BANK).getValue(MachineBlock.FACING);
        IEnergyStorage out = face(helper, front);
        helper.assertTrue(out != null && out.canExtract(), "the front face should hand power out");
        helper.assertTrue(out.extractEnergy(Integer.MAX_VALUE, true) >= 10_000,
                "and in a gulp, not a machine-sized trickle");

        IEnergyStorage in = face(helper, front.getOpposite());
        helper.assertTrue(in != null && in.canReceive(), "the other faces should take power in");
        helper.assertTrue(!in.canExtract(), "an input face must not double as an output");

        helper.assertTrue(helper.getLevel().getCapability(
                        Capabilities.ItemHandler.BLOCK, helper.absolutePos(BANK), front) == null,
                "a battery has no inventory to expose");

        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void takesEnergyUpgradesAndNothingElse(GameTestHelper helper) {
        helper.setBlock(BANK, ModBlocks.SURGE_BANK.get());
        SurgeBankBlockEntity bank = bank(helper);

        IItemHandler upgrades = bank.upgradeInventory();
        helper.assertTrue(upgrades.isItemValid(UpgradeType.ENERGY.ordinal(),
                        new ItemStack(ModItems.ENERGY_UPGRADE.get())),
                "energy upgrades raise both its buffer and its throughput");

        // It converts nothing, so there is no speed to buy and no batch to enlarge.
        for (UpgradeType refused : new UpgradeType[]{UpgradeType.SPEED, UpgradeType.OVERCLOCK, UpgradeType.STACK}) {
            ItemStack upgrade = ModItems.upgradeItem(refused).toStack();
            helper.assertTrue(!upgrades.isItemValid(refused.ordinal(), upgrade),
                    refused + " should be refused by a block that only holds power");
            helper.assertValueEqual(bank.maxUpgrades(refused), 0, refused + " should count for nothing");
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SurgeBankMenu menu = new SurgeBankMenu(1, player.getInventory(), bank);
        helper.assertValueEqual(menu.upgradeSlotCount(), 1, "so the screen shows one slot");
        helper.assertTrue(!menu.hasRamp(), "and no ramp bar that could never move");

        helper.succeed();
    }

    /**
     * Two banks side by side end up holding the same amount, and the total is exactly what went
     * in. Sharing that rounds in its own favour would be a duplication bug in a battery.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void banksThatTouchPoolWhatTheyHold(GameTestHelper helper) {
        int charge = 1_000_000;
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(BANK, ModBlocks.SURGE_BANK.get());
                    helper.setBlock(NEIGHBOUR, ModBlocks.SURGE_BANK.get());
                    bank(helper).energyStorage().setEnergy(charge);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    int mine = bank(helper).energyStorage().getEnergyStored();
                    int theirs = neighbour(helper).energyStorage().getEnergyStored();

                    helper.assertValueEqual(mine + theirs, charge,
                            "sharing must move a charge, never mint or lose one");
                    helper.assertTrue(Math.abs(mine - theirs) <= 1,
                            "and the two should have evened out, got " + mine + " and " + theirs);
                })
                .thenSucceed();
    }

    /** Shutting a bank's faces takes it out of the pool, which is how a wall gets split up. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void aBankWithItsFacesShutKeepsItsChargeToItself(GameTestHelper helper) {
        int charge = 1_000_000;
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(BANK, ModBlocks.SURGE_BANK.get());
                    helper.setBlock(NEIGHBOUR, ModBlocks.SURGE_BANK.get());
                    SurgeBankBlockEntity bank = bank(helper);
                    for (RelativeSide side : RelativeSide.all()) {
                        bank.sideConfig().set(TransferKind.ENERGY, side, IoMode.DISABLED);
                    }
                    bank.energyStorage().setEnergy(charge);
                })
                .thenIdle(LEAK_IDLE_TICKS - 20)
                .thenExecute(() -> {
                    helper.assertValueEqual(bank(helper).energyStorage().getEnergyStored(), charge,
                            "a bank with nothing open should have shared nothing");
                    helper.assertValueEqual(neighbour(helper).energyStorage().getEnergyStored(), 0,
                            "and its neighbour should have received nothing");
                })
                .thenSucceed();
    }

    /**
     * The leak, which is the whole trade the block asks for: a bank being used loses nothing, a
     * bank left alone bleeds.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 500)
    public static void aQuietBankBleedsAndABusyOneDoesNot(GameTestHelper helper) {
        int charge = 1_000_000;
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(BANK, ModBlocks.SURGE_BANK.get());
                    bank(helper).energyStorage().setEnergy(charge);
                })
                .thenIdle(LEAK_IDLE_TICKS - 40)
                .thenExecute(() -> {
                    helper.assertValueEqual(bank(helper).energyStorage().getEnergyStored(), charge,
                            "a bank that has only just gone quiet has not started bleeding yet");
                    helper.assertTrue(!bank(helper).isLeaking(), "and does not claim to be");
                })
                .thenIdle(160)
                .thenExecute(() -> {
                    SurgeBankBlockEntity bank = bank(helper);
                    helper.assertTrue(bank.energyStorage().getEnergyStored() < charge,
                            "a bank left alone should have bled some off");
                    helper.assertTrue(bank.isLeaking(), "and should say so on the screen");
                    helper.assertTrue(bank.leakPerSecond() > 0, "with a figure to go with it");

                    // Touching it puts the clock back to the start.
                    bank.energyStorage().setEnergy(charge / 2);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    SurgeBankBlockEntity bank = bank(helper);
                    helper.assertTrue(!bank.isLeaking(), "a bank that was just used is not leaking");
                    helper.assertValueEqual(bank.energyStorage().getEnergyStored(), charge / 2,
                            "and has lost nothing since it was");
                })
                .thenSucceed();
    }

    private static IEnergyStorage face(GameTestHelper helper, Direction side) {
        return helper.getLevel().getCapability(
                Capabilities.EnergyStorage.BLOCK, helper.absolutePos(BANK), side);
    }

    private static SurgeBankBlockEntity bank(GameTestHelper helper) {
        return bankAt(helper, BANK);
    }

    private static SurgeBankBlockEntity neighbour(GameTestHelper helper) {
        return bankAt(helper, NEIGHBOUR);
    }

    private static SurgeBankBlockEntity bankAt(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof SurgeBankBlockEntity bank) {
            return bank;
        }
        throw new IllegalStateException("no surge bank at " + pos);
    }
}
