---
navigation:
  title: The four numbers
  parent: logic-ports.md
  position: 20
---

# The four numbers

Under the switches sit four quiet numbers, per pad, per channel and per kind: **Keep**, **Amt**,
**Prio** and **Delay**. Each is a caption, a value and two small arrows. The arrows step by one, by
eight with shift or a right click, by sixty-four with ctrl. Counts step by the kind's unit: an item,
a bucket, a thousand FE.

A value drawn in muted grey is one the pad's tier decided; one drawn in black is a number you set.

## Typing a number

Click the value (or its caption) and type. Enter sets it, Escape leaves it as it was, and a value
that does not work out turns red and stays put. Sums, brackets and suffixes all work:

- `400k`, `1.5M`, `2B`: thousands, millions, billions. On a fluid, `B` is buckets: `2B` is 2000 mB.
- `64*3`, `(2k+500)/2`, `-3`: the four operators, brackets, a sign.
- `2s`: seconds, for Delay. `40t` is the same thing in ticks.
- Units are ignored: `500 mB` and `20 FE` read as 500 and 20.

Whatever you type is clamped exactly as the arrows would clamp it.

## Priority

Where this pad stands in the queue for this kind. Higher receives first and sends first. Steps
both ways and goes negative, because "behind everything else" is a thing you want to say.
Default 0.

## Keep

A stock, counted per item type over the whole block behind the pad. The same number works both
ways:

- A **receiving** pad stops filling its block once it holds this many of an item. This is what makes
  a catalyst loop work: a crafter that uses a rune without consuming it needs exactly one rune and
  never a second.
- A **sending** pad leaves this many of each item behind and ships only the surplus.

Zero is off. Energy has no count and so no keep.

## Amount

The most this pad moves in **one send**, sending or receiving. Left alone it follows the pad's
tier: thirty-two items, a bucket, twenty thousand FE for an untiered pad, and more with a
[tier](tiers.md) in the pad. Step it down to make a pad trickle where the network would pour; step
it back up to the limit (or type anything at or above it) and it follows the tier again, so a tier
fitted later is felt at once.

A receiver's Amount caps what it takes in one send, exactly as a sender's caps what goes out.

## Delay

Ticks between this pad's sends of this kind. Left alone it sits on the tier's floor: ten ticks for
an untiered pad, one tick for netherite. Step it up to make a pad send less often; it never goes
below the floor, and a tier is the only thing that lowers the floor.

## Rate

**Rate = Amount / Delay.** An untiered pad moves thirty-two items every ten ticks; a netherite pad
with both numbers left alone moves five hundred and twelve items every tick. There is no third knob
and no hidden interval underneath: every link keeps its own clock, and a network sleeps until the
next sender on it is due. A network whose senders had nothing to move backs off to a longer look-in
until something wakes it, so an idle network costs nothing.

Energy has an Amount and a Delay, and no keep. Redstone has one number, **Full**, where Keep sits:
what the stock source calls fifteen. See [Redstone over the network](redstone.md).
