package dev.symo.actualgenerators.gametest;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.item.FluxCouplerItem;
import dev.symo.actualgenerators.item.FluxCrystalItem;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.menu.FluxCouplerMenu;
import dev.symo.actualgenerators.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.List;

/**
 * The coupler hands charge from the crystals loaded into it to whatever else the player is
 * carrying, so what these tests watch is the total: every FE that leaves a crystal has to arrive
 * somewhere, and a spent crystal has to come back as an empty rather than quietly vanishing.
 *
 * <p>The gear being charged is a flux crystal in the tests, because it is the one FE item this mod
 * can be sure exists. The coupler would never pick one out of an inventory — crystals are its fuel,
 * not its customers — which is why the gear list is handed in directly here, and why there is a
 * separate test for the rule that keeps them off that list.
 */
@GameTestHolder(ActualGenerators.MODID)
@PrefixGameTestTemplate(false)
public final class FluxCouplerTests {
    private static final String EMPTY = "empty";

    private FluxCouplerTests() {
    }

    /**
     * The headline behaviour: charge comes out of the crystal in hand, goes onto the gear, and the
     * books balance to the FE.
     */
    @GameTest(template = EMPTY)
    public static void chargeComesOutOfTheCrystalAndLandsOnTheGear(GameTestHelper helper) {
        ItemStack coupler = ModItems.FLUX_COUPLER.toStack();
        IItemHandlerModifiable slots = FluxCouplerItem.slots(coupler);
        slots.setStackInSlot(FluxCouplerItem.SLOT_INPUT,
                FluxCrystalItem.withEnergy(ModItems.FLUX_CRYSTAL.toStack(), 1, FluxCrystalItem.capacity()));

        ItemStack gear = ModItems.FLUX_CRYSTAL.toStack();
        long expected = (long) FluxCouplerItem.ratePerTick(coupler) * FluxCouplerItem.intervalTicks();

        FluxCouplerItem.run(coupler, List.of(gear));

        helper.assertValueEqual(FluxCrystalItem.energyOf(gear), expected,
                "a pass hands over exactly one interval's worth of FE");
        helper.assertValueEqual(FluxCouplerItem.chargeHeld(coupler), FluxCrystalItem.capacity() - expected,
                "and the crystals it is holding are down by the same amount");
        helper.assertValueEqual(
                FluxCrystalItem.energyOf(FluxCouplerItem.slots(coupler).getStackInSlot(FluxCouplerItem.SLOT_WORKING)),
                FluxCrystalItem.capacity() - expected,
                "with the part-charged crystal still in hand rather than back in the pile");

        helper.succeed();
    }

    /** A spent crystal is retired to the output slot and the next one comes through behind it. */
    @GameTest(template = EMPTY)
    public static void spentCrystalsPileUpInTheOutputSlot(GameTestHelper helper) {
        ItemStack coupler = ModItems.FLUX_COUPLER.toStack();
        IItemHandlerModifiable slots = FluxCouplerItem.slots(coupler);
        // A crystal has to be in there before the rate means anything: the crystal sets the pace.
        slots.setStackInSlot(FluxCouplerItem.SLOT_INPUT, ModItems.FLUX_CRYSTAL.toStack());
        long perPass = (long) FluxCouplerItem.ratePerTick(coupler) * FluxCouplerItem.intervalTicks();

        // Two crystals holding a single pass between them, so one pass empties both.
        slots.setStackInSlot(FluxCouplerItem.SLOT_INPUT,
                FluxCrystalItem.withEnergy(ModItems.FLUX_CRYSTAL.toStack(), 2, perPass / 2));

        ItemStack gear = ModItems.FLUX_CRYSTAL.toStack();
        FluxCouplerItem.run(coupler, List.of(gear));

        slots = FluxCouplerItem.slots(coupler);
        helper.assertValueEqual(FluxCrystalItem.energyOf(gear), perPass,
                "both crystals should have gone into the gear");
        helper.assertValueEqual(slots.getStackInSlot(FluxCouplerItem.SLOT_OUTPUT).getCount(), 2,
                "and both empties should be waiting in the output slot");
        helper.assertValueEqual(
                FluxCrystalItem.energyOf(slots.getStackInSlot(FluxCouplerItem.SLOT_OUTPUT)), 0L,
                "empty, so they stack");
        helper.assertTrue(slots.getStackInSlot(FluxCouplerItem.SLOT_INPUT).isEmpty(),
                "with nothing left in the input");
        helper.assertTrue(slots.getStackInSlot(FluxCouplerItem.SLOT_WORKING).isEmpty(),
                "and nothing left in hand");

        helper.succeed();
    }

    /** The crystal sets the pace, so a coupler with nothing loaded has no rate at all. */
    @GameTest(template = EMPTY)
    public static void theRateComesFromTheCrystalItIsDrawingOn(GameTestHelper helper) {
        ItemStack coupler = ModItems.FLUX_COUPLER.toStack();

        helper.assertValueEqual(FluxCouplerItem.baseRatePerTick(coupler), 0,
                "an empty coupler has nothing to draw on and no rate to quote");

        FluxCouplerItem.slots(coupler).setStackInSlot(FluxCouplerItem.SLOT_INPUT,
                FluxCrystalItem.withEnergy(ModItems.FLUX_CRYSTAL.toStack(), 1, FluxCrystalItem.capacity()));

        helper.assertValueEqual(FluxCouplerItem.baseRatePerTick(coupler),
                FluxCrystalItem.transferRate(ModItems.FLUX_CRYSTAL.toStack()),
                "and a loaded one moves charge at the crystal's own rate");

        helper.succeed();
    }

    /** Energy upgrades are the only kind it takes, and what they buy is throughput. */
    @GameTest(template = EMPTY)
    public static void anEnergyUpgradeRaisesWhatOnePassHandsOver(GameTestHelper helper) {
        ItemStack plain = ModItems.FLUX_COUPLER.toStack();
        ItemStack upgraded = ModItems.FLUX_COUPLER.toStack();
        FluxCouplerItem.slots(upgraded).setStackInSlot(FluxCouplerItem.SLOT_UPGRADE,
                ModItems.upgradeItem(UpgradeType.ENERGY).toStack());

        for (ItemStack coupler : new ItemStack[]{plain, upgraded}) {
            FluxCouplerItem.slots(coupler).setStackInSlot(FluxCouplerItem.SLOT_INPUT,
                    FluxCrystalItem.withEnergy(ModItems.FLUX_CRYSTAL.toStack(), 1, FluxCrystalItem.capacity()));
        }

        helper.assertTrue(FluxCouplerItem.ratePerTick(upgraded) > FluxCouplerItem.ratePerTick(plain),
                "an energy upgrade has to buy something, got " + FluxCouplerItem.ratePerTick(upgraded)
                        + " against " + FluxCouplerItem.ratePerTick(plain));

        ItemStack plainGear = ModItems.FLUX_CRYSTAL.toStack();
        ItemStack upgradedGear = ModItems.FLUX_CRYSTAL.toStack();
        FluxCouplerItem.run(plain, List.of(plainGear));
        FluxCouplerItem.run(upgraded, List.of(upgradedGear));

        helper.assertTrue(FluxCrystalItem.energyOf(upgradedGear) > FluxCrystalItem.energyOf(plainGear),
                "and the upgraded coupler should have moved more in the same pass");

        helper.succeed();
    }

    /** Switched off it is inert, which is the point of the switch. */
    @GameTest(template = EMPTY)
    public static void aCouplerThatIsSwitchedOffMovesNothing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();

        ItemStack coupler = ModItems.FLUX_COUPLER.toStack();
        FluxCouplerItem.slots(coupler).setStackInSlot(FluxCouplerItem.SLOT_INPUT,
                FluxCrystalItem.withEnergy(ModItems.FLUX_CRYSTAL.toStack(), 1, FluxCrystalItem.capacity()));
        ItemStack gear = ModItems.FLUX_CRYSTAL.toStack();

        helper.assertTrue(FluxCouplerItem.isActive(coupler), "a fresh coupler starts switched on");
        FluxCouplerItem.setActive(coupler, false);

        inventory.setItem(0, coupler);
        inventory.setItem(1, gear);
        // inventoryTick is where the switch is honoured, so drive it the way the game does.
        for (int tick = 0; tick < FluxCouplerItem.intervalTicks() * 2; tick++) {
            coupler.inventoryTick(helper.getLevel(), player, 0, false);
        }

        helper.assertValueEqual(FluxCouplerItem.chargeHeld(coupler), FluxCrystalItem.capacity(),
                "a coupler that is off spends nothing");
        helper.assertValueEqual(FluxCrystalItem.energyOf(gear), 0L, "and charges nothing");

        helper.succeed();
    }

    /**
     * Shift-clicking a stack into the window must move it, not eat it.
     *
     * <p>This is the one that bit: vanilla's own transfer code edits the stack a slot hands out in
     * place, so a menu whose slots hand out copies loses whatever was moved. Forty-seven crystals
     * went that way once; the count is what this test watches.
     */
    @GameTest(template = EMPTY)
    public static void shiftClickingCrystalsInDoesNotDestroyThem(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();

        int count = 47;
        inventory.setItem(0, ModItems.FLUX_COUPLER.toStack());
        inventory.setItem(1, FluxCrystalItem.withEnergy(ModItems.FLUX_CRYSTAL.toStack(), count, FluxCrystalItem.capacity()));

        FluxCouplerMenu menu = new FluxCouplerMenu(1, inventory, 0);
        // Menu slots: four for the coupler, 27 for the inventory, then the hotbar -- so the second
        // hotbar slot, which is where the crystals are, is the one after the first.
        menu.quickMoveStack(player, 4 + 27 + 1);

        helper.assertValueEqual(crystalsIn(inventory, menu), count, "no crystal may go missing in transit");
        helper.assertValueEqual(
                FluxCouplerItem.slots(inventory.getItem(0)).getStackInSlot(FluxCouplerItem.SLOT_INPUT).getCount(),
                count, "and they should all have landed in the input slot");

        helper.succeed();
    }

    /** The arrow says how far through the crystal in hand the coupler is. */
    @GameTest(template = EMPTY)
    public static void theArrowTracksHowMuchOfTheCrystalIsLeft(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        inventory.setItem(0, ModItems.FLUX_COUPLER.toStack());

        FluxCouplerMenu menu = new FluxCouplerMenu(1, inventory, 0);
        helper.assertValueEqual(menu.progress(), 0.0, "an empty hand is no progress at all");

        long capacity = FluxCrystalItem.capacity();
        menu.getSlot(FluxCouplerItem.SLOT_WORKING).set(
                FluxCrystalItem.withEnergy(ModItems.FLUX_CRYSTAL.toStack(), 1, capacity / 4));
        helper.assertValueEqual(Math.round(menu.progress() * 100), 75L,
                "a crystal down to a quarter is three quarters used");
        helper.assertValueEqual(menu.chargeInHand(), capacity / 4, "and the tooltip shows what is left");

        helper.succeed();
    }

    /** Upgrades stack in the coupler's slot the same way they do in a machine's. */
    @GameTest(template = EMPTY)
    public static void theUpgradeSlotTakesAsManyAsAMachineWould(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        inventory.setItem(0, ModItems.FLUX_COUPLER.toStack());

        FluxCouplerMenu menu = new FluxCouplerMenu(1, inventory, 0);
        int ceiling = FluxCouplerMenu.maxUpgrades();

        helper.assertTrue(ceiling > 1, "the tuning should count more than one energy upgrade, got " + ceiling);
        helper.assertValueEqual(menu.slots.get(FluxCouplerItem.SLOT_UPGRADE).getMaxStackSize(), ceiling,
                "so the slot has to hold that many");

        helper.succeed();
    }

    /** Every crystal the player and the coupler are holding between them. */
    private static int crystalsIn(Inventory inventory, FluxCouplerMenu menu) {
        int found = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof FluxCrystalItem) {
                found += stack.getCount();
            }
        }
        IItemHandlerModifiable slots = FluxCouplerItem.slots(menu.coupler());
        for (int slot = 0; slot < FluxCouplerItem.SLOT_COUNT; slot++) {
            ItemStack stack = slots.getStackInSlot(slot);
            if (stack.getItem() instanceof FluxCrystalItem) {
                found += stack.getCount();
            }
        }
        return found;
    }

    /**
     * Crystals in the player's inventory are fuel, not customers. A coupler that charged them
     * would sit there shuffling the same energy between crystals for ever.
     */
    @GameTest(template = EMPTY)
    public static void looseCrystalsAreNeverTreatedAsGear(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();

        ItemStack coupler = ModItems.FLUX_COUPLER.toStack();
        inventory.setItem(0, coupler);
        inventory.setItem(1, ModItems.FLUX_CRYSTAL.toStack(8));
        inventory.setItem(2, ModItems.FLUX_COUPLER.toStack());

        List<ItemStack> gear = FluxCouplerItem.chargeableGear(player, coupler);

        helper.assertValueEqual(gear.size(), 0,
                "neither loose crystals nor another working coupler are things to charge");

        helper.succeed();
    }
}
