---
navigation:
  title: Generators
  icon: actualgenerators:corrosion_cell
  position: 50
item_ids:
  - actualgenerators:corrosion_cell
  - actualgenerators:hydrostatic_generator
  - actualgenerators:photovore
  - actualgenerators:impact_dynamo
  - actualgenerators:spawner_siphon
  - actualgenerators:enchantment_combustor
  - actualgenerators:geothermal_tap
  - actualgenerators:corium_bucket
  - actualgenerators:thermal_probe
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

## Geothermal Fissure Tap

<BlockImage id="actualgenerators:geothermal_tap" scale="4" />

Real geothermal: the heat is down there, it is finite, and it is not everywhere. A tap makes its
rating only near the world floor, full within eight blocks of it and fading to nothing
forty-eight blocks up, and only while its chunk's **heat pocket** has heat in it. Every chunk
either has a pocket or does not, rolled once from the world seed the first time anything asks,
and what is in it is shared by every tap in the chunk; a plate that lies across several chunks
draws on every pocket under it, an even share from each, so a plate over a chunk corner has four
to drink from. A drained pocket regrows at a trickle; a chunk with no pocket makes nothing at
all, and the window says so: relocate.

It is a flat [multiblock](multiblocks.md): a square plate of **Geothermal Casing** laid on the
ground, five to fifteen blocks on a side and always odd, a 3×3 cap of Machine Casing centred on
top of it, and the tap in the middle of one of the cap's sides, facing out.

<GameScene zoom="4" interactive="true">
  <ImportStructure src="structures/geothermal_tap.snbt" />
  <IsometricCamera yaw="225" pitch="30" />
</GameScene>

The plate is the bore. Every plate block is one more share of the rating, 20 FE/t each by
default, so the smallest tap, 5×5, makes 500 FE/t at full depth and a 15×15 makes 4,500; the
buffer, the hatch rate and the corium tank grow with it, and so does the draw on the pocket.
Depth is measured at the plate, so dig: a plate laid on bedrock earns the whole rating. Nothing
is made before the plate and the cap stand. No upgrades and no tier; a warm-up ramp, like every
generator paid by the world. Energy leaves through **Energy Hatches** and corium through
**Fluid Hatches**, set anywhere in the cap.

The window's eye and steppers preview the plate before the first casing; once it stands, the
gauge shows the pocket, the lines the pocket, the plate with its depth, and the output.

As it works it leaves **Corium** behind, a millibucket for every hundred thousand FE, in a tank of
its own beside the gauge, 8,000 mB per plate block. A bucket used on the controller takes
a bucket's worth out, and so does a bucket, or any fluid container, clicked on the tank in the
window; a Fluid Hatch is the door for a pad or a pipe. Nothing puts anything in, and a full
tank stops collecting. Corium is the heat the [Annihilation Furnace](multiblocks.md) runs on.
It behaves like lava in the world, burns like it, and never makes new source blocks, so what
you pour is what you have.

<RecipeFor id="actualgenerators:geothermal_tap" />
<RecipeFor id="actualgenerators:geothermal_casing" />

### Thermal Probe

<ItemImage id="actualgenerators:thermal_probe" scale="2" />

Before a single casing: use the probe anywhere and it reads the chunk you stand in on the action
bar. How much heat is down there and how full the pocket is, or that there is no pocket at all,
and the height the box's floor has to be at or below for the full rating. Walk, use, walk.

<RecipeFor id="actualgenerators:thermal_probe" />

## Annihilation Furnace

<BlockImage id="actualgenerators:annihilation_furnace" scale="4" />

Mass into energy: feed it blocks and it pays by their hardness, at an efficiency set by the
heat on its floor and how its slots are managed. It is a multiblock, a tall box of Machine
Casing whose size is its grade, fed and drained through hatches, heated by Corium. See
[Multiblocks](multiblocks.md) for the box, the heat, the hatches and the numbers.
