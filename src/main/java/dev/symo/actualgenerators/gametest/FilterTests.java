package dev.symo.actualgenerators.gametest;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.item.FilterItem;
import dev.symo.actualgenerators.logistics.FilterContents;
import dev.symo.actualgenerators.menu.FilterMenu;
import dev.symo.actualgenerators.recipe.FilterCopyRecipe;
import dev.symo.actualgenerators.registry.ModDataComponents;
import dev.symo.actualgenerators.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The Filter item on its own: one list, each entry on the lists it is switched onto and letting
 * its match through or stopping it, what a click adds, what "match NBT" means, what a trait entry
 * reads, what the window's buttons do, and what crafting does to it.
 */
@GameTestHolder(ActualGenerators.MODID)
@PrefixGameTestTemplate(false)
public final class FilterTests {
    private static final String EMPTY = "empty";

    private FilterTests() {
    }

    @GameTest(template = EMPTY)
    public static void anEmptyListMeansAnythingAndAWhitelistMeansExactly(GameTestHelper helper) {
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);
        ItemStack dirt = new ItemStack(Items.DIRT);

        FilterContents blank = FilterContents.EMPTY;
        helper.assertTrue(blank.allowsItem(cobble, true) && blank.allowsItem(dirt, false),
                "a blank filter passes anything, either way");

        FilterContents both = blank.add(FilterContents.Entry.ofItem(cobble));
        helper.assertTrue(both.allowsItem(cobble, true) && both.allowsItem(cobble, false), "a fresh entry counts both ways");
        helper.assertTrue(!both.allowsItem(dirt, true) && !both.allowsItem(dirt, false), "and stops what is not on it, both ways");

        FilterContents only = blank.add(FilterContents.Entry.ofItem(cobble).withDirections(true, false));
        helper.assertTrue(only.allowsItem(cobble, true) && !only.allowsItem(dirt, true),
                "switched to receiving only, it is the receiving whitelist");
        helper.assertTrue(only.allowsItem(dirt, false), "while the sending list, with nothing on it, passes anything");

        FilterContents sending = blank.add(FilterContents.Entry.ofItem(dirt).withDirections(false, true));
        helper.assertTrue(sending.allowsItem(dirt, false) && !sending.allowsItem(cobble, false), "and the other way round");
        helper.assertTrue(sending.allowsItem(cobble, true), "says nothing about receiving");

        FilterContents off = blank.add(FilterContents.Entry.ofItem(cobble).withDirections(false, false));
        helper.assertTrue(off.allowsItem(dirt, true) && off.allowsItem(dirt, false), "an entry switched off both ways is no list");
        helper.assertValueEqual(off.count(true) + off.count(false), 0, "and counts for neither");
        helper.assertValueEqual(both.count(true) + both.count(false), 2, "where a fresh one counts for both");

        helper.succeed();
    }

    /** Whitelist or blacklist is the entry's own, and a blacklist entry wins. */
    @GameTest(template = EMPTY)
    public static void aBlacklistEntryStopsItsMatchAndWinsOverAWhitelistOne(GameTestHelper helper) {
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);
        ItemStack dirt = new ItemStack(Items.DIRT);
        ItemStack sand = new ItemStack(Items.SAND);

        FilterContents allBut = FilterContents.EMPTY.add(FilterContents.Entry.ofItem(dirt).withBlacklist(true));
        helper.assertTrue(allBut.allowsItem(cobble, true) && allBut.allowsItem(sand, false),
                "a blacklist entry alone passes everything else");
        helper.assertTrue(!allBut.allowsItem(dirt, true) && !allBut.allowsItem(dirt, false), "and stops its match, both ways");

        FilterContents mixed = allBut.add(FilterContents.Entry.ofItem(cobble));
        helper.assertTrue(mixed.allowsItem(cobble, true), "with a whitelist entry beside it, the whitelisted thing passes");
        helper.assertTrue(!mixed.allowsItem(sand, true), "what is on neither list is stopped, since there is now a whitelist");
        helper.assertTrue(!mixed.allowsItem(dirt, true), "and the blacklisted thing is still stopped");

        FilterContents logs = FilterContents.EMPTY
                .add(FilterContents.Entry.ofItem(new ItemStack(Items.OAK_LOG)).withTag(ResourceLocation.parse("minecraft:logs")))
                .add(FilterContents.Entry.ofItem(new ItemStack(Items.BIRCH_LOG)).withBlacklist(true));
        helper.assertTrue(logs.allowsItem(new ItemStack(Items.OAK_LOG), true), "every log is allowed by the tag");
        helper.assertTrue(!logs.allowsItem(new ItemStack(Items.BIRCH_LOG), true), "except the one a blacklist entry names");

        FilterContents oneWay = FilterContents.EMPTY.add(FilterContents.Entry.ofItem(dirt).withBlacklist(true).withDirections(true, false));
        helper.assertTrue(!oneWay.allowsItem(dirt, true) && oneWay.allowsItem(dirt, false), "a blacklist entry has directions too");

        helper.assertTrue(!FilterContents.Entry.ofItem(dirt).withBlacklist(true).equals(FilterContents.Entry.ofItem(dirt)),
                "and the mode is part of the entry, so the filters differ");

        helper.succeed();
    }

    /** Pipez's two modes: ignore the data (the default), or insist on every component the entry has. */
    @GameTest(template = EMPTY)
    public static void matchingComponentsMeansTheStackMustCarryWhatTheEntryCarries(GameTestHelper helper) {
        ItemStack plain = new ItemStack(Items.COBBLESTONE);
        ItemStack named = plain.copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Rock"));
        ItemStack namedAndMore = named.copy();
        namedAndMore.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("A fine rock"))));
        ItemStack otherName = plain.copy();
        otherName.set(DataComponents.CUSTOM_NAME, Component.literal("Stone"));

        FilterContents.Entry ignoring = FilterContents.Entry.ofItem(named);
        helper.assertTrue(!ignoring.matchComponents(), "an entry ignores data unless told otherwise");
        helper.assertTrue(ignoring.matches(plain) && ignoring.matches(named) && ignoring.matches(otherName),
                "ignoring: the item is the item, whatever it is called");

        FilterContents.Entry matching = ignoring.withMatchComponents(true);
        helper.assertTrue(!matching.matches(plain), "matching: a plain one has no name, so no");
        helper.assertTrue(matching.matches(named), "the same name passes");
        helper.assertTrue(matching.matches(namedAndMore), "so does one with more on it, which is Pipez's rule");
        helper.assertTrue(!matching.matches(otherName), "a different name does not");
        helper.assertTrue(!matching.equals(ignoring), "and the flag is part of the entry, so the filters differ");

        helper.succeed();
    }

    /** A fluid entry answers fluid questions only, an item entry item questions only. */
    @GameTest(template = EMPTY)
    public static void aFluidEntryFiltersFluidsAndLeavesItemsAlone(GameTestHelper helper) {
        FluidStack water = new FluidStack(Fluids.WATER, 1000);
        FluidStack lava = new FluidStack(Fluids.LAVA, 1000);

        FilterContents filter = FilterContents.EMPTY.add(FilterContents.Entry.ofFluid(water));
        helper.assertTrue(filter.allowsFluid(water, true), "a water entry passes water");
        helper.assertTrue(!filter.allowsFluid(lava, true), "and stops lava");
        helper.assertTrue(filter.allowsItem(new ItemStack(Items.COBBLESTONE), true),
                "while items, which it says nothing about, all pass");

        helper.succeed();
    }

    /** Any damaged item, any enchanted item, from older saves: the picture is only a picture, the stack is what is read. */
    @GameTest(template = EMPTY)
    public static void damagedAndEnchantedEntriesReadTheStackNotThePicture(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        ItemStack worn = sword.copy();
        worn.setDamageValue(10);
        Holder<Enchantment> sharpness = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
        ItemStack sharp = sword.copy();
        sharp.enchant(sharpness, 1);
        ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(sharpness, 1));

        FilterContents.Entry damaged = FilterContents.Entry.ofItem(pickaxe).withKind(FilterContents.Kind.DAMAGED, null);
        helper.assertTrue(damaged.matches(worn), "a damaged entry passes a worn sword, whatever the picture shows");
        helper.assertTrue(!damaged.matches(sword) && !damaged.matches(pickaxe), "and not a fresh one, not even the pictured item");

        FilterContents.Entry enchanted = FilterContents.Entry.ofItem(pickaxe).withKind(FilterContents.Kind.ENCHANTED, null);
        helper.assertTrue(enchanted.matches(sharp), "an enchanted entry passes an enchanted sword");
        helper.assertTrue(enchanted.matches(book), "and an enchanted book, whose enchantment is stored, not worn");
        helper.assertTrue(!enchanted.matches(sword) && !enchanted.matches(new ItemStack(Items.BOOK)), "and stops plain ones");

        helper.assertTrue(!damaged.withMatchComponents(true).matchComponents(), "a trait entry reads no NBT");
        helper.assertValueEqual(damaged.itself(), FilterContents.Entry.ofItem(pickaxe), "and itself again is the pictured pickaxe");
        helper.assertTrue(!FilterContents.Entry.ofFluid(new FluidStack(Fluids.WATER, 1000))
                .withKind(FilterContents.Kind.DAMAGED, null).matches(new FluidStack(Fluids.WATER, 1000)), "a fluid is never damaged");

        helper.succeed();
    }

    /** Left click writes the item, right click the fluid inside it, an empty hand nothing. */
    @GameTest(template = EMPTY)
    public static void aClickPutsTheItemInAndARightClickItsFluid(GameTestHelper helper) {
        ItemStack bucket = new ItemStack(Items.WATER_BUCKET);

        FilterContents.Entry left = FilterMenu.entryFor(bucket, false);
        helper.assertTrue(!left.isFluid() && left.item().is(Items.WATER_BUCKET), "left: the bucket");

        FilterContents.Entry right = FilterMenu.entryFor(bucket, true);
        helper.assertTrue(right.isFluid() && right.fluid().is(Fluids.WATER), "right: the water in it");

        FilterContents.Entry cobble = FilterMenu.entryFor(new ItemStack(Items.COBBLESTONE), true);
        helper.assertTrue(!cobble.isFluid() && cobble.item().is(Items.COBBLESTONE),
                "right on something with no fluid in it: the item, since there is nothing else to write");
        helper.assertTrue(FilterMenu.entryFor(ItemStack.EMPTY, false).isEmpty(), "an empty hand writes nothing");
        helper.assertTrue(FilterContents.EMPTY.add(FilterMenu.entryFor(ItemStack.EMPTY, false)).isBlank(),
                "and adding nothing adds nothing");

        FilterContents.Entry sendingBlock = FilterContents.Entry.ofItem(bucket).withDirections(false, true).withBlacklist(true);
        FilterContents.Entry repictured = sendingBlock.repictured(FilterContents.Entry.ofItem(new ItemStack(Items.DIRT)));
        helper.assertTrue(repictured.item().is(Items.DIRT) && !repictured.receive() && repictured.send() && repictured.blacklist(),
                "a new picture on an entry keeps the entry's switches");

        helper.succeed();
    }

    /** The five buttons of a row, by absolute index, through the menu as the window presses them. */
    @GameTest(template = EMPTY)
    public static void theWindowsButtonsFlipAnEntrysSwitchesAndRemoveIt(GameTestHelper helper) {
        ServerPlayer player = LinkPortTests.testPlayer(helper);
        ItemStack filter = ModItems.FILTER.toStack();
        FilterItem.setContents(filter, FilterContents.EMPTY
                .add(FilterContents.Entry.ofItem(new ItemStack(Items.COBBLESTONE)))
                .add(FilterContents.Entry.ofItem(new ItemStack(Items.DIRT))));
        player.getInventory().setItem(0, filter);
        FilterMenu menu = new FilterMenu(1, player.getInventory(), 0);

        helper.assertTrue(menu.clickMenuButton(player, FilterMenu.entryButton(1, FilterMenu.ENTRY_RECEIVE)), "IN is a button");
        helper.assertTrue(!menu.entry(1).receive() && menu.entry(1).send(), "that switches receiving off for that entry");
        helper.assertTrue(menu.entry(0).receive(), "and no other");
        menu.clickMenuButton(player, FilterMenu.entryButton(1, FilterMenu.ENTRY_SEND));
        helper.assertTrue(!menu.entry(1).send(), "EX likewise");
        menu.clickMenuButton(player, FilterMenu.entryButton(1, FilterMenu.ENTRY_MODE));
        helper.assertTrue(menu.entry(1).blacklist(), "the mode button makes it a blacklist entry");
        menu.clickMenuButton(player, FilterMenu.entryButton(1, FilterMenu.ENTRY_MATCH));
        helper.assertTrue(menu.entry(1).matchComponents(), "the match button makes its data count");
        helper.assertTrue(!menu.clickMenuButton(player, FilterMenu.entryButton(5, FilterMenu.ENTRY_MODE)),
                "a button for a row that is not there does nothing");

        menu.setEntry(-1, FilterContents.Entry.ofFluid(new FluidStack(Fluids.WATER, 1000)));
        helper.assertValueEqual(menu.contents().size(), 3, "an entry set past the end is added, the way a click on the page's slot adds");
        menu.setEntry(0, FilterContents.Entry.ofItem(new ItemStack(Items.SAND)));
        helper.assertTrue(menu.entry(0).item().is(Items.SAND), "and one set at an index replaces");

        menu.clickMenuButton(player, FilterMenu.entryButton(0, FilterMenu.ENTRY_REMOVE));
        helper.assertValueEqual(menu.contents().size(), 2, "remove takes the row out");
        helper.assertTrue(menu.entry(0).item().is(Items.DIRT) && menu.entry(0).blacklist(), "and the rest move up, switches and all");

        helper.succeed();
    }

    /** The list is a list: entries go on the end, come off by index, and there are at most sixty-four. */
    @GameTest(template = EMPTY)
    public static void theListAddsAtTheEndRemovesByIndexAndStopsAtSixtyFour(GameTestHelper helper) {
        FilterContents list = FilterContents.EMPTY
                .add(FilterContents.Entry.ofItem(new ItemStack(Items.COBBLESTONE)))
                .add(FilterContents.Entry.ofItem(new ItemStack(Items.DIRT)))
                .add(FilterContents.Entry.ofItem(new ItemStack(Items.SAND)));
        helper.assertValueEqual(list.size(), 3, "three added");
        helper.assertTrue(list.entry(1).item().is(Items.DIRT), "in the order they went in");

        FilterContents shorter = list.remove(0);
        helper.assertValueEqual(shorter.size(), 2, "one removed");
        helper.assertTrue(shorter.entry(0).item().is(Items.DIRT), "and the rest moved up");
        helper.assertTrue(list.set(1, FilterContents.Entry.EMPTY).entry(1).item().is(Items.SAND), "setting an entry to nothing removes it");
        helper.assertTrue(list.set(99, FilterContents.Entry.ofItem(new ItemStack(Items.GRAVEL))).entry(3).item().is(Items.GRAVEL),
                "setting past the end adds");
        helper.assertTrue(list.entry(99).isEmpty() && list.remove(99).equals(list), "past the end is nothing, and removing it changes nothing");

        FilterContents full = FilterContents.EMPTY;
        for (int index = 0; index < FilterContents.MAX_ENTRIES + 10; index++) {
            full = full.add(FilterContents.Entry.ofItem(new ItemStack(Items.STONE)));
        }
        helper.assertValueEqual(full.size(), FilterContents.MAX_ENTRIES, "the list stops at its cap");

        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void craftingCopiesAFilterOntoABlankAndClearsOneAlone(GameTestHelper helper) {
        ItemStack set = ModItems.FILTER.toStack();
        FilterItem.setContents(set, FilterContents.EMPTY.add(FilterContents.Entry.ofItem(new ItemStack(Items.COBBLESTONE))));
        ItemStack blank = ModItems.FILTER.toStack();

        ItemStack copied = FilterCopyRecipe.result(CraftingInput.of(2, 1, List.of(set, blank)));
        helper.assertTrue(copied != null, "a set filter and a blank one is a recipe");
        helper.assertValueEqual(copied.getCount(), 2, "that gives two");
        helper.assertTrue(FilterItem.contents(copied).equals(FilterItem.contents(set)), "both set up like the first");

        ItemStack cleared = FilterCopyRecipe.result(CraftingInput.of(1, 1, List.of(set)));
        helper.assertTrue(cleared != null, "a set filter alone is a recipe too");
        helper.assertTrue(!FilterItem.isConfigured(cleared), "that hands back a blank one");

        helper.assertTrue(FilterCopyRecipe.result(CraftingInput.of(2, 1, List.of(blank, blank.copy()))) == null,
                "two blanks are not a recipe: there is nothing to copy");
        helper.assertTrue(FilterCopyRecipe.result(CraftingInput.of(1, 1, List.of(blank))) == null,
                "and a blank alone is not either: there is nothing to clear");

        helper.succeed();
    }

    /**
     * A filter saved as two grids (the first sixteen slots receiving, the rest sending, empties
     * kept, a tag or a mod as its own field, a blacklist mode per direction) reads as the list it
     * meant, the direction's mode becoming each entry's own; one saved with the modes per direction
     * over a list too; and one saved before the grids had a direction, a bare list, still reads.
     * Worlds have all three.
     */
    @GameTest(template = EMPTY)
    public static void aFilterSavedTheOldWaysStillLoads(GameTestHelper helper) {
        ListTag grids = new ListTag();
        for (int slot = 0; slot < 32; slot++) {
            grids.add(new CompoundTag());
        }
        grids.set(0, entryTag("minecraft:raw_iron", null));
        grids.set(16, entryTag("minecraft:cobblestone", "c:cobblestones"));
        CompoundTag old = new CompoundTag();
        old.put("entries", grids);
        old.putBoolean("send_blacklist", true);

        RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        FilterContents loaded = FilterContents.CODEC.parse(ops, old).getOrThrow();
        helper.assertValueEqual(loaded.size(), 2, "the empties are gone");
        FilterContents.Entry iron = loaded.entry(0);
        helper.assertTrue(iron.item().is(Items.RAW_IRON) && iron.receive() && !iron.send() && !iron.blacklist(),
                "the top grid was the receiving list, a whitelist");
        FilterContents.Entry cobble = loaded.entry(1);
        helper.assertTrue(cobble.item().is(Items.COBBLESTONE) && !cobble.receive() && cobble.send() && cobble.blacklist(),
                "the bottom grid the sending one, which was a blacklist, so its entry is one");
        helper.assertTrue(cobble.kind() == FilterContents.Kind.TAG
                        && cobble.tag().equals(java.util.Optional.of(ResourceLocation.parse("c:cobblestones"))),
                "and a tag written the old way is still a tag entry");

        Tag saved = FilterContents.CODEC.encodeStart(ops, loaded).getOrThrow();
        helper.assertTrue(saved instanceof CompoundTag compound && compound.getInt("version") == 3, "and it saves in the new shape");
        helper.assertTrue(FilterContents.CODEC.parse(ops, saved).getOrThrow().equals(loaded), "which reads back the same");

        // Version 2: a list with the modes per direction. An entry on both directions with the
        // modes disagreeing is two entries now.
        ListTag list = new ListTag();
        CompoundTag both = entryTag("minecraft:dirt", null);
        list.add(both);
        CompoundTag two = new CompoundTag();
        two.put("entries", list);
        two.putBoolean("receive_blacklist", true);
        two.putInt("version", 2);
        FilterContents fromTwo = FilterContents.CODEC.parse(ops, two).getOrThrow();
        helper.assertValueEqual(fromTwo.size(), 2, "one entry on a blacklisted and a whitelisted direction is split in two");
        helper.assertTrue(fromTwo.entry(0).receive() && !fromTwo.entry(0).send() && fromTwo.entry(0).blacklist(),
                "the receiving half a blacklist entry");
        helper.assertTrue(!fromTwo.entry(1).receive() && fromTwo.entry(1).send() && !fromTwo.entry(1).blacklist(),
                "the sending half a whitelist one");

        ListTag bare = new ListTag();
        bare.add(entryTag("minecraft:raw_iron", null));
        FilterContents fromBare = FilterContents.CODEC.parse(ops, bare).getOrThrow();
        helper.assertTrue(fromBare.size() == 1 && fromBare.entry(0).item().is(Items.RAW_IRON) && fromBare.entry(0).receive()
                && !fromBare.entry(0).send() && !fromBare.entry(0).blacklist(), "a bare list reads as the receiving grid it was");

        helper.succeed();
    }

    private static CompoundTag entryTag(String id, String tag) {
        CompoundTag item = new CompoundTag();
        item.putString("id", id);
        item.putInt("count", 1);
        CompoundTag entry = new CompoundTag();
        entry.put("item", item);
        if (tag != null) {
            entry.putString("tag", tag);
        }
        return entry;
    }

    /**
     * A tag entry typed by name rather than picked off an item: pictured by the first item under
     * the tag when it has one, by nothing when it has none, and an entry either way, one that
     * stands for the tag, that a list keeps, and that comes back from a save as it went.
     */
    @GameTest(template = EMPTY)
    public static void aTagTypedByNameNeedsNoPicture(GameTestHelper helper) {
        FilterContents.Entry ingots = FilterContents.Entry.ofTag(ResourceLocation.parse("c:ingots"));
        helper.assertTrue(!ingots.isEmpty(), "a typed tag is an entry");
        helper.assertTrue(!ingots.item().isEmpty(), "pictured by something under the tag");
        helper.assertTrue(ingots.matches(new ItemStack(Items.IRON_INGOT)), "and it stands for the tag");
        helper.assertTrue(!ingots.matches(new ItemStack(Items.COBBLESTONE)), "not for anything else");

        FilterContents.Entry nothing = FilterContents.Entry.ofTag(ResourceLocation.parse("actualgenerators:nothing_is_under_this"));
        helper.assertTrue(!nothing.isEmpty() && nothing.item().isEmpty(), "a tag with nothing under it is an entry with no picture");
        helper.assertTrue(!nothing.matches(new ItemStack(Items.IRON_INGOT)), "that matches nothing");
        FilterContents list = FilterContents.EMPTY.add(nothing);
        helper.assertValueEqual(list.size(), 1, "and a list keeps it");
        helper.assertTrue(!list.allowsItem(new ItemStack(Items.IRON_INGOT), true), "as a whitelist line nothing gets past");

        RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        FilterContents back = FilterContents.CODEC.parse(ops, FilterContents.CODEC.encodeStart(ops, list).getOrThrow()).getOrThrow();
        helper.assertTrue(!back.entry(0).isEmpty() && back.entry(0).tag().equals(nothing.tag()), "and it comes back from a save as it went");
        helper.succeed();
    }

    /** A cleared filter loses its component entirely, so cleared and fresh ones share a stack. */
    @GameTest(template = EMPTY)
    public static void blankFiltersStackBecauseABlankCarriesNoComponent(GameTestHelper helper) {
        ItemStack filter = ModItems.FILTER.toStack();
        FilterItem.setContents(filter, FilterContents.EMPTY.add(FilterContents.Entry.ofItem(new ItemStack(Items.DIRT))));
        helper.assertTrue(filter.has(ModDataComponents.FILTER.get()), "a set filter carries its list");

        FilterItem.setContents(filter, FilterContents.EMPTY);
        helper.assertTrue(!filter.has(ModDataComponents.FILTER.get()), "a blank one carries nothing");
        helper.assertTrue(ItemStack.isSameItemSameComponents(filter, ModItems.FILTER.toStack()),
                "so it stacks with one straight out of the crafting table");

        helper.succeed();
    }
}
