package dev.symo.actualgenerators.logistics;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import dev.symo.actualgenerators.item.FilterItem;
import dev.symo.actualgenerators.machine.TransferKind;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything a pad is set to, as one piece of NBT: its label and network, what its channels carry,
 * every link's switches and numbers, the contents of every filter in its slots and the upgrades
 * in its three. What a label replicates, what the config card carries and what Export puts on the
 * clipboard are all this, so the three can never disagree about what "the settings" are.
 *
 * <p>Applying one writes the settings straight in and moves the items it needs, filters and
 * upgrades, out of the player's inventory: a filter slot that is empty gets a blank filter from the
 * player with the contents written onto it, an upgrade slot gets the upgrade. Items never come from
 * nowhere: with no player, or a player who has none, the slot stays as it is and is counted, and
 * the count is shown to whoever asked. Items a slot no longer needs go back to the player.
 *
 * <p>The text form is SNBT, not JSON: a JSON round trip turns byte arrays into lists and a UUID
 * into four numbers, and nothing would read back.
 */
public record PadSnapshot(CompoundTag tag) {
    public static final Codec<PadSnapshot> CODEC = CompoundTag.CODEC.xmap(PadSnapshot::new, PadSnapshot::tag);
    public static final StreamCodec<ByteBuf, PadSnapshot> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(PadSnapshot::new, PadSnapshot::tag);

    /** The longest clipboard text the server will look at. Forty-eight filters of sixty-four entries fit. */
    public static final int MAX_TEXT = 1 << 20;

    private static final String KEY_LABEL = "Label";
    private static final String KEY_NETWORK = "Network";
    private static final String KEY_KINDS = "KindMasks";
    private static final String KEY_LINKS = "Channels";
    private static final String KEY_FILTERS = "Filters";
    private static final String KEY_UPGRADES = "Upgrades";
    private static final String KEY_SLOT = "Slot";
    private static final String KEY_FILTER = "Filter";
    private static final String KEY_ITEM = "Item";

    /** What applying one did: how many pads, and how many of them could not be given a filter or an upgrade. */
    public record Applied(int pads, int missingFilters, int missingUpgrades) {
        public static final Applied NONE = new Applied(0, 0, 0);

        public Applied plus(Applied other) {
            return new Applied(pads + other.pads, missingFilters + other.missingFilters, missingUpgrades + other.missingUpgrades);
        }

        public boolean lacksAnything() {
            return missingFilters > 0 || missingUpgrades > 0;
        }
    }

    /** The pad as it is now. */
    public static PadSnapshot of(PortFace face) {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_LABEL, face.label());
        if (face.networkId() != null) {
            tag.putString(KEY_NETWORK, face.networkId().toString());
        }
        byte[] kinds = new byte[PortFace.CHANNELS];
        for (int channel = 0; channel < PortFace.CHANNELS; channel++) {
            kinds[channel] = (byte) face.currentKinds(channel);
        }
        tag.putByteArray(KEY_KINDS, kinds);
        ListTag links = new ListTag();
        for (int channel = 0; channel < PortFace.CHANNELS; channel++) {
            for (TransferKind kind : TransferKind.all()) {
                PortChannel link = face.link(channel, kind);
                if (!link.isBlank()) {
                    links.add(link.save());
                }
            }
        }
        tag.put(KEY_LINKS, links);

        Level level = face.level();
        HolderLookup.Provider registries = level == null ? null : level.registryAccess();
        ListTag filters = new ListTag();
        ItemStackHandler filterSlots = face.filterHandler();
        for (int slot = 0; slot < filterSlots.getSlots(); slot++) {
            ItemStack stack = filterSlots.getStackInSlot(slot);
            if (!(stack.getItem() instanceof FilterItem) || registries == null) {
                continue;
            }
            FilterContents contents = FilterItem.contents(stack);
            if (contents.isBlank()) {
                continue;
            }
            int at = slot;
            FilterContents.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), contents)
                    .result()
                    .ifPresent(encoded -> {
                        CompoundTag entry = new CompoundTag();
                        entry.putInt(KEY_SLOT, at);
                        entry.put(KEY_FILTER, encoded);
                        filters.add(entry);
                    });
        }
        tag.put(KEY_FILTERS, filters);
        ListTag upgrades = new ListTag();
        ItemStackHandler upgradeSlots = face.upgradeHandler();
        for (int slot = 0; slot < upgradeSlots.getSlots(); slot++) {
            ItemStack stack = upgradeSlots.getStackInSlot(slot);
            if (stack.isEmpty() || registries == null) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(KEY_SLOT, slot);
            entry.put(KEY_ITEM, stack.save(registries));
            upgrades.add(entry);
        }
        tag.put(KEY_UPGRADES, upgrades);
        return new PadSnapshot(tag);
    }

    /** The clipboard form. */
    public String toText() {
        return tag.toString();
    }

    /** What the clipboard held, if it was one of these; nothing for anything else, quietly. */
    public static Optional<PadSnapshot> parse(@Nullable String text) {
        if (text == null || text.isBlank() || text.length() > MAX_TEXT) {
            return Optional.empty();
        }
        try {
            CompoundTag tag = TagParser.parseTag(text.strip());
            return tag.contains(KEY_LINKS, Tag.TAG_LIST) ? Optional.of(new PadSnapshot(tag)) : Optional.empty();
        } catch (CommandSyntaxException e) {
            return Optional.empty();
        }
    }

    public String label() {
        return tag.getString(KEY_LABEL);
    }

    public Optional<UUID> network() {
        if (!tag.contains(KEY_NETWORK, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(tag.getString(KEY_NETWORK)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** How many links are switched on or set to anything. */
    public int linkCount() {
        return tag.getList(KEY_LINKS, Tag.TAG_COMPOUND).size();
    }

    /**
     * Writes these settings onto a pad.
     *
     * @param source      whose inventory filters and upgrades come from and go back to; null for
     *                    nobody, in which case items stay where they are and are counted
     * @param withNetwork whether the pad also joins the network the settings came from, when the
     *                    source may use it
     */
    public Applied applyTo(PortFace face, @Nullable Player source, boolean withNetwork) {
        Level level = face.level();
        if (level == null) {
            return Applied.NONE;
        }
        HolderLookup.Provider registries = level.registryAccess();

        if (tag.contains(KEY_KINDS)) {
            byte[] masks = tag.getByteArray(KEY_KINDS);
            int[] kinds = new int[PortFace.CHANNELS];
            for (int channel = 0; channel < PortFace.CHANNELS; channel++) {
                kinds[channel] = channel < masks.length ? masks[channel] & ChannelSettings.ALL_KINDS : 0;
            }
            face.rememberKinds(kinds);
        }

        for (int channel = 0; channel < PortFace.CHANNELS; channel++) {
            for (TransferKind kind : TransferKind.all()) {
                face.link(channel, kind).reset();
            }
        }
        for (Tag entry : tag.getList(KEY_LINKS, Tag.TAG_COMPOUND)) {
            CompoundTag linkTag = (CompoundTag) entry;
            TransferKind kind = PortChannel.kindOf(linkTag);
            if (kind != null) {
                PortChannel link = face.link(PortChannel.indexOf(linkTag), kind);
                link.load(linkTag);
                // Off the clipboard, every number is anyone's: clamped as the arrows would.
                link.sanitize();
            }
        }

        int missingFilters = applyFilters(face, source, registries);
        int missingUpgrades = applyUpgrades(face, source, registries);
        face.setLabel(label());

        if (withNetwork && level instanceof ServerLevel serverLevel) {
            network().ifPresent(id -> {
                LinkNetworkManager manager = LinkNetworkManager.get(serverLevel.getServer());
                if (manager.exists(id) && !id.equals(face.networkId()) && (source == null || manager.canAccess(id, source))) {
                    manager.join(face, id);
                }
            });
        }
        face.changed();
        return new Applied(1, missingFilters, missingUpgrades);
    }

    /** Every filter slot ends up holding what the snapshot says: written onto the filter there, or onto a blank one from the player. */
    private int applyFilters(PortFace face, @Nullable Player source, HolderLookup.Provider registries) {
        Map<Integer, FilterContents> wanted = new HashMap<>();
        for (Tag entry : tag.getList(KEY_FILTERS, Tag.TAG_COMPOUND)) {
            CompoundTag filterTag = (CompoundTag) entry;
            FilterContents.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), filterTag.get(KEY_FILTER))
                    .result()
                    .ifPresent(contents -> wanted.put(filterTag.getInt(KEY_SLOT), contents));
        }
        ItemStackHandler filters = face.filterHandler();
        int missing = 0;
        for (int slot = 0; slot < filters.getSlots(); slot++) {
            ItemStack have = filters.getStackInSlot(slot);
            FilterContents want = wanted.getOrDefault(slot, FilterContents.EMPTY);
            if (have.getItem() instanceof FilterItem) {
                if (!FilterItem.contents(have).equals(want)) {
                    FilterItem.setContents(have, want);
                }
            } else if (!want.isBlank()) {
                ItemStack blank = takeBlankFilter(source);
                if (blank.isEmpty()) {
                    missing++;
                } else {
                    FilterItem.setContents(blank, want);
                    filters.setStackInSlot(slot, blank);
                }
            }
        }
        return missing;
    }

    /** Every upgrade slot ends up holding what the snapshot says, from and back to the player's inventory. */
    private int applyUpgrades(PortFace face, @Nullable Player source, HolderLookup.Provider registries) {
        Map<Integer, ItemStack> wanted = new HashMap<>();
        for (Tag entry : tag.getList(KEY_UPGRADES, Tag.TAG_COMPOUND)) {
            CompoundTag upgradeTag = (CompoundTag) entry;
            ItemStack.parse(registries, upgradeTag.getCompound(KEY_ITEM))
                    .ifPresent(stack -> wanted.put(upgradeTag.getInt(KEY_SLOT), stack));
        }
        ItemStackHandler upgrades = face.upgradeHandler();
        int missing = 0;
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            ItemStack have = upgrades.getStackInSlot(slot);
            ItemStack want = wanted.getOrDefault(slot, ItemStack.EMPTY);
            if (ItemStack.isSameItemSameComponents(have, want) && have.getCount() == want.getCount()) {
                continue;
            }
            if (source == null) {
                if (!want.isEmpty()) {
                    missing++;
                }
                continue;
            }
            if (!have.isEmpty() && !ItemStack.isSameItemSameComponents(have, want)) {
                giveBack(source, upgrades.extractItem(slot, have.getCount(), false));
                have = ItemStack.EMPTY;
            }
            if (want.isEmpty()) {
                continue;
            }
            int need = want.getCount() - have.getCount();
            if (need < 0) {
                giveBack(source, upgrades.extractItem(slot, -need, false));
                continue;
            }
            ItemStack pulled = take(source, want, need);
            if (!pulled.isEmpty()) {
                giveBack(source, upgrades.insertItem(slot, pulled, false));
            }
            if (upgrades.getStackInSlot(slot).getCount() < want.getCount()) {
                missing++;
            }
        }
        return missing;
    }

    /** One blank filter out of the player's inventory, or nothing. A set one is somebody's and is never taken. */
    private static ItemStack takeBlankFilter(@Nullable Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof FilterItem && !FilterItem.isConfigured(stack)) {
                return inventory.removeItem(slot, 1);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Up to {@code count} of the sample out of the player's inventory, as one stack; fewer when there are fewer. */
    private static ItemStack take(Player player, ItemStack sample, int count) {
        Inventory inventory = player.getInventory();
        ItemStack taken = ItemStack.EMPTY;
        for (int slot = 0; slot < inventory.getContainerSize() && count > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!ItemStack.isSameItemSameComponents(stack, sample)) {
                continue;
            }
            ItemStack part = inventory.removeItem(slot, Math.min(count, stack.getCount()));
            count -= part.getCount();
            if (taken.isEmpty()) {
                taken = part;
            } else {
                taken.grow(part.getCount());
            }
        }
        return taken;
    }

    private static void giveBack(Player player, ItemStack stack) {
        if (!stack.isEmpty()) {
            player.getInventory().placeItemBackInInventory(stack);
        }
    }
}
