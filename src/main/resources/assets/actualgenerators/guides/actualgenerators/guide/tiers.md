---
navigation:
  title: Tiers
  icon: actualgenerators:netherite_tier_upgrade
  position: 40
---

# Tiers

A tier upgrades the block itself. Iron, gold, diamond, netherite: one tier item goes into the
machine's tier slot (left of the input slot) or a pad's tier slot, and the block is a better block.

Crouch and use a tier upgrade on a machine or a pad to fit it. A higher tier swaps in and hands the
fitted one back to you; a lower or equal one is refused. One tier per block.

## Why a tier is not an upgrade

Upgrades are traded against energy: speed costs power superlinearly, so a fast machine is an
expensive machine. A tier raises the base stats **at the same FE per operation**. A netherite
crusher works four times as fast and crushes three times as much per operation, and each operation
still costs what it did on an untiered block. FE per tick rises with the tier, but linearly, the
way batching does, never the way speed upgrades do.

That is the ladder every pack already teaches, and it is why the ladder is worth climbing.

## On a machine

| Tier | Speed | Batch |
|------|-------|-------|
| none | x1 | x1 |
| iron | x1.5 | x1 |
| gold | x2 | x2 |
| diamond | x3 | x2 |
| netherite | x4 | x3 |

Batch, never a second processing line. A machine does one operation a tick at most, whatever its
tier; a tier makes the operation bigger and shorter, so a netherite machine costs the server
exactly what an iron one does. The slots grow with the batch too, so it can be fed and emptied as
fast as it works; see [upgrades](upgrades.md). The FE buffer and the face rate grow with the batch
and the tier's speed together, so a fitted machine holds the same seconds of work as a bare one.

Generators refuse tiers. They have no fuel to trade for the speed, and a free multiplier on FE is
not a tier, it is a bug. The Surge Bank, Crystal Charger and Energy Injector refuse them too:
there is nothing on them for a tier to speed up.

## On a pad

| Tier | Amount | Delay floor |
|------|--------|-------------|
| none | x1 | 10 ticks |
| iron | x2 | 8 ticks |
| gold | x4 | 5 ticks |
| diamond | x8 | 2 ticks |
| netherite | x16 | 1 tick |

A pad's rate is its Amount divided by its Delay. A tier raises the most one send may carry and
lowers the fewest ticks it may wait between sends; see [the four numbers](pad-numbers.md).
Nothing else about the pad changes.

## Crafting the ladder

Each rung is four of the material round the rung below, so the metal in an iron tier is never
lost when a machine goes up to gold. Iron sits round a Machine Frame.

<RecipeFor id="actualgenerators:iron_tier_upgrade" />
<RecipeFor id="actualgenerators:gold_tier_upgrade" />
<RecipeFor id="actualgenerators:diamond_tier_upgrade" />
<RecipeFor id="actualgenerators:netherite_tier_upgrade" />

All the multipliers are server config, per tier.
