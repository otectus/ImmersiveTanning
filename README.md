# Immersive Tanning

A Forge 1.20.1 mod that makes leather a craft instead of an ingredient. It is the sister mod of
[Immersive Smithing](https://github.com/otectus/Smithing): two short skill interactions decide quality, failures never
destroy material, datapacks drive compatibility, and automation handles inputs but never the craftsmanship.

> **Two moments of skill. One strategic material choice. Batch everything passive. Show state physically.
> Make the resulting leather change how the player plays.**

## The loop

**Hunt -> Flesh -> Tan -> Choose Finish -> Make -> Maintain**

1. **Hides.** Animals drop a **Small**, **Medium** or **Large Hide** instead of leather (cows and horses large, pigs
   and sheep medium, rabbits and foxes small). A large hide yields three leather. A few creatures keep an innate
   **trait** in their hide: hoglins are *Tough*, polar bears *Insulating*, striders *Heatproof*. Modded animals and
   beasts drop hides too, and skins and pelts from other mods (a skinned Butchery carcass, a kangaroo hide) mount on
   the rack as raw hides (see Compatibility).
2. **Flesh - Tanner's Rack.** Mount a raw hide on the rack's beam and use a **Tanner's Knife** on it. Hold the mouse
   button and drag the knife through the broad patches of flesh. Along the grain a patch comes off in one pass,
   across it takes two. Lift the knife between patches: scraping clean hide is *overworking*, slipping off the edge
   costs *control*. The result is the hide's **Integrity**. Running out of time never loses the hide; it is simply
   scraped as it is. With more raw hides in your inventory, **Next hide** mounts the next one straight away.
3. **Tan - Tanner's Vat.** Fill it with a bucket of water, add **Bark** (strip logs with the Tanner's Knife) and put
   in a whole stack of scraped hides. Each hide spends tannin by size (small and medium 1, large 2) and tans on its
   own clock, following world time, so you can leave. Use an empty hand to take tanned hides out. The liquor darkens
   as bark goes in; comparators read how many hides are ready.
4. **Finish and dry - Tanner's Rack.** Lace a wet tanned hide into the frame and choose its finish. The choice takes
   two seconds; the consequences last for the leather's whole life. Sneak-use repeats your last finish. The hide
   hangs loose, tensioned or taut, lightens as it dries and settles when it is done.
5. **Make - Leatherworker's Bench.** Use an **Awl** on the bench and choose a piece and the leather to cut it from
   (all of one finish and trait; the best Integrity is cut first). The panels are cut for you; then stitch the seam
   in rhythm - click or press Space as the needle reaches each hole. Your timing is the piece's **Workmanship**.
   Misses leave uneven stitches but never abort the piece. Offcuts come back as **Leather Scraps**.
6. **Maintain.** Right-click Leather Scraps onto damaged leather in any inventory (with an awl in your inventory), or
   keep using the awl to patch the most worn piece you are wearing. Enchantments, finish and quality are kept.

## The three finishes

| Finish | Identity | Per piece |
|---|---|---|
| **Supple** | Light & Quiet | +2.5% movement speed; mobs notice you from 8% less far; supple **boots** keep your footsteps from sculk |
| **Harness** | Rugged & Repairable | 1.5x durability; field repairs restore twice as much |
| **Hardened** | Protective | +75% of the piece's armor and +0.5 toughness - roughly chain, never iron |

Every effect is per piece, so each piece contributes and a full set is never a threshold. **Workmanship** scales
armor and the finish's effect (0.85x-1.15x); **Integrity** scales durability (0.8x-1.2x). A piece reads, for example:

```
Fine Supple Leather Boots
Supple - Light & Quiet
Integrity: Fine (86)
Workmanship: Masterwork (91)
```

Grades: Crude 0-34, Standard 35-69, Fine 70-89, Masterwork 90-100 (on the average of both scores).

## Items and recipes

- **Tanner's Rack** - sticks around a string. **Tanner's Vat** - planks and iron. **Leatherworker's Bench** - string,
  iron and planks.
- **Tanner's Knife** - an iron ingot or a flint on a stick. **Awl** - an iron nugget or a bone on a stick.
- Leather armor, leather horse armor and the **saddle** (5 leather, an iron ingot, 2 string) are made at the bench;
  the crafting-table recipes for leather armor and horse armor are disabled.
- **Binding Leather replaces vanilla leather.** Every recipe that took leather - books, item frames, anvil repair of
  leather armor, and modded recipes of any type that name leather by item or tag - takes Binding Leather instead, and
  recipes that made leather (rabbit hide) make Binding Leather. Leather in loot (mob drops, chests, fishing) and in
  newly generated villager and wandering-trader offers becomes Binding Leather too. Cut it from any finished leather (1:1); it cuts into three Leather Scraps. Vanilla leather
  you still hold converts one to one in a crafting grid.
- JEI shows the tannery for each hide size, every bench recipe with its seam, and info pages for the stations,
  tools, bark, scraps, raw hides, the three leathers and Binding Leather.

## Compatibility

Everything below is data or an optional hook; none of these mods is required.

- **Livestock mods** - hide sources also name the Forge convention entity tags (`#forge:cow`, `#forge:horses`,
  `#forge:pig`, `#forge:sheep`, `#forge:goat`, `#forge:wolves`, `#forge:rabbit`, `#forge:foxes`, ...), so animals that
  replace vanilla ones (Livestock Overhaul, Pet Overhaul) drop the same hides.
- **Creatures** - Alex's Mobs, Lycanites Mobs, Ice and Fire, Legendary Creatures and Livestock Overhaul
  beasts drop hides whose trait follows where they live: cold beasts *Insulating*, nether beasts *Heatproof*, sea beasts
  *Water-Resistant*, swift ones *Lightweight*, big ones *Tough*. Apex beasts (rhinoceros, maka alpha, zoataur, sea bear)
  carry the stronger **Ironhide** and **Tidehide**. Mobs that hand out drops without a loot table (Lycanites) are handled
  at `LivingDropsEvent`: their leather becomes the hide, and vanilla leather in any mob's custom drops becomes Binding Leather.
- **Butchery** - its skins mount on the Tanner's Rack (hide mounting recipes; so do kangaroo hide, seal leather,
  warphopper fur and racoon fur). A kill with a cleaver (or Butcher's
  Touch) on an animal Butchery skins drops no hide, because the carcass supplies the skin. The Tanner's Knife is a
  skinning knife (`#forge:skinning_knives`).
- **Magic and dragons** - Heatproof also resists Iron's Spellbooks fire magic, Ice and Fire dragonfire, Apothic fire
  damage and Ignis's flame strike; Insulating resists ice magic, dragon ice and Apothic cold damage.
- **Equipment** - Spartan Weaponry quivers, Livestock Overhaul saddles and harnesses, Alex's Mobs falconry gear and
  Pet Overhaul leather dog armor are made at the bench; modded saddles stitch the saddle seam. (Lycanites saddles stack,
  so like books they stay crafting-table recipes.)
- **Villages** - leatherworkers sell the tools, buy hides and sell leatherwork of their own making (village quality,
  signed with the villager's name; their leather armor and saddles become leatherwork too). More Villagers' hunters buy
  hides and sell trophy hides; wandering traders sometimes carry one. With MCA, two racks and a vat in a building make a
  **Tannery**.
- **Progression** - a finished bench piece gives experience scaled by its quality (skill mods that spend experience,
  such as Runic Skills, see it too).
- **Jade** - the rack shows its hide, finish and drying progress, the vat its water, tannin and each hide's progress.
- **Performance mods** - the Binding Leather ingredient hook applies after FastSuite's and ModernFix's rewrites of
  `Ingredient#getItems`.

## Configuration

`serverconfig/immersive_tanning-server.toml` (per world, synced): hide drop replacement and looting, Binding Leather in
loot and in trades (in recipes it is always on), bench recipe
replacement and automatic detection (with recipe and mod blacklists), tanning and drying time (world ticks), vat
capacity and tannin limit, bark per log, fleshing time limit, timing tolerance, quality multiplier ranges, every
finish value, field-repair amount and scrap yield, and automation.

`config/immersive_tanning-client.toml`: numeric scores in tooltips, grain hints, particle amount, high-contrast and
colour-blind palettes, larger stitch markers, timing cue sounds. These never change scoring.

The server config also sets the carcass enchantments (default `butchery:butcherstouch`), village trades
(`enableVillagerTrades`) and bench experience (`benchExperience`).

`/immersive_tanning report` prints the recipe, hide and trait summary; `/immersive_tanning advance <pos> <ticks>` moves
a vat's or rack's clock forward (operators).

## Datapacks

All paths are `data/<namespace>/...`.

**Hide sources** - `immersive_tanning/hide_sources/<name>.json`. The highest `priority` source matching an entity
wins; `"size": "none"` stops an entity dropping hides. Entity tags are allowed.

```json
{ "entities": ["minecraft:hoglin", "#forge:some_tag"], "size": "large", "count": 1,
  "trait": "immersive_tanning:tough", "priority": 10 }
```

**Hide traits** - `immersive_tanning/hide_traits/<name>.json`. The file id is the trait id; its name is the
translation key `hide_trait.<namespace>.<name>`. Built-in effects: `immersive_tanning:heatproof`, `insulating`,
`lightweight`, `tough`, `water_resistant`; `strength` scales them (the built-in Ironhide and Tidehide are Tough and
Water-Resistant at 1.6). The trait travels with the hide, its leather and the finished piece.

```json
{ "effect": "immersive_tanning:heatproof", "strength": 1.0, "color": "#E0703A" }
```

**Hide mounting** - a recipe of type `immersive_tanning:hide_mounting`: another mod's skin or pelt that the Tanner's Rack
accepts as a raw hide of one size, optionally with a trait. It becomes an ordinary raw hide when mounted. Use
`forge:mod_loaded` conditions for optional mods; JEI shows these items in the tannery and they get a tooltip line.

```json
{ "type": "immersive_tanning:hide_mounting", "conditions": [{ "type": "forge:mod_loaded", "modid": "butchery" }],
  "ingredient": [{ "item": "butchery:polar_bear_skin" }], "size": "large", "trait": "immersive_tanning:insulating" }
```

**Bench recipes** - a normal recipe of type `immersive_tanning:leatherworking`. `seam` is optional; without it the
seam follows the result's equipment slot.

```json
{ "type": "immersive_tanning:leatherworking", "leather": 5,
  "auxiliary": [{ "ingredient": { "item": "minecraft:string" }, "count": 2 }],
  "result": { "item": "minecraft:saddle" }, "seam": "immersive_tanning:saddle" }
```

**Seams** - `immersive_tanning/seam_patterns/<name>.json`: the panels drawn on the bench, the seams stitched along
them, stitches per seam, a rhythm (beat multipliers, cycled), and timing. Coordinates are 0..1, y down. Recipes change
the shape and rhythm of a seam, never the rules.

```json
{ "panels": [[[0.2, 0.2], [0.8, 0.2], [0.8, 0.8], [0.2, 0.8]]],
  "seams": [{ "path": [[0.25, 0.3], [0.75, 0.3]], "stitches": 6 }],
  "rhythm": [1, 0.75], "beat_ms": 520, "window_ms": 170, "perfect_ms": 45, "lead_ms": 900 }
```

**Loot functions** - `immersive_tanning:set_hide_trait` (`"trait"`) gives a hide, tanned hide, leather or finished piece
a trait (trophy hides stack with dropped ones). `immersive_tanning:set_leatherwork` makes an item a finished piece from
someone else's bench: optional `"finish"`, `"integrity"` and `"workmanship"` (number providers; village quality
without them) and `"trait"`.

**Item tags** (`immersive_tanning:`)

| Tag | Meaning |
|---|---|
| `leatherwork_equipment` | Unstackable items whose leather crafting recipes are routed through the bench (automatic recipes). Leather armor is included without the tag. Ships optional entries for common backpack and quiver mods. |
| `non_leatherwork_equipment` | Never routed through the bench. |
| `leather_ingredients` | What counts as leather when detecting leather equipment recipes (Binding Leather, `#forge:leather`, finished leather). |
| `tannin_sources` | Items the vat accepts as tannin (bark, plus optional `#forge:bark` and tree bark from other mods). |
| `replaced_by_hides` | Drops removed from animals that drop a hide (leather, rabbit hide). |
| `field_repairable` | Leather items scraps can patch even without bench data. |
| `raw_hides`, `finished_leather` | The mod's own items, for other mods' recipes. |
| `carcass_tools` | Weapons that butcher a carcass instead of dropping a hide (default: `#forge:cleaver`, `#c:cleaver`). |
| `seams/<seam>` | Items stitched with the seam `immersive_tanning:<seam>` when their recipe names none (e.g. `seams/saddle`). |

Entity tag `immersive_tanning:carcass_animals` lists the animals a carcass tool skins (Butchery's). Damage type tags
`immersive_tanning:resisted_by/heatproof`, `resisted_by/insulating` and `resisted_by/water_resistant` are the damage
the built-in traits reduce.

Binding Leather is in `#forge:leather`; the finished leathers are not (cut them into Binding Leather first).

Block tag `immersive_tanning:bark_sources` (default `#minecraft:logs`) limits what the knife strips; the strip itself
follows the axe's mapping, so modded logs work.

## For mod developers

`ImmersiveTanningAPI.registerTraitEffect(id, effect)` adds a trait effect that datapacks can name.
`ImmersiveTanningAPI.leatherwork(stack)` reads a piece's finish, Integrity, Workmanship and trait. Everything else is
implementation detail.

## Building

```
./gradlew build                  # build/libs/immersive-tanning-<version>.jar
./gradlew runGameTestServer      # headless GameTests
./gradlew runClient -PsmokeTest  # visual smoke test: screenshots in run/screenshots, result in run/smoketest
python3 tools/generate_textures.py --check   # textures match art/tanning_art.py
./gradlew build smokeTestJar && python3 tools/packtest/ultima_pack_test.py --expect dragn,lycanites,alexsmobs,butchery,magic,bench,trades
                                 # the jar in an isolated copy of the Ultima modpack: pack checks and real kills
```

Licensed under GPL-3.0.
