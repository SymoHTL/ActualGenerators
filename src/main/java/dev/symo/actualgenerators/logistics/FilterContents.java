package dev.symo.actualgenerators.logistics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a Filter item holds: one list of entries, each saying what it stands for, which way it
 * counts, and whether it lets its match through or stops it.
 *
 * <p>An entry pictures an item or a fluid, never both; the click that set it decided which, a
 * left click the bucket, a right click the water. It stands for that thing exactly, or for
 * everything with one of its tags (and, from older saves, everything from its mod, any damaged
 * item or any enchanted one; {@link Kind}). Which way it counts is its own two switches: the
 * receiving list, the sending list, or both. A pad reads only the entries of the kind its channel
 * carries, so a bucket entry does nothing on a fluid channel and a water entry nothing on an item
 * channel.
 *
 * <p>Whitelist or blacklist is the entry's own: a whitelist entry lets its match through, a
 * blacklist entry stops it, and a blacklist entry wins over a whitelist one that matches the same
 * thing. A direction with no whitelist entry of a kind passes everything of that kind that no
 * blacklist entry stops, so a fresh filter is not a wall.
 *
 * <p>An exact entry may insist on its data components (the thing that used to be NBT): then a
 * stack has to carry every component the entry was set with, and may carry more. Off by default,
 * since most of the time a diamond pickaxe is a diamond pickaxe whatever it is called.
 */
public record FilterContents(List<Entry> entries) {
    /** As many as a window can be scrolled through without the list becoming a chore. */
    public static final int MAX_ENTRIES = 64;
    /**
     * The shape written now. A saved filter without it is from the two-grid days and is read by
     * position; one at 2 had its whitelist-or-blacklist mode per direction rather than per entry.
     */
    private static final int VERSION = 3;
    /** The two-grid layout's receiving list ran this many slots before the sending list began. */
    private static final int LEGACY_LIST_SIZE = 16;

    public static final FilterContents EMPTY = new FilterContents(List.of());

    public static final Codec<FilterContents> CODEC = Codec.withAlternative(
            RecordCodecBuilder.create(instance -> instance.group(
                    Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(FilterContents::entries),
                    // The per-direction modes of version 2. Read, never written.
                    Codec.BOOL.optionalFieldOf("receive_blacklist", false).forGetter(contents -> false),
                    Codec.BOOL.optionalFieldOf("send_blacklist", false).forGetter(contents -> false),
                    Codec.INT.optionalFieldOf("version", 0).forGetter(contents -> VERSION)
            ).apply(instance, FilterContents::load)),
            // Older still: a bare list of entries, from before the lists had a direction. Vanilla
            // drops an item whose component will not parse, so those still have to read.
            Entry.CODEC.listOf().xmap(list -> load(list, false, false, 0), FilterContents::entries));

    public static final StreamCodec<RegistryFriendlyByteBuf, FilterContents> STREAM_CODEC =
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(FilterContents::new, FilterContents::entries);

    public FilterContents {
        entries = List.copyOf(entries);
    }

    /**
     * What was saved, made current. The two-grid shape kept empty slots and told the lists apart by
     * position (the first sixteen received, the rest sent); the list shape keeps neither. Version 2
     * had one mode per direction: an entry on a blacklisted direction becomes a blacklist entry,
     * and one on both directions with the two modes disagreeing is split into two entries.
     */
    private static FilterContents load(List<Entry> list, boolean receiveBlacklist, boolean sendBlacklist, int version) {
        List<Entry> kept = new ArrayList<>();
        for (int slot = 0; slot < list.size() && kept.size() < MAX_ENTRIES; slot++) {
            Entry entry = list.get(slot);
            if (entry.isEmpty()) {
                continue;
            }
            if (version < 2) {
                entry = entry.withDirections(slot < LEGACY_LIST_SIZE, slot >= LEGACY_LIST_SIZE);
            }
            if (version < VERSION) {
                if (entry.receive() && entry.send() && receiveBlacklist != sendBlacklist) {
                    kept.add(entry.withDirections(true, false).withBlacklist(receiveBlacklist));
                    if (kept.size() < MAX_ENTRIES) {
                        kept.add(entry.withDirections(false, true).withBlacklist(sendBlacklist));
                    }
                    continue;
                }
                entry = entry.withBlacklist(entry.receive() ? receiveBlacklist : entry.send() && sendBlacklist);
            }
            kept.add(entry);
        }
        return new FilterContents(kept);
    }

    /** What an entry stands for. Everything but {@link #EXACT} treats the item or fluid as a picture. */
    public enum Kind implements StringRepresentable {
        /** The pictured item or fluid, its data too when the entry insists. */
        EXACT("exact"),
        /** Everything with the tag in the entry's key. */
        TAG("tag"),
        /** Everything from the mod whose namespace is in the entry's key. Read from older saves; the window no longer makes one. */
        MOD("mod"),
        /** Any item with damage on it. Items only. Older saves. */
        DAMAGED("damaged"),
        /** Any enchanted item, books included. Items only. Older saves. */
        ENCHANTED("enchanted");

        private static final Kind[] VALUES = values();
        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        public static final StreamCodec<ByteBuf, Kind> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
                ordinal -> VALUES[Math.clamp(ordinal, 0, VALUES.length - 1)], Kind::ordinal);

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** Whether the entry stands for more than the thing pictured. */
        public boolean isGroup() {
            return this != EXACT;
        }
    }

    /**
     * One line of a filter: a picture (an item or a fluid), what it stands for, whether its
     * components count (exact entries only), which lists it is on, and whether it lets its match
     * through or stops it.
     *
     * <p>The key is the tag for a tag entry, and for a mod entry the namespace with an empty path;
     * one typed field for both, so a tag check is an interned {@link TagKey} lookup and no parse.
     */
    public record Entry(ItemStack item, FluidStack fluid, Kind kind, Optional<ResourceLocation> key,
                        boolean matchComponents, boolean receive, boolean send, boolean blacklist) {
        public static final Entry EMPTY = new Entry(ItemStack.EMPTY, FluidStack.EMPTY, Kind.EXACT, Optional.empty(),
                false, true, true, false);

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.OPTIONAL_CODEC.optionalFieldOf("item", ItemStack.EMPTY).forGetter(Entry::item),
                FluidStack.OPTIONAL_CODEC.optionalFieldOf("fluid", FluidStack.EMPTY).forGetter(Entry::fluid),
                Kind.CODEC.optionalFieldOf("kind", Kind.EXACT).forGetter(Entry::kind),
                ResourceLocation.CODEC.optionalFieldOf("key").forGetter(Entry::key),
                Codec.BOOL.optionalFieldOf("match_components", false).forGetter(Entry::matchComponents),
                Codec.BOOL.optionalFieldOf("receive", true).forGetter(Entry::receive),
                Codec.BOOL.optionalFieldOf("send", true).forGetter(Entry::send),
                Codec.BOOL.optionalFieldOf("blacklist", false).forGetter(Entry::blacklist),
                // The two-grid days wrote a tag or a mod as its own field. Read, never written.
                ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(entry -> Optional.<ResourceLocation>empty()),
                Codec.STRING.optionalFieldOf("mod").forGetter(entry -> Optional.<String>empty())
        ).apply(instance, Entry::load));

        private static final StreamCodec<ByteBuf, Optional<ResourceLocation>> KEY_STREAM_CODEC =
                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC);

        // Eight fields: more than vanilla's composite takes, so written out.
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.of(
                (buffer, entry) -> {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.item);
                    FluidStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.fluid);
                    Kind.STREAM_CODEC.encode(buffer, entry.kind);
                    KEY_STREAM_CODEC.encode(buffer, entry.key);
                    buffer.writeBoolean(entry.matchComponents);
                    buffer.writeBoolean(entry.receive);
                    buffer.writeBoolean(entry.send);
                    buffer.writeBoolean(entry.blacklist);
                },
                buffer -> new Entry(
                        ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                        FluidStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                        Kind.STREAM_CODEC.decode(buffer),
                        KEY_STREAM_CODEC.decode(buffer),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readBoolean()));

        private static Entry load(ItemStack item, FluidStack fluid, Kind kind, Optional<ResourceLocation> key,
                                  boolean matchComponents, boolean receive, boolean send, boolean blacklist,
                                  Optional<ResourceLocation> legacyTag, Optional<String> legacyMod) {
            Entry entry = new Entry(item, fluid, kind, key, matchComponents, receive, send, blacklist);
            if (legacyTag.isPresent()) {
                return entry.withTag(legacyTag.get());
            }
            if (legacyMod.isPresent()) {
                return entry.withMod(legacyMod.get());
            }
            return entry;
        }

        /** An item entry, on both lists, letting exactly the item through. */
        public static Entry ofItem(ItemStack stack) {
            return stack.isEmpty() ? EMPTY : new Entry(stack.copyWithCount(1), FluidStack.EMPTY, Kind.EXACT, Optional.empty(),
                    false, true, true, false);
        }

        public static Entry ofFluid(FluidStack fluid) {
            return fluid.isEmpty() ? EMPTY : new Entry(ItemStack.EMPTY, fluid.copyWithAmount(1000), Kind.EXACT, Optional.empty(),
                    false, true, true, false);
        }

        /**
         * A tag entry typed by name rather than picked off an item, on both lists: pictured by the
         * first item under the tag when it has one, so its row shows something, and by nothing when
         * it has none. Either way it stands for the tag, never for the picture.
         */
        public static Entry ofTag(ResourceLocation tag) {
            ItemStack picture = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tag))
                    .flatMap(named -> named.stream().findFirst())
                    .map(holder -> new ItemStack(holder.value()))
                    .orElse(ItemStack.EMPTY);
            return new Entry(picture, FluidStack.EMPTY, Kind.TAG, Optional.of(tag), false, true, true, false);
        }

        /** Only an exact entry reads its data; for the rest the picture is only a picture. */
        public Entry withMatchComponents(boolean match) {
            return isEmpty() || kind.isGroup() ? this : new Entry(item, fluid, kind, key, match, receive, send, blacklist);
        }

        /** The same picture and the same switches, standing for something else. */
        public Entry withKind(Kind kind, @Nullable ResourceLocation key) {
            return isEmpty() ? EMPTY : new Entry(item, fluid, kind, Optional.ofNullable(key),
                    kind == Kind.EXACT && matchComponents, receive, send, blacklist);
        }

        /** Standing for everything with the tag. */
        public Entry withTag(ResourceLocation tag) {
            return withKind(Kind.TAG, tag);
        }

        /** Standing for everything from the mod (a registry namespace). */
        public Entry withMod(String namespace) {
            return withKind(Kind.MOD, ResourceLocation.fromNamespaceAndPath(namespace, ""));
        }

        /** Back to being exactly its item or fluid. */
        public Entry itself() {
            return withKind(Kind.EXACT, null);
        }

        /** The same entry on the lists asked for. Off both is allowed, and does nothing, like a switched-off pad. */
        public Entry withDirections(boolean receive, boolean send) {
            return isEmpty() ? EMPTY : new Entry(item, fluid, kind, key, matchComponents, receive, send, blacklist);
        }

        /** The same entry stopping its match (a blacklist entry) or letting it through (a whitelist one). */
        public Entry withBlacklist(boolean blacklist) {
            return isEmpty() ? EMPTY : new Entry(item, fluid, kind, key, matchComponents, receive, send, blacklist);
        }

        /** A new picture with this entry's switches: what a drop onto an existing row keeps. */
        public Entry repictured(Entry picture) {
            return picture.isEmpty() ? EMPTY : new Entry(picture.item, picture.fluid, picture.kind, picture.key,
                    picture.matchComponents, receive, send, blacklist);
        }

        /** Whether the entry counts for this direction. */
        public boolean on(boolean receiving) {
            return receiving ? receive : send;
        }

        /** Nothing in it. A tag typed by name is an entry with no picture, not an empty one. */
        public boolean isEmpty() {
            return item.isEmpty() && fluid.isEmpty() && !(kind == Kind.TAG && key.isPresent());
        }

        public boolean isFluid() {
            return !fluid.isEmpty();
        }

        /** Whether the entry stands for a tag, a mod or a trait rather than for the thing pictured. */
        public boolean isGroup() {
            return kind.isGroup();
        }

        /** The tag a tag entry stands for, as written. */
        public Optional<ResourceLocation> tag() {
            return kind == Kind.TAG ? key : Optional.empty();
        }

        /** The mod a mod entry stands for, as a registry namespace. */
        public Optional<String> mod() {
            return kind == Kind.MOD ? key.map(ResourceLocation::getNamespace) : Optional.empty();
        }

        public boolean matches(ItemStack stack) {
            if (item.isEmpty() && kind != Kind.TAG) {
                return false;
            }
            return switch (kind) {
                case EXACT -> ItemStack.isSameItem(item, stack)
                        && (!matchComponents || carries(item.getComponentsPatch().entrySet(), stack::has, stack::get));
                case TAG -> key.isPresent() && stack.is(TagKey.create(Registries.ITEM, key.get()));
                case MOD -> key.isPresent()
                        && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(key.get().getNamespace());
                case DAMAGED -> stack.isDamaged();
                case ENCHANTED -> stack.isEnchanted()
                        || !stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
            };
        }

        public boolean matches(FluidStack other) {
            if (fluid.isEmpty()) {
                return false;
            }
            return switch (kind) {
                case EXACT -> other.is(fluid.getFluid())
                        && (!matchComponents || carries(fluid.getComponentsPatch().entrySet(), other::has, other::get));
                case TAG -> key.isPresent() && other.is(TagKey.create(Registries.FLUID, key.get()));
                case MOD -> key.isPresent()
                        && BuiltInRegistries.FLUID.getKey(other.getFluid()).getNamespace().equals(key.get().getNamespace());
                case DAMAGED, ENCHANTED -> false;
            };
        }

        /**
         * Whether a thing carries every component this entry was set with, whatever else it has:
         * a component the entry sets must be equal, one the entry removed must be absent.
         */
        private static boolean carries(Iterable<Map.Entry<DataComponentType<?>, Optional<?>>> wanted,
                                       java.util.function.Predicate<DataComponentType<?>> has,
                                       java.util.function.Function<DataComponentType<?>, Object> get) {
            for (Map.Entry<DataComponentType<?>, Optional<?>> component : wanted) {
                Optional<?> value = component.getValue();
                if (value.isPresent()
                        ? !value.get().equals(get.apply(component.getKey()))
                        : has.test(component.getKey())) {
                    return false;
                }
            }
            return true;
        }

        // A record over stacks compares by identity unless told otherwise, and two filters written
        // the same way have to be the same filter, or they will never stack.
        @Override
        public boolean equals(Object other) {
            return other instanceof Entry entry
                    && kind == entry.kind
                    && key.equals(entry.key)
                    && matchComponents == entry.matchComponents
                    && receive == entry.receive
                    && send == entry.send
                    && blacklist == entry.blacklist
                    && ItemStack.isSameItemSameComponents(item, entry.item)
                    && FluidStack.isSameFluidSameComponents(fluid, entry.fluid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(item.getItem(), fluid.getFluid(), kind, key, matchComponents, receive, send, blacklist);
        }
    }

    /** The fluid a container item is holding, or nothing for something that holds none. */
    public static FluidStack fluidIn(ItemStack stack) {
        return stack.isEmpty() ? FluidStack.EMPTY : FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
    }

    public int size() {
        return entries.size();
    }

    public Entry entry(int index) {
        return index >= 0 && index < entries.size() ? entries.get(index) : Entry.EMPTY;
    }

    /** One more line at the end; a full list or an empty entry changes nothing. */
    public FilterContents add(Entry entry) {
        if (entry.isEmpty() || entries.size() >= MAX_ENTRIES) {
            return this;
        }
        List<Entry> copy = new ArrayList<>(entries);
        copy.add(entry);
        return new FilterContents(copy);
    }

    /** Replaces a line; an empty entry removes it, an index past the end adds it. */
    public FilterContents set(int index, Entry entry) {
        if (index < 0 || index >= entries.size()) {
            return add(entry);
        }
        if (entry.isEmpty()) {
            return remove(index);
        }
        List<Entry> copy = new ArrayList<>(entries);
        copy.set(index, entry);
        return new FilterContents(copy);
    }

    public FilterContents remove(int index) {
        if (index < 0 || index >= entries.size()) {
            return this;
        }
        List<Entry> copy = new ArrayList<>(entries);
        copy.remove(index);
        return new FilterContents(copy);
    }

    /** Nothing set at all. A blank filter carries no component, so blanks stack. */
    public boolean isBlank() {
        return entries.isEmpty();
    }

    /** How many entries count for one direction. */
    public int count(boolean receiving) {
        int found = 0;
        for (Entry entry : entries) {
            if (entry.on(receiving)) {
                found++;
            }
        }
        return found;
    }

    /** Whether the list for this direction lets an item through. */
    public boolean allowsItem(ItemStack stack, boolean receiving) {
        boolean anyWhitelisted = false;
        boolean listed = false;
        for (Entry entry : entries) {
            if (entry.isFluid() || !entry.on(receiving)) {
                continue;
            }
            if (entry.blacklist()) {
                if (entry.matches(stack)) {
                    return false;
                }
                continue;
            }
            anyWhitelisted = true;
            listed |= entry.matches(stack);
        }
        return !anyWhitelisted || listed;
    }

    public boolean allowsFluid(FluidStack fluid, boolean receiving) {
        boolean anyWhitelisted = false;
        boolean listed = false;
        for (Entry entry : entries) {
            if (!entry.isFluid() || !entry.on(receiving)) {
                continue;
            }
            if (entry.blacklist()) {
                if (entry.matches(fluid)) {
                    return false;
                }
                continue;
            }
            anyWhitelisted = true;
            listed |= entry.matches(fluid);
        }
        return !anyWhitelisted || listed;
    }
}
