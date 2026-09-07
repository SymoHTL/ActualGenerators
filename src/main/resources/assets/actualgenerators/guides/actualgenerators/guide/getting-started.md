---
navigation:
  title: Getting started
  icon: actualgenerators:machine_frame
  position: 10
item_ids:
  - actualgenerators:machine_frame
---

# Getting started

Everything routes through one part: the **Machine Frame**. Iron, copper and a little redstone.

<RecipeFor id="actualgenerators:machine_frame" />

A machine is a frame plus the two things that say what it does. That is the whole recipe
scheme: rebalancing a tier of machines means editing one recipe, not nine.

## A first generator

The **Corrosion Cell** is the early one. It eats copper and gives FE while the copper turns green;
it needs nothing but the copper.

<RecipeFor id="actualgenerators:corrosion_cell" />

Place it, drop copper in, and it runs. Every machine opens a window on a plain right click; every
window has the same chrome, so once one is familiar they all are. See [the machine chassis](machines.md).

## Moving the energy

Nothing in this mod is a cable. Energy, items and fluids all travel through [Logic Ports](logic-ports.md):
flat pads stuck onto a face, put on a named network with the Linking Tool. A working link is two
pads, one Energy Injector, and two clicks in the pad window. The pages under Logic Ports walk
through it, and the Linking Tool draws the whole network in the world while you hold it.

## What to read next

- [Upgrades](upgrades.md) if a machine is too slow.
- [Tiers](tiers.md) if it is too slow and you are not made of FE.
- [Generators](generators.md) for the rest of the roster.
