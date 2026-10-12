# Changelog

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
