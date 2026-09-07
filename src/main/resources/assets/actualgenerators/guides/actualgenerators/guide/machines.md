---
navigation:
  title: The machine chassis
  icon: actualgenerators:resonance_crusher
  position: 20
item_ids:
  - actualgenerators:config_card
---

# The machine chassis

Every machine in the mod, generators included, is built on one chassis. Learn it once.

## Faces

Each of the six faces has a mode per kind of thing (items, energy): in, out, both, or closed. The
face buttons in the window are a cross of six squares; a left click steps a face forward, a right
click steps it back. A face says what is *allowed* through it. A pipe or a pad on that face may pull
or push whatever the mode allows, whenever it likes.

## Auto push and pull

Under the face cross sit two more switches per kind: **auto push** and **auto pull**. These are
the machine's own initiative: with auto push on, it shoves its output into whatever sits on an
output face; with auto pull on, it takes from whatever sits on an input face.

Both are **off** on a freshly placed machine. A machine moves nothing on its own until told to,
but its faces still let anything else move things through them. The two questions are kept apart
on purpose: "leave this face open for a pipe" and "go and fill the chest next door" are different
wishes, and a mod that merges them makes one of them impossible.

Auto transfer runs in bulk on an interval, but the interval never caps the throughput. An energy
pass carries the whole configured FE/t for every tick since the last pass, and an item pass is
brought forward whenever a slot can no longer cover one more batch.

## Redstone

The **R** button cycles through the redstone modes: always run, run on high, run on low, run on
pulse. Left click next, right click previous. A machine that is not allowed to run keeps its
buffer and does nothing.

## The config card

The **Config Card** copies a machine's whole configuration: face modes, auto switches, redstone
mode, and the Resonance Crusher's learned recipes. Crouch and use it on a machine to copy;
use it on another to paste. The card is cheap on purpose, so copying is what you do rather than
clicking six faces twice.

<RecipeFor id="actualgenerators:config_card" />

## Breaking a machine

Breaking a machine drops everything it held: inputs, outputs, upgrades and its tier. A pickaxe
that ate your upgrades would be a bug.

## Reading the window

- The **energy meter** on the right is the buffer, with capacity and FE/t on hover.
- The **ramp bar** beside the upgrade row is the overclock ramp on a processing machine, and the
  warm-up on a passive generator. Same bar, different label.
- The **progress arrow**, where there is one, says what fraction of the current operation is done,
  and "Nothing in progress" when idle.
- Every empty upgrade slot draws a faded picture of what it takes. Nothing is a bare square.
- A machine with a **tank** shows it beside the gauge, with the fluid, the amount and the
  capacity on hover. Click the tank with a bucket or any fluid container on the cursor and it
  fills from the tank, or empties into it, whichever the tank allows; a stack of buckets fills
  one and puts it in your inventory. A bucket used on the block itself does the same before
  the window opens.
