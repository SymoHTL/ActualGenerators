package dev.symo.actualgenerators.item;

import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.menu.FluxCouplerMenu;
import dev.symo.actualgenerators.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ComponentItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.List;

/**
 * A pocket charger: it draws on the flux crystals loaded into it and keeps everything else in your
 * inventory topped up — tools, armour, any other mod's gear, because all it speaks is the standard
 * FE item capability.
 *
 * <p>It holds no charge of its own. Crystals go in one side, the coupler draws straight out of the
 * one it is working on, and each spent crystal lands in the output slot as an empty, where they
 * stack. That is why it works a crystal at a time: crystals in a stack share one set of components,
 * so there is no such thing as draining half of sixty-four without charging all sixty-four for the
 * price of one.
 *
 * <p>Right click opens it — the slots, the energy upgrade and the on/off switch all live in there.
 * Energy upgrades raise the rate it hands out, which is the only thing there is to buy: nothing is
 * converted here, so there is no speed and no efficiency, only throughput.
 *
 * <p>It runs on an interval rather than every tick. A full inventory scan sixty times a second,
 * per coupler, per player, is not something a server should be asked to do for a convenience item.
 */
public class FluxCouplerItem extends Item {
    /** Crystals waiting to be used. */
    public static final int SLOT_INPUT = 0;
    /** The one being drawn from, always a single crystal. */
    public static final int SLOT_WORKING = 1;
    /** Spent crystals, which stack because they are all empty. */
    public static final int SLOT_OUTPUT = 2;
    /** One energy upgrade, the only kind that means anything here. */
    public static final int SLOT_UPGRADE = 3;
    public static final int SLOT_COUNT = 4;

    /** The same flux colour the crystals use, so the two read as one system. */
    private static final int BAR_COLOUR = 0x4FD8E8;

    public FluxCouplerItem(Properties properties) {
        super(properties.stacksTo(1).component(ModDataComponents.ACTIVE.get(), true));
    }

    /**
     * FE/t before upgrades: whatever the crystal it is drawing on can give.
     *
     * <p>The pace belongs to the crystal rather than to the coupler. A better crystal is a faster
     * one wherever it is used, so a tier of crystal that moves charge quickly does so in a pocket
     * as well as in a machine, and the coupler needs to know nothing about tiers to benefit.
     */
    public static int baseRatePerTick(ItemStack coupler) {
        return FluxCrystalItem.transferRate(crystalInHand(coupler));
    }

    /** FE/t it hands out with whatever upgrade is installed. */
    public static int ratePerTick(ItemStack coupler) {
        return ServerConfig.tuning().transferRate(baseRatePerTick(coupler), upgradeCount(coupler));
    }

    /** The crystal setting the pace: the one in hand, or the next one up if it is between crystals. */
    private static ItemStack crystalInHand(ItemStack coupler) {
        IItemHandlerModifiable slots = slots(coupler);
        ItemStack working = slots.getStackInSlot(SLOT_WORKING);
        return working.isEmpty() ? slots.getStackInSlot(SLOT_INPUT) : working;
    }

    /** How often it looks at the inventory at all. */
    public static int intervalTicks() {
        return ServerConfig.valueOr(ServerConfig.FLUX_COUPLER_INTERVAL, 20);
    }

    /** The coupler's own slots, read and written straight through to the stack. */
    public static IItemHandlerModifiable slots(ItemStack coupler) {
        return new ComponentItemHandler(coupler, ModDataComponents.SLOTS.get(), SLOT_COUNT);
    }

    public static boolean isActive(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.ACTIVE.get(), true);
    }

    public static void setActive(ItemStack stack, boolean active) {
        stack.set(ModDataComponents.ACTIVE.get(), active);
    }

    /** Energy upgrades installed, capped at what the tuning would count. */
    public static int upgradeCount(ItemStack coupler) {
        ItemStack upgrade = slots(coupler).getStackInSlot(SLOT_UPGRADE);
        return Math.min(upgrade.getCount(), ServerConfig.tuning().maxUpgrades(UpgradeType.ENERGY));
    }

    /** FE loaded into the coupler right now, across every crystal it is holding. */
    public static long chargeHeld(ItemStack coupler) {
        IItemHandlerModifiable slots = slots(coupler);
        long total = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = slots.getStackInSlot(slot);
            if (stack.getItem() instanceof FluxCrystalItem) {
                total += FluxCrystalItem.energyOf(stack) * stack.getCount();
            }
        }
        return total;
    }

    /** What those crystals would hold if they were all full, for the bar and the readout. */
    public static long chargeCapacity(ItemStack coupler) {
        IItemHandlerModifiable slots = slots(coupler);
        long total = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = slots.getStackInSlot(slot);
            if (stack.getItem() instanceof FluxCrystalItem) {
                total += FluxCrystalItem.capacity() * stack.getCount();
            }
        }
        return total;
    }

    // ------------------------------------------------------------------ opening it

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            int slot = inventorySlot(player, hand);
            serverPlayer.openMenu(
                    new SimpleMenuProvider((id, inventory, viewer) -> new FluxCouplerMenu(id, inventory, slot),
                            stack.getHoverName()),
                    buffer -> buffer.writeVarInt(slot));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Where the held coupler sits in the player's inventory, which is how the menu finds it again. */
    private static int inventorySlot(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
    }

    // ------------------------------------------------------------------ the work

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !isActive(stack) || !(entity instanceof Player player)) {
            return;
        }
        // Staggered by slot so two couplers in one inventory do not both fire on the same tick,
        // and so two players carrying one do not line up either.
        if ((level.getGameTime() + slot) % intervalTicks() != 0) {
            return;
        }
        run(stack, chargeableGear(player, stack));
    }

    /**
     * One pass: hand out this interval's worth of FE, drawn straight from the crystal in the
     * working slot and refilled from the input slot as each one runs dry.
     *
     * <p>Nothing is taken from a crystal unless something is actually asking for it, so a coupler
     * carried through a day of doing nothing spends nothing.
     */
    public static void run(ItemStack coupler, List<ItemStack> gear) {
        if (gear.isEmpty()) {
            return;
        }

        IItemHandlerModifiable slots = slots(coupler);
        long budget = (long) ratePerTick(coupler) * intervalTicks();

        while (budget > 0) {
            if (FluxCrystalItem.energyOf(slots.getStackInSlot(SLOT_WORKING)) <= 0 && !nextCrystal(slots)) {
                break;
            }

            ItemStack working = slots.getStackInSlot(SLOT_WORKING);
            long charge = FluxCrystalItem.energyOf(working);
            long spent = spend(gear, Math.min(budget, charge));
            if (spent <= 0) {
                break;
            }
            FluxCrystalItem.setEnergy(working, charge - spent);
            slots.setStackInSlot(SLOT_WORKING, working);
            budget -= spent;
        }

        // A crystal that finished on the last FE of the pass should not sit in the machine until
        // the next one comes round: retire it now, so what a player sees is an empty in the output.
        retireIfSpent(slots);
    }

    /** Everything in the inventory that takes FE — minus the crystals, and minus the couplers. */
    public static List<ItemStack> chargeableGear(Player player, ItemStack coupler) {
        Inventory inventory = player.getInventory();
        List<ItemStack> gear = new ArrayList<>();
        // getContainerSize covers the main inventory, the armour slots and the offhand.
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            // Crystals are the fuel, not the customer, and a coupler must not charge itself.
            if (stack.isEmpty() || stack == coupler || stack.getItem() instanceof FluxCrystalItem) {
                continue;
            }
            // Nor another working coupler: two of them would spend the rest of the game handing
            // the same charge back and forth. A switched-off one is just a battery, so it counts.
            if (stack.getItem() instanceof FluxCouplerItem && isActive(stack)) {
                continue;
            }
            IEnergyStorage battery = stack.getCapability(Capabilities.EnergyStorage.ITEM);
            if (battery != null && battery.canReceive() && battery.getEnergyStored() < battery.getMaxEnergyStored()) {
                gear.add(stack);
            }
        }
        return gear;
    }

    /** Hands out up to {@code available} FE across the gear. Returns what was actually taken. */
    private static long spend(List<ItemStack> gear, long available) {
        long moved = 0;
        for (ItemStack stack : gear) {
            long left = available - moved;
            if (left <= 0) {
                break;
            }
            IEnergyStorage battery = stack.getCapability(Capabilities.EnergyStorage.ITEM);
            if (battery == null) {
                continue;
            }
            moved += Math.max(0, battery.receiveEnergy((int) Math.min(left, Integer.MAX_VALUE), false));
        }
        return moved;
    }

    /** Retires a spent crystal and brings the next one through. */
    private static boolean nextCrystal(IItemHandlerModifiable slots) {
        if (!retireIfSpent(slots)) {
            return false;
        }
        ItemStack input = slots.getStackInSlot(SLOT_INPUT);
        if (!(input.getItem() instanceof FluxCrystalItem) || FluxCrystalItem.energyOf(input) <= 0) {
            return false;
        }
        ItemStack taken = input.split(1);
        slots.setStackInSlot(SLOT_INPUT, input);
        slots.setStackInSlot(SLOT_WORKING, taken);
        return true;
    }

    /**
     * Puts the working crystal in the output slot once it has nothing left to give.
     *
     * @return false when it is still in the way — an empty with nowhere to go blocks the coupler
     *         rather than being deleted
     */
    private static boolean retireIfSpent(IItemHandlerModifiable slots) {
        ItemStack working = slots.getStackInSlot(SLOT_WORKING);
        if (working.isEmpty()) {
            return true;
        }
        if (FluxCrystalItem.energyOf(working) > 0) {
            return false;
        }

        ItemStack output = slots.getStackInSlot(SLOT_OUTPUT);
        if (output.isEmpty()) {
            slots.setStackInSlot(SLOT_OUTPUT, working);
        } else if (ItemStack.isSameItemSameComponents(output, working)
                && output.getCount() + working.getCount() <= output.getMaxStackSize()) {
            output.grow(working.getCount());
            slots.setStackInSlot(SLOT_OUTPUT, output);
        } else {
            return false;
        }
        slots.setStackInSlot(SLOT_WORKING, ItemStack.EMPTY);
        return true;
    }

    // ------------------------------------------------------------------ presentation

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return chargeCapacity(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        long capacity = chargeCapacity(stack);
        return capacity <= 0 ? 0 : (int) Math.round(13.0 * chargeHeld(stack) / capacity);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOUR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean active = isActive(stack);
        tooltip.add(Component.translatable("gui.actualgenerators.stored", String.format("%,d", chargeHeld(stack)))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.actualgenerators.flux_coupler." + (active ? "on" : "off"))
                .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.RED));
        tooltip.add(Component.translatable("item.actualgenerators.flux_coupler.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
