# Changelog

## 1.3.0 - Hands-on stations, Genetic Animals

### Upgrading from 1.2

- **Network protocol 3.** Client and server must both run 1.3.0: Forge refuses a 1.2 client on a 1.3 server and the
  reverse. The 1.2 screen and session packets are gone; the stations now send bounded work commands and their results.
- **Worlds convert on load, once and in place.** Loading a converted station again changes nothing more.
  - **Tanner's Rack** (save format 2): empty racks and drying hides load as they were. A raw hide mounted before 1.3
    gets untouched fleshing work: a fresh hide to scrape, nothing lost or granted.
  - **Tanner's Vat** (save format 3): water, tannin, every hide and its clock are kept, and hides steeping before 1.3
    keep their one paddle-tending credit. The vat now has a facing (it turns its model and working view only); vats
    placed before 1.3 face north.
  - **Leatherworker's Bench** (save format 2): materials a 1.2 bench still held for an interrupted stitching session
    (its escrow) move to the bench's output pad, once, for the player to collect. A 1.2 stitching session is not
    resumed (sessions ended when their player left).
  - **Items** are not converted: hides, leather and finished pieces keep their data.
- **Config.**
  - Server: a new `work` section with `challengeTiming` (false: fleshing has no time limit), `vatTendingCredit` (0.1),
    `leaseIdleSeconds` (90), `allowAssistedProfile` (true), and `overrideQualityWeights` (false) with `cuttingWeight`,
    `assemblyWeight` and `stitchingWeight` (0.25, 0.15, 0.6). `minigames.fleshingTimeSeconds` and
    `maxAssistTimeMultiplier` now apply only with `challengeTiming = true`. `readyDelayMs`, `timingToleranceMs` and
    `maxLatencyCompensationMs` are no longer used but still load.
  - Client: a new `focus` section (camera mode, transition time, field of view, sway, hold or toggle gestures, pointer
    sensitivity, tool smoothing, hands or tools only, supporting hand, edge switching, assisted profile, large targets,
    hints, tension gauge, Jade while working, favourite patterns). `accessibility.largeMinigameMarkers` still loads;
    `focus.largeTargets` replaces it.
- **Datapacks.** Bench recipes load unchanged. A recipe may name a `work_pattern`
  (`data/<namespace>/immersive_tanning/work_patterns/<name>.json`, schema version 1). Without one, the recipe uses the
  work pattern named like its seam, else its 1.2 seam pattern adapted, else the generic wrap; 1.2 seam patterns still
  load. A pattern that does not parse is skipped with its reason in the log and in `/immersive_tanning report`, and its
  recipes fall back the same way.
- **API.** Every public type and method is kept. `HideFleshedEvent.isTimedOut()` is true only under challenge timing;
  `LeatherworkStitchedEvent` is posted when the piece is tied off and is never timed out. `ITanningAssistSource`
  lengthens fleshing only under challenge timing. Recipe gates are asked when a pattern is offered, when it is prepared
  and again when the piece is tied off.

### Hands-on stations

Fleshing, finishing and leatherworking are now done in the world, at the station, instead of in separate screens. Using
a station leans the view in to the work (`cameraMode`: cinematic, reduced motion or stationary). The real tools and
materials are used, and the work stays on the station when you leave.

- **One way to work.** Press and drag (or, with `gestureMode = TOGGLE`, click to put the tool down and again to lift it)
  on the part of the station you mean. A label beside the pointer says what will happen. The keyboard does everything
  too: Tab cycles what you can work, the arrow keys move the tool, Space presses, Q and R rotate a carried panel, F
  finishes a hide roughly, B opens the material drawer, ] and [ move to the next or previous station, V changes the
  view, and Esc or the inventory key leaves. All of these keys can be rebound. Sneak-use still does the quick action
  without leaning in. Automation still never fleshes, finishes or stitches.
- **Tanner's Rack, fleshing.** The raw hide lies over the beam. Scrape the flesh off with the Tanner's Knife; it comes
  away where the blade touches, along the grain in one pass and across it in two. There is no time limit unless the
  server turns on `work.challengeTiming`. Once 30% of the hide is clean, "Finish roughly" (F) scores it as it is.
  Leaving keeps every scraped cell, and a hide taken off half-scraped carries its work with it. The clean hide keeps its
  worked look on the beam until it is picked up. With more raw hides in your inventory, a click on the empty beam lays
  the next one over it.
- **Tanner's Vat.** Water, bark and scraped hides go in through the opening; the material drawer brings what you carry
  to hand. A ready hide is lifted out on the hook. Hovering a hide says exactly what it waits for (water, more bark) or
  how long it has left. One slow sweep with the paddle through the liquor moves every steeping hide 10% of its tanning
  time closer to done, once per hide ever (`vatTendingCredit`; 0 makes it only for show). Untended hides finish at the
  normal time with the same quality. Tannin, capacity, start order, draining and refilling follow the 1.2 rules exactly.
- **Tanner's Rack, finishing.** Lace a wet hide into the upper frame and move the tension lever to a labelled detent:
  Supple (slack), Harness (even) or Hardened (taut). Each detent shows what the finish does. Then do that finish's short
  treatment: smooth Supple with the slicker, draw the Harness cords to even tension, or burnish Hardened with the block.
  Click the lever (or its hub) to seat it, which commits the finish and starts drying. Until it is seated, changing the
  detent is free. Sneak-use with a wet hide repeats your last finish, as before. Taken off before it is dry, a hide
  keeps its finish and its drying so far.
- **Leatherworker's Bench.** The pattern book, at the side of the view, lists what you can make first. It has search,
  categories and favourites, and shows each pattern's cost, the leather group it would be cut from (best Integrity
  first) and any progression refusal before anything is taken. Results that share a name carry their variant, for
  example "Dragon Scale Chestplate (Red)". "Prepare" lays the leather and components on the bench and nothing else.
  - Cut each panel with the knife along its line; a cut advances only near its line, from where it stopped.
  - Carry the freed panels onto the jig and seat them on their outlines (Q and R turn them).
  - Stitch each seam: press the awl through the lit holes and draw the thread straight away from the seam. Slack and
    puckered stitches show as such and cost Workmanship; the last stitch can be loosened and redone, up to three
    times, at a capped grade.
  - Tie off. Workmanship weighs cutting, assembly and stitching (25/15/60 by default, set per pattern).
  - The piece, its offcuts as Leather Scraps and any component remainders wait on the output pad.
  - "Make another" prepares the same pattern from the same leather group.
  - Before the first cut, "Cancel" returns the exact materials. After it, "Take the workpiece" gives an Unfinished
    Leatherwork item that carries the whole job; put it on any bench to carry on.
  - Bench repair: hold a damaged leather piece and click the bench's mat. A short patch is stitched from Leather
    Scraps (up to eight), and the piece keeps its finish, quality, enchantments, dye and maker.
- **Ownership is exact.** Materials move only between your inventory, the bench's ledger and the output pad. Each
  stage has one route out:
  - a refund before the first cut;
  - the workpiece after it;
  - recovery when the recipe is removed or a progression gate now refuses it;
  - one drop route when a bench is broken;
  - the output pad for the finished piece.

  A refund or workpiece that does not fit in your inventory drops at your feet. Collecting from the pad takes what
  fits and leaves the rest there. Tool wear, experience, events and advancements happen once, when the piece is tied
  off.
- **Shared stations.** One player works a rack or bench at a time (anyone may tend a vat); others watch the accepted
  strokes, tools and poses. A station is let go after `leaseIdleSeconds`, on logout, death, dimension change or when it
  is broken, and the work stays. "Hand off" passes a bench job or a half-scraped hide to the next player who works it.
  Players joining later see the same work.
- **Server-authoritative.** Clients send only bounded intents: points along a stroke, a press or a release. The
  server checks reach, sight, tools, rate and path length, replays the strokes and scores them. No packet carries a
  score or an item.
- **Sound and look.** Fifteen new sound events, each with a subtitle: rough scraping, wet leather, lacing, smoothing,
  burnishing, seating the lever, pouring, the paddle, lifting hides, setting a panel down, the awl, thread drawn tight,
  tying off, loosening a stitch and the pattern book. Scraping, bark and cutting have new recordings too (18 sound
  files, synthesized by `tools/generate_sounds.py` from `art/tanning_sounds.py`). Hands follow the operator's skin,
  sleeves, arm model and main hand, with a tools-only option. Contact effects are a few small flecks, and nothing
  flashes.
- **Accessibility.** Reduced motion and stationary views, hold or toggle gestures, full keyboard control, larger
  targets, an assisted profile (wider guides and pull band, the same best quality; the server may refuse it), a labelled
  Slack / Even / Puckered gauge, high-contrast plates and hints. None of them changes scoring.
- **Jade** shows each station's stage, its owner or operator, what a vat hide waits for and drying progress. It stays
  out of the working view unless `jadeInFocus` is on. **JEI** shows each bench recipe's work (cuts, panels, stitches)
  and "Prepare at bench" lays out a pattern from its page when you are working at a bench.
- **Removed:** the fleshing, finish-select and stitching screens, the rhythm stitching run and their session packets.

### Genetic Animals

- Fixed: Genetic Animals' cows, mooshrooms and moobloom dropped Binding Leather and never a raw hide, and its pigs and
  rabbits never a hide either, because Immersive Tanning knew them by neither their ids nor their tags (`#forge:cows`,
  not `#forge:cow`). Its sheep were matched through `#forge:sheep`, but from the whole drop list, carried leather
  included, and its llamas' deaths failed (next item).
- Fixed: killing a Genetic Animals llama threw a NullPointerException inside the hide loot modifier, which assumed every
  mob has a loot table id (its llama has none). The modifier now checks for that.
- Its cows, mooshrooms, moobloom and llamas drop large hides, its sheep and pigs medium, its rabbits small; its
  chickens, turtles and axolotls drop none, and its unfinished horse (no renderer, spawn egg or spawning in 0.11.12) is
  left out. Hides are decided once at its death, from a record of which drop came from the animal's own death loot and
  which from what it carried or wore.
- **NATIVE (default):** the leather and rabbit hide the animal really drops become raw hides: each leather one
  leather's worth, in the fewest hides up to the animal's size (a cow's 4 leather is a large and a small hide), each
  rabbit hide a small hide. No skin, no hide: wool coats, burning animals and pigs give none. Looting is only what
  Genetic Animals itself gives.
- **TANNING:** every adult drops its species' hide whatever it drops, pigs included; juveniles by Genetic Animals' own
  adult age drop none; a burning animal keeps its hide; `lootingAddsHides` applies once.
- Carried and worn items (chest contents, saddle, bridle, collar, blanket) drop exactly as they were: carried leather
  is never made into hides or Binding Leather, and a carried raw hide never stands in for the animal's own.
- `doMobLoot` off, cancelled drops, a hide another mod removes, and drops handled twice give no extra hide. A
  higher-priority `"size": "none"` or `"count": 0` stops the hides and keeps the animal's own skin.
- Butchery: only a carcass that actually appears at the death stops the hide (Butchery 5.3.1 makes one of Genetic
  Animals' sheep, which are in `#forge:sheep`, and of none of its other animals); a cleaver alone never costs the skin.
- Its leather collar (1 leather) and leather bridle (4 leather, 2 iron nuggets) are stitched at the bench on a new strap
  seam, with their crafting recipes routed there (`compat.geneticAnimals.leatherTackAtBench`). Saddles, cloth tack and
  the ringed, belled and decorated pieces keep the trades they come from.
- Bridles and saddles put on an animal by hand keep the stack used: finish, quality, maker, name, colour and
  enchantments. Genetic Animals rebuilt them from item and colour (a vanilla saddle from nothing).
- Its leatherworker, shepherd and wandering-trader offers, added straight to the merchant, ask for Binding Leather; its
  offers saved earlier convert when their trades are opened. Every other field of the offer is kept.
- `compat.geneticAnimals.enabled = false` leaves its drops, tack and equipping to Genetic Animals (no hides, the
  generic plural tags included); Binding Leather's own settings still apply.
- Verified against Genetic Animals 0.11.12 (SHA-1 `7809ea135dd2abbb18060d832032b590ce0d8294`); its hooks are checked
  as its classes load, and another release keeps its own behaviour wherever a hook does not apply. The full guide is
  `docs/compat/genetic-animals.md`.

### Everything else

- Trades converted to Binding Leather keep their reward-experience flag and special price; they were reset before.
- Saddles, horse armor and animal tack made at the bench no longer claim durability, field repair or trait effects in
  their tooltip; they say their finish and quality are a record of their making. JEI's bench output says the same.
- `/immersive_tanning inspect <entity>` and `inspect type <entity type>` (operators) show how an animal's hides are
  decided; `/immersive_tanning report` also lists skipped hide sources and other mods' integration.
- New tag `#immersive_tanning:carcasses` (Butchery's carcass items, optional entries).

### Tools and tests

- `./gradlew runGameTestServer -PgeneticAnimals` runs the GameTests with the pinned Genetic Animals release (Modrinth maven,
  hash-checked by the `geneticAnimalsJar` task, never compiled against or shipped). They spawn its animals through its
  own spawn path and kill pairs of identical animals with the integration off and on, so each hide is checked against
  the skin Genetic Animals really dropped; plus TANNING mode, the drop settings, cargo, one result per death, carcasses,
  tack on the animal, its offer factories, the bench, and its recorded inventory against the running registries.
- The pack test takes `--genetic-animals`, `--extra-mod` and `--pack-mods` (a small instance of named pack jars) and a
  `geneticanimals` group: real kills, cargo, tack and trades with the reobfuscated jar.
- The client smoke test works every station through the focused view with real mouse and key events on the parts as
  drawn (`FocusDriver`, `FocusTour`). It covers:
  - fleshing to "Finish roughly", the lever and a seated finish, the paddle and hook;
  - a vat that says what a hide waits for;
  - the pattern book (labels for shared names, GUI scale 3), a pair of boots cut, seated, stitched with a held Space
    and the mouse, tied and collected;
  - the stationary and reduced-motion views.

  The playthrough now plays the whole tannery the same way. `-PjoinServer=host:port` makes the agent join a running
  dedicated server instead and work a rack there (`ServerJoinCheck`).
- New GameTests (95 in all) cover:
  - the station frame and pointer rays in every facing;
  - fleshing that is identical however its points are batched, and survives saving;
  - work patterns, valid and malformed;
  - oversized commands, leases and replays;
  - workpieces and recovery, the ledger, breaking a bench, bench repair;
  - tending once per hide, drying paused off the rack;
  - every save migration;
  - a release that brings no new point.
- `tools/generate_sounds.py` writes and checks the station sounds from `art/tanning_sounds.py`
  (`art/tanning_sounds.manifest.json` holds their hashes).

## 1.2.0 - Dragon scales at the bench, a knife that strips

### Upgrading from 1.1

- **Network protocol unchanged (2)**; the recipe format on the wire is the same too. Update both sides anyway: a 1.1
  client would show ordinary leather numbers in scale-backed pieces' tooltips.
- **Config.** The new `scaleBacked` section takes its defaults. `replaceLeatherEquipmentRecipes`, `recipeBlacklist` and
  `modBlacklist` now also cover the recipes a bench recipe names in `replaces`; changes apply at the next `/reload`.
- **Saved items.** Nothing is migrated and every piece keeps its data. A piece in `#immersive_tanning:scale_backed`
  that a 1.1 datapack made at the bench follows the scale-backed values from now on.
- **Datapacks.** Bench recipes take an optional `replaces`; recipes without it load and behave as before.

### Tanner's Knife

- Fixed: using the knife on a log did nothing. Forge hands the stripped log only to a tool that can perform the strip
  action, and the knife never said it could. It now can (and does no other axe work, such as scraping or waxing), so
  vanilla and modded logs strip again for `barkPerLog` bark and one point of wear, keeping the log's direction.
- The server alone strips the log and then pays the bark, the wear and the advancements; the client only predicts the
  strip. A strip another mod refuses, a log that does not change, a stripped log and any other block give nothing.
  Creative strips without wear; bark that does not fit is dropped at your feet.
- Farmer's Delight's cutting board now takes the knife for its log recipes (a stripped log and tree bark), as it takes
  an axe. Its tree bark still feeds the vat.

### Ice and Fire dragon-scale armor

- With Ice and Fire installed, its dragon-scale armor (twelve colours, four pieces) is stitched at the Leatherworker's
  Bench from finished leather (the backing: helmet 1, chestplate 2, leggings 2, boots 1) and Ice and Fire's own scale
  count of one colour (5, 8, 7, 4). The piece is Ice and Fire's own armor, with its model, colour, armor and breath
  protection; the backing gives finish, Integrity and trait, the stitching Workmanship. Ice and Fire's crafting-table
  recipes for these pieces are disabled while the bench recipes are active.
- Verified against Ice and Fire 2.1.13-beta-5 and Ice and Fire: Community Edition 1.2.7 (both for 1.20.1); the
  Community Edition's `amethyst` spelling is covered next to the original `amythest`. The recipes load only when the
  mod and the items exist (`forge:mod_loaded`, `forge:item_exists`); without Ice and Fire nothing changes.
- **Scale-backed finishes.** On these pieces the backing's finish and quality act lightly: Workmanship 0.95x-1.05x,
  Integrity 0.9x-1.1x durability, Supple +1% speed and 4% less detection per piece (boots still muffle footsteps),
  Harness 1.2x durability, Hardened +0.5 armor and +0.25 toughness per piece instead of +75% of the piece's armor. A full
  Masterwork Hardened set reaches 29.4 armor and 9.05 toughness, where ordinary leather's values would pass the armor cap
  of 30 at every quality. Hide traits act as on any piece. Everything is in the new `scaleBacked` config section; an
  inverted range there turns these effects off (with a warning) instead of falling back to the leather values.
- **No cheap repairs.** Leather Scraps no longer patch scale-backed pieces, neither by an inventory click nor with the
  awl (which now says why); they repair as Ice and Fire repairs them, on an anvil with their own dragon scales.
  `scaleBacked.allowScrapRepair` allows scraps again.
- **Turning it off:** `iceandfire` in `modBlacklist` removes the bench recipes and restores crafting;
  `replaceLeatherEquipmentRecipes = false` keeps both; `recipeBlacklist` keeps single crafting recipes.

### Bench, JEI and tooltips

- Items that share a name (every Ice and Fire colour is called "Dragon Scale Chestplate") carry their variant in the
  bench list, for example "Supple Dragon Scale Chestplate (Red)". Hovering a component's icon shows its full tooltip.
- JEI's leatherworking page notes scale-backed pieces, and their tooltip says so; every number in it comes from the
  same scale-backed or leather values the server applies.

### Datapacks

- **`replaces`** in a bench recipe names the crafting recipes it takes over (a list or one id). Each is disabled only
  while replacement is on, the bench recipe is active and can be made (every auxiliary matches an item), the named
  recipe is a crafting recipe making the same item, and neither blacklist protects it. Every recipe it keeps is a note
  in `/immersive_tanning report`. Rebuilds start from the recipes as loaded, so `/reload` after a config or datapack
  change restores or disables them with nothing left over.
- **`#immersive_tanning:scale_backed`** marks other mods' armor that is stitched over a leather backing. A piece made
  while tagged records it (`Profile` in its `immersive_tanning` data) and keeps the scale-backed values if the tag
  changes later.

### Tools and tests

- `tools/generate_compat_recipes.py` writes and checks the Ice and Fire recipes and the scale-backed tags.
- New GameTests: the knife through the server's own interaction path (every axis, nether stems, refused strips,
  creative, a full inventory), explicit replacement with its switches and real datapack reloads, the bench with
  stand-in scales (offers, grouping, refunds on cancel, logout, a broken bench and an unloaded one), the scale-backed
  policy, profiles, traits and both repair paths. The client smoke test strips a log through the real client, checks a
  scale-backed tooltip and the bench's variant names; the Ultima pack test gains `iceandfire` and `knife` groups.
- GameTests run on the server config's defaults held in memory (`TestConfig`), so tests that change a value no longer
  write the world's config file, which Forge's watcher reloaded concurrently and which could keep a stale value.

## 1.1.1 - Vanilla screens

### Vanilla look

- **Screens.** Every screen uses Minecraft's own look in place of the Stoneborn workshop theme: vanilla container
  panels, buttons, slots and the creative-inventory scroller, with dark grey labels. This covers fleshing, the finish
  choice and the Leatherworker's Bench. The hide, the stitching board and the bench's grade sit in dark displays, so
  their colours keep their contrast.
- **Finish choice.** The three cards are vanilla button faces that highlight on hover and size to the screen. A
  description that doesn't fit shows in full on hover.
- **Bench.** Pieces are listed as vanilla button rows with a draggable scroller, which replaces the "1-5 of 9" line.
- **Readability.** Quality grades and hide traits sit in dark readouts, since their colours are chosen for dark
  backgrounds. Clipped titles and hints show in full on hover, and tooltips stay full size on small screens.
- **Small screens.** Every screen fits Minecraft's 320 x 240 minimum without being scaled down.
- **JEI.** The Tannery and Leatherworking categories use JEI's own slots, output slot and arrows. Hovering a rack, vat
  or bench names it.
- **Retired.** The theme's textures and classes are removed (`gui/stone.png`, `gui/slot.png`, `StonebornGui`,
  `StonebornJei`), and the texture checker fails if they return.
- Minigame rules, timing, scoring, keys and packets are unchanged.

## 1.1.0 - Progression hooks, a vat that explains itself

### Upgrading from 1.0.0

- **Network protocol 2.** Client and server must both run 1.1.0; a 1.0.0 client cannot join a 1.1.0 server, and a
  1.1.0 client cannot join a 1.0.0 server.
- **Existing vats migrate on their first tick.** In a vat saved by 1.0.0 that holds no water, hides still tanning
  pause (in 1.0.0 they kept tanning while dry) and resume when it is refilled. Small or medium hides stuck behind a
  waiting large hide start as soon as water and tannin allow.
- **Config.** New keys take their defaults. A `vatMaxTannin` of 1 still loads, but acts as 2.

### Tanner's Vat

- Water, bark and hides can go in in any order and end in the same state. Adding any of them, by hand or by
  automation, runs one start transition, in which each waiting hide spends its size's tannin once, when it starts.
- A large hide waiting for its second bark no longer holds back a smaller hide behind it.
- Draining the vat pauses started hides where they are. A refill resumes them for their remaining time without more
  bark; tannin they already spent stays spent. In 1.0.0 a drained vat kept their clocks running.
- `vatMaxTannin` below 2 (the cost of a large hide) acts as 2, so large hides can always start.
- The vat now says what it is doing. Filling it, adding bark or hides, or using it with an empty hand while nothing
  is ready to take out reports water,
  tannin (of its limit), hides (of its capacity), how many are tanning (and when the next is ready), paused, waiting
  and ready to take out. Anything waiting gets its reason and the next step as a separate highlighted notice:
  a bucket of water, or exactly how much more bark.
- Draining with started hides warns how many are paused. Adding more hides than fit says the vat is full, and a vat
  full of tannin says its limit.
- **Jade** shows a partly filled vat's water in mB, what each waiting hide needs (water, or how much bark) and a
  paused hide's progress.

### Station messages

- Station messages (vat status, what a hide waits for, errors, a cancelled session's reason) stay readable above the
  hotbar instead of on the action bar, where the next message replaced them. Up to three show at once, newest lowest;
  a repeated message refreshes instead of stacking. Notices are narrated when the narrator reads system messages.
- New client config: `stationNotices` (default on; off uses the vanilla action bar) and `noticeSeconds` (default 8,
  2-60).

### Stitching

- One physical action is one awl press: a held Space's key repeat, or a click landing together with Space, no longer
  counts as another press.
- A press too early to count shows "Too early" (with a short cue sound when `timingCueSounds` is on) instead of
  vanishing. It uses up no stitch.
- The approach ring shows when a press will count: faint while a press would be ignored, solid once a press would
  be taken but spoil the stitch, bright with the hole lit once a press scores. The on-screen instructions say so.
- The Perfect/Good/Rough/Miss grade stays readable longer; new client config `feedbackLabelMs` (default 2500 ms,
  500-10000). A newer result replaces it at once.
- Space still held from the last stitch no longer closes the result screen before it is read: Space or Enter closes
  it only as a fresh press, 600 ms after the result appears.

### API for progression mods

- `HideFleshedEvent` (Forge bus, server side, not cancellable): posted when fleshing finishes, before the scraped hide
  is made. Carries the raw hide, the rack, whether time ran out and the fleshing score (0-100) that Integrity is
  rounded from; `setScore` changes it.
- `LeatherworkStitchedEvent` (Forge bus, server side, not cancellable): posted when a stitch run finishes, before the
  leatherwork is written and the piece reaches the inventory. Carries the live result stack, the recipe id, finish,
  Integrity, trait, leather used, bench, whether time ran out and the Workmanship (0-100); `setWorkmanship` changes
  it. Bench experience and the Master Tanner advancement follow the final value.
- `ImmersiveTanningAPI.registerRecipeGate(ILeatherworkRecipeGate)`: refuses a bench recipe to a player with a reason.
  The bench leaves the recipe off its list, shows the first reason when nothing is left, and checks again when the
  recipe is selected, before any leather or auxiliary is taken. A gate that throws is skipped.
- `ImmersiveTanningAPI.registerAssistSource(ITanningAssistSource)`: per-player fleshing time. Answers multiply and
  the product is clamped to `1.0..maxAssistTimeMultiplier` (new server config under `minigames`, default 1.5,
  1.0-3.0). Stitching is paced by its seam pattern and is not assisted.
- Matching `unregisterRecipeGate` and `unregisterAssistSource`.

### Testing

- GameTests for the vat (every resource order, mixed batches, pausing and resuming, save and reload, migration of
  older vats, `vatMaxTannin = 1`, automation), stitch input and the new API. The client smoke test stages the station
  notices, an early press, held Space and click-with-Space, and the result screen under a held key.

## 1.0.0

First version, for Minecraft 1.20.1 and Forge 47.

### The loop

- **Hides.** Animals drop Small, Medium or Large Hides instead of leather. Some carry an innate trait (hoglins
  *Tough*, polar bears *Insulating*, striders *Heatproof*).
- **Tanner's Rack - fleshing.** Mount a raw hide and scrape it with a Tanner's Knife in a timed minigame; the result
  is the hide's Integrity. Running out of time never loses the hide.
- **Tanner's Vat - tanning.** Water, bark (stripped from logs with the Tanner's Knife) and a stack of scraped hides.
  Hides tan on world time, so the vat works while nobody is near. Comparators read the ready count.
- **Tanner's Rack - finish and drying.** Choose Supple, Harness or Hardened for a wet tanned hide (screen, keys 1-3,
  or sneak-use to repeat the last finish) and let it dry.
- **Leatherworker's Bench - stitching.** Use an Awl, choose a piece and its leather, and stitch the seam in rhythm;
  the timing is the piece's Workmanship. Misses never abort the piece; offcuts return as Leather Scraps.
- **Field repair.** Leather Scraps and an awl patch leather equipment, keeping enchantments, finish and quality.
- Every effect is per piece: Workmanship scales armor and the finish's effect, Integrity scales durability, and the
  grade runs Crude, Standard, Fine, Masterwork.

### Items and recipes

- Leather armor, leather horse armor and the saddle are made at the bench; their crafting-table recipes are disabled.
- **Binding Leather** replaces vanilla leather in recipes, loot and new villager offers. It is cut from any finished
  leather, and vanilla leather converts to it one to one.
- Advancements from the first hide to a Masterwork piece, field repair and trait leather.

### Datapacks and compatibility

- Datapack hide sources, hide traits, hide mounting recipes, leatherworking recipes, seam patterns, loot functions and
  tags. Modded leather equipment recipes are detected and routed through the bench.
- Optional support for livestock and creature mods, Butchery (skins mount on the rack; carcass kills drop no hide),
  magic and dragon damage for trait resistance, modded equipment, villager and wandering-trader trades, MCA
  tanneries, JEI and Jade. None of these mods is required.

### Configuration, commands and API

- Server config (synced, per world) for drops, recipes, timings, the vat, the minigames and every finish value;
  client config for presentation and accessibility (high-contrast and colour-blind palettes, larger stitch markers,
  timing cue sounds). Client settings never change scoring.
- `/immersive_tanning report` and `/immersive_tanning advance <pos> <ticks>` (operators).
- `ImmersiveTanningAPI`: `registerTraitEffect`, `leatherwork` and `playerMade`. The bench records the player who
  stitched a piece, so `playerMade` tells it from villager and loot leatherwork.
- The fleshing, finish, bench and JEI screens share the Stoneborn workshop GUI.
