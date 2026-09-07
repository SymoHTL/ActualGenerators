# Actual Generators

Novel energy generators, machines and item logistics for Minecraft — NeoForge 1.21.1.

A ground-up rewrite of the 1.19.4 Forge mod
([ActualGeneratorsOLD](https://github.com/SymoHTL/ActualGeneratorsOLD), archived).
The concepts survive — item pipes, machines, the Tesla Coil — but every system is
being redesigned from scratch.

> **Status: early development.** The machine framework — energy buffers,
> per-face configuration, upgrades, the overclock ramp and the machine GUI — is
> in, and six generators run on it: the Corrosion Cell, the Hydrostatic
> Generator, the Photovore, the Impact Dynamo, the Spawner Siphon and the
> Enchantment Combustor. The Surge Bank gives them somewhere to put the power,
> and the Crystal Charger turns it into Flux Crystals — a standard FE battery
> item that stacks. The Resonance Crusher is the first machine that spends power
> rather than making it: it doubles ores, once it has calibrated them. All of it
> is covered by gametests, and a Linking Tool puts pads and injectors on named
> networks. Everything has a recipe, so it is playable in survival — the recipes
> themselves are a first pass, meant to be rebalanced.

## Design pillars

- **Generators nobody has done before.** No coal generator clones, no solar panel
  number 500. Every generator has a mechanic you haven't seen: it reacts to the
  world, carries risk, or feeds on gameplay itself.
- **A full progression.** Early, mid, late and endgame tiers, capped by the
  Tesla Array reactor. Tiers gate by material cost — rush if you can afford
  it — except the endgame, which asks for one End-tier trophy.
- **Performance is a feature.** Full feature set, minimal cost: event-driven
  logic instead of per-tick world scans, no ticking cable networks, idle
  machines sleep, and visuals stay renderer-cheap. Server TPS and client FPS
  are treated as part of the mod's design.
- **One machine framework.** Every machine shares the same per-side auto I/O,
  redstone control, copy/paste configuration and upgrade system.
- **No cable spaghetti.** Logistics is wireless and port-based — your base
  doesn't disappear under pipe runs.
- **Standalone playable.** No hard dependencies. Crafting is deliberately
  expensive, so [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2)
  is the recommended companion for autocrafting — but never required.
- **Plays nice with modpacks.** Standard Forge Energy (FE), materials registered
  under `c:` common tags, recipes accept any mod's equivalent ores, and worldgen
  is datapack-disableable.
- **Configurable balance.** Generator outputs, machine speeds and energy
  capacities live in server config, not hardcoded.

## Planned content

### Generators

Concrete designs are still in flux. Current candidates (ideas, not commitments):

| Tier | Candidate | The twist |
|---|---|---|
| Early | Impact Dynamo | **Built.** Catches falling blocks and keeps both halves of the impact: FE scaling with how far the block fell, and the block itself, banked instead of destroyed. Build stupidly tall gravel droppers. Back it up and it stops catching — the block lands on the lid where you can see the jam. |
| Early | Hydrostatic Generator | **Built.** Output scales with the water column standing above it — flood a shaft, let pressure pay. Only still water counts, so one bucket down a hole earns nothing; waterlogged blocks do, so the shaft can be built rather than dug. It burns no fuel, and so takes no speed or overclock upgrades: what you built is what it pays. (Physically impossible. Kept anyway.) |
| Early | Photovore | **Built.** Grazes on placed light sources: eats the nearest one and pays by how brightly it burned. What counts as food is a tag, so it will never touch a beacon, a portal or a lava lake — but it will absolutely eat your torches, and then come the mobs. |
| Early | Corrosion Cell | **Built.** A galvanic pile that drives copper up the weathering ladder and harvests the reaction. Copper in, the next oxidation stage out, FE the whole way — nothing is destroyed. Waxed copper is sealed and refused. |
| Mid | Geothermal Fissure Tap | **Built.** IRL-style geothermal: taps the finite heat pockets under its plate, one per chunk it lies in, rolled once from the seed, full only near the world floor and fading to nothing higher up. The pocket drains as it runs and regrows at a trickle; a chunk with no pocket makes nothing — relocate, and the **Thermal Probe** reads a chunk before you build. A flat multiblock: a plate of Geothermal Casing five to fifteen on a side, a 3×3 cap of Machine Casing on top, the tap in a side of the cap, and the plate is the bore — every plate block is another share of the rating, depth measured at the plate, so lay it on bedrock. Leaves Corium behind, a millibucket per hundred thousand FE, which is the heat the Annihilation Furnace runs on; it leaves through Fluid Hatches. |
| Mid | Portal Flux Generator | Siphons the dimensional gradient of an active nether portal. Destabilizes it: flickering, uninvited zombified piglins. |
| Mid | Spawner Siphon | **Built.** Clamps onto a spawner, holds its countdown out of reach and banks FE for every spawn it denies. The rate is read off the spawner itself, so an Apothic-upgraded one pays more with no integration code involved. Reversible: break the siphon and the spawner goes back to work. |
| Mid | Enchantment Combustor | **Built.** Burns the enchantments off gear and hands the gear back — a grindstone that pays the grid instead of the player. FE scales with the total levels on the item, so one kitted-out drop beats a pile of single-enchantment books. Enchanted books come back as plain ones. |
| Late | Storm Capacitor | Trickle-charges in thunderstorms; a baited lightning-rod strike delivers an enormous burst (an IRL bolt is 1–10 GJ) that only a Surge Bank can fully catch. |
| Late | Crystal Resonator | Masquerades as a dragon: player-placed end crystals lock their healing beams onto it, and it drinks the stream. No dragon required — you build your own arena. Overdraw and crystals detonate in a chain. |
| Late | Annihilation Furnace | **Built.** Mass–energy conversion (E=mc²): feed it bulk junk blocks, get FE by mass. The endgame trash can. A multiblock: a tall hollow box of Machine Casing, seven high at the least, with the controller in the front wall, and the box is the grade — every block inside is one more item per operation. It runs on heat: Corium on the floor is full heat, lava a poor second, nothing is nothing. Heat times load is its efficiency, and load is how the slots are managed — stuff them past the batch and every item pays less. A block's hardness is its mass, so a pack's blocks are worth what they are made of with no recipe written; only blocks burn, and shulker boxes are refused by tag. |
| Endgame | Tesla Array | A multiblock field of coil towers harvesting the ground-to-ionosphere charge gradient — Tesla's actual Wardenclyffe dream, working here. Needs open sky, scales with altitude and array size, storms multiply output, and the top tier summons its own storms. |

### The machine framework

Every machine in the mod ships with the same chassis:

- **Per-side auto input/output** — push and pull, fully configurable per face, with the
  machine's own initiative on a separate switch, off until you ask for it: a face left
  open for a pipe to pull from is not the same thing as a machine shoving its results
  into whatever it was set down next to.
- **Redstone modes** — ignore / high / low / pulse.
- **Copy & paste** — the entire machine configuration (sides, redstone,
  filters) copies to a tool and pastes onto any other machine.
- **Four upgrades** —
  **Energy** (more FE I/O and capacity),
  **Speed** (raises base speed up to a ceiling),
  **Overclock** (scales *past* that ceiling),
  **Stack** (processes more items per operation).
  Machines take the upgrades they can actually use and refuse the rest, rather
  than accepting one and quietly ignoring it. A generator with no fuel to trade
  away — one paid by depth, weather or sunlight — takes energy upgrades only.
  Each type has its own slot, a machine only shows the slots it will accept, and
  a slot stops at the number the machine actually counts — no dropping a stack of
  sixty-four in to have sixty do nothing.
- **Tiers** — iron, gold, diamond, netherite, one per block, upgrading the block
  itself: speed and batch go up at the same FE per operation, so FE/t rises
  linearly with the tier the way it does with batching, never the way it does
  with speed upgrades. Processing machines only; a generator has no fuel to
  trade for it. A higher tier crouch-used on the block swaps in and hands the
  old one back. Each rung is four of its material round the rung below.
- **Persistent overclock** — the overclock ramps up while the machine works and
  does **not** reset between recipes; it only decays when the machine runs out
  of work or power. Keep the supply chain fed and the machine stays hot.
- **Two ways to go faster, priced differently** — speed and overclock raise FE/t
  *superlinearly* (Mekanism / Modern Industrialization style), while stack
  upgrades raise it only *linearly*. Batching is the efficient path; overclocking
  is the expensive one — and still worth it, because batching needs inputs
  arriving in bulk, and an overclock helps a machine fed a trickle.
- **One operation per tick, always** — a machine can be overclocked until it
  finishes something every single tick, and never goes beyond that. Past that
  point extra speed is wasted and only stack upgrades add throughput. This is
  deliberate: it caps what any one machine can cost the server, whatever the
  player bolts onto it.
- **Slots grow with the batch** — a slot holds a configured number of
  operations' worth (eight by default), never less than a stack, so the batch
  is the only cap on what a machine can be fed and a fitted machine visibly
  holds more than a bare one. An AE2 pattern
  provider pushes until the slot says no; a slot stuck at sixty-four would have
  capped a netherite machine at sixty-four a tick whatever its batch said.

### Multiblocks

The late-game generators are structures, not blocks, built the way Modern Industrialization and
Extreme Reactors build theirs: **Machine Casing** with one controller set into it facing out,
in a shape of the machine's own. The furnace is a hollow box, three to seven wide and deep and
seven to fifteen tall; the tap a plate of its own casing, five to fifteen on a side, with a 3×3
cap of Machine Casing on top and the controller in the cap's side. Place the last casing
and it forms on the next tick, every casing and hatch lighting its seams; break one and it all
goes dark the same tick.

- **The size is the grade.** No upgrade slots, no tier: every block of air inside the furnace
  is one more item per operation, every block in the tap's plate one more share of the rating,
  and the buffer, hatch rate and tank grow with it. Want more, build bigger.
- **The shape is the controller's.** The framework walks a hollow box by default; a controller
  that is another shape says where it stands and what goes where, and the finder, the
  hologram, the guide's scenes and the test rigs all draw from that one answer.
- **Hatches are the only doors.** The controller's own faces move nothing. An **Item Hatch**,
  an **Energy Hatch**, a **Fluid Hatch** or a **Redstone Hatch** goes anywhere Machine Casing goes, as many as you like,
  and each answers for the controller: a hatch holds nothing, ticks never, and hands out a
  proxy that is right whether the box stands or not, so a pad or a pipe on it never has to be
  reconnected.
- **Nothing is scanned.** A casing has no block entity. The controller looks at its box only
  when a casing, hatch or controller is placed or broken within reach, when something inside
  the box changes, or when a chunk its box reaches into loads. Casings cannot be pushed by
  pistons, so a sealed box stays sealed.
- **A preview before a single casing.** The controller's window has an eye and three steppers:
  press the eye and a hologram of the structure stands round the controller, a ghost of every
  block still to place drawn as itself, orange where a floor wants a fluid, red through walls
  where something is in the way. Hold a hatch and every spot it may go is framed green, and a
  hatch used on a casing takes its place. The sizes stick with the controller. The guide shows
  every structure as a scene generated from the same conventions.
- **A hatch is a machine face and is set up like one.** Click it and the controller's window opens
  with the same side panel every machine has: the hatch's six faces relative to how you placed it,
  the ones looking into the structure greyed out, and IN and OUT toggles for whether the structure
  pulls and pushes through it on its own transfer pass. Every face wears a marker for what it lets
  through. No pipes needed, no second window.
- **A formed structure looks like one machine.** Casings, hatches and the controller join into one
  surface, rimmed only where the structure ends; the tap's plate becomes rings of fins round its
  centre, every second block from the outside in.
- **Tanks take a click.** A machine with a tank draws it in its window; a bucket or any fluid
  container on the cursor, clicked on the tank, fills from it or empties into it, and a bucket
  on the block does the same before the window opens.
- **Hatches have windows.** An item or energy hatch opens the controller's window from
  wherever on the box it is; a redstone hatch opens its own, six modes: Control (a signal here
  is a signal at the controller), Formed, Working, Energy, Items and Efficiency, reported as
  nought to fifteen like a comparator. A hatch never powers its own box.
- **Heat and efficiency.** The furnace's floor takes a fluid: Corium, a bucket per floor
  block, is full heat, lava forty percent, poured and scooped through the controller with a
  bucket. Heat times load is the efficiency every item is paid at; load is how the slots are
  managed, no load up to a batch held and a straight line down to a quarter with the slots
  stuffed. Efficiency scales what a lot pays, never how long it takes.

### Machines

- **Resonance Crusher** (ore processing) — **Built.** Every material has a
  resonant frequency, and a crusher does not know it until it has taken that
  material apart the slow way. The first run on anything unfamiliar is a
  *calibration*: several times as long, and it pays the plain yield only. After
  that the frequency is written down — the material runs at full speed and the
  crusher shakes a bonus loose on top. Calibration is one-time per material, it
  survives being mined up, and the standard config card copies it to the next
  crusher, so the work is never done twice. Ores double (iron, copper, gold,
  coal, redstone, lapis, diamond, emerald, quartz) through the common `c:ores/*`
  tags, so another mod's ore crushes like vanilla's; cobble and gravel run the
  gravel cycle for anyone who just wants sand. Recipes are a datapack type
  (`actualgenerators:crushing`), so packs add their own.
- **Surge Bank** (energy storage) — **Built.** A capacitor bank with monstrous
  I/O and a slight idle leak. Exists to catch lightning. It is a multiblock
  without the ceremony: banks that touch pool what they hold, so a wall of them
  behaves as one buffer with no structure to form, no controller to elect and
  nothing to break when you mine one out of the middle. Shutting a face splits
  the wall there. The leak is the trade — a bank being pushed into or pulled
  out of loses nothing, one sat on a full charge bleeds. Buffer for a spiky
  base, not a vault.
- **Crystal Charger** (energy storage) — **Built.** Charger/discharger
  for **Flux Crystals**: quantized, stackable energy items that expose the
  *standard* FE item capability. That makes them ecosystem-native, not proprietary: any
  mod's battery slot drains them, any mod's item charger fills them, and any
  item automation moves them. Full and empty crystals stack (chests, AE2
  cells); the Crystal Charger is just the fast path plus the AE2
  hook — an export bus feeds it like coal feeds a generator, and a processing
  pattern (empty → full) lets AE2 autocraft energy on demand. High-tier
  recipes consume a full crystal and return the empty one as a crafting
  remainder (milk-bucket style), so the vessel loop survives crafting. Dense
  and lossless while solid — the opposite pole from the Surge Bank. Strictly
  optional: energy *transport* runs through Logic Ports — crystals are the big
  dumb battery, never a toll gate. It takes one crystal at a time out of whatever
  stack is waiting and drops each finished one straight into the output, so a
  stack feeds through steadily instead of vanishing for minutes and landing in a
  lump. Energy upgrades only, since a lossless transfer has no speed to buy,
  only throughput. A button flips it between filling crystals and emptying them,
  and partly-charged ones are welcome either way, which is how a mixed pile gets
  normalised back into something that stacks.
- **Flux Coupler** (portable charging) — **Built.** A carried item with a window of
  its own: load it with Flux Crystals and it keeps everything else in your inventory
  charged — tools, armor, any mod's gear that speaks the FE item capability. It holds
  no charge itself; it draws straight out of the crystal it has in hand, a crystal at a
  time, and each spent one lands in its output slot as an empty. The pace is the
  crystal's own transfer rate rather than a number the coupler owns, so a better
  crystal is a faster one wherever you use it. Takes energy upgrades, which are the
  only thing there is to buy in a machine that transfers rather than converts. Switched on and off in the same window, so your crystals don't bleed
  into a jetpack you're not wearing.
- **Field Projector** (farming & automation) — one block projects holographic
  work zones (till, plant, harvest, collect) drawn directly in the world.
  No machine rows, no fake-player entities, batched block operations.

### Logistics

No pipes, no cables. **Logic Ports** are flat pads placed on the face of
anything and put on named networks with the Linking Tool, carrying items,
fluids, energy and a redstone level — all four, and **built**.

A pad is a *face*, not a block: one port block holds up to six of them, so a
single block in the gap between two machines serves both. A network is sixteen
colour-coded **channels**, XNet's way widened: each channel carries any set of
items, fluids, energy and redstone for everyone on the network, and each pad says
per channel *and per kind* whether it sends, receives, or stays out. The pad's
window is three levels: pick a colour on the palette, pick a kind on its four
tabs, and everything on the open tab is that one link. Nothing is set up for
you: a channel carries nothing until you switch a kind ON, and a pad does
nothing until you press IN or EX — two clicks in the pad's window, and that is
all that is required. A pad can be set up completely before it is on any
network; the first network it joins takes what its channels carry. Per link,
four quiet numbers: priority (steps both ways, goes negative), keep-N (a
receiver stops filling at N, a sender leaves N behind), amount (what one send
carries) and delay (ticks between sends) — rate is amount over delay, and both
follow the pad's tier until you set them. Every number is typeable, sums and
suffixes included (`400k`, `1.5M`, `64*3`, `(2k+500)/2`). Spread and redstone
are per link too, and so is the **Filter** item in the link's slot. A Filter is set
up in its own window: one list of entries and one button, Add, which offers an
item-or-fluid entry or a tag entry. An entry's page is a slot (click with an
item for the item, right click for the fluid inside it, or drag from JEI), its
NBT as text to edit with Match or Ignore beside it (Pipez's rule: matching means
the stack carries what the entry carries and may carry more), IN and EX for
which of the pad's two jobs it counts in, and Allow or Block. A tag entry takes a
tag typed by name, or one picked off the item's list. Allow entries are a whitelist once there are any,
Block entries always win. Craft a set filter with a blank one to copy it, alone
to clear it.

Redstone crosses the network too, and for free: on the fourth tab a sending
pad reads a level (the power reaching the pad from any side, the comparator
reading of the block behind it, or a count of what the tab's filter lets
through against a number you set) and every receiving pad on the channel
powers the block it is on, the way a lever would, at the highest level any
sender read. A lever beside one pad reaches a lamp beside another the same
tick; a button's pulse is not missed. The held tool also draws each network's
reach as a globe round its centre, hidden by walls; crouch-use the tool to
choose what it draws.

A pad is also a door: whatever the block behind it pushes into it (an AE2
pattern provider, a hopper, any mod's pipe) goes straight out on the channels
the pad has PU on, filtered, prioritised and spread like a send, and whatever
nobody takes comes back in the same call. The pad holds nothing in between,
and a pushed lot costs no FE and ignores Amount and Delay: the pusher's limits
are the limits.

A network has a name, a colour and a centre. Clicking an unlinked pad with the
Linking Tool offers the networks that exist, or a name box for a new one; a
linked pad opens its own window, whose network line does the same and can leave.
An eye on every row of that list opens the network's overview: its channels,
every pad with its reach and its links, every injector and what it holds.
A network is its maker's, private until shared: only the owner and the players
they invite by name see it in the picker, put a block on it or open the window
of one that is on it, and a Public switch opens it to everyone. The owner can hand
the network to an invited player, and a player taken off takes their pads with
them. Operators own every network. Breaking is never refused: that is a claim
mod's job. The picker paints the
network any dye colour. Reach is measured from the centre
of all the network's pads, so a network grows from the middle out rather than
from whichever pad happened to be placed first. Holding the tool draws every pad
and injector in view through walls, in the network's colour, with its name, the
network's centre and a line from the centre to each pad, and a pad's label under
the name. Clicking a pad or injector in the overview flashes the block red
through walls for seven seconds.

Many pads, one setup. A pad takes a **label** in the box at the top of its
window (type one or pick one the network knows, like an address bar): every pad
with the same label on the same network has the same settings, and a change made
by hand on one reaches all of them, filters and upgrades included, out of your
inventory; a red banner counts the pads that went without when you had none to
give. Taking a label the network knows asks which way round: take its settings,
or push this pad's onto it. The **config card** copies a whole pad with a crouch
and, used on another, asks whether it means this pad or this pad and every
touching pad on the same face, neighbour to neighbour and never diagonally; used
at nothing it is blank again. Two tabs off the window's right edge **export** the
pad's setup to the clipboard as text and **import** one from it, every number
clamped as the window would clamp it.

Wireless is not free. An **Energy Injector** is linked onto a network the same
way a pad is, and pays FE for everything that network moves; a network with
nothing powering it moves nothing at all.

Range is upgraded **per pad**: tiered range upgrades, cross-dimension at the
top, plus the *unbound card* — unlimited reach inside one dimension, and a
network tolerates exactly one of them. A tier upgrade in the pad's third slot
raises what one send carries and lowers how few ticks it waits between sends.

Three rules decide how it is built, and all three are about not costing
anything when nothing is happening:

- **Ports never tick. Networks do.** A port is a block entity that is never
  ticked; the network runs the passes, and a network with no sending port
  never runs at all. One whose last pass moved nothing backs off to a forty-
  tick look-in until an event wakes it — placing, breaking, configuring or
  powering a port, or a neighbour changing.
- **There is no interval, only the senders' clocks.** Every sending pad has an
  amount per send and a delay between sends; a pass runs the senders whose
  delay is up and the network sleeps until the next one is due. The rate a
  player set is the rate a player gets, and the server works exactly as often
  as the pads asked.
- **Nothing is configured for the player.** A fresh network carries nothing
  until a channel is told what, and a pad does nothing until IN or EX is on —
  so two pads never shuffle a chest back and forth for ever, and a pad can be
  set up completely before it is on any network.

A port has no inventory, no tank and no buffer: it reads and writes the block
in front of it through that block's own sided capability, so a machine's face
rules still apply and a pad never gets to go round them.

#### What the mods already in every pack leave on the table

This is the crowded end of the mod list, and Logic Ports only earn their place
by fixing something. Each gap below is in a mod that a kitchen-sink pack
already ships, and each one is a requirement here rather than an ambition:

- **Pipez** cannot do input and output on a single face. A port configures
  insert and extract independently on the same face — the machine framework
  already models faces that way, so this costs nothing.
- **LaserIO** is configuration before it is function. A port that has just been
  linked and had nothing else done to it must already move things. Filters,
  channels and priorities live behind a second tab and never stand in front of
  the default case.
- **EnderIO conduits** move fluid on a five-tick interval and have no
  round-robin. Throughput here is never expressed as an interval: the rate is
  what the player is promised, and the interval is an implementation detail that
  carries the whole budget between passes — the rule the machine framework
  already follows for auto-transfer. Round-robin ships in v0.1.
- **XNet** does things nothing else does — feeding a crafter a catalyst that is
  used but never consumed, for one — but it is slow, and that trick is emergent
  rather than offered. Here it is a stock port mode: *keep N in target*, insert
  until the destination holds that many and then stop. Catalyst loops work by
  design instead of by cleverness.

Settled: wireless pads, no cable blocks. Cables would be allowed as topology
and decoration as long as no block ever ticks, but nothing in the design needs
them, so they are not being built.

### Tools

One **Linking Tool** does the linking: use it on a pad or an Energy Injector
to put it on a network (or, once it is on one, to open its window), hold it to
see every network in view. It does nothing else — the wrench and pickup modes it
once had were not worth the mode switch.
Machine and pad config copy/paste lives on a separate cheap early-game **config
card**: crouch to copy, use to paste, use at nothing to blank it.

### Crafting

Everything routes through one **Machine Frame** (iron, copper, a little
redstone). A machine is that frame plus the two things that say what it does —
glass and copper for the Corrosion Cell, pistons and iron for the Impact
Dynamo, and so on — so rebalancing a whole tier means editing one recipe rather
than nine. Upgrades are single-row recipes because they are made by the
handful; a tier is four of its material round the rung below it, iron round a
frame. Ingredients come from `c:` common tags wherever a tag exists, so
another mod's copper is the same as vanilla's. No mod-specific materials are
needed yet: nothing here asks for an ore that does not already exist, which is
the point of the need-driven rule below. A gametest fails the build if anything
the mod registers has no recipe.

### Materials

A lean, need-driven set of the usual modpack staples — nickel, lead, silver
and machine-made alloys, each added only once a recipe actually calls for it —
registered under `c:` common tags. Recipes are tag-based, so any other mod's
nickel works, and ore generation can be disabled per-datapack for packs that
already have them.

### Roadmap to 0.1

The first playable release is a vertical slice: machine framework, two or
three early generators, the Resonance Crusher, and basic Logic Ports (items,
fluids, energy). Every core system present, content thin. Framework,
generators, the Crusher, crafting recipes, the JEI/Jade integrations and Link
Ports are all in. **What is left is play-testing it as a whole.**

### After 0.1: getting it played

Being good is not the same as being installed, and the things that decide the
second are mostly not code:

- **Publish on CurseForge and Modrinth**, even at 0.1. Kitchen-sink packs pull
  from CurseForge; a mod that is not there does not exist to them.
- **EMI** alongside the JEI and Jade support that already ships. Pack authors
  read a missing recipe viewer as a bug, not a gap.
- **A smaller pack first.** Inclusion in the big kitchen-sink packs follows
  people asking for a mod, and people ask for what they have already played.
- **A showcase clip of the generators**, because novelty is the whole pitch and
  it does not survive a screenshot of a GUI.

An honest read of the pitch itself: the generators are the case — a Spawner
Siphon, an Enchantment Combustor and a Photovore are things the big packs have
no equivalent of, while ore processing is a crowded room the Crusher enters
with a novel mechanic and a lot of competition. The sleeper is the Flux
Crystal: packs are AE2-heavy, and a processing pattern that turns an empty
crystal into a full one is autocraftable energy with no integration code behind
it. Logic Ports are the weakest card competitively — the gaps above are real,
but the room is full — so they mostly have to be fast, obvious and not lag.

## Integrations

None of these is required, and none of them is a compile-time dependency of the
mod's own logic — install what you like.

- **JEI** — a Resonance Crusher page for every crushing recipe, showing the
  frequency the material rings at, plus notes on the Flux Crystal, the Linking
  Tool and the Filter. Ingredients drag out of JEI straight into a Filter.
- **Jade** — machine progress and, for the Crusher, whether it has calibrated
  what it is holding. The energy bar comes from Jade's own reading of the Forge
  Energy capability, which every machine here exposes.
- **GuideME** — an in-game guide with a page for everything: the chassis,
  upgrades and tiers, every generator, the Crusher, storage, and the whole of
  Logic Ports. Craft it from a book and a copper ingot; the recipe only exists
  when GuideME is installed, and the mod never compiles against it.
- **EMI** — planned; recipe viewing for all machines.

## Downloads

Releases will be published on **Modrinth**, **CurseForge** and
[GitHub Releases](https://github.com/SymoHTL/ActualGenerators/releases) once
there is something worth playing.

## Development

| | |
|---|---|
| Minecraft | 1.21.1 |
| Mod loader | NeoForge 21.1.249 |
| Build | ModDevGradle 2.0.144, Gradle 8.14.2 |
| Mappings | Mojang official + Parchment 2024.11.17 |
| Java | 21 |

Gradle itself runs on Java 17+, but compilation and the game need a **JDK 21**
toolchain. If no JDK 21 is installed, Gradle auto-provisions one via the foojay
toolchain resolver configured in `settings.gradle`.

```bash
./gradlew build              # build the mod jar into build/libs
./gradlew runClient          # launch a dev client
./gradlew runServer          # launch a dev server
./gradlew runData            # run data generators into src/generated/resources
./gradlew runGameTestServer  # run registered gametests headlessly
```

### Layout

```
src/main/java/dev/symo/actualgenerators/
  ActualGenerators.java     @Mod entrypoint
  config/                   server config; balance numbers live here, never in code
  machine/                  the machine framework -- chassis, energy, sides, upgrades
  machine/multiblock/       casing, hatches, and the controller a box of casing forms round
  generator/                the generators
  storage/                  energy storage
  menu/                     containers and the GUI layout the screens read
  client/                   screens and other client-only code
  item/                     upgrades and the config card
  registry/                 DeferredRegisters
  datagen/                  data providers; run with ./gradlew runData
  gametest/                 gametests; run with ./gradlew runGameTestServer
src/main/resources/assets/actualgenerators/     textures (hand-authored)
src/generated/resources/                        everything datagen owns -- never hand-edit
src/main/templates/META-INF/neoforge.mods.toml  mod metadata template
```

Mod metadata (id, name, version, dependency ranges) is edited in
`gradle.properties`, not in the toml — the toml is a template that
`gradle.properties` values are expanded into.

Texture conventions, the shared palette and the tools used to draw them live
in [art/README.md](art/README.md).

## License

[LGPL-3.0-or-later](LICENSE) — Copyright © 2026 Symo.

Free to use in modpacks. Forks and derivatives of the code must remain LGPL.
