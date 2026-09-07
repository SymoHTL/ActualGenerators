---
navigation:
  title: Networks, the injector and the tool
  parent: logic-ports.md
  position: 40
item_ids:
  - actualgenerators:linking_tool
  - actualgenerators:energy_injector
---

# Networks, the injector and the tool

## The Linking Tool

<RecipeFor id="actualgenerators:linking_tool" />

The **Linking Tool** does one thing: it puts pads and injectors on networks. Use it on an unlinked
pad or injector and the **network picker** opens: type a name and press Create, or pick a network
from the list. Use it on a linked one and the block's own window opens; its network line picks
(left click) or leaves (right click).

Hold the tool and every pad and injector in view is drawn through walls, in its network's colour,
with the network's name; each network's centre is drawn too, with a line from it to each pad. Round
the centre, for every reach the network's pads have, a globe of lines marks the sphere a pad has to
stand inside, with the reach written at the top; the world hides it, so inside a base it stays out
of the way. **Crouch and use** the tool to choose what it draws: pads, centres and lines, the reach,
and whether the first two show through walls.

## A network is a named, coloured thing you make

No pad ever joins a network on its own. A network with nothing left on it is kept, with its
channels, colour and labels, until its owner clicks the cross on its row in the picker; the cross
does nothing while anything is still on the network. The picker has a row of sixteen dye cells
that paint the current network; a fresh network gets a colour from its name so two are told apart
before anyone picks.

## Whose network it is

A network belongs to whoever created it, and it is **private** until they say otherwise: nobody
else sees it in their picker, can put a pad or injector on it, or can so much as open the window of
one that is on it. A refused click says so on the action bar. The picker shows the owner under the
colours, with a button that reads **Private** or **Public**; the owner clicks it to open the
network to everyone, and again to close it.

Below that the owner invites players by name: type it and press Invite, or Enter. Invited players
use the network as the owner does, short of inviting others, painting it or opening it up. The
list shows who is invited, each with a crown that hands them the network (you stay invited) and an
**x** that takes them off. A player taken off can no longer open any pad on the network, and every
pad and injector they placed comes off it with them, the ones in far-off chunks when those load.
Breaking is never refused by the mod: that is a claim mod's job.

Server operators (permission level 2) own every network, so nothing is stuck when an owner leaves
for good.

A network from before there were owners belongs to everyone, and its owner line reads **Claim**:
one click makes it yours, and private.

## Reach is measured from the centre

A network's centre is the mean position of its pads in its home dimension, the one with the most
pads, moved every time a pad joins or leaves. A pad takes part while it is within its own range of
that centre, so a network grows from the middle out rather than from whichever pad was placed
first. Range is upgraded per pad; see [pad upgrades](pad-upgrades.md). A pad in another dimension
than the centre needs the top range tier.

The pad window's network line turns red with "out of reach" when a pad is too far.

## The Energy Injector

<BlockImage id="actualgenerators:energy_injector" scale="4" />

<RecipeFor id="actualgenerators:energy_injector" />

Wireless costs FE, and the injector is what pays. Link it onto a network with the same tool and the
same two clicks as a pad. Every send is paid for out of the injectors on the network: a price per
item, per bucket, and a cut in permille of any FE moved. Every budget is clamped to what the network
can afford before anything moves, so a network with no injector, or with flat ones, moves nothing.

The injector is the one block in the mod that **auto-pulls energy by default**. The network that
would carry FE to an injector is the network the injector has to pay for first, so a buffer that
waited to be pushed into could never fill.

Injectors are drawn by the tool like pads, but they are not part of the reach and get no line to
the centre.

## The overview

Every network row in the picker ends in an eye. Click it and the network is laid out to read:
whose it is and where its centre is; every channel that carries something, with its spread per kind
and how many loaded pads send and receive on it; every pad, with a dot for its reach (green in, red
out, grey when its chunk is not loaded) and a letter and arrows per link in the channel's colour
(I, F, E, R for the kind; ▲ sends, ▼ receives, » door); every injector and what it holds. Hover a
line for the words, and a pad's label sits on its line. Click a pad or injector line and the block
flashes red in the world for seven seconds, through walls, tool in hand or not, so it can be found.
Nothing in it changes anything, and looking joins nothing; Back is the picker.
