# Immersive Tanning

A Forge 1.20.1 mod that makes leather a craft instead of an ingredient. It is the sister mod of
[Immersive Smithing](https://github.com/otectus/Smithing): hands-on work at the stations decides quality, failures never
destroy material, datapacks drive compatibility, and automation handles inputs but never the craftsmanship. Since 1.3
the work is done in the world itself: the view leans in to the station, and the knife, slicker and awl touch the hide
and the leather where you point them.

> **Two moments of skill. One strategic material choice. Batch everything passive. Show state physically.
> Make the resulting leather change how the player plays.**

## The loop

**Hunt -> Flesh -> Tan -> Choose Finish -> Make -> Maintain**

1. **Hides.** Animals drop a **Small**, **Medium** or **Large Hide** instead of leather (cows and horses large, pigs
   and sheep medium, rabbits and foxes small). A large hide yields three leather. A few creatures keep an innate
   **trait** in their hide: hoglins are *Tough*, polar bears *Insulating*, striders *Heatproof*. Modded animals and
   beasts drop hides too, and skins and pelts from other mods (a skinned Butchery carcass, a kangaroo hide) mount on
   the rack as raw hides (see Compatibility).
2. **Flesh - Tanner's Rack.** Use the rack with a raw hide to lay it over the beam and lean in to work. Press and drag
   the **Tanner's Knife** through the flesh: it comes away where the blade touches, along the grain in one pass and
   across it in two. Lift the knife between patches: scraping clean hide is *overworking*, slipping off the edge costs
   *control*. The result is the hide's **Integrity**. There is no time limit (servers can turn on a challenge timer).
   Once 30% of the hide is clean, **Finish roughly** (F) scores it as it stands. Leaving keeps the work, and a hide
   taken off half-scraped carries it with it. The clean hide waits on the beam until you pick it up; with more raw
   hides in your inventory, a click on the empty beam lays the next one over it.
3. **Tan - Tanner's Vat.** Pour in a bucket of water, scatter **Bark** (strip logs with the Tanner's Knife) and lower
   scraped hides through the opening, in any order. The material drawer (B) brings what you carry to hand. Each hide
   spends tannin by size (small and medium 1, large 2) once, when it starts, and tans on its own clock, following world
   time, so you can leave. A large hide waiting for its second bark does not hold back a smaller one behind it.
   Draining the vat pauses started hides; a refill resumes them for their remaining time without more bark. Hover a
   hide to see what it waits for or how long it has left; lift tanned hides out on the hook. One slow sweep of the
   paddle through the liquor brings every steeping hide a little closer to done, once per hide. The liquor darkens as
   bark goes in; comparators read how many hides are ready.
4. **Finish and dry - Tanner's Rack.** Lace a wet tanned hide into the upper frame and move the tension lever to a
   labelled detent: Supple (slack), Harness (even) or Hardened (taut). Then work that finish's short treatment: smooth
   Supple with the slicker, draw the Harness cords down, or burnish Hardened with the block. Click the lever to seat
   it; that commits the finish and drying starts. Before that, changing the detent is free. Sneak-use with a wet hide
   repeats your last finish. The hide hangs loose, tensioned or taut, lightens as it dries and settles when it is done.
5. **Make - Leatherworker's Bench.** Lean in with an **Awl** and open the **pattern book**. It lists what you can make
   first, with each pattern's cost and the leather group it would be cut from (all of one finish and trait, best
   Integrity first). **Prepare** lays the leather out; nothing is made yet. Then:
   - cut each panel along its line with the knife;
   - seat the panels on the jig (Q and R turn a carried panel);
   - stitch each seam: press the awl through the lit holes and draw the thread straight away until the seam closes,
     not slack and not puckered;
   - tie off.
   Your cutting, seating and stitching are the piece's **Workmanship**. The piece and its offcuts (**Leather
   Scraps**) wait on the output pad. **Make another** repeats the pattern.
6. **Maintain.** Right-click Leather Scraps onto damaged leather in any inventory (with an awl in your inventory), keep
   using the awl to patch the most worn piece you are wearing, or hold a damaged piece and click the bench's mat: a
   short patch is stitched from Leather Scraps (up to eight). Enchantments, finish and quality are kept.

## Working at a station

Using a station leans your view in to the work; sneak-use does the quick action without it (insert, take, or lace a wet
hide with your last finish). In the view, press and drag on the part you mean. A label at the pointer says what will
happen, and a short guide at the bottom says what to do next.

| Key | Action |
|---|---|
| Mouse, or Space | Press and drag the tool (hold, or toggle with `gestureMode = TOGGLE`); right-click puts a tool down |
| Tab / Shift+Tab | Cycle what you can work |
| Arrow keys | Move the tool |
| Q / R | Turn a carried panel |
| F | Finish a hide roughly |
| B | Material drawer: bring what this station takes to hand |
| ] / [ | Next or previous station in reach |
| V | Change the view (cinematic, reduced motion, stationary) |
| Esc, or the inventory key | Leave |

All of these keys can be rebound (Controls, "Immersive Tanning (at a station)") and act only while working at a
station.

- **The work stays.** Leaving, a full inventory, logging out or dying never loses work. A half-scraped hide, a bench
  job (from its first cut it can also be taken away as an **Unfinished Leatherwork** item and put back on any bench)
  and a drying hide all wait where they are.
- **One operator.** One player works a rack or a bench at a time; others who lean in watch the accepted strokes and
  tools, and anyone may work a vat. **Hand off** lets the next player take over a job. Walking away, taking damage, or
  the station being broken or unloaded ends your turn at once.
- **Views.** *Cinematic* (default) leans in with a short glide and frames each task. *Reduced motion* cuts straight to
  the same framing, with no sway. *Stationary* keeps your own view, with larger targets.
- **Accessibility.** Toggle gestures, full keyboard control, larger targets, an assisted profile (wider guides and pull
  band, the same best quality; servers may refuse it), a labelled tension gauge, high-contrast plates, and every sound
  subtitled. None of these changes scoring.

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
- **Tanner's Knife** - an iron ingot or a flint on a stick. **Awl** - an iron nugget or a bone on a stick. The knife strips
  bark from logs as an axe strips them (modded logs too; `#immersive_tanning:bark_sources` says which) for two bark
  (`barkPerLog`) and one point of wear, keeping the log's direction; a stripped log gives nothing more. It does no other axe work, but Farmer's Delight's cutting board
  takes it as a stripping tool.
- Leather armor, leather horse armor and the **saddle** (5 leather, an iron ingot, 2 string) are made at the bench;
  the crafting-table recipes for leather armor and horse armor are disabled.
- **Binding Leather replaces vanilla leather.** Every recipe that took leather - books, item frames, anvil repair of
  leather armor, and modded recipes of any type that name leather by item or tag - takes Binding Leather instead, and
  recipes that made leather (rabbit hide) make Binding Leather. Leather in loot (mob drops, chests, fishing) and in
  newly generated villager and wandering-trader offers becomes Binding Leather too. Cut it from any finished leather (1:1); it cuts into three Leather Scraps. Vanilla leather
  you still hold converts one to one in a crafting grid.
- JEI shows the tannery for each hide size, every bench recipe with its work (cuts, panels and stitches), and info pages
  for the stations, tools, bark, scraps, raw hides, the three leathers and Binding Leather. While you work at a bench,
  **Prepare at bench** on a recipe's page lays that pattern out (the server checks it again, as the book does).
- **Unfinished Leatherwork** is a bench job taken off the bench after its first cut: its leather, components and every
  cut, seat and stitch so far. Put it on any bench to carry on.

## Compatibility

Everything below is data or an optional hook; none of these mods is required.

- **Livestock mods** - hide sources also name the Forge convention entity tags (`#forge:cow` and `#forge:cows`,
  `#forge:horses`, `#forge:pig` and `#forge:pigs`, `#forge:sheep`, `#forge:goat`, `#forge:wolves`, `#forge:rabbit` and
  `#forge:rabbits`, `#forge:foxes`, ...), so animals that replace vanilla ones (Livestock Overhaul, Pet Overhaul) drop
  the same hides.
- **Genetic Animals** - its cows, mooshrooms, moobloom, llamas, sheep, pigs and rabbits drop raw hides made from the
  skin their genetics really give (or, as an option, a species hide for every adult), its leather collar and bridle are
  made at the bench, tack keeps its data when it is put on, and its trades deal in Binding Leather (see below).
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
- **Ice and Fire** - dragon-scale armor is made at the Leatherworker's Bench: finished leather is the backing, the
  dragon scales go on top, and you stitch the seam (see below).
- **Progression** - a finished bench piece gives experience scaled by its quality (skill mods that spend experience,
  such as Runic Skills, see it too).
- **Jade** - the rack shows its hide and stage (how clean, the previewed finish and its treatment, drying progress),
  whose work it is and who works it; the vat its water, tannin and each hide's progress or what it waits for; the bench
  its job's stage and step, its owner and what waits on the output pad. While you work at a station Jade stays out of
  the view (`focus.jadeInFocus` keeps it).
- **Performance mods** - the Binding Leather ingredient hook applies after FastSuite's and ModernFix's rewrites of
  `Ingredient#getItems`.

### Genetic Animals

Verified against Genetic Animals 0.11.12 for 1.20.1 (mod id `eanimod`, `GeneticAnimals-0_11_12.jar`, SHA-1
`7809ea135dd2abbb18060d832032b590ce0d8294`); the full guide, with every rule and setting, is
[docs/compat/genetic-animals.md](docs/compat/genetic-animals.md).

- **Hides follow its genetics.** By default the leather and rabbit hide an animal really drops become raw hides for the
  usual rack, vat, finish and bench: each leather is one leather's worth, made up from the fewest hides up to the
  animal's size (a cow's 4 leather is a large and a small hide), each rabbit hide a small hide. A sheep or llama in
  wool, a burning animal and a pig drop none; that is its genetics, not a lost drop. `compat.geneticAnimals.hideMode =
  "TANNING"` gives every adult its species' hide instead (large: cow, mooshroom, moobloom, llama; medium: sheep, pig;
  small: rabbit), whatever it drops.
- **Cargo is cargo.** What an animal carries or wears drops exactly as it was; carried leather is never made into
  hides, and a carried raw hide never stands in for the animal's own.
- **Butchery.** It makes a carcass of Genetic Animals' sheep (they are in `#forge:sheep`) and of none of its other
  animals. Only a carcass that actually appears at the death stops the hide; a cleaver alone never does.
- **Tack.** The leather collar (1 leather) and leather bridle (4 leather, 2 iron nuggets) are stitched at the bench on
  a strap seam, at Genetic Animals' own costs; saddles and the ringed, belled and decorated pieces keep the trades they
  come from. Bridles and saddles put on by hand keep their finish, quality, maker, name and enchantments (Genetic
  Animals used to rebuild them from item and colour). Their finish and quality are a record of their making: they give
  the animal nothing.
- **Trades.** Its leatherworker, shepherd and wandering-trader offers ask for Binding Leather, and offers saved earlier
  convert the first time their trades are opened, with uses, discounts and everything else kept.

### Ice and Fire dragon-scale armor

With Ice and Fire installed, its dragon-scale armor - all twelve colours, fire, ice and lightning dragons alike, all
four pieces - is stitched at the bench instead of crafted. The piece is Ice and Fire's own armor, with its own model,
colour, armor, toughness, enchantability and its protection from dragon breath; the leather backing adds its finish,
Integrity and hide trait, and your stitching adds the Workmanship.

| Piece | Finished leather | Dragon scales (one colour) | Seam |
|---|---|---|---|
| Helmet | 1 | 5 | helmet |
| Chestplate | 2 | 8 | chestplate |
| Leggings | 2 | 7 | leggings |
| Boots | 1 | 4 | boots |

The scale counts are Ice and Fire's own; the colour of the scales is the colour of the piece. The pattern book lists
each colour separately (Ice and Fire names every colour alike, so the book adds the colour), and JEI shows the leather,
the scales and the piece. Leather and scales are laid out together and come back together, exactly once, if the piece
is cancelled before its first cut; a poor seam still makes the piece, at a lower Workmanship. Ice and Fire's crafting-table
recipes for these pieces are disabled while the bench recipes are active; its other recipes are untouched.

**Scale-backed finishes.** Dragon scales do the protecting, so the backing's finish and quality act lightly on these
pieces (they are *scale-backed*; ordinary leather keeps its values). Per piece:

| | Scale-backed | Ordinary leather |
|---|---|---|
| Workmanship (armor and finish effects) | 0.95x-1.05x | 0.85x-1.15x |
| Integrity (durability) | 0.9x-1.1x | 0.8x-1.2x |
| Supple | +1% speed, noticed 4% less far; boots muffle footsteps | +2.5% speed, 8% less far; boots muffle footsteps |
| Harness | 1.2x durability | 1.5x durability, double field repair |
| Hardened | +0.5 armor and +0.25 toughness | +75% of the piece's armor and +0.5 toughness |

Hide traits act as on any piece. A full red set (26 armor, 8 toughness, 1980 durability on its own; Ice and Fire
2.1.13 gives helmet 5, chestplate 7, leggings 9, boots 5) comes out as:

| Quality (Integrity and Workmanship) | Armor | Hardened | Supple | Effective durability (Harness) |
|---|---|---|---|---|
| 0 | 24.70 | 26.60 armor, 8.95 toughness | +3.8% speed, 14.4% less detection | 1782 (2138) |
| 50 | 26.00 | 28.00 armor, 9.00 toughness | +4.0% speed, 15.1% less detection | 1980 (2376) |
| 100 | 27.30 | 29.40 armor, 9.05 toughness | +4.2% speed, 15.8% less detection | 2178 (2614) |

With ordinary leather's values the same Hardened set would be 38.7-52.3 armor - past Minecraft's armor cap of 30 at
every quality - so the scale-backed values keep Workmanship and the finish choice meaningful. Every number is in the
`scaleBacked` config section.

**Repair.** Leather Scraps do not patch scale-backed pieces (an inventory click or the awl), since scraps are not what
holds them together: repair them the way Ice and Fire does, on an anvil with the piece's own dragon scales or by
combining two pieces. `scaleBacked.allowScrapRepair` turns scrap repair on for them.

**Versions.** Verified against the jars of Ice and Fire 2.1.13-beta-5 for 1.20.1 (mod id `iceandfire`, with Citadel)
and Ice and Fire: Community Edition 1.2.7 for 1.20.1 (same mod id, with Uranus and Jupiter). Both register
`iceandfire:armor_<colour>_<slot>` and `iceandfire:dragonscales_<colour>`; the Community Edition spells its amethyst
items `..._amethyst` where the original says `..._amythest`, and both spellings are covered. Fire, ice and lightning come
from Ice and Fire's own scale tags. The original build was also run in the Ultima modpack test; the Community Edition
was checked from its jar only. Other builds that keep these ids should work, but were not tested.

**Turning it off.** Add `iceandfire` to `modBlacklist` (the bench recipes go, the crafting-table recipes return), or set
`replaceLeatherEquipmentRecipes = false` (both stay), or list single recipe ids such as
`iceandfire:armor_red_chestplate` in `recipeBlacklist`; the change applies at the next `/reload`. A datapack can also
override the recipes (`data/immersive_tanning/recipes/leatherworking/compat/iceandfire/*.json`, for example to change
the leather costs) or empty `#immersive_tanning:scale_backed`. Pieces already made keep their data either way.

## Configuration

`serverconfig/immersive_tanning-server.toml` (per world, synced): hide drop replacement and looting, Binding Leather in
loot and in trades (in recipes it is always on), bench recipe
replacement and automatic detection (with recipe and mod blacklists), tanning and drying time (world ticks), vat
capacity and tannin limit (at least 2, the cost of a large hide; a configured 1 acts as 2), bark per log, the station
work (`work`: `challengeTiming` for an optional fleshing timer, off by default, with `minigames.fleshingTimeSeconds` and
`maxAssistTimeMultiplier`; `vatTendingCredit`; `leaseIdleSeconds`; `allowAssistedProfile`; and quality weights that
override each pattern's own), how far a working player may stand from the station (`minigames.maxStationDistance`),
quality multiplier ranges, every finish value, the scale-backed values (`scaleBacked`: quality ranges, Supple, Harness, Hardened and `allowScrapRepair`;
a minimum above its maximum turns these pieces' finish and quality effects off rather than using the leather values),
field-repair amount and scrap yield, and automation.

`config/immersive_tanning-client.toml`: how working at a station looks and feels (`focus`: camera mode, transition
time, field of view, idle sway, hold or toggle gestures, pointer sensitivity, tool smoothing, hands and tools or tools
only, the supporting hand, switching stations at the screen edge, the assisted profile, larger targets, hints, the
tension gauge, Jade while working, favourite patterns), numeric scores in tooltips, grain hints, particle amount,
high-contrast and colour-blind palettes, cue sounds, how long a work result stays readable (`feedbackLabelMs`), and
station messages kept on screen (`stationNotices`, `noticeSeconds`; off uses the action bar). These never change
scoring.

The server config also sets the carcass enchantments (default `butchery:butcherstouch`), village trades
(`enableVillagerTrades`), bench experience (`benchExperience`) and other mods' integration (`compat`: Genetic Animals'
`enabled`, `hideMode` and `leatherTackAtBench`).

`/immersive_tanning report` prints the recipe, hide and trait summary, skipped hide sources and other mods'
integration; `/immersive_tanning inspect <entity>` (or `inspect type <entity type>`) shows how one animal's hides are
decided, without rolling a drop; `/immersive_tanning advance <pos> <ticks>` moves a vat's or rack's clock forward
(operators).

## Datapacks

All paths are `data/<namespace>/...`.

**Hide sources** - `immersive_tanning/hide_sources/<name>.json`. The highest `priority` source matching an entity
wins (ties by id); `"size": "none"` or `"count": 0` stops an entity dropping hides, which is not the same as no source:
the animal's own leather then stays. Entity tags are allowed. A file that does not parse is skipped with its reason in
the log and in `/immersive_tanning report`. Genetic Animals' animals use `count` only as 0 in the default NATIVE mode,
where `size` is the largest hide their own leather is made into.

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
seam follows the result's equipment slot. `work_pattern` is optional too: the work pattern (below) the piece is cut,
seated and stitched with; without it the recipe uses the work pattern named like its seam, else its seam pattern
adapted, else the generic wrap, so a recipe never becomes unusable. `replaces` is optional: the crafting recipe ids (a list, or one id) this
recipe takes over. Each is disabled only while replacement is on, the bench recipe is active and can be made (every
auxiliary matches an item), the named recipe is a crafting recipe that makes the same item, and neither blacklist
protects it; `/immersive_tanning report` notes every one it keeps. Without `replaces`, only crafting recipes of the same
item that contain leather are disabled.

```json
{ "type": "immersive_tanning:leatherworking", "leather": 5,
  "auxiliary": [{ "ingredient": { "item": "minecraft:string" }, "count": 2 }],
  "result": { "item": "minecraft:saddle" }, "seam": "immersive_tanning:saddle" }
```

Recipes for other mods use Forge's conditions, as the Ice and Fire ones do:

```json
{ "type": "immersive_tanning:leatherworking",
  "conditions": [{ "type": "forge:mod_loaded", "modid": "iceandfire" },
                 { "type": "forge:item_exists", "item": "iceandfire:armor_red_chestplate" }],
  "leather": 2, "auxiliary": [{ "ingredient": { "item": "iceandfire:dragonscales_red" }, "count": 8 }],
  "result": { "item": "iceandfire:armor_red_chestplate" }, "seam": "immersive_tanning:chestplate",
  "replaces": ["iceandfire:armor_red_chestplate"] }
```

**Work patterns** - `immersive_tanning/work_patterns/<name>.json` (since 1.3): what the bench work is made of.
`schema_version` is 1. Coordinates are 0..1 with y down: a panel's `outline` in the panel's own square; cut and seam
paths too; assembly targets on the jig.

- `panels` (1-6): `id`, `outline` (at least 3 points), `size` (its width and height on the bench), and optional `layout`
  and `layout_rotation_deg` (where it lies on the leather to be cut).
- `cuts` (0-6): `id`, `panel`, a `path` of at least 2 points, `tolerance` (0.01-0.25).
- `assembly`: per panel, a `target` on the jig, `rotation_deg`, `position_tolerance` (0.02-0.3) and
  `rotation_tolerance_deg` (2-90).
- `joins` (1-6): `id`, the two edges `a` and `b` (each a `panel` and a `path`), `actions` (stitches, 1-12; at most 16 in
  all) and `pull` (`min` < `ideal` < `max`, at most 0.8), the thread draw that makes an even stitch.
- `quality_weights` (`cutting`, `assembly`, `stitching`) and an optional `presentation` id.

A file that does not parse is skipped with its reason in the log and in `/immersive_tanning report`, and recipes that
named it fall back as above. Patterns change the work's shape, never its rules: the server scores every cut, seat and
stitch the same way.

```json
{ "schema_version": 1,
  "panels": [{ "id": "strap", "outline": [[0.02, 0.1], [0.98, 0.1], [0.98, 0.9], [0.02, 0.9]], "size": [0.64, 0.1] }],
  "cuts": [{ "id": "edge", "panel": "strap", "path": [[0.02, 0.15], [0.98, 0.15]], "tolerance": 0.06 }],
  "assembly": [{ "panel": "strap", "target": [0.42, 0.55], "rotation_deg": 0 }],
  "joins": [{ "id": "fold", "a": { "panel": "strap", "path": [[0.1, 0.25], [0.4, 0.25]] },
              "b": { "panel": "strap", "path": [[0.1, 0.75], [0.4, 0.75]] },
              "actions": 2, "pull": { "min": 0.18, "ideal": 0.25, "max": 0.35 } }],
  "quality_weights": { "cutting": 0.25, "assembly": 0.15, "stitching": 0.6 } }
```

**Seams** - `immersive_tanning/seam_patterns/<name>.json`, the 1.2 format: the panels drawn on the bench and the seams
stitched along them (its rhythm and timing fields still load but no longer apply). A recipe whose seam has no work
pattern of the same name is worked with the seam adapted into one.

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
| `scale_backed` | Other mods' armor stitched over a leather backing (Ice and Fire's dragon-scale armor): the scale-backed finish values, no scrap repair. A piece made while tagged keeps that for good. |
| `raw_hides`, `finished_leather` | The mod's own items, for other mods' recipes. |
| `carcass_tools` | Weapons that butcher a carcass instead of dropping a hide (default: `#forge:cleaver`, `#c:cleaver`). |
| `carcasses` | Carcass items another mod spawns at a death (Butchery's); for Genetic Animals only such an item appearing at the death stops the hide. |
| `seams/<seam>` | Items stitched with the seam `immersive_tanning:<seam>` when their recipe names none (e.g. `seams/saddle`). |

Entity tag `immersive_tanning:carcass_animals` lists the animals a carcass tool skins (Butchery's). Damage type tags
`immersive_tanning:resisted_by/heatproof`, `resisted_by/insulating` and `resisted_by/water_resistant` are the damage
the built-in traits reduce.

Binding Leather is in `#forge:leather`; the finished leathers are not (cut them into Binding Leather first).

Block tag `immersive_tanning:bark_sources` (default `#minecraft:logs`) limits what the knife strips; the strip itself
follows the axe's mapping, so modded logs work.

## For mod developers

`ImmersiveTanningAPI.registerTraitEffect(id, effect)` adds a trait effect that datapacks can name.
`ImmersiveTanningAPI.leatherwork(stack)` reads a piece's finish, Integrity, Workmanship and trait.
`ImmersiveTanningAPI.playerMade(stack)` tells whether a player stitched it at the bench (the bench records the player;
villager and loot leatherwork carry only a crafter's name). Everything else is implementation detail.

Since 1.1.0, two Forge-bus events (server side, not cancellable) and two hooks exist for progression mods:

- `HideFleshedEvent`: posted when fleshing finishes (clean, finished roughly, or out of time under challenge timing),
  before the scraped hide is made. Carries the raw hide, the rack, whether time ran out (only ever with
  `work.challengeTiming`), and the fleshing score (0..100) that Integrity is rounded from; `setScore` changes it.
- `LeatherworkStitchedEvent`: posted when the piece is tied off at the bench, before the leatherwork is written and
  before the piece reaches the output pad (never timed out). Carries the live result stack (changes stay on it), the recipe id, finish, Integrity, trait,
  leather used, bench and whether time ran out, and the Workmanship (0..100); `setWorkmanship` changes it, and the
  bench experience and the Master Tanner advancement follow the final value.
- `ImmersiveTanningAPI.registerRecipeGate(ILeatherworkRecipeGate)`: refuses a bench recipe to a player with a reason.
  The pattern book shows the refusal; preparing the pattern asks again before any leather or component is taken, and
  tying the piece off asks once more (a job a gate now refuses can be recovered with its materials).
- `ImmersiveTanningAPI.registerAssistSource(ITanningAssistSource)`: per-player fleshing time under challenge timing.
  Answers multiply, clamped to `1.0..minigames.maxAssistTimeMultiplier`. Without challenge timing fleshing has no time
  limit, so there is nothing to lengthen. Bench work is untimed and is not assisted.

## Building

```
./gradlew build                  # build/libs/immersive-tanning-<version>.jar
./gradlew runGameTestServer      # headless GameTests
./gradlew runClient -PsmokeTest  # visual smoke test: screenshots in run/screenshots, result in run/smoketest
python3 tools/generate_textures.py --check   # textures match art/tanning_art.py
python3 tools/generate_compat_recipes.py --check   # Ice and Fire recipes and scale-backed tags match the generator
./gradlew build smokeTestJar && python3 tools/packtest/ultima_pack_test.py --expect dragn,lycanites,alexsmobs,butchery,magic,bench,trades,iceandfire,knife
                                 # the jar in an isolated copy of the Ultima modpack: pack checks and real kills
./gradlew runGameTestServer -PgeneticAnimals     # the GameTests with the pinned Genetic Animals release loaded
./gradlew geneticAnimalsJar && python3 tools/packtest/ultima_pack_test.py --genetic-animals --pack-mods jei-,butchery- --expect geneticanimals
                                 # the jar with Genetic Animals (and only the named pack jars) in a real instance
```

Licensed under GPL-3.0.
