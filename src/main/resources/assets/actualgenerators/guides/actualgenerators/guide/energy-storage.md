---
navigation:
  title: Storing energy
  icon: actualgenerators:flux_crystal
  position: 70
item_ids:
  - actualgenerators:surge_bank
  - actualgenerators:flux_crystal
  - actualgenerators:crystal_charger
  - actualgenerators:flux_coupler
---

# Storing energy

## Surge Bank

<BlockImage id="actualgenerators:surge_bank" scale="4" />

A big buffer built for bursts: it takes and gives FE fast, and it leaks a little while it sits
idle. Fill it before the load comes, not instead of a generator.

<RecipeFor id="actualgenerators:surge_bank" />

## Flux Crystals

A **Flux Crystal** is a battery item with a fixed capacity, exposing the ordinary Forge Energy item
capability, so any mod's battery slot or charger works with it. A full one and an empty one are
otherwise identical items, so each stacks. Recipes that need energy consume a full crystal and
hand the empty one back, the way a milk bucket hands back a bucket.

The crystal's own transfer rate is the base speed of everything that draws on it, so a better
crystal is a faster one wherever you use it.

<RecipeFor id="actualgenerators:flux_crystal" />

Crystals are optional everywhere. Energy travels through [Logic Ports](logic-ports.md); crystals are
for carrying it in a pocket, or for autocrafting it, since an empty-to-full pattern is a recipe like
any other.

## Crystal Charger

<BlockImage id="actualgenerators:crystal_charger" scale="4" />

Charges crystals from its buffer, or discharges them into it; the mode button flips it round. It
works on one crystal at a time and outputs each as it finishes, so a stack trickles out rather than
landing in a lump. A partly charged crystal goes in and comes out normalised. The crystal leaves the
input slot before an FE moves and its charge is written back exactly, so breaking the block mid-charge
can neither mint energy nor lose it.

Energy upgrades only: a lossless transfer has no speed to buy, only throughput.

<RecipeFor id="actualgenerators:crystal_charger" />

## Flux Coupler

The **Flux Coupler** charges every energy item in your inventory, any mod's, out of the crystal in
its working slot. Right click opens its window: an input slot for crystals, the working slot it
feeds itself from, an output slot where the empties stack, an energy upgrade slot, and an on/off
button. It has no buffer of its own and spends nothing unless something is asking.

<RecipeFor id="actualgenerators:flux_coupler" />
