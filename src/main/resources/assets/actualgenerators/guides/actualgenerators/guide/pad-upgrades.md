---
navigation:
  title: Pad upgrades
  parent: logic-ports.md
  position: 50
---

# Pad upgrades

Three slots along the top of the pad window, each with a faded picture of what it takes. Upgrades
are **per pad**: a pad in a gap with a second pad on its other face upgrades each separately.

## Range Upgrade

<RecipeFor id="actualgenerators:link_range_upgrade" />

Each one adds a configured number of blocks to how far from the network's centre this pad may
sit. A pad holding the full count also reaches across dimensions, which is the only way a pad in
the Nether joins a network centred in the Overworld.

## Unbound Link Card

<RecipeFor id="actualgenerators:unbound_link_card" />

Unlimited reach inside one dimension, the centre's, and never out of it. A network tolerates
exactly one: the slot refuses a second card outright rather than taking it and quietly ignoring it.

## Tier

A [tier upgrade](tiers.md) in the third slot raises the most one send may carry and lowers the
fewest ticks the pad may wait between sends. Crouch and use the tier on the pad, or drop it in the
slot; a higher tier swaps in and hands the fitted one back.

| Tier | Amount | Delay floor |
|------|--------|-------------|
| none | x1 | 10 ticks |
| iron | x2 | 8 ticks |
| gold | x4 | 5 ticks |
| diamond | x8 | 2 ticks |
| netherite | x16 | 1 tick |

A pad whose Amount and Delay were left alone follows its tier on its own; see
[the four numbers](pad-numbers.md).
