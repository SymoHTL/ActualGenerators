---
navigation:
  title: Labels, the card and the clipboard
  parent: logic-ports.md
  position: 60
---

# Labels, the card and the clipboard

Three ways to set up many pads at once, one pad's settings at a time. All three carry the same
thing: the pad's links on every channel and kind, what its channels carry, its filters and
upgrades, its label and its network.

## Labels

The box at the top of a pad's window, where a title would be, takes a **label**. Click it and type,
or pick one of the labels the pad's network already knows from the list that opens under it: the
list narrows as you type, like an address bar.

**Every pad with the same label on the same network has the same settings.** Change anything on
one of them by hand (a switch, a number, a filter dropped in, an upgrade) and every other pad
wearing the label takes the change at once; pads whose chunk is not loaded take it when it loads.
A pad that takes a label the network already knows asks which way round: **Take its settings**
makes this pad like the others, **Push mine to it** makes the others like this pad. A label the
network has not seen yet takes this pad's settings. Empty the box to take the label off; a pad
without a label follows nobody.

A labelled pad off any network keeps its label, and is set up like the label when it joins one.
Held, the Linking Tool shows a pad's label under its network name, and the overview lists it on
the pad's line.

Filters and upgrades replicate too, out of your inventory: a pad that needs a filter it does not
have gets a blank Filter from your inventory with the right contents written on it, and a pad that
needs an upgrade gets one from there as well. When you have none to give, the label box shows a red
banner for how many pads went without; click it to dismiss.

## The config card on a pad

The **Config Card** copies a pad like it copies a machine: crouch and use it on the pad. Use it on
another pad and it asks: **This pad**, or **This pad and every touching pad on the same face**. The
second walks from pad block to pad block, neighbour to neighbour and never diagonally, and sets up
every pad on the same side as the one you clicked, so a wall of chests with a pad on each is one
click. A block with no pad on that side ends the walk there. Pads on networks that are not yours
are skipped and counted. Filters and upgrades come out of your inventory as with labels; the card
puts the pads on the copied pad's network when you may use it.

Use the card at nothing (at the sky) to blank it.

<RecipeFor id="actualgenerators:config_card" />

## Export and import

Two tabs hang off the right edge of the pad's window. **Export** puts the pad's setup on your
clipboard as text; **Import** applies whatever pad setup is on the clipboard to this pad. Paste the
text into a chat message, a wiki or a friend's message, and they import it on theirs. Every number
that comes off the clipboard is clamped as the window's arrows would clamp it, and text that is not
a pad's setup is refused whole.
