---
navigation:
  title: Logic Ports
  icon: actualgenerators:logic_port
  position: 80
item_ids:
  - actualgenerators:logic_port
---

# Logic Ports

Wireless logistics for items, fluids, energy and a redstone level, all four. No cables, no pipes: a
**Logic Port** is a flat pad stuck onto a face, and pads on the same named network hand things to
each other.

<RecipeFor id="actualgenerators:logic_port" />

## A pad is a face

Crouch and place a Logic Port against a face of a chest, tank or machine. One block position holds
up to six pads, one per direction, so a single pad block in a one-wide gap serves the machines on
both sides. Pads are waterloggable and water never washes one off.

A pad has no inventory, no tank and no buffer. It reads and writes the block it is on through that
block's own sided capability, so a machine's face rules still apply and a pad never gets round them.

## Two clicks to a working link

1. Put two pads on one network with the **Linking Tool** (see [the injector and the tool](injector-and-tool.md)).
2. Open a pad's window. Pick a channel on the palette along the top, open the tab of the kind you
   want to move and switch it **ON** so the channel carries it, then press **EX** on the sending pad
   and **IN** on the receiving one.

That is the whole price. Nothing is set up for you: a fresh network carries nothing until a channel
is told what, and a pad does nothing until IN or EX is on. Filters, priorities, keep, amount and
delay are all optional.

A pad can be set up completely before it is on any network. The network line reads red until it
is on one, and the first network the pad joins takes what its channels were told to carry.

## Wireless costs FE

An **Energy Injector** is linked onto a network the same way a pad is, and pays FE for everything
that network moves. A network with no injector, or with empty ones, moves nothing at all. Prices
per item, per bucket and per thousand FE are server config.

## Pushing into a pad

A pad is also a door. Anything the block behind it pushes into the pad (an AE2 pattern provider, a
hopper, a pipe from any mod) goes straight out on the channels where the pad has **PU** on, to the same
receivers a send would reach, and whatever nobody takes is handed back in the same moment. The pad
holds nothing in between: no inventory, no tank, no buffer.

A pushed lot is free and unmetered: no FE, no Amount, no Delay and no keep-behind, since the
pusher's own limits are the limits. Everything else still applies: the channel must carry the kind,
the link's redstone mode, the filters at both ends, priority, spread and the receivers' keep. Items,
fluids and energy all go through this way.

**PU** is its own switch beside IN and EX. EX is the timed pull from the block behind the pad, and a
hopper there would be pulled from as well as pushing, so leave EX off unless you want both.

## Pages

- [Channels](channels.md): sixteen colours, any set of kinds, a tab per kind, spread and redstone.
- [The four numbers](pad-numbers.md): priority, keep, amount, delay, and typing them.
- [Redstone over the network](redstone.md): a level read at one pad and put out at another, for free.
- [Filters](filters.md): a filter item per channel and kind, a list of item and tag entries, each allowing or blocking.
- [The injector and the tool](injector-and-tool.md): networks, names, colours, reach, the overlay, the overview.
- [Pad upgrades](pad-upgrades.md): range, the unbound card, and tiers.
- [Labels, the card and the clipboard](labels-and-cards.md): the same settings on many pads at once.
