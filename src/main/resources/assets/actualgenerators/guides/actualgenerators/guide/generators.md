---
navigation:
  title: Generators
  icon: actualgenerators:corrosion_cell
  position: 50
---

# Generators

No coal generator and no solar panel. Every generator here is paid by something the world does,
and each one tells you what it refuses.

Passive generators, the ones paid by depth, weather, light or water, **warm up** instead of
overclocking. Output starts at a fraction of the rating and climbs to exactly the rating while the
generator keeps running, never past it. The ramp bar shows the warm-up. They take energy upgrades
only.

## Corrosion Cell

<BlockImage id="actualgenerators:corrosion_cell" scale="4" />

Eats copper and gives FE while the copper corrodes through its stages. A fuel-burning generator:
speed upgrades make it burn faster, at a yield penalty per ingot.

<RecipeFor id="actualgenerators:corrosion_cell" />

## Hydrostatic Generator

<BlockImage id="actualgenerators:hydrostatic_generator" scale="4" />

Output scales with the column of **still** water standing above it, up to a configured height.
Flowing water never counts, so one bucket poured down a shaft earns nothing; waterlogged blocks
do count, so the shaft can be built rather than dug. The column is measured on an interval and
whenever a neighbour changes, never every tick. Energy upgrades only.

<RecipeFor id="actualgenerators:hydrostatic_generator" />

## Photovore

<BlockImage id="actualgenerators:photovore" scale="4" />

Grazes on light-source blocks in its radius: it eats a torch, a glowstone block, a lantern, and
banks FE for the light level. What it may eat is the block tag `actualgenerators:photovore_food`,
which a datapack can edit. It never eats beacons, portals, lava or fire, and it never touches the
light engine. Energy upgrades only.

<RecipeFor id="actualgenerators:photovore" />

## Impact Dynamo

<BlockImage id="actualgenerators:impact_dynamo" scale="4" />

Paid by things landing on it: falling blocks, dropped anvils, anything with a fall distance. Each
impact holds the ramp open for a window of ticks; keep them coming and it stays warm. Salvage from
what shatters comes out of the side slots.

<RecipeFor id="actualgenerators:impact_dynamo" />

## Spawner Siphon

<BlockImage id="actualgenerators:spawner_siphon" scale="4" />

Clamps onto a spawner, holds its countdown out of reach and banks FE for every spawn it denies.
The rate is read off the spawner itself, so a spawner another mod has upgraded pays more with no
integration involved. Break the siphon and the spawner goes back to work.

<RecipeFor id="actualgenerators:spawner_siphon" />

## Enchantment Combustor

<BlockImage id="actualgenerators:enchantment_combustor" scale="4" />

Burns enchanted items for their enchantment levels. Anything you would otherwise grind down is
fuel, and the more levels on it the longer it burns. A fuel-burning generator, so speed upgrades
apply, at the usual yield penalty.

<RecipeFor id="actualgenerators:enchantment_combustor" />
