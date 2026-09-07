---
navigation:
  title: Upgrades
  icon: actualgenerators:speed_upgrade
  position: 30
item_ids:
  - actualgenerators:energy_upgrade
  - actualgenerators:speed_upgrade
  - actualgenerators:overclock_upgrade
  - actualgenerators:stack_upgrade
---

# Upgrades

Four kinds, each with its own slot in the machine window, each made by the handful in a one-row
recipe. A machine shows only the slots it will accept, and a slot stops at the number the machine
actually counts, so a stack of sixty-four dropped in never has sixty doing nothing.

Drop an upgrade into its slot, or crouch and use it on the machine.

## Energy

More buffer and more FE per tick through the faces. Every machine takes these.

<RecipeFor id="actualgenerators:energy_upgrade" />

## Speed

Raises base speed, up to a ceiling. Past the ceiling, speed upgrades buy nothing and overclocks
take over.

<RecipeFor id="actualgenerators:speed_upgrade" />

## Overclock

Scales speed *past* the ceiling. The overclock ramps up while the machine works, shown on the ramp
bar, and does not reset between recipes; it decays only when the machine runs out of work or power.
Keep a machine fed and it stays hot.

<RecipeFor id="actualgenerators:overclock_upgrade" />

## Stack

More items per operation. A machine never does more than one operation a tick, whatever you bolt
onto it, so once it finishes something every tick the only way to more throughput is a bigger
operation.

The slots grow with the batch: each holds eight operations' worth (a server config), never less
than a stack, so a netherite machine with a full stack row holds a few hundred ore where a bare
one holds sixty-four. A pattern provider pushes until the slot says no, so the batch is the only
cap on what a machine can be fed.

<RecipeFor id="actualgenerators:stack_upgrade" />

## Two ways to go faster, priced differently

Speed and overclock raise FE/t **superlinearly**: three times the speed costs about nine times the
power. Stack upgrades raise it only **linearly**. Batching is the efficient path; overclocking is
the expensive one, and still worth having, because batching needs inputs arriving in bulk and an
overclock helps a machine that is fed a trickle.

The window tells you when speed is saturated, that is, when the machine already finishes an
operation every tick. Past that point extra speed is quadratic energy for nothing.

## What refuses what

A generator with no fuel to trade away, one paid by depth, weather or sunlight, takes energy
upgrades only. Speed on such a machine would conjure FE rather than burn fuel faster for less, so
the slot refuses the upgrade instead of taking it and ignoring it. Fuel-burning generators keep
the normal speed scaling, at a yield penalty per item: faster burn, less energy per item.

For the cheaper way to make a machine faster, see [tiers](tiers.md).
