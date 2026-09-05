package dev.symo.actualgenerators.gametest;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.generator.EnchantmentCombustorBlockEntity;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.menu.EnchantmentCombustorMenu;
import dev.symo.actualgenerators.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the generator that burns enchantments.
 *
 * <p>What it pays has to track the levels on the item rather than the item itself, and the item
 * has to come back out — a machine that ate a Netherite pickaxe to make power would be a trap
 * rather than a generator.
 */
@GameTestHolder(ActualGenerators.MODID)
@PrefixGameTestTemplate(false)
public final class EnchantmentCombustorTests {
    private static final String PLATFORM = "platform";
    private static final BlockPos COMBUSTOR = new BlockPos(2, 1, 2);

    /** Matches the config defaults. */
    private static final int FE_PER_LEVEL = 2_000;
    private static final int TICKS_PER_ITEM = 100;

    private EnchantmentCombustorTests() {
    }

    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void burnsASwordAndHandsItBack(GameTestHelper helper) {
        int levels = 5;
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(COMBUSTOR, ModBlocks.ENCHANTMENT_COMBUSTOR.get());
                    combustor(helper).inputHandler()
                            .insertItem(0, enchanted(helper, Items.IRON_SWORD, Enchantments.SHARPNESS, levels), false);
                })
                .thenIdle(TICKS_PER_ITEM + 20)
                .thenExecute(() -> {
                    EnchantmentCombustorBlockEntity combustor = combustor(helper);

                    ItemStack result = combustor.outputHandler().getStackInSlot(0);
                    helper.assertTrue(result.is(Items.IRON_SWORD), "the sword itself must survive the burn");
                    helper.assertValueEqual(EnchantmentCombustorBlockEntity.totalEnchantmentLevels(result), 0,
                            "but with nothing left on it");
                    helper.assertTrue(combustor.inputHandler().getStackInSlot(0).isEmpty(),
                            "and the input slot should be clear");

                    helper.assertValueEqual(combustor.energyStorage().getEnergyStored(), levels * FE_PER_LEVEL,
                            "five levels at the configured rate");
                })
                .thenSucceed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void moreLevelsAreWorthMore(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(COMBUSTOR, ModBlocks.ENCHANTMENT_COMBUSTOR.get());
                    combustor(helper).inputHandler()
                            .insertItem(0, enchanted(helper, Items.IRON_SWORD, Enchantments.SHARPNESS, 1), false);
                })
                .thenIdle(TICKS_PER_ITEM + 20)
                .thenExecute(() -> helper.assertValueEqual(
                        combustor(helper).energyStorage().getEnergyStored(), FE_PER_LEVEL,
                        "one level should be worth exactly a fifth of five"))
                .thenSucceed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void anEnchantedBookComesBackAsAPlainOne(GameTestHelper helper) {
        int levels = 3;
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(COMBUSTOR, ModBlocks.ENCHANTMENT_COMBUSTOR.get());
                    combustor(helper).inputHandler().insertItem(0, book(helper, Enchantments.SHARPNESS, levels), false);
                })
                .thenIdle(TICKS_PER_ITEM + 20)
                .thenExecute(() -> {
                    EnchantmentCombustorBlockEntity combustor = combustor(helper);
                    ItemStack result = combustor.outputHandler().getStackInSlot(0);
                    // A blank enchanted book is a nonsense item; the paper it was written on is not.
                    helper.assertTrue(result.is(Items.BOOK), "a stripped enchanted book is just a book");
                    helper.assertValueEqual(combustor.energyStorage().getEnergyStored(), levels * FE_PER_LEVEL,
                            "stored enchantments count the same as applied ones");
                })
                .thenSucceed();
    }

    @GameTest(template = PLATFORM)
    public static void refusesItemsWithNothingToBurn(GameTestHelper helper) {
        helper.setBlock(COMBUSTOR, ModBlocks.ENCHANTMENT_COMBUSTOR.get());
        EnchantmentCombustorBlockEntity combustor = combustor(helper);

        ItemStack plain = new ItemStack(Items.IRON_SWORD);
        helper.assertTrue(!combustor.inputHandler().isItemValid(0, plain),
                "a plain sword is not fuel");
        helper.assertTrue(!combustor.inputHandler().insertItem(0, plain, true).isEmpty(),
                "and must not be insertable either");

        helper.assertTrue(combustor.inputHandler()
                        .isItemValid(0, enchanted(helper, Items.IRON_SWORD, Enchantments.SHARPNESS, 1)),
                "an enchanted one is");

        helper.assertValueEqual(EnchantmentCombustorBlockEntity.totalEnchantmentLevels(ItemStack.EMPTY), 0,
                "an empty slot is worth nothing");

        helper.succeed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void stopsWhenTheOutputIsBlocked(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(COMBUSTOR, ModBlocks.ENCHANTMENT_COMBUSTOR.get());
                    EnchantmentCombustorBlockEntity combustor = combustor(helper);
                    // A different item in the slot: the result has nowhere to go.
                    combustor.outputHandler().insertItem(0, new ItemStack(Items.DIAMOND_SWORD), false);
                    combustor.inputHandler()
                            .insertItem(0, enchanted(helper, Items.IRON_SWORD, Enchantments.SHARPNESS, 5), false);
                })
                .thenIdle(TICKS_PER_ITEM + 20)
                .thenExecute(() -> {
                    EnchantmentCombustorBlockEntity combustor = combustor(helper);
                    helper.assertValueEqual(combustor.energyStorage().getEnergyStored(), 0,
                            "a blocked machine must not burn the enchantment for nothing");
                    helper.assertTrue(!combustor.inputHandler().getStackInSlot(0).isEmpty(),
                            "and must still be holding the sword");
                })
                .thenSucceed();
    }

    @GameTest(template = PLATFORM)
    public static void takesEveryUpgradeExceptStack(GameTestHelper helper) {
        helper.setBlock(COMBUSTOR, ModBlocks.ENCHANTMENT_COMBUSTOR.get());
        EnchantmentCombustorBlockEntity combustor = combustor(helper);

        for (UpgradeType accepted : new UpgradeType[]{UpgradeType.ENERGY, UpgradeType.SPEED, UpgradeType.OVERCLOCK}) {
            helper.assertTrue(combustor.maxUpgrades(accepted) > 0, accepted + " should be usable here");
        }
        // Enchanted items do not stack, so a batch of them cannot exist.
        helper.assertValueEqual(combustor.maxUpgrades(UpgradeType.STACK), 0,
                "stack upgrades would promise a batch that can never be assembled");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        EnchantmentCombustorMenu menu = new EnchantmentCombustorMenu(1, player.getInventory(), combustor);
        helper.assertValueEqual(menu.upgradeSlotCount(), 3, "the screen should show three slots, not four");

        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void countsEveryLevelOfEveryEnchantment(GameTestHelper helper) {
        ItemStack sword = enchanted(helper, Items.IRON_SWORD, Enchantments.SHARPNESS, 5);
        sword.enchant(holder(helper, Enchantments.UNBREAKING), 3);

        helper.assertValueEqual(EnchantmentCombustorBlockEntity.totalEnchantmentLevels(sword), 8,
                "levels are summed across enchantments, not counted per enchantment");

        ItemStack stripped = EnchantmentCombustorBlockEntity.stripped(sword);
        helper.assertTrue(stripped.is(Items.IRON_SWORD), "stripping keeps the item");
        helper.assertValueEqual(EnchantmentCombustorBlockEntity.totalEnchantmentLevels(stripped), 0,
                "and takes everything off it");

        helper.succeed();
    }

    /**
     * The item is spent the moment the burn starts, so it cannot be pulled back out still
     * enchanted after the machine has already paid for most of it.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void theItemIsSpentBeforeAnyEnergyIsPaidForIt(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(COMBUSTOR, ModBlocks.ENCHANTMENT_COMBUSTOR.get());
                    combustor(helper).inputHandler()
                            .insertItem(0, enchanted(helper, Items.IRON_SWORD, Enchantments.SHARPNESS, 5), false);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    EnchantmentCombustorBlockEntity combustor = combustor(helper);

                    helper.assertTrue(combustor.energyStorage().getEnergyStored() > 0,
                            "it should be burning by now");
                    helper.assertTrue(combustor.inputHandler().getStackInSlot(0).isEmpty(),
                            "and the sword should already have left the input slot");
                    helper.assertTrue(combustor.processing().is(Items.IRON_SWORD),
                            "because the machine is holding it");
                    helper.assertTrue(combustor.inputHandler().extractItem(0, 1, false).isEmpty(),
                            "so there is nothing left to pull back out still enchanted");
                })
                .thenSucceed();
    }

    private static Holder<Enchantment> holder(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
    }

    private static ItemStack enchanted(GameTestHelper helper,
                                       net.minecraft.world.item.Item item,
                                       ResourceKey<Enchantment> enchantment,
                                       int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(holder(helper, enchantment), level);
        return stack;
    }

    private static ItemStack book(GameTestHelper helper, ResourceKey<Enchantment> enchantment, int level) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(holder(helper, enchantment), level);
        stack.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        return stack;
    }

    private static EnchantmentCombustorBlockEntity combustor(GameTestHelper helper) {
        if (helper.getBlockEntity(COMBUSTOR) instanceof EnchantmentCombustorBlockEntity combustor) {
            return combustor;
        }
        throw new IllegalStateException("no enchantment combustor at " + COMBUSTOR);
    }
}
