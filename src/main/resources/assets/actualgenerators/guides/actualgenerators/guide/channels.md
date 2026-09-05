---
navigation:
  title: Channels
  parent: logic-ports.md
  position: 10
---

# Channels

A network has sixteen channels, one per dye colour. Each channel carries **any set** of items,
fluids, energy and redstone for the whole network: one kind, two, or all four. A pad's window shows
the sixteen as a palette along the top: the colour, a small picture per kind the channel carries (a
chest, a drop, a bolt, a torch, two by two), and an arrow up or down when this pad sends or receives
on it. Click a cell to pick the channel.

## Three levels

The window is three levels, one band each:

1. **The palette** picks a channel.
2. **The tabs** under it pick a kind: Items, Fluids, Energy or Redstone. The tabs are pictures,
   since four names do not fit; the band above them names the open one. A tab is filled in the
   network's colour when the channel carries that kind, and dark when it does not.
3. **The open tab** is everything this pad does with that kind on that channel: whether the
   channel carries it, IN, EX and PU, spread, redstone, the Filter slot and the four numbers.

Nothing on a tab is shared with another tab. A pad can pour energy on white while it trickles
items on white, each with its own numbers, its own filter and its own redstone mode.

## What a channel carries

The first button on a tab reads **ON** or **OFF**: whether the channel carries the tab's kind, for
the whole network. Click it to switch. A channel with nothing switched on carries nothing, and
every colour waits to be told.

Before a pad is on a network the kinds live on the pad, and the first network it joins takes them
for every channel nobody has given a job yet.

## What this pad does with it

- **IN**: this pad receives this kind on the channel.
- **EX**: this pad sends this kind on the channel, pulling it out of the block behind it.
- **PU**: what the block behind the pad pushes into it leaves on the channel. EX is the timed
  pull; PU is the door, and a hopper or a pattern provider behind the pad wants only the door.
  See [Logic Ports](logic-ports.md).

Click to toggle; any of them may be on at once. The two are separate on purpose. One pad on a chest can
send cobblestone on white and receive iron on lime, or send and receive on the same channel. A
face that can only be one or the other is the most common complaint about wired logistics, and
a pad is a face.

## Spread

Each kind on each channel has a spread mode, shown on the spread button by two letters; a click
goes to the next one:

- **Nearest first** (NF): a sender hands everything to the highest-priority receiver, nearest first
  among equals, and only moves on when that one is full. This is what "send it over there" means,
  so it is the default.
- **Round robin** (RR): each send goes whole to the next receiver in line, and the line moves along
  one place every send, so three chests fill in turn.
- **Random** (RN): each send goes whole to a receiver picked at random.
- **Even split** (ES): a sender shares what it has on offer evenly among all its receivers, and the
  front of the queue moves along one place every send, so three chests fill together rather than in
  turn. It shares what is actually there, never the budget: a chest holding sixty-four never hands
  all sixty-four to the first receiver.

Priority always decides first. Spread only settles ties. The first three cost one insert per send;
even split costs one per receiver, which is what filling everything at once is worth. With
hundreds of receivers on one channel that adds up, so it is the one to pick on purpose.

## Redstone

The **R** button is this link's redstone mode: always, with signal, without signal, never. It
reads the signal at the pad's block. Left click steps forward, right click back. Items on white
can wait for a lever while energy on white keeps flowing.

For redstone *over* the network, a level read behind one pad and put out behind another, see
[Redstone over the network](redstone.md): the fourth tab.
