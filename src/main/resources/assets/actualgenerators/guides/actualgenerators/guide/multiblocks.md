---
navigation:
  title: Multiblocks
  icon: actualgenerators:annihilation_furnace
  position: 55
item_ids:
  - actualgenerators:machine_casing
  - actualgenerators:geothermal_casing
  - actualgenerators:item_hatch
  - actualgenerators:energy_hatch
  - actualgenerators:redstone_hatch
  - actualgenerators:fluid_hatch
  - actualgenerators:annihilation_furnace
---

# Multiblocks

The late-game generators are structures you build, not blocks you place. Every one is made of
**Machine Casing** with one controller set into it, facing out, and every one has a shape of
its own: the Annihilation Furnace is a hollow box, three to seven blocks wide and deep and
seven to fifteen tall, so 3×7×3 is the smallest furnace and 5×7×5 or 3×15×3 are as good as
7×15×7; the Geothermal Fissure Tap is a flat plate of its own casing with a 3×3 cap of Machine
Casing on top. Each size has its own limits, and the controller's window shows the shape before
you place a single casing.

<GameScene zoom="3" interactive="true">
  <ImportStructure src="structures/annihilation_furnace.snbt" />
  <IsometricCamera yaw="225" pitch="30" />
</GameScene>

<GameScene zoom="4" interactive="true">
  <ImportStructure src="structures/geothermal_tap.snbt" />
  <IsometricCamera yaw="225" pitch="30" />
</GameScene>

<BlockImage id="actualgenerators:machine_casing" scale="4" />

**The size is the grade.** There are no upgrade slots and no tier on a controller. Every block
inside the furnace's shell is one more item per operation, so a 5×7×5 with forty-five blocks
inside does forty-five times the work of a 3×7×3 in the same time, and makes forty-five times
the FE/t; every block in the tap's plate is one more share of its rating. The buffer and the
hatch rate grow with it, the way they grow with the batch on a single-block machine. Want more?
Build bigger.

<RecipeFor id="actualgenerators:machine_casing" />

## The preview

Place the controller first, open it, and press the eye. A hologram of the structure stands in
the world round the controller: a ghost of every block still to be placed, Machine Casing and
the machine's own casing drawn as themselves; orange where a floor wants a fluid; red, through
walls, where something is in the way. Hold a hatch and every place one may go wears a green
frame, built or not: a hatch used on a casing takes its place and hands the casing back. The three
steppers beside the eye set the width, height and depth within the controller's limits (a size
that cannot change, like the tap's height, stays put), and the sizes stick with the controller
until you change them. The label over the hologram says the size and what it buys. Build what
the hologram shows and the structure forms on the last block; what is already right is not
drawn, so the picture empties as you build.

The preview is yours alone. Nothing about it reaches the server or other players.

## Forming

Place the last casing and the structure forms on the next tick. The casings, the hatches and
the controller join up into one surface, rimmed only where the structure ends, the controller
keeping its front, and its window reads the size. The tap's plate becomes rings of fins round
its centre, every second block from the outside in.
Break any casing and it all goes dark the same tick. Nothing is scanned while nobody touches
the shell: the controller looks again only when a casing, hatch or controller is placed or
broken within its reach, when something inside a box changes, or when a chunk it reaches into
loads.

A box will not form round anything inside it but air, and on the floor, a heating fluid. Nothing
forms round two controllers. Casings and hatches cannot be pushed by pistons, so a sealed box
stays sealed.

## Hatches

The controller's own faces move nothing. Everything in or out goes through a **hatch**: a casing
with a door in it, set anywhere Machine Casing goes (the walls of a box, the cap of a tap).
There are four, one per kind of thing, and a structure takes as many of each as you like.

A hatch is a machine face and is set up exactly like one. Click it and the controller's window
opens with the side panel every machine has, showing the hatch's six faces relative to the way
you placed it: click a face to set what may pass through it (input, output, both, nothing);
faces that look into the structure are greyed out. The IN and OUT toggles under the cross say
whether the structure pulls in through the input faces and pushes out through the output faces
of its own accord. Every face wears a marker for what it lets through, blue for input, orange
for output, both split along the diagonal, so you can read a wall of hatches from across the
room. Placed, a hatch is open both ways on every face and moves nothing itself, so a hopper, a
pipe or a Logic Port pad works it from outside. An item hatch pulling from a chest and an
energy hatch pushing into a bank is a furnace running with no pipes at all. A hatch used on a
casing, or on a hatch of another kind, takes its place and hands the old block back; hold one
and every spot it may go is framed green.

<BlockImage id="actualgenerators:item_hatch" scale="2" />
<BlockImage id="actualgenerators:energy_hatch" scale="2" />
<BlockImage id="actualgenerators:redstone_hatch" scale="2" />
<BlockImage id="actualgenerators:fluid_hatch" scale="2" />

- **Item Hatch**: the controller's item slots, for a hopper, a pipe or a Logic Port pad.
  Click it and the controller's window opens, wherever on the box you are.
- **Energy Hatch**: the controller's FE buffer. On a generator it gives energy out and takes none.
  Each hatch hands out up to the controller's rate, so more hatches drain a big box faster.
  Click it and the controller's window opens too.
- **Fluid Hatch**: the controller's tank, on the controllers that have one: the tap's corium
  comes out through it, and nothing goes in. On a controller with no tank it is a hatch with
  nothing behind it. Click it and the controller's window opens.
- **Redstone Hatch**: a signal in, or a reading out. Click it for its own window, six rows:
  - **Control**: a signal reaching the hatch counts as a signal at the controller, for its
    redstone mode. Put the lever where you stand, not where the controller is.
  - **Formed**: full signal while the box stands.
  - **Working**: full signal while the controller is working.
  - **Energy**: how full the buffer is, nought to fifteen.
  - **Items**: how full the slots are, nought to fifteen.
  - **Efficiency**: the controller's efficiency, nought to fifteen.

  A reporting hatch powers the blocks round it like a lever. It never powers its own box: a
  redstone hatch beside the controller is not read as a signal, so a lamp on the hatch cannot
  start the furnace that lit it.

<RecipeFor id="actualgenerators:item_hatch" />
<RecipeFor id="actualgenerators:energy_hatch" />
<RecipeFor id="actualgenerators:redstone_hatch" />
<RecipeFor id="actualgenerators:fluid_hatch" />

A hatch holds nothing and ticks never. It answers for its controller while the box stands, and
as an empty handler while it does not, so a pad or a pipe on a hatch never has to be reconnected.

## Annihilation Furnace

<BlockImage id="actualgenerators:annihilation_furnace" scale="4" />

Mass into energy. Feed it blocks, any blocks, and it pays by how much block they were: a block's
**hardness** is its mass, times a configured FE per point, between a configured floor and
ceiling. Cobblestone (hardness 2) is worth 40,000 FE by default, deepslate 60,000, obsidian the
1,000,000 ceiling; a torch or a sapling gets the 10,000 floor. A pack's own blocks are worth
whatever their hardness says, with no recipe written anywhere.

Only blocks burn. Ingots, tools and food are refused at the slot, so are blocks that cannot be
broken, and so is anything in the block tag `actualgenerators:annihilation_refused`, which holds
the shulker boxes by default: a full one would take its contents with it.

### Heat

A cold furnace eats nothing. The heat is what stands on its floor, the lowest layer inside the
box: **Corium**, one bucket per floor block, is full heat, and lava is a poor second at
forty percent. The floor's heat is the average over it, so a 5×7×5 wants nine buckets on its
nine floor blocks; one bucket in the middle spreads, but only the source block counts.

Corium comes out of a [Geothermal Fissure Tap](generators.md) as it works. You never have to open
the box: a bucket of corium or lava used on the controller goes on to the floor, and an empty
bucket used on it takes one back. Corium placed on the floor stays; it does not cool.

### Efficiency

Heat times load is the furnace's **efficiency**, and every item pays that share of its worth.
Load is how the slots are managed: the furnace eats one batch at a time, one item per block of
interior, and up to a batch held in the slots is no load at all. Stuff the slots past the batch
and every item is paid less, in a straight line down to a quarter with all four slots full, so
a furnace buried under a chest of cobblestone makes a quarter of what a furnace fed a batch at
a time makes from the same cobblestone. Keep the slots at the batch and the rating is what you
get. The window shows heat, load and the efficiency bar, and Jade shows the efficiency on the
controller.

### Rate

An operation takes forty ticks whatever its size and eats one item per block of interior, mixed
freely across the four slots. The items are gone the moment it starts and the FE for the whole
lot is paid out evenly over the forty ticks, so nothing can be pulled back out half-burned and
nothing is paid twice. A full buffer pauses the payment rather than losing it. At full
efficiency a 3×7×3 fed cobblestone makes 5,000 FE/t; a 5×7×5, 45,000; a 7×15×7, 325,000.

<RecipeFor id="actualgenerators:annihilation_furnace" />

The window shows the box, the batch it buys and the rate coming out, or the preview controls
while there is no box; Jade shows the box, the batch and the efficiency on the controller, and
the FE bar on an energy hatch.

## Geothermal Fissure Tap

<GameScene zoom="4" interactive="true">
  <ImportStructure src="structures/geothermal_tap.snbt" />
  <IsometricCamera yaw="225" pitch="30" />
</GameScene>

A plate, not a box: a square of **Geothermal Casing** on the ground, five to fifteen on a side
and always odd, with a 3×3 cap of Machine Casing centred on top of it and the tap in the middle
of one of the cap's sides, facing out. Hatches go in the cap. Every plate block is a share of
the rating and the plate is what has to lie deep, so dig to bedrock and lay it there. See
[Generators](generators.md) for what it makes and the heat it draws on.

<RecipeFor id="actualgenerators:geothermal_casing" />
