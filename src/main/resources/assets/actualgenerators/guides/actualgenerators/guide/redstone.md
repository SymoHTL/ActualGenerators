---
navigation:
  title: Redstone over the network
  parent: logic-ports.md
  position: 25
---

# Redstone over the network

A channel can carry a **redstone level** beside its items, fluids and energy. Nothing is moved: a
sending pad reads a level, 0 to 15, off the block behind it, and every receiving pad on the
channel puts that level into the block it is on. It costs no FE, so a network with no injector
still carries a signal.

## Two clicks, as ever

Open the fourth tab, the torch. Switch it **ON** so the channel carries redstone, press **EX** on
the pad that reads and **IN** on the pad that emits. A lever behind one reaches a lamp behind the
other the same tick.

A channel's level is the highest any of its senders reads. A pad receiving on several channels
emits the highest of them.

## What a sender reads

Where the other tabs have PU, the redstone tab has a **source** button. Left click steps to the
next reading, right click back:

- **RS**, signal: the power reaching the pad's block from any side, exactly what a redstone lamp
  in the pad's place would see. A lever or a button beside the pad, dust running into it, a torch,
  a comparator's output, or the block behind it with a lever fixed to it.
- **CP**, comparator: the block's comparator reading, without a comparator. How full a chest is,
  what a jukebox plays, how far a cauldron is filled.
- **ST**, stock: how many items the block holds of what the tab's **filter** lets through,
  against the tab's one number, **Full**. Fifteen at Full or more, one at the first item, and in
  between what a comparator would say. A level emitter for the things you name.

The pad's own output is left out of every reading, so one pad may send and receive on the same
channel without holding itself high.

## What a receiver does

A receiving pad is a lever screwed onto the block it is on: it powers that block strongly, at
the channel's level, and everything round the pad's block weakly, as a lever does. A lamp, a
piston or a door behind the pad or beside it works at once; a stone block behind the pad passes
the power on to whatever touches it, as a block with a lever on it does. The block does not read
its own signal back, so the pad's other links keep their redstone modes.

## When it is read

A sender is read whenever something next to a pad changes, which is the same tick a lever moves,
and at least every idle interval while any channel carries redstone; a button's pulse is not
missed; a lever off beside a sender is dust off beside a receiver on the next tick, both ways. A
redstone lamp still takes its own two redstone ticks to go dark, as it does off a lever, and a
block that dust from a receiver runs into is a powered block, which a sender next to it reads:
that is vanilla redstone, not the network. Delay does not apply, and there is no Amount, no PU and
no spread: a level has no lot to carry and reaches every receiver alike. The **R** button still
applies at both ends: a sender held off by its redstone mode reads nothing, a receiver held off
emits nothing.

Jade shows the level a pad puts out.
