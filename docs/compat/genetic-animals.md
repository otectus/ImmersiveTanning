# Genetic Animals

Immersive Tanning 1.3.0 works with [Genetic Animals](https://modrinth.com/mod/genetic-animals) (mod id `eanimod`).
Nothing is required: without Genetic Animals none of this does anything, and Genetic Animals itself is not changed.

## Supported version

| | |
|---|---|
| Release | Genetic Animals **0.11.12** for Minecraft 1.20.1 / Forge (`GeneticAnimals-0_11_12.jar`, Modrinth version `QpwmrbVK`) |
| SHA-1 | `7809ea135dd2abbb18060d832032b590ce0d8294` |
| SHA-256 | `2f5cfa5014bec5297e7db08d8ff574e1e891b1c8613c993724c7b597ee9465e4` |
| Manifest | `Implementation-Version: 0_11_12`, built 2025-01-29T02:47:40+1000 |
| Inventory | `src/gameTest/resources/compat/genetic-animals-0.11.12.json`: its entities, entity tags, tack items and how each is obtained, tack recipes, adult ages and the methods hooked, read from that jar. A GameTest checks it against the running registries. |

Every fact below was read from that jar (its data files and decompiled classes), not from a source branch. Other
releases are not claimed. On another release the integration still starts: the hooks into its classes are checked as
those classes load, and `/immersive_tanning report` names any that did not apply (that path then keeps Genetic
Animals' own behaviour).

## Which animals drop which hide

| Entity | Hide | What Genetic Animals itself drops as skin |
|---|---|---|
| `eanimod:enhanced_cow` | large | 0-5 leather, by its size, genes, age and chance; none while burning (cooked beef instead) |
| `eanimod:enhanced_mooshroom`, `enhanced_moobloom` | large | as the cow (they inherit its death loot) |
| `eanimod:enhanced_llama` | large | 0-2 leather, or wool if its coat genes say so; none while burning |
| `eanimod:enhanced_sheep` | medium | 1 leather only from short-coated sheep (maximum coat length under 5) that drop no wool |
| `eanimod:enhanced_pig` | medium | none: pork only |
| `eanimod:enhanced_rabbit` | small | rabbit hide (1 + Looting) or white wool, by coat and a chance that grows with size |
| `eanimod:enhanced_chicken`, `enhanced_turtle`, `enhanced_axolotl` | none | none |
| `eanimod:enhanced_horse` | none | registered with attributes only in 0.11.12: no renderer, spawn egg or natural spawning, so it is not covered |
| eggs, llama spit | - | not animals |

The sources are `data/immersive_tanning/immersive_tanning/hide_sources/compat/eanimod/` (`large`, `medium`, `small`
and `none`), at priority 5: above the generic sources, so Genetic Animals' own `#forge:cows`, `#forge:pigs` and
`#forge:rabbits` tags never decide for it, and below a datapack that wants to (priority 6 and up).

The generic sources now also name `#forge:cows`, `#forge:pigs` and `#forge:rabbits` next to the singular tags, for other
mods that use those spellings.

## How a death becomes hides

Genetic Animals has no entity loot tables: its animals drop meat, wool and skin from Java code at death, and then
everything they carry, in one `LivingDropsEvent`. Immersive Tanning records which item came from where while Forge
collects those drops (the animal's own death loot, its inventory, anything worn), then decides the hides once per
death. The loot-table modifier never decides a Genetic Animals hide.

**NATIVE** (the default, `hideMode = "NATIVE"`). The skin the animal really dropped becomes the hides. Each leather is
one leather's worth, made up from the fewest hides no larger than the animal's size (hides yield 1, 2 and 3 leather):

| Leather | Cow (large at most) | Sheep, pig (medium at most) |
|---|---|---|
| 1 | small | small |
| 2 | medium | medium |
| 3 | large | medium + small |
| 4 | large + small | 2 medium |
| 5 | large + medium | 2 medium + small |

Each rabbit hide is one small hide, as on the rack. No skin, no hide: a sheep or llama in wool, a burning animal and a
pig drop none, and a cow can give a smaller hide than a vanilla cow. That is Genetic Animals' genetics at work, not a
lost drop. Its own rules decide when skin drops (age, coat, fire, Looting); Immersive Tanning adds no Looting bonus.

**TANNING** (`hideMode = "TANNING"`). Every adult drops its species' hide (the source's size and count) like a vanilla
animal, whatever Genetic Animals drops: pigs and sheep in wool included, and a burning animal keeps its hide and its
cooked meat. Juveniles (Genetic Animals' own adult age, from its config) drop none. `lootingAddsHides` applies once.

**Either mode:**

- The animal's own leather and rabbit hide are taken when it drops hides and `replaceLeatherDrops` is on. With it off
  they stay next to the hides, so the animal yields both; that is the setting's meaning, not a way to stop hides.
- What the animal carried or wore (chest contents, saddle, bridle, collar, blanket) drops exactly as it was: carried
  leather stays leather, carried rabbit hides and raw hides stay, and a carried raw hide never stands in for the
  animal's own. Genetic Animals' Curse of Vanishing rule for its inventory is unchanged.
- Leather that is not cargo becomes Binding Leather when `replaceLeatherLoot` is on (as for every mob).
- `doMobLoot` off, or drops another mod cancels, means no hide. A hide another mod removes afterwards is not made again.
- A higher-priority source with `"size": "none"` or `"count": 0` stops the hides and keeps the animal's own skin (its
  leather still becomes Binding Leather when `replaceLeatherLoot` is on).

In NATIVE mode a source's `size` is the largest hide and its `count` only matters as 0 (no hides); in TANNING mode both
decide the fixed drop.

### Butchery carcasses

Butchery 5.3.1 decides from its own config and the weapon whether a kill becomes a carcass, and only for a vanilla
animal or one in that animal's singular tag (`#forge:sheep`, `#forge:cow`, `#forge:pig`, `#forge:llama`, ... and the
`#c:` twins; its red mooshroom checks the vanilla class only). Genetic Animals puts its sheep in `#forge:sheep`, so
Butchery makes a sheep carcass from a Genetic Animals sheep (whose skin is `butchery:sheep_skin`, a medium hide on the
rack). Its cows, mooshrooms, pigs, rabbits and llamas are in `#forge:cows`, `#forge:mooshroom`, `#forge:pigs`,
`#forge:rabbits` and `#forge:llamas`, which Butchery does not read, so it makes no carcass of them.

For Genetic Animals, Immersive Tanning does not guess from the weapon: the hide is left out only when a carcass item
(`#immersive_tanning:carcasses`) actually appears where the animal died. Then the animal's own skin goes too and the
carcass supplies it. A cleaver kill that Butchery does not answer drops the hide as usual. (Vanilla animals keep the
rule they have always had: a carcass tool on an animal in `#immersive_tanning:carcass_animals`.)

## Collars, bridles and saddles

- **At the bench.** The leather collar (1 leather) and the leather bridle (4 leather and 2 iron nuggets), Genetic
  Animals' own costs, are made at the Leatherworker's Bench on a strap seam; their crafting-table recipes are routed
  there, as for other leather equipment. They are still Genetic Animals' items: the bench gives them the finish's
  colour (dye them as usual), and they work on the animal exactly as before. Their finish and quality are a record of
  their making; they give the animal nothing (the tooltip says so).
- **Everything else stays where it comes from.** Saddles (basic, western, English), cloth tack and the ringed, belled,
  gold, diamond and wooden pieces come from trades and are not made at the bench. The vanilla saddle keeps its bench
  recipe.
- **Putting tack on keeps it.** Genetic Animals rebuilds a bridle or one of its saddles from its item and colour, and a
  vanilla saddle from nothing, when it is put on by hand, which lost the finish, quality, maker, name and enchantments.
  Now the animal gets the very piece used (one of it), and the one it replaces comes back as it was. Through the
  animal's inventory screen items always moved whole. Saving, reloading and death keep every piece intact.
- `leatherTackAtBench = false` keeps the collar and bridle on their crafting recipes; `modBlacklist = ["eanimod"]` and
  `replaceLeatherEquipmentRecipes = false` work as for any mod.

## Trades

Genetic Animals adds collar, bridle and saddle offers to leatherworkers, shepherds and wandering traders straight into
their offer lists, so Immersive Tanning's usual trade wrapping never sees them. With `replaceLeatherInTrades` on, the
offers it creates ask for Binding Leather instead of vanilla leather (its factories are hooked), and offers saved
earlier are converted the first time a player opens that merchant's trades. Only leather changes: emeralds, wool,
string, saddles, its random dye, the uses, experience, demand, price multiplier and any discount stay. Other mods'
saved offers are left as they are. Genetic Animals' own trade switches still decide whether those offers exist. A
vanilla saddle a leatherworker sells in exchange for a Genetic Animals saddle is a plain saddle, as before.

## Settings

`serverconfig/immersive_tanning-server.toml`:

```toml
[compat.geneticAnimals]
	enabled = true              # off: no hide is added; its drops, tack and equipping are Genetic Animals' own
	hideMode = "NATIVE"         # or "TANNING"
	leatherTackAtBench = true   # the leather collar and bridle at the bench
```

With `enabled = false` Genetic Animals' animals get no hides at all (the generic `#forge:pigs` and similar entries do
not apply to them either); their leather still becomes Binding Leather when `replaceLeatherLoot` is on, and its offers
still deal in Binding Leather when `replaceLeatherInTrades` is on, since those are Binding Leather's own settings.

## Diagnostics

- `/immersive_tanning report` ends with a line on Genetic Animals: the version (and whether it is the verified one),
  whether the integration is on, the mode, where the tack is made, and each hook: `applied`, `failed`, or `pending`
  until its class first loads.
- `/immersive_tanning inspect <entity>` (or `inspect type <entity type>`; operators) shows how one animal's hides are
  decided: juvenile or adult, the winning source with its priority and how it matched, the sources it shadows, which
  stage drops the hide, the mode and what it depends on, what happens to its own skin, and whether a carcass route
  exists. It describes the rule and never rolls a drop.
- At debug level every Genetic Animals death logs one line: natural leather and other skins, how many carried items
  were kept, and the result or why there was none (`integration disabled`, `native skin absent`, `explicit exclusion`,
  `juvenile`, `no loot`, `carcass kill`). Drops that arrive without a record of their origin (posted outside a death),
  or every death if another mod has rewritten `LivingEntity#dropAllDeathLoot` so the record cannot be kept (the
  `death_drops` hook then shows `failed`), are left untouched with a warning, at most eight per session: no hide is
  guessed and no cargo is touched.

## Testing it

```
./gradlew geneticAnimalsJar                      # the pinned release in build/compat/, SHA-1 checked
./gradlew runGameTestServer -PgeneticAnimals     # every GameTest with Genetic Animals loaded (world in run-gametest-ga/)
./gradlew build smokeTestJar geneticAnimalsJar
python3 tools/packtest/ultima_pack_test.py --genetic-animals --pack-mods jei-,butchery-,jade-1.20.1 --expect geneticanimals
python3 tools/packtest/ultima_pack_test.py --genetic-animals --expect dragn,lycanites,alexsmobs,butchery,magic,bench,trades,iceandfire,knife,geneticanimals
```

`-PgeneticAnimals` adds the pinned jar (from Modrinth's maven, `maven.modrinth:genetic-animals:0.11.12`) to the runs
only; nothing compiles against it and it is never shipped. The GameTests spawn its animals through its own spawn path
(so their genes are real), and compare pairs of identical animals (one saved animal loaded twice, the same random seed)
killed with the integration off and on, so every hide is checked against the skin Genetic Animals really dropped. Its
100-tick spawn protection is cleared in the GameTests; the pack test waits it out. The pack test runs the reobfuscated
jar in a copy of a real CurseForge instance: `--pack-mods` keeps only the named pack jars (a small instance without the
whole pack), `--genetic-animals` adds the pinned release.

## Verification record (1.3.0)

Run on 2026-10-27 against the files above (Minecraft 1.20.1, Forge 47.4.23, Java 17):

| Check | Result |
|---|---|
| `./gradlew runGameTestServer` (no Genetic Animals) | 76 of 76 passed |
| `./gradlew runGameTestServer -PgeneticAnimals` | 87 of 87 passed; all five hooks `applied` |
| `./gradlew runClient -PsmokeTest` | 68 of 68 checks passed |
| Pack test, Forge + reobfuscated jar + Genetic Animals 0.11.12 + Butchery 5.3.1 + JEI + Jade (`--pack-mods`) | passed, 83 checks |
| Pack test, the Ultima instance (350 jars) without Genetic Animals | passed, 107 checks |
| Pack test, the Ultima instance with Genetic Animals 0.11.12 added | passed, 148 checks |

What the real kills showed (pack test, reobfuscated jar, default settings): adult cows dropped beef and a small,
medium or large hide (no leather left), or only beef when their genes gave no leather; pigs only pork; a short-coated
sheep its leather as a small hide; a wool sheep wool and no hide; a sheep killed with Butchery's iron cleaver a
`butchery:sheep_carcass` and no hide; a cow killed with the same cleaver its hide (Butchery made no carcass); a llama
carrying named leather x7, rabbit hide x3, large raw hide x2, Binding Leather x5 and Harness leather x4 dropped every one
of them unchanged, plus its own hide when it had leather; a cow wearing a bench-made bridle and saddle (finish,
Integrity, Workmanship, maker, name, colour) dropped both exactly. Of 400 leatherworker offers from its factory, none
asked for vanilla leather (58 to 79 per run asked for Binding Leather).

In the GameTests, pairs of identical animals killed with the integration off and on gave, per species over 18 kills
each, the same meat and wool and exactly the hides their leather was worth (for example cows 24 leather into 15 hides,
rabbits 11 rabbit hides into 11 small hides, pigs and wool sheep none). Bench-made tack put on by hand matched the
stack used byte for byte after swapping, saving and loading, and death; with the integration off Genetic Animals'
rebuilt bridle had lost its data.

Not run: a dedicated server with two players and a reconnect; Genetic Animals with changed adult ages; projectile and
non-player kills; an MCA Reborn villager's trades; JEI absent on a client; a world saved by 1.2.0. Equipping through
the animal's inventory screen and the client sync of tack were checked in Genetic Animals' code (vanilla slot moves,
a full item stack in its equipment packet), not in a running game.
