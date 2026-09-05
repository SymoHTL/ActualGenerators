---
navigation:
  title: Filters
  parent: logic-ports.md
  position: 30
---

# Filters

A **Filter** is an item. Set it up in its own window, then drop it into the pad's filter slot: one
slot per channel and kind (items, fluids, and the redstone tab's stock), shown on the tab you have
open, at the end of its row of switches. No filter, or a blank one, lets everything through.

<RecipeFor id="actualgenerators:filter" />

## One list, one button

The window is a list of entries, one per row, with one button over it: **Add an entry**. Click it
and it becomes two: **Item or fluid** and **Tag**. Either opens the entry's page in the same window,
and Back is the list again. A row shows the entry's picture and name, an IN and an EX mark for the
directions it counts in, a W or a B for allow or block, and a × that removes it. Click a row to open
its page. The wheel scrolls; a list holds sixty-four.

## An item or fluid entry

The page has a slot. Click it with an item in hand and that is the entry; right click and the entry
is the **fluid** inside the item, so a water bucket right-clicked in is a water entry. Ingredients
drag straight out of JEI onto the slot. An entry is an item or a fluid, never both; the channel's
kind decides which entries the pad reads.

Under the slot, the item's data is text you can edit: its components as they are saved, one per
line. Change a line, delete one, or type components onto an item that had none, then **Apply**;
text that is not a component list is refused with the reason and nothing changes. Beside the slot
is the choice: **Ignore NBT** (the default: the item is the item, whatever it is named or enchanted
with) or **Match NBT** (a stack must carry every component the entry was set with, and may carry
more, so a "Rock"-named cobblestone entry passes a "Rock" with lore on it and not a plain one).
Applying data switches Match on for you. That is Pipez's rule.

## A tag entry

The page has the same slot and, under it, a box to **type a tag by name**: `c:ingots`,
`#minecraft:logs`, with or without the hash, and Enter sets it. A tag the game knows nothing under
still works as an entry, shown with a hash where the picture would be. Or set an item in the slot
and every tag the item has is listed under the box; click one. Either way the entry stands for
**everything under that tag**, and its picture walks through everything the tag holds, one a
second, so you see what it lets through. A tag entry reads no NBT.

## Three switches on every entry

**IN** and **EX** say which of the pad's two jobs the entry counts for: what the pad receives, what
it sends, or both (a fresh entry is on both). **Allow** or **Block** says what the entry does:

- An **Allow** entry is a whitelist line. Once a kind has any Allow entries for a direction, only
  what one of them matches passes that way. A kind with no Allow entries passes everything of
  that kind.
- A **Block** entry is a blacklist line: what it matches never passes, whatever else is on the
  list. Block wins over Allow.

So one filter can allow `#c:ingots` and block iron ingots, or receive only cobblestone and send
everything but cobblestone.

## The stock filter

The redstone tab has a filter slot of its own. It is not a gate, since nothing moves on that tab:
the **stock** source counts what the filter lets through, in either direction, in the block behind
the pad. See [Redstone over the network](redstone.md).

## Copying and clearing

Craft a set filter with a blank one to get two set ones. A set filter alone in the grid comes out
blank. Blanks carry no data and stack. Filters saved by older versions of the mod, with a mode per
direction or with mod, damaged and enchanted entries, load as they were.
