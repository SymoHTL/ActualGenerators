---
navigation:
  title: The Resonance Crusher
  icon: actualgenerators:resonance_crusher
  position: 60
---

# The Resonance Crusher

<BlockImage id="actualgenerators:resonance_crusher" scale="4" />

Ore processing by frequency. Every material rings at a frequency of its own, and the crusher has to
find it before it can shake the material apart efficiently.

<RecipeFor id="actualgenerators:resonance_crusher" />

## Calibration

The first time the crusher meets a material it does not know, it **calibrates**: the run takes
several times as long and yields no bonus. After that the material is learned, and every later
run is quick and pays the bonus yield. The window shows whether the material in the slot is
learned or still being calibrated, and so does Jade.

The learned list rides on the **Config Card**: copy a calibrated crusher's configuration and paste
it onto a fresh one, and the new machine already knows every frequency the old one found. Tune
once per material, per base.

## What it crushes

Crushing recipes are a datapack recipe type, `actualgenerators:crushing`: an ingredient, a count,
a result, and optionally how long it takes. Ore recipes are written against the common `c:ores/*`
tags, so another mod's iron ore crushes exactly like vanilla's. A material's frequency is derived
from the recipe id, so a pack's new recipe has one for free.

<RecipesFor id="actualgenerators:iron_dust" />

Dusts smelt into ingots the ordinary way.

## Yield

The bonus a tuned crusher shakes loose is a server config value in permille, not a recipe field:
recipes say what a material *is*, config says what it is *worth*. Swapping the input mid-run resets
progress, so a cheap crush can never finish an expensive one.

## Making it faster

The crusher takes all four [upgrades](upgrades.md) and a [tier](tiers.md). It is a processing
machine, so a tier makes it crush faster and in bigger lots at the same FE per operation.
