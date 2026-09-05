# Actual Generators — NeoForge 1.21.1 mod

Novel energy generators + machines + wireless logistics. Rewrite of archived 1.19.4 Forge mod
(https://github.com/SymoHTL/ActualGeneratorsOLD — reference for old concepts only, never copy code 1:1).

## Commands

- `./gradlew build` — jar into `build/libs`
- `./gradlew runClient` / `runServer` — dev game
- `./gradlew runData` — datagen into `src/generated/resources`
- `./gradlew runGameTestServer` — headless gametests
- Requires JDK 21 toolchain (auto-provisioned via foojay).

## Hard rules

- **Datagen everything**: models, blockstates, recipes, tags, loot tables, lang via DataProviders.
  Handwritten JSON only where datagen can't reach (textures, custom model geometry).
  Never hand-edit `src/generated/resources`. After adding content: run `runData`.
- **Energy = Forge Energy (FE)** via NeoForge `Capabilities.EnergyStorage`. No custom energy type.
- **Balance numbers in server config**, never hardcoded: FE/t outputs, capacities, transfer rates, machine speeds.
- **Energy amounts are `long`, rates stay `int`**: buffers, stored charge and crystal capacity count
  in longs (late game goes past two billion); FE/t and transfer rates stay ints, since a rate that
  large is not a real balance. The FE capability view clamps to `Integer.MAX_VALUE` as every other
  mod does. Menu sync: a long travels as four 16-bit slices (`huge`/`piece`), an int as a pair.
- **Everything registered must be craftable** — `everythingTheModAddsCanBeCrafted` walks every
  registered item and fails the build if no crafting recipe makes it. Machines are built from one
  `MACHINE_FRAME` plus two flavour ingredients, so a tier is rebalanced in one recipe; upgrades are
  one-row recipes. Ingredients use `c:` tags wherever one exists.
- **Tags over items**: materials (nickel, lead, …) registered under `c:` common tags;
  recipes reference tags so other mods' equivalents work. Worldgen must be datapack-disableable.
- **GameTests required** for machine/energy/logistics logic (energy transfer, routing, machine ticks).
- **Test the gesture, not the method.** `ServerPlayerGameMode.useItemOn` asks the BLOCK first and
  the item second: a block whose `useWithoutItem` opens a window returns `CONSUME` and the item's
  `useOn` never runs, so the only click that reaches an item is a crouch — which is also the
  gesture that means "the other way round". Consequences, all learned the hard way:
  - An item that acts on a block hooks **`onItemUseFirst`**, never `useOn`. `useOn` is unreachable
    on any block of ours, because every one of them opens a window.
  - A gametest that calls `ItemStack.useOn(...)` or a block method directly proves NOTHING about a
    player's click. Drive `player.gameMode.useItemOn(...)` — `LinkPortTests.rightClick` does it,
    and `LinkPortTests.testPlayer` makes a connection-less `ServerPlayer` that survives it
    (`makeMockServerPlayerInLevel` runs the whole join, and Jade's greeting payload kills it). It
    also overrides `openMenu` to set `containerMenu` without a packet, so a click that opens a
    window (the tool's picker, the pad's window) can be followed by `clickMenuButton` calls.
  - This shipped a linking tool that could only ever UNLINK, past 124 green tests.
- **Build the rig the way a player builds it.** A gametest that calls `setBlock` and the manager
  proves the arithmetic and nothing else; `aPlayerCanBuildAWorkingLinkWithNothingButTheItems`
  places the pads by clicking barrels with the port item, puts them on a network through the
  tool's picker and sets the channel up with the pad window's own buttons, and it is the one that
  catches "doesn't work". Two facts it cost an evening to learn: the `platform`
  template's floor lands on helper-relative **y=1**, so a placed rig stands a layer above where the
  `setBlock` tests write; and placing a block against anything with a right-click action (a barrel,
  any machine) is a **crouch**, because the block's own window wins otherwise.
  Gametests all run on ONE server at once, so `LinkNetworkManager` is shared: never assert a
  global count or an empty list, search for your own network by id or a name only your test uses.
  The gametest server has its own game directory (`run/gametest`) and its world is deleted before
  every run (`build.gradle`): the structures are never cleared at the end, so a kept world carried
  every earlier run's networks into the next one and "the network is not there before it is made"
  failed on the second run and never the first.
- **No diagnosis text.** The per-guard "why it is not moving" readout (`LinkProblem`) was removed
  at Symo's request ("completely remove the debug log shit"). The window states facts instead —
  network name and size, reach (red "out of reach" on the network line), what the channel carries,
  what the pad does on it — and the held tool shows the network in the world. Do not bring the
  problem list back; make the facts visible where the player is looking.
- **A left click does the thing; a right click is only ever the bigger step, the previous mode
  or leaving**: arrows step 1 / 8 (shift or right) / 64 (ctrl), the redstone button walks back,
  the network line leaves. Nothing else changes meaning with the button, and every number is
  typeable (`AmountExpression`: 400k, 1.5M, 2B, 64*3, (2k+500)/2, 2s; Enter sets, Esc leaves).
  The old "left toggles, right goes exclusive / cycles the role" scheme was "kinda confusing" and
  went with the tab redesign. `LinkPortScreen.mouseClicked` maps clicks; a tooltip's last line
  says what a click does.
- **Tooltips are one line of fact and one line of hint.** No sentences explaining the concept; the
  item tooltip and the README are where prose lives.
- **Run the client and do the thing before calling it done.** Tests that assert against your own
  model of the code agree with it by construction. Nothing is "working" until it has been used in
  a running game — say "untested in game" rather than "works" when that has not happened.
  Symo drives the client. Never script keystrokes or mouse input into it (or anything else on
  this machine): synthetic input goes to whatever window has focus, and once it typed chat
  commands into the Claude Code prompt instead of Minecraft. Ask, then wait for the report.
- **UI is not a place to be lazy.** Cycling buttons (`next()` per click) are the cheapest control
  to write and the worst to use:
  - `Chrome.TEXT`/`MUTED` carry no alpha: fine for `drawString`, invisible with `fill`. OR in
    `0xFF000000` before filling (the field arrows shipped invisible).
  - Nothing with a sign cycles — priority has +/- and goes NEGATIVE.
  - Nothing with more than ~4 states cycles — channels are a palette of sixteen tabs along the top
    of the window (colour, kind glyph, this pad's role marker), never a cycling button.
  - Steppers take modifiers: click ±1, shift or right-click ±8, ctrl ±64 (`Screen.hasShiftDown`,
    `hasControlDown` — key mappings do not update while a screen is open).
  - A window draws its OWN background from the job it does. The machine sheet has an energy meter
    baked in; blitting it under a block with no energy shipped an energy meter on a pad.
  - Everything drawn is a `MachineLayout.Box` in the menu's `chrome(page)` — text lines and
    captions included, not just buttons. The priority readout that overlapped the tab button was
    a `drawString` the test could not see.
  - A window must fit 1080p at auto GUI scale, which is scale 4 = 270 logical pixels tall: keep
    HEIGHT ≤ ~240. The pad window is 236: palette, one channel band, three kind tabs, one row of
    switches and a 2×2 of numbers, and that is all that fits.
  - Every slot says what it is: upgrade and filter slots draw a faded hint sprite, never a bare
    square the player has to guess at.
  - A window is designed from the job it does. Copying the machine window because it is there is
    how a port ended up with machine chrome and no way to say "these but not those".
- **Face-mounted blocks (pads, covers) are per-face, not per-block-space.** One block position
  holds up to six of them, one per direction — a machine has six faces and a player will use them.
  They are waterloggable; water must never wash one off.
- **Multiplayer-safe always**: logic server-side, sync via payloads/`ContainerData`; no client-only state in game logic.
- **Performance is a core pillar** (server TPS + client FPS, without cutting features):
  - Event-driven over polling: no per-tick area/world scans; cache lookups, invalidate on neighbor-change events.
  - Idle machines/networks sleep — gate ticking, wake on events.
  - Logistics has NO ticking cable blocks: wireless port graph, transfers event-driven.
  - Client: baked models + animated textures preferred over BlockEntityRenderers; BER only when unavoidable; visual effects config-reducible.
  - No entity-based automation (fake players, drones) in core machines.
- Mod metadata (id, version, deps) edited in `gradle.properties` — `neoforge.mods.toml` is a template, don't put values there.
- License LGPL-3.0-or-later; new files carry no per-file header (repo-level LICENSE suffices).

## Architecture

- Package root `dev.symo.actualgenerators`, MODID `actualgenerators` (constant in `ActualGenerators.java`).
- Registration via `DeferredRegister` in `registry/` (ModBlocks, ModItems, ModCreativeTabs);
  new registry classes follow same pattern: static `register(IEventBus)` called from mod constructor.
- Mappings: Mojang official + Parchment.
- v0.1 target = vertical slice: machine framework + 2–3 early generators + Resonance Crusher +
  basic Logic Ports (items/fluids/energy). Framework lands first — everything builds on it.

## Design constraints (product)

- Generators must be *novel* — no coal-gen/solar clones. Flavors: environment-reactive,
  risk/reward, world-interactive (player-driven rejected). Tiers: early / mid / late / endgame
  (Tesla Array = endgame reactor, harvests ground↔ionosphere gradient). Candidate roster in README.
- **Machine framework — every machine gets the same chassis**:
  - Per-side auto input/output (push+pull, per-face config), redstone modes.
  - **Face mode and auto flag are separate questions**: a face says what is *allowed* through it,
    the per-kind auto push/pull flags (`SideConfig.autoPush/autoPull`, two toggles under the face
    cross) say whether the machine goes looking. Auto-output off must leave the face open for a
    pipe to pull from. Both travel in the config-card snapshot. **Both default OFF** — a placed
    machine moves nothing on its own initiative until told to; its faces still let anything else
    move things through them.
  - Whole config copy/paste between machines via tool.
  - 4 upgrade types: Energy (FE I/O + capacity), Speed (base speed up to ceiling),
    Overclock (scales past ceiling), Stack (batch size — more items per operation).
  - **Tiers are the block's own grade, not upgrades** (`MachineTier` NONE/IRON/GOLD/DIAMOND/
    NETHERITE, `TierUpgradeItem`, one per block in `MachineBlockEntity.tierSlot`): speed × and
    batch × at a FLAT FE per operation — the tier multiplies OUTSIDE the superlinear term
    (`currentEnergyPerTick`), so FE/t rises linearly with it like batch, never like speed.
    Mekanism's installer, Symo's ask. Netherite's "3 slots" is batch ×3: one op a tick, never a
    second line. Processing machines only (`acceptsTier()`, Crusher true; generators, Surge Bank,
    Charger, Injector refuse). Crouch-use swaps a higher tier in and hands the old one back; lower
    or equal is refused. Ladder recipe: four of the material round the rung below, iron round a
    Machine Frame. Every number is a config list per tier (`ServerConfig.tierValue`).
    **The buffer and the face rate follow the work** (`applyUpgrades`, `workFactor()` = maxBatch ×
    tier speed, on top of the energy-upgrade factor): a machine drawing a hundred times the FE
    holds the same seconds of it and can still be fed. Symo's ask; generators and storage blocks
    batch one at no tier, so it costs them nothing.
  - Overclock ramps while working, **persists across recipes**; decays ONLY when out of work or power.
  - FE/t scales **superlinearly with speed/overclock** (Mekanism/MI-style) but **linearly with batch**.
    ∴ batching = efficient path, overclock = expensive path; overclock still needed because batching
    requires bulk input. Don't "fix" this asymmetry — it's the intended trade-off.
- **Throughput scales by batch, never by more work per tick** (perf-critical):
  - A machine resolves **at most ONE operation per tick**, always. `ticksForOperation` floors at 1.
  - Past 1 op/tick, extra speed is wasted; only Stack upgrades raise throughput. Surface saturation
    in GUIs (`isSpeedSaturated`) so players don't pay quadratic energy for nothing.
  - NEVER loop N operations in a tick, and NEVER add parallel processing lines (Mekanism-factory
    style): both multiply recipe lookups, capability lookups, slot scans and sync payload.
    One batched op is O(1) regardless of batch size.
  - Batch cap bounds worst-case per-tick cost per machine — keep it capped.
  - **Slots hold `slotOperations` operations' worth (config, default 8), never less than a
    stack** (`MachineItemHandler`, limit = `max(64, slotOperations × maxBatch × per-batch-item
    count)`; the crusher sizes from its loaded recipes, result + 1 for the bonus). Two would cover
    the rate; eight is so a fitted machine visibly holds more than a bare one (Symo: "netherite +
    4 stack upgrades should also make input bigger"). An AE2 pattern provider pushes until insert refuses, so the slot
    limit IS the feed rate, and a 64-slot capped a netherite machine at 64 a tick. Symo's call
    over "3 slots": more slots move the ceiling, a bigger slot removes it. Vanilla's `ItemStack`
    codec refuses count > 99 and `save` throws, so the handler writes the count beside the item
    (the old shape still loads); `extractItem` is uncapped so an operation can take its whole
    lot; counts ≥ 1000 draw short (`Chrome.compactCount`). Never add slots for throughput.
  - Auto-transfer runs in bulk on an interval, and the interval must never become the real
    throughput cap. Energy: a pass carries `rate * ticksSinceLastPass` (clamped to the interval),
    so the configured FE/t is what a player actually gets, offered in ONE CAPABILITY CALL PER TICK
    COVERED: every `receiveEnergy`/`extractEnergy` call is capped at its owner's per-tick rate,
    so one call for ten ticks' worth moved a tenth of it (a 1k FE/t cell fed a 1k FE/t crusher
    at 100 FE/t, "it only transfers every second"; the Surge Bank's 20k face hid it from the
    test). Link energy sends make one call per tick of Delay for the same reason. Items: bring the pass forward when a
    slot can no longer cover one more batch — output room `< maxBatch()`, or input count
    `< maxBatch()`. A machine batching a stack a tick therefore gets a pass a tick (it has to);
    an unupgraded one keeps to the interval. Item and energy neighbour lookups both go through
    `BlockCapabilityCache`, so a per-tick pass stays cheap.
- Logistics = **Logic Ports**: wireless networks (items/fluids/energy — ALL THREE from v0.1 — and
  a redstone level as a fourth kind),
  flat port pads on machine faces linked via tool. Full config per port: insert/extract, filters,
  channels, priorities, round-robin/nearest-first, redstone modes. No cable/pipe blocks — don't
  port old pipe code. Range upgrades apply PER PORT: tiered range, cross-dim at top tier, plus
  special trade-off upgrades (big power, hard constraint — e.g. infinite in-dim range but only
  one such sender per network).
  - **Wireless pads is settled** — no cable blocks, and not for a lack of considering them.
  - Four requirements, each one a gap in a mod every kitchen-sink pack already ships. They are
    the reason Logic Ports exist at all, so none of them is optional:
    - **Insert and extract configure independently on the SAME face** (Pipez cannot).
    - **Nothing is configured for the player.** No default channel kinds and no roles adopted from
      the face: a fresh network carries nothing until a channel is told what it carries, and a
      pad does nothing until IN or EX is on. Zero-config was built and rejected ("why do you have
      default channels setup"); two clicks in the pad window is the price, and it is the whole
      price — filters, priorities, keep, amount and delay stay optional.
    - **A pad is set up before it is on anything.** Every button in the pad window works on an
      unlinked pad; what a channel carries then lives on the pad (`PortFace.localKind`), the first
      network the pad joins takes those kinds for every channel nobody has given a job, and a pad
      that leaves keeps a copy. Only spread waits for a network. The network line reads red
      ("No network") until there is one — "configure first, join later" confused nobody once it
      said so.
    - **Throughput is never a tick interval.** Rate is what the player is promised: every sender
      has an Amount per send and a Delay between sends, rate = amount / delay, and the network
      sleeps until its next sender is due. Round-robin from v0.1 (EnderIO conduits: 5-tick fluids,
      no round-robin).
    - **"Keep N in target" is a stock port mode** — insert until the destination holds N, then
      stop. That is what makes catalyst loops work (a rune used but not consumed); XNet can do it
      only as an emergent trick, and slowly.
  - **A pad is a FACE, not a block** (`PortFace`): one `LinkPortBlockEntity` carries up to six,
    each with its own network, filters, upgrades and switches, so one block in a gap serves the
    machines on both sides. The network addresses `PortRef(GlobalPos, Direction)`, never a bare
    position. Waterlogged; water never washes a pad off.
  - **Wireless costs FE.** The Energy Injector (`EnergyInjectorBlockEntity`) is linked onto a
    network with the same two clicks as a pad and pays for every pass out of its buffer; a network
    with no injector, or with flat ones, moves NOTHING. It is the ONE block that auto-pulls energy
    by default, and that is not a mistake to tidy up: the network that would carry FE to an
    injector is the network the injector has to pay for first, so a buffer that waited to be
    pushed into could never be filled at all. Prices are config
    (`LINK_FE_PER_ITEM`, `LINK_FE_PER_BUCKET`, `LINK_FE_PERMILLE_OF_ENERGY`), and every budget is
    clamped to what the network can afford before anything moves.
  - **Channels are XNet's, not EnderIO's — widened, then split per kind.** A network has sixteen
    channels (dye colours); each channel carries ANY SET of items, fluids, energy and redstone
    (`ChannelSettings.kinds`, a bitmask, network-wide; Symo: "we should be able to have items,
    fluid and energy on the same channel") and a spread mode PER KIND. A pad's part is one
    `PortChannel` per channel per kind (`PortFace.link(channel, kind)`, sixty-four per pad):
    send, receive, priority, keep, amount, delay, redstone mode and filter are all per kind and
    nothing is shared across kinds ("spread and redstone should also be per type"). The window is
    three levels in three bands: the palette picks a channel (a small glyph per carried kind,
    ▲/▼ for this pad's part, two by two), four TABS pick a kind (chest / drop / bolt / torch,
    glyphs only since four names do not fit at 38 px, the band under the palette names the open
    one; filled in the network colour when carried), and the open tab is that one link: a plain
    ON/OFF "carried" button
    (network-wide), IN, EX, PU, spread, R, the filter slot and the four numbers. A cycling kind
    button and a "right click shows this kind's numbers" scheme were both built and sent back.
    Each link keeps its own send clock; a pass runs every carried kind of every channel.
  - **Redstone is the fourth kind** (`TransferKind.REDSTONE`; machine code walks
    `TransferKind.material()` and never sees it, `SideConfig` stays 3×6). Nothing is moved: a
    channel's level is the max of what its EX pads read off their blocks, and every IN pad puts
    that level into the block it is on (`PortFace.signalOut`, saved; `LinkPortBlock.getSignal/
    getDirectSignal`, lever semantics: strong into the block the pad is on, nothing anywhere
    else). It is FREE — `LinkNetworkManager.runSignals` runs before the injector check and a
    network with no injector still carries a signal — and it is read on EVERY pass, never on the
    link's clock: a button's pulse is ten ticks and a Delay would miss it. A neighbour change
    wakes the network the same tick; while any channel carries redstone `nextDueAt` is clamped
    to the idle interval so a chest filled from above (no comparator update reaches a pad there)
    is still re-read. Three sources on the redstone tab where PU sits (`SignalSource`, RS / CP /
    ST, cycles, right goes back): the signal the block gives off (`level.getSignal(target,
    direction)`), its comparator reading (`getAnalogOutputSignal`), or a stock count of what the
    tab's OWN filter slot lets through (both lists) against the tab's one number, "Full", which
    lives in Keep's slot (`fieldApplies`). RS reads `level.getBestNeighborSignal(hostPos())`,
    the power reaching the PORT BLOCK from any side, not the target's signal alone: Symo's first
    try was a lever beside the pad and it read nothing ("couldnt get the non comparator modes to
    work"). A receiver emits like a lever: strong into its own target (`getDirectSignal`, per
    face) and weak all round the port block (`getSignal` → `signalAround`, the max of the faces).
    **Every port block is muted while any one reads** (`LinkPortBlockEntity.readPower/
    whileMuted`, `muted` is STATIC): a receiving pad strongly powers its block and that block
    reports the signal straight back, so a pad lighting a lamp read "powered" and held up its
    other links; a per-block mute then let a sending pad beside the lamp another pad lit (or
    beside that pad's block) read the lit lamp and hold the channel high forever after one button
    press ("the redstone lamp never turns off"). Pads never see pads; a repeater between them does.
    `aButtonPulseNextToTheLitLampDoesNotLatchTheChannel` is the rig. `onNeighborChange` (the comparator hook, horizontal only) wakes the
    network only for a pad whose link senses contents (`sensesContents`). No PU, no spread, no
    Amount or Delay on the tab; `filterSlot` is three a channel now (`FILTERS_PER_CHANNEL`, a
    save with two a channel is spread out in `onLoad`).
  - **Filters are an ITEM** (`FilterItem`, data component `FilterContents`), edited in its own
    window (`FilterMenu`) like every other mod's filter, then dropped into the pad's filter slot —
    one slot per channel and kind for items, fluids and the redstone tab's stock
    (`PortFace.filterSlot`, forty-eight slots at one spot, only the open tab's shown; energy has
    none). Crafting a set filter with a blank one gives two
    set ones; a set filter alone comes out blank (`FilterCopyRecipe`); a blank carries no component
    so blanks stack.
  - **A filter is ONE LIST, and each entry carries its own three switches** (`FilterContents.entries`,
    `Entry.receive/send/blacklist`): IN and EX for which of the pad's two jobs it counts in (a
    fresh entry is on both) and Allow or Block. Matching (`allowsItem/allowsFluid`): a Block
    entry that matches refuses; otherwise, once a kind has any Allow entries for the direction,
    one of them must match; a kind with none passes. The mode PER DIRECTION (`receiveBlacklist`/
    `sendBlacklist`) was the second shape and is gone: an old save's per-direction mode becomes
    the entry's own, and an entry on both directions whose modes disagreed is split in two
    (`FilterContents.load`, `VERSION` 3; the two-grid save before that, 32 slots with the first
    sixteen receiving, loads by position, and the bare list before that too). The window
    (`FilterMenu`/`FilterScreen`) is Symo's fourth shape ("in the top row i should see 1 button,
    when i click that i have 2 options: ItemStack or Tag"): the list page has ONE button, Add,
    which becomes Item-or-fluid and Tag (`ADD_BUTTON`, `ADD_ITEM_BUTTON`, `ADD_TAG_BUTTON`); an
    entry's page opens IN THE SAME WINDOW (`Page.ITEM`/`Page.TAG`, `itemEntryChrome()`/
    `tagEntryChrome()`, one slot `ENTRY_SLOT`, name, Match/Ignore NBT, IN, EX, Allow/Block, the
    data box or the tag rows, Back and Remove). Never a second screen: `FilterEntryScreen` was
    built and thrown out. Rows: `rowIcon/rowLabel/rowMarks/rowRemove`, buttons `entryButton(index,
    which)` with the ABSOLUTE index (the scroll offset is the screen's), `MAX_ENTRIES` 64. The
    entry slot is drawn by the client and is not a menu slot: a click reads `menu.getCarried()`
    (left the item, right the fluid inside it) and sends `SetFilterPayload`; JEI drops through
    `FilterGhostHandler` onto the Add button, a row's picture or the entry slot
    (`FilterScreen.acceptPicture`). An entry is an item OR a fluid; the channel's kind decides
    which entries the pad reads. Never "an entry speaks for both kinds".
  - **NBT is per entry, Pipez's way** (`Entry.matchComponents`, the Match/Ignore button on the
    item page): off by default, the item is the item; on, a stack must carry every component the
    entry was set with and may carry more (`Entry.carries`). The item page shows the components
    as saved AND EDITABLE, in a vanilla `MultiLineEditBox` over `ENTRY_DATA` (`DataComponentPatch.
    CODEC` → `NbtUtils.prettyPrint` SNBT; `FilterScreen.applyData` parses it back with `TagParser`
    + the codec, rebuilds the picture from a plain item or fluid with the patch on, runs
    `ItemStack.validateComponents`, and a non-empty patch switches Match on; the status text under
    the box says Applied or why not), WITHOUT another window ("WITHOUT opening another screen the
    NBT data of the itemstack/fluid gets displayed"; Symo: "the nbt data of the item should be
    editable in the filter selector"). The editor is refreshed from the server's entry only while
    it has no focus (`containerTick`, `dataShown`), and while it has focus every key is its own.
    The movers ask the sender's filter with `receiving=false` and each receiver's with `true`.
    The row's label says "· NBT" when it matches.
  - **An entry has a KIND** (`Entry.kind`: EXACT or TAG in the window; MOD, DAMAGED and ENCHANTED
    still load and match from older saves but are no longer offered, Symo's spec being "ItemStack
    or Tag"): a TAG entry treats the item or fluid as a picture and matches `stack.is(TagKey)`;
    the NBT flag is dropped and refused while it is a group. The key is one
    `Optional<ResourceLocation>` (the tag, or for MOD the namespace with an empty path), so a tag
    check is an interned `TagKey.create` lookup and no parse. The tag page has a box to TYPE a
    tag by name (`ENTRY_TAG_BOX`, an `EditBox`, Enter → `FilterScreen.applyTag`, '#' optional,
    red when it does not parse) and lists every tag the pictured item has under it
    (`FilterScreen.tagsOf`, six rows, wheel to scroll) with the chosen row framed. A typed tag
    needs no item: `Entry.ofTag(id)` pictures it with the first item under the tag, or with
    nothing, and a PICTURELESS TAG ENTRY IS NOT EMPTY (`Entry.isEmpty()` is false for
    `TAG` with a key; `matches(ItemStack)` skips the empty-picture guard for TAG; `Chrome.
    drawEntry` draws a '#'). **A tag entry's picture cycles** through the tag's members, one a
    second (`Chrome.drawTagMember`, `BuiltInRegistries.ITEM/FLUID.getTag` on the client, drawn
    only, never saved or sent; Symo: "cycle through the items that have the tag we are matching
    for? client side only in the UI's"). Setting a picture on the tag page keeps a tag already chosen, else
    takes the picture's first tag (`withFirstTag`). Symo: "there should also be the option to
    just put in a string as the matching tag not only grab them of an item".
  - **Keep works both ways**: a receiving pad stops filling its block at N, a sending pad leaves N
    behind (`surplusCount`). Same number, same button; it is XNet's insert-count/extract-count.
  - **Amount and Delay are the speed knobs; Batch is gone** (`PortChannel.amount/delay`; Symo:
    "batch and Amt doesnt make any sense, we should only keep amount and the new Delay value").
    Amount is what ONE send carries, sending or receiving (a receiver's Amount caps its intake);
    Delay is the ticks between this pad's sends. Both store 0 for "follow the tier": Amount 0 reads
    as the pad's `amountCeiling(kind)` (config base per kind × tier multiplier), Delay 0 as its
    `delayFloor()` (tier list), and nudging back to the limit stores 0 again so a tier fitted later
    is felt at once. Auto values draw MUTED. Energy has no keep, only amount and delay.
    Counts step by the kind's unit (`unitFor`: an item, a bucket, 1000 FE) up to `ceilingFor`.
    A typed value goes through `SetPadNumberPayload` to `LinkPortMenu.setNumber`, which clamps
    exactly as the arrows do (`setKeep/setAmount/setPriority/setDelay`).
  - **The four numbers are quiet text, not boxed steppers** (`fieldCaption/fieldValue/fieldUp/
    fieldDown`, a 2×2 grid under the switches: Keep, Amt / Prio, Delay): a muted caption, the
    value, two small arrows. A click on the value or caption opens it for typing (an `EditBox`
    over both, Enter sets, Esc leaves, red when it does not parse; `keyPressed` swallows every
    key while it is open or the inventory key closes the window); arrows step 1 / 8 (shift or
    right) / 64 (ctrl). Four boxed steppers in a row shouted louder than the things that matter
    ("still way too prominent").
  - Defaults that are settled: spread NEAREST_FIRST per channel per kind (it is what "send it over there"
    means), priority 0, keep 0, amount and delay at the tier's limit.
  - How it is actually built, and what not to undo:
    - **A pad is a door for whatever the block behind it pushes** (`PortPassthrough`, the port
      block's item, fluid and energy capabilities per face, `PortFace.pushItems/pushFluid/
      pushEnergy`): a push goes out at once on every channel where the link's PU switch is on
      (`PortChannel.pushEnabled`, its own switch beside IN and EX: EX is the timed pull, and a
      hopper behind a pad would be pulled from as well as pushing, Symo's point), to
      the receivers a send would reach (`LinkNetworkManager.receiversFor`), and the remainder
      goes back to the pusher in the same call. Free and unmetered (no FE, no Amount, no Delay,
      no keep-behind: the pusher's limits are the limits); filters, priority, spread, redstone
      and the receivers' keep still apply. The pattern-provider case, Symo's ask. It reports
      itself empty with no capacity and takes nothing out, and it must never grow a buffer. A
      push that lands on another port is refused there (`PortFace.pushing`), or two ports facing
      each other would recurse. **A simulated push takes no round-robin turn**
      (`receiversFor(..., simulate)`, `PortChannel.peekRoundRobinCursor`): NeoForge's hopper
      simulates before it moves, and a cursor that advanced for both put every hopper item on
      the same receiver ("round robin doesn't work for PU").
    - **Nothing about the network is computed twice while nothing changes.** A network keeps
      its loaded pads and injectors (`Network.loaded`, `loadedInjectors`), its senders per
      channel and kind (`Network.senders`) and, on every sending link, that link's sorted
      receivers (`PortChannel.receivers`, the links registered in `Network.cachers`); round
      robin reads through a `Rotated` view, never a copy. All of it is dropped
      by `Network.changed()` (join, leave, chunk load via `refresh`, unload via `unloaded` from
      `setRemoved`) or `Network.touch()` (`padChanged`: a switch, number, filter, upgrade or
      the block's redstone power). A NEIGHBOUR change only `wake`s, never touches: a redstone
      clock next to a pad would otherwise re-sort three hundred receivers every tick. A pass is
      then its senders' sends and nothing else, and a send asks receivers for a handler as it
      reaches them, never all of them up front (`LinkTransfer`, no sink list). Sorting keys
      are computed once per pad (`Ranked`), not once per comparison; item inserts start at
      the receiving link's `slotHint`, the slot that took the last item, so a chest with thirty
      full slots is not scanned from the top for every item. `threeHundredPadsAndFiftyPushers
      StayCheap` logs cold and warm numbers and guards them. What is left is per INSERT, about
      1.2 µs, and most of that is vanilla: every `setChanged` on a barrel walks its six
      neighbours for a comparator. A send costs its inserts, so even split over hundreds of
      receivers with a share of one item is the worst case (50 senders × 32 barrels ≈ 2 ms
      warm); the same pass nearest-first, the default, is 0.1 ms, a pushed item 1 µs. The
      simulate-then-move shape stays: two calls fewer per item is not worth losing items on a
      source that refuses a put-back.
    - **Ports never tick; the NETWORK ticks** (`LinkNetworkManager`, one `ServerTickEvent.Post`
      for the whole server). A network with under two loaded ports does nothing. A pass runs the
      senders whose Delay is up (`PortChannel.dueAt/sentAt`; an attempt restarts the clock whether
      anything moved or not) and the network sleeps until the soonest `nextSendAt`; a pass that
      moved nothing backs off to `LINK_IDLE_INTERVAL_TICKS` until an event wakes it. Never add a
      ticker to `LinkPortBlockEntity`, and never bring a pass interval back: the senders' clocks
      ARE the schedule.
    - **A network is a named, coloured thing a player makes.** The Linking Tool (`LinkingToolItem`,
      `onItemUseFirst`) on an UNLINKED pad or injector opens `NetworkPickerMenu`: type a name and
      Create, or pick one from the list. On a linked one it opens the block's own window, whose
      network line (pad: `NETWORK_BUTTON`; injector: `EnergyInjectorMenu.NETWORK_BUTTON`) picks
      (left) or leaves (right). The picker also has a row of sixteen dye cells that paint the
      current network (`LinkNetworkManager.setColour`, default `defaultColour(id)` from the hash);
      pads and injectors carry copies (`networkColour`) that the manager `broadcast`s, and every
      colour drawn — palette line, overlay, picker rows, injector line — comes from those copies,
      never from hashing the id client-side. `LinkNetworkManager.create/join/leave/summaries`; a
      network with nothing left on it is KEPT, setup and all, until its owner deletes it from the
      picker's cross cell (`LinkNetworkManager.delete`, `BUTTON_DELETE_BASE`, refused while
      anything is on it; Symo: "a network gets deleted when there are no ports left this should
      not be happening"). Nothing forgets a network on its own. No pad ever joins a network on its own.
    - **A network is its maker's** (`Network.owner/shared/members`, Flux Networks' model, Symo's
      ask): private by default, so only the owner and the players they invite see it in the
      picker (`summaries(player)`), put a block on it or open the window of a pad or injector on
      it (`canAccess`; `LinkNetworkManager.admits(player, id)` is the gate in `LinkPortBlock`,
      `EnergyInjectorBlock` and `LinkingToolItem` and tells the player on the action bar, the
      menus refuse every click and close through `stillValid`, the picker refuses a pick). An
      unlinked block is anyone's. Breaking is never refused, that is a claim mod's job: Symo's
      call ("breaking should be allowed tho").
      The owner alone paints it, flips Public/Private, invites, removes or hands it over
      (`isOwner`, `setShared`, `invite`, `removeMember`, `transfer`); operators (permission 2)
      count as owner everywhere, Flux's super admin, so a departed owner leaves nothing stuck.
      Pads and injectors remember who placed them (`PortFace.placer`, `setPlacedBy`), and one
      placed by someone the network no longer admits comes off it: loaded ones at once (`evict`
      after remove, transfer, claim, going private), the rest when their chunk loads
      (`refresh`); a pad nobody placed stays. An invite by name finds an online player or one
      the profile cache knows. The picker's list is a snapshot; an owner action sends a fresh one
      down (`PickerSnapshotPayload`, `NetworkPickerMenu.update/version`), NEVER reopens the
      window: a reopen closes the old one first and the client grabs the mouse in between, so
      the cursor jumped to the middle of the screen on every click. A network with no owner
      (from before there were owners, or made by a test) is everyone's, to use and to run, until
      someone clicks Claim (`claim`).
    - **The overview is read-only and rides the opening packet** (`NetworkOverview`, built by
      `LinkNetworkManager.overview(id)`; `NetworkOverviewMenu`/`NetworkOverviewScreen`): the eye
      at the end of every picker row (`NetworkPickerMenu.rowView`, `BUTTON_VIEW_BASE`) opens it
      for anyone the network admits, joining nothing; Back reopens the picker with the same
      pos/face/returnToBlock. Every pad on the record, loaded or not (an unloaded one is a
      position and a face and nothing else), its reach as a dot, its switched-on links as a kind
      letter and arrows in the channel colour; injectors with their FE; channels that carry
      something with spread per kind and sender/receiver counts of the loaded pads. One scrolling
      list of fifteen lines, built once in `init`. Symo: "a network overview would also be
      nice". A pad's label is on its line (`NetworkOverview.Pad.label`, seven fields so a
      hand-written stream codec). **Clicking a pad or injector line flashes the block** red
      through walls for 7.5 s (`LinkOverlayRenderer.highlight(GlobalPos)`, `HIGHLIGHTS`, a
      quarter-second flash, drawn tool in hand or not: the renderer's early return is on "no
      tool AND nothing to flash"). Symo: "if i click on an injector or Port that it gets
      highlighted for like 7.5 seconds flashing red (through walls)". **The row answers the
      click**: a clickable row lights on hover, the click makes the button sound, and while the
      block flashes the row wears a red frame and dot blinking in step (`LinkOverlayRenderer.
      isHighlighted/flashOn`) with "Flashing in the world now" in its tooltip. Symo: "should
      provide some feedback that the player clicked and the block is highlighted".
    - **Labels: same label, same network, same settings** (`PortFace.label`, `LinkNetworkManager.
      relabel/publish/labels`, `Network.labels: Map<String, CompoundTag>` saved under `Labels`).
      Super Factory Manager's idea, Symo's ask. The label box stands where the title was
      (`LinkPortMenu.LABEL_BOX`, the screen drops `renderLabels`' title): an `EditBox` with the
      network's labels listed under it as you type (`labelRow(i)`, prefix match over
      `menu.labels()`; the list rides the opener: `writeOpener(buffer, port, facing)` writes it,
      the client ctor reads it). Picking or typing a label the network KNOWS asks which way
      (`LABEL_CAPTION` names it, `LABEL_PULL_BUTTON` "Take its settings" / `LABEL_PUSH_BUTTON`
      "Push mine to it" → `SetPadLabelPayload.push`); an unknown label takes this pad's settings;
      empty takes the label off. **The list is a POPOVER that owns the window while it is up**
      (`LinkPortScreen.drawLabelList`, `popoverPanel`): its own `Chrome.panel` over the palette,
      the hovered row lit in ACCENT with a SELECTED_EDGE frame, no tooltip from anything under
      it (`render` returns after drawing it), and a click anywhere but a row, an answer or the
      box closes it and reaches NOTHING underneath (`mouseClicked` returns true); Tab takes the
      first suggestion. Symo: "needs a background and good hover stuff which blocks interaction
      with the stuff behind". Every by-hand change on a labelled pad publishes (`LinkPortMenu.afterChange`
      after switches, nudges, `setNumber`, `importText`, and the `FilterSlot`/`UpgradeSlot`
      `setChanged` overrides): the pad's `PadSnapshot` becomes the label's and every other LOADED
      pad wearing it takes it (`applyTo(peer, source, false)`); a joining or chunk-loading pad
      adopts the label's stored snapshot (`adoptLabel` in `join` and `refresh`, skipped when it
      already matches). Filters and upgrades come OUT OF THE PLAYER'S INVENTORY (`takeBlankFilter`,
      `take`), never minted: what could not be given is counted (`PadSnapshot.Applied`, synced as
      `DATA_MISSING_FILTERS/UPGRADES`) and the label box shows a red banner until clicked
      (Symo: "otherwise show a banner in the title bar that X Ports dont have filters ... this
      should also apply to all upgrades").
    - **One snapshot for the label, the card and the clipboard** (`PadSnapshot`, a `CompoundTag`:
      Label, Network, KindMasks, Channels as `PortChannel.save()`, Filters as encoded
      `FilterContents` per slot, Upgrades as saved stacks per slot). `applyTo` resets every link
      (`PortChannel.reset`, quiet), loads, then `sanitize`s (every number clamped as the arrows
      clamp; off the clipboard any number is anyone's), rewrites filters and upgrades, sets the
      label, joins the network when asked and allowed, and `changed()`s once. The config card
      holds it as `ModDataComponents.PAD_CONFIG` (crouch-use on a pad copies, `onItemUseFirst`,
      admits-gated); a plain use with one on the card asks on the client (`PadApplyScreen`, vanilla
      widgets) and `ApplyPadConfigPayload` → `ConfigCardItem.applyPad` does this pad, or a BFS over
      the six neighbours of port blocks that have a pad on the SAME FACE (Symo: "only the same
      face direction is good"), cap 256, refused networks counted; use at nothing (`use`,
      `getPlayerPOVHitResult` MISS) blanks the card (Symo: "clearable when clicked in the air").
      Export/Import are a BAY off the window's right edge, Mekanism's way (`EXPORT_TAB`/
      `IMPORT_TAB`, `BAY_WIDTH` 24, in `chrome()`, the layout test's width is `WIDTH + BAY_WIDTH`):
      Export is `BUTTON_EXPORT` → `ClipboardPayload` → `ClientHooks.setClipboard`; Import reads
      the client clipboard → `ImportPadConfigPayload` → `LinkPortMenu.importText` (SNBT via
      `TagParser`, not JSON: a JSON round trip loses byte arrays and typed lists; text that is
      not a pad is refused whole). Gametests: `aLabelIsOneSetOfSettingsForEveryPadWearingIt`,
      `theCardCopiesAPadAndFloodsTheSameFaceAcrossTouchingPortBlocks`,
      `theClipboardTextIsThePadAndComesBackClamped`. Two test traps: the hotbar's slot 0 IS the
      main hand (an inventory `setItem(0, …)` replaced the card being tested), and the gametest
      box is barriers on every side within reach, so "clicked in the air" needs
      `BLOCK_INTERACTION_RANGE` shortened.
    - **Reach is measured from the network's CENTRE**: the mean of its pads' block centres in its
      home dimension (the one with most pads), recomputed on every join and leave
      (`computeCenter`); every pad carries a copy (`PortFace.networkCenter`) and a pad is in reach
      when `withinReach(pad, centre, range)`. A pad in another dimension needs the top range tier.
      Range upgrades and the unbound card stay per pad.
    - **A held tool shows the network in the world** (`LinkOverlayRenderer`,
      `RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKS`, see-through line render type in
      `ModRenderTypes`): a box round every loaded pad AND injector through walls, its network
      name, the centre and a line from it to each pad (injectors get a box and a name, never a
      line: they are not part of the reach), and for every distinct `range()` among the pads
      seen a globe of lines round the centre (six meridians, five parallels, `sphere`) with a
      reach label at the top: the sphere a pad has to stand inside (Symo: "we also need to render
      the maxrange of the Ports"). **The globe is always depth-tested** (`RenderType.lines()`,
      ended before the depth test goes off; its text through `SOLID_TEXT`, a buffer source of its
      own, because the shared one is ended with the test off): three circles through walls "make
      me real dizzy since they are on my screen at all times". What is drawn is four switches in
      the CLIENT config (`ClientConfig.OVERLAY_PADS/LINES/REACH/THROUGH_WALLS`, saved on flip),
      opened by crouch-using the tool (`LinkingToolItem.use` and `onItemUseFirst`, client side
      only via `ClientHooks`, vanilla widgets in `LinkOverlayOptionsScreen`). Pads and injectors register themselves client-side on
      load (`LinkPortBlockEntity.clientPorts`, `EnergyInjectorBlockEntity.clientInjectors`);
      never scan chunks for them. **Through walls needs `RenderSystem.disableDepthTest()` by
      hand** round the batches: NeoForge fires that stage inside the section-layer pass before the
      layer clears its state, so the depth test is still on, and `RenderStateShard.NO_DEPTH_TEST`
      is a shard that does nothing in setup. A see-through render type alone drew nothing behind
      a wall, and it took a client run to see it.
    - A port holds no inventory, tank or buffer — it reads and writes the target through that
      block's own sided capability via `BlockCapabilityCache`, so a machine's face rules still
      apply and a pad never routes around them.
    - **Four spreads** (`Distribution`, per channel per kind, a cycling button of four, which is
      the cap): nearest-first (default), round robin (each send whole to the next in line),
      random (whole to one it rolled) and even split (a share to every receiver, the only one
      that costs an insert per receiver). The roll and the step happen on the REAL push, a
      simulated one only peeks (`takeRandomCursor`/`peekRoundRobinCursor`). Even split keeps
      ordinal 0: it was the first mode and old worlds saved it. Symo's naming: "round robin" is
      what every other mod means by it, "even split" is what ours used to call that.
    - **Even split shares what is on offer, not the budget.** A pass with room for 160 items and
      a chest holding 64 must not hand all 64 to the first receiver; the share comes from
      `min(budget, available)`. Same in all three movers.
    - Every mover simulates, moves, then puts back whatever the destination refused, and fluid
      drains **by `FluidStack`** rather than by amount so a multi-fluid tank cannot hand back the
      wrong one.
    - Filter slots are ghosts: `mayPlace`/`mayPickup` false plus a `clicked()` override that
      copies the carried stack. Never let a vanilla move path touch them.
- Standalone playable, no hard deps. AE2 = recommended companion (crafting intentionally expensive), not required.
- Progression gating: hybrid — tiers gate by material cost only (rushable), EXCEPT endgame
  (Tesla Array) which requires one End-tier milestone ingredient.
- Materials: lean + need-driven — nickel, lead, silver + machine-made alloys; register a material
  only when a concrete recipe needs it. Never add speculative ores.
- Player tools: the **Linking Tool** (linking only — the wrench and pickup modes were removed as
  "useless as fuck", and a mode switch went with them) + separate cheap early-game config card for
  machine AND pad config copy/paste (one component at a time, `MACHINE_CONFIG` or `PAD_CONFIG`;
  use at nothing blanks it). Breaking a machine spills everything it held, always
  (`MachineBlock.onRemove`); a pickaxe that ate a player's upgrades would be a bug.
- Textures = **two-stage pipeline**: Claude writes scripted 16x PNG *mockups* (shared palette,
  single light direction, correct dimensions) so code/datagen never block on art; Symo redraws
  finals by hand in Aseprite (+ Blockbench for 3D/tiling preview). Mockups are placeholders —
  never treat them as shippable. Shared palette file lives in `art/palette.gpl`.
  No AI-diffusion image generation (mushy, off-grid, anti-aliased — wrong for 16x).
  - **Never copy another mod's textures.** Credit is not a licence: art is separately copyrighted
    and most packs' favourite mods reserve it even when the code is MIT/LGPL. Even where a licence
    does allow it, two items that look identical in one pack is a player-facing bug.
  - Shapes small enough to see at once are written as a pixel map (`Canvas.paint`, digits index the
    ramp) rather than derived from rules — a heap of powder has no formula, and procedural grain
    scatter turns into a visible lattice at 16x.
- CI: GitHub Actions (`.github/workflows/build.yml`) runs build + gametests on every push/PR;
  repo is public → Actions free/unlimited.
- Integrations live in `compat/<mod>/`, `compileOnly` + `localRuntime` in `build.gradle`, versions in
  `gradle.properties`. Never a hard dependency: those classes are loaded by the other mod's own
  plugin scan, so an absent mod means an unloaded class. JEI and Jade are in; EMI planned.
  - GuideME is `localRuntime` only, never compiled against: the guide is data
    (`assets/actualgenerators/guideme_guides/guide.json`, pages under
    `guides/actualgenerators/guide/*.md`), and the guide book's recipe is datagen'd by id lookup
    (`ModRecipeProvider.guide`) behind `ModLoadedCondition("guideme")`. Every feature gets a page;
    a number in a window with no page explaining it is a gap (Symo: "make a detailed guide on
    everything").
  - Jade: expose the capability and let Jade's universal providers do the work — the FE bar is free
    because machines expose `Capabilities.EnergyStorage`. Only write a provider for what Jade cannot
    know (progress, the Crusher's calibration state).
  - A readout Jade and a menu both want lives on the block entity, not in the menu
    (`MachineBlockEntity.progressFraction()`); a tooltip must not re-derive what an arrow already knows.
  - Jade shows the same bars the window does: the ramp on EVERY machine (generators included —
    overclock or warm-up, same bar, different label) and the work bar where there is work. Draw them
    with `IElementHelper.progress(...)`, not as text lines, and never re-derive a number client-side
    that the server already computed.
- Machine concepts stay novel too: Resonance Crusher (frequency-tuned ore processing, tune 1×/ore,
  rides config card), Surge Bank (burst multiblock storage, idle leak) vs Crystal Charger
  (FE⇄Flux Crystal items; craftable-ingredient energy), Field Projector (holographic work zones, no machine rows).
  - Flux Crystals = ONE item exposing standard FE ITEM capability (fixed capacity) → any mod's
    battery slot/charger works with them; never a proprietary cell. Capability active only at
    stack size 1 (battery-item convention); full & empty states have identical components so each
    stacks. Recipes consume full crystal, return empty via crafting remainder (milk-bucket style).
    Crystal Charger = charge/discharge + accepts partials to normalize them.
    AE2 works natively: export bus feeds it furnace-style, processing pattern empty→full =
    autocraftable energy. NO AE2 integration code.
    Crystals OPTIONAL always — energy transport = Logic Ports; crystals never required as medium.
    Charger works ONE crystal at a time and outputs each as it finishes, so a stack trickles
    out rather than landing in a lump; ENERGY upgrades only — a lossless transfer has no speed
    to buy, only throughput, and a batch upgrade would only delay the output. Mode button
    (`hasModeButton`) flips charge/discharge; consume-first like every other machine — the crystal
    leaves the input slot before an FE moves, and its charge is written back exactly, so breaking
    the block mid-charge can neither mint energy nor lose it.
  - Flux Coupler item: right click opens its own menu (input / working / output crystal slots +
    energy upgrade slot + on/off button); charges all FE items in player inventory (any mod's, via
    item capability). NO internal buffer — it draws straight out of the crystal in the working
    slot, one crystal at a time, and retires each spent one to the output slot where empties stack.
    The working slot is a readout: no insert, no extract; the coupler feeds it from the input.
    Base FE/t comes from the CRYSTAL (`FluxCrystalItem.transferRate(stack)`), not from the coupler
    — so a higher-tier crystal is faster everywhere and nothing that draws on one needs to learn
    about tiers. Energy upgrades scale that; the upgrade slot holds up to `maxUpgrades(ENERGY)`.
    Throttled tick; nothing is spent unless something is asking. Loose crystals and other *active*
    couplers are never charge targets.
  - **Item-hosted menus must NOT put `SlotItemHandler` over a component-backed handler.** Vanilla
    `moveItemStackTo` edits the stack a slot hands out IN PLACE; a `ComponentItemHandler` hands out
    copies, so shift-clicking silently destroys the items (it ate 47 crystals once). Pattern that
    works: `SimpleContainer` holding live stacks, write straight back to the component on every
    change, reload from the component in `broadcastChanges()` so the item's own ticking is not
    clobbered. `ItemContainerContents` drops trailing empty slots, so ALWAYS guard reads with
    `slot < contents.getSlots()`.
- **Passive generators take energy upgrades only** (`acceptsUpgrade`): a generator paid by the
  world — depth, weather, light — has no fuel to trade away, so speed/overclock would conjure FE
  rather than burn faster for less. Refuse those upgrades at the slot; don't accept-and-ignore.
  Fuel-burning generators keep the normal sublinear speed scaling.
- **Every machine shows the ramp bar**; passive generators warm up instead of overclocking
  (`usesWarmupRamp`). Output starts at `generatorWarmupFloor` of the rating and climbs to exactly
  the rating — never past it, so running continuously is rewarded without conjuring FE. Keep the
  rating as `ratedEnergyPerTick()` and the live figure as `generatedEnergyPerTick()`; tests assert
  against the rating so the ramp can't hide a balance change. Event-driven generators hold the ramp
  open for a configured window per event (Impact Dynamo's `warmTicksPerImpact`) rather than ticking.
- **The progress arrow is framework, not per-screen**: a window says `hasProgressArrow()` and
  `progress()`, and `MachineScreen` draws and explains it — hovering gives the percentage, and
  "Nothing in progress" when idle. Screens never blit their own arrow.
- **Every window publishes its boxes server-side**, not just machines: `MachineLayout.Box` lists
  live in the menu (`FluxCouplerMenu.chrome()`), so `noWindowDrawsTwoThingsInTheSamePlace` can
  check them on a dedicated server, where client classes cannot load. A new window with chrome of
  its own adds itself to that test; a screen with hardcoded coordinates the test cannot see is
  a screen that will overlap sooner or later.
- **Upgrade slot tooltips are numbers only** — green for what the upgrade buys, red for what it
  costs (power drawn for a consumer, yield per item for a generator). No prose descriptions; the
  item tooltip is where the sentence explaining the type lives.
- **Upgrade slots are one-per-type, indexed by `UpgradeType.ordinal()`**, and each slot caps at
  `tuning().maxUpgrades(type)` so surplus upgrades are refused rather than wasted. Menus show only
  accepted types; empty slots draw a faded hint sprite from the GUI sheet at (176,136)+16*ordinal.
  Never add a generic "any upgrade" slot back. The tier slot is its own thing (`MachineMenu.TierSlot`
  at (8,34), pad `PortFace.SLOT_TIER`), hinted from (240,136).
- Generator implementation notes:
  - Spawner Siphon reads generic `BaseSpawner` stats (delays, counts) — never hardcode vanilla
    values, so Apothic Spawners upgrades raise FE output for free.
  - Photovore eats light-source blocks (event-clean); NO light-engine modification/darkness aura.
  - Hydrostatic Generator counts only *still* water above it (sources incl. waterlogged blocks),
    capped by config; measured on a game-time interval + immediately on neighbour change, never
    per tick. Flowing water must never count or one bucket beats a flooded shaft.
  - Photovore food = `actualgenerators:photovore_food` block tag (whitelist, datapack-editable).
    Never "anything with light emission" — that eats beacons, portals, lava, and fire (which
    regrows off netherrack = free energy). Its area scan is a poll: gate it behind an empty cache
    + buffer-not-full + `scanIntervalTicks`, cache results nearest-first, never scan per tick.
  - Resonance Crusher: recipes are a datapack type (`actualgenerators:crushing`,
    ingredient + count + result + optional ticks). A material's frequency is *derived from the
    recipe id*, never stored, so a pack's new recipe has one for free. The first run on an
    unknown recipe is a calibration — `calibrationMultiplier` times as long, no bonus — and then
    the recipe id joins the machine's `learned()` list, which `MachineConfigSnapshot` copies.
    Bonus yield is a config permille, never a recipe field: recipes say what a material *is*,
    config says what it is *worth*. Batch room must reserve the bonus too (`result + 1` per item)
    or a full slot loses items. Progress is keyed to the recipe id — swapping the input resets it,
    or a cheap crush would finish an expensive one.
  - Crystal Resonator: player-placed end crystals beam at it (heal-target masquerade), no dragon
    involved; overdraw → detonation.
