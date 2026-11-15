# Immersive Tanning × Genetic Animals — Implementation Handoff

Prepared October 27, 2026. Target: Minecraft 1.20.1, Forge 47, Java 17.

## 1. Objective and scope

Implement complete, optional Genetic Animals compatibility in [otectus/ImmersiveTanning](https://github.com/otectus/ImmersiveTanning). Resolve the report that Genetic Animals livestock drop Binding Leather instead of raw hides, then cover the related leatherworking, equipment, trade, configuration, documentation, and multiplayer paths.

The implementation must work with existing animals and worlds. Players must not need to regenerate terrain, replace their livestock, or edit JSON to obtain supported behavior. Genetic Animals must remain optional.

Treat this document as implementation instructions, including the acceptance criteria. Complete the integration rather than stopping after adding entity IDs. Do not modify Genetic Animals itself or publish a release as part of this handoff unless separately instructed.

**Default design decision:** preserve Genetic Animals' actual skin-drop eligibility and material yield. Convert the skin products it really generates into raw tanning materials; do not reroll its genetics or replace its other drops. Provide a clearly documented alternative that grants the usual species-sized Tanning hide to eligible adults. These two policies are specified below so the implementation does not silently choose a balance model.

“Full support” includes preserving existing animal behavior and equipment functionality. It does not require adding hide drops to chickens, axolotls, or turtles, inventing genetic armor bonuses, making trade-only equipment freely craftable, or redesigning Genetic Animals' breeding and husbandry systems.

## 2. Establish the correct baseline first

The source review found a version discrepancy that must be resolved before editing:

| Component | Inspected baseline | Implication |
|---|---|---|
| Immersive Tanning | `main` at `06416690288ea07c6c7258d806762488823d8a7d`; `gradle.properties` says `1.1.1`, Forge `47.4.23` | Public CurseForge currently lists `1.2.0`. Locate the actual current development/release source and preserve its newer work. Do not overwrite it with the reviewed snapshot. |
| Genetic Animals | Upstream `SaemonMoki/ExtendedAnimals`; source commit `32ecdbcec031f5f960c03eb24332288da6f1078d` declares `0_11_12` and Minecraft `1.20.1` | Use the released `0.11.12` JAR as the first runtime compatibility target and record its hash. A source version string alone does not establish binary equivalence. |
| Other upstream branches inspected | `1.20_release` at `7c14eb9602655d05d5cce939c1dc1589ef8d26ad` declares `0_11_12_TurtleUpgrade_1`; `1.20` at `b39a49aae33fcf5a7cc8cb852e95b4de86517f28` declares `0_11_13` | Do not silently substitute these development snapshots for the public release. Test them separately if claiming support. |

The mammal drop classes, entity/item registrations, relevant leather recipes, and animal tags examined were identical between the inspected `0_11_12` source commit and the newer `1.20_release` snapshot. The base animal class was also read at the `0_11_12` source commit for the equipment findings below.

Read applicable repository instructions, then `CLAUDE.md`, `README.md`, `MODMAP.md`, the current changelog, and the relevant implementation. Reconcile the file locations below against the working branch. Choose the next project-appropriate version after identifying the real release baseline; the user has not requested a specific version number.

**Evidence boundary:** this handoff is based on source inspection and official project/Forge documentation. No paired Minecraft instance was run during this review. Source-confirmed mechanisms are distinguished below from runtime checks the implementing agent must perform. See the linked sources at the end.

## 3. Findings the implementation must address

### 3.1 Entity recognition is incomplete

Genetic Animals uses mod ID **`eanimod`**, not `geneticanimals`. Its animals are separate registered entities.

Tanning's large/medium/small source files use `#forge:cow`, `#forge:pig`, and `#forge:rabbit`. Genetic Animals' inspected resources supply `#forge:cows`, `#forge:pigs`, and `#forge:rabbits`. Neither spelling automatically includes the other. Tanning already recognizes `#forge:sheep` and `#forge:llamas`, so this is not a blanket absence of all livestock compatibility. Cow-family variants also have their own entity IDs and tags. [IT sources][it-hide-sources] · [GA tags][ga-tags]

### 3.2 Their skin drops are generated in Java

The inspected GA cows, sheep, llamas, pigs, and rabbits implement `dropCustomDeathLoot`. Their output cannot be fixed reliably by replacing an assumed `eanimod:entities/...` JSON loot table.

Tanning's `HideDrops.onLivingDrops` supplies a fallback, but it requires both a matching hide source and an item in `replaced_by_hides`. Without recognition, its final Binding Leather conversion still runs. This is a source-backed explanation consistent with the reported symptom; reproduce it against the player's version before marking the report fixed. [IT drop processing][it-drops]

### 3.3 A recognized animal does not always produce native skin

The upstream classes contain material differences:

| Animal | Observed native behavior relevant to tanning |
|---|---|
| Cow | Leather amount depends on size, genetics, age, and random results; the inspected burning path emits cooked meat without leather. |
| Mooshroom / moobloom | Cow-derived entities; verify inherited behavior and transformations in the target JAR. |
| Sheep | Coat and age influence whether it emits wool or leather; preserve the chosen non-skin output. |
| Llama | Coat genes can select wool rather than leather; age and fire also matter. |
| Rabbit | May produce rabbit hide or wool; size, coat, age, fire, and Looting affect its outputs. |
| Pig | The inspected custom death method emits pork, not leather. A fallback requiring preexisting leather cannot grant it a Tanning hide. |

Do not copy gene-array indices or reimplement GA's random drop formulas. Observe the actual native output. These are compatibility behaviors to respect, not bugs to “correct” in the other mod. [GA drop implementations][ga-entities]

### 3.4 Carried items are mixed into the final death-drop collection

GA's `EnhancedAnimalAbstract.dropEquipment()` emits its animal inventory. A llama can therefore drop carried leather, hides, finished leather, and tack alongside biological loot. Tanning currently scans the combined collection and treats the presence of any raw hide as evidence that a hide was already generated. That is insufficient for an inventory-bearing animal.

The new integration must distinguish natural skin, equipment/cargo, and its own generated hides. Never turn carried leather into harvested hides, delete carried rabbit hides, or suppress a legitimate hide because the animal was carrying one. [GA base animal][ga-base]

### 3.5 Equipping tack can discard craftsmanship metadata

`EnhancedAnimalAbstract.getReplacementItemWithColour()` constructs a fresh stack and transfers its color. `EnhancedAnimalRideableAbstract.saddleAnimal()` separately constructs a fresh vanilla saddle stack. These paths can lose Tanning's finish, integrity, workmanship, and crafter data, as well as other stack metadata. Merely adding bench recipes does not solve this. [GA base animal][ga-base] · [GA riding equipment][ga-riding]

### 3.6 Some GA trades bypass ordinary listing wrappers

`EventSubscriber` adds generated offers directly to merchant offer lists, including from `LivingTickEvent` and wandering-trader setup. `EanimodVillagerTrades` uses vanilla leather as payment in several cases. Tanning's normal listing wrappers do not automatically intercept direct additions. Inspect the creation path instead of assuming `VillagerTradesEvent` covers every offer. [GA event subscriber][ga-events] · [GA trades][ga-trades]

### 3.7 Leather tack has existing acquisition rules

The inspected simple recipes produce a leather collar from one leather, and a leather bridle from four leather plus two iron nuggets. Numerous saddle, collar, and bridle variants are registered, with many supplied through trades. Most inspected tack is unstackable but not damageable. Tanning's automatic bench detection requires equipment classification; unstackability alone is insufficient. [GA items][ga-items] · [GA recipes][ga-recipes] · [IT recipe detection][it-recipes]

## 4. Required coverage and inventory

Create `docs/compat/genetic-animals.md` and a machine-readable test inventory that records the actual supported JAR's entity IDs, relevant tags, leather recipes, tack IDs, acquisition methods, and version/hash. The following is the source-grounded starting inventory, not permission to invent missing content:

| Entity ID | Standard Tanning policy | Native policy |
|---|---|---|
| `eanimod:enhanced_cow` | One large raw hide | Convert actual native leather yield |
| `eanimod:enhanced_mooshroom` | One large raw hide | Convert actual native leather yield |
| `eanimod:enhanced_moobloom` | One large raw hide | Convert actual native leather yield |
| `eanimod:enhanced_llama` | One large raw hide | Convert actual leather; preserve wool outcomes |
| `eanimod:enhanced_sheep` | One medium raw hide | Convert actual leather; preserve wool outcomes |
| `eanimod:enhanced_pig` | One medium raw hide | No added hide when GA emits no native skin |
| `eanimod:enhanced_rabbit` | One small raw hide | Convert actual rabbit hides; preserve wool outcomes |
| `eanimod:enhanced_horse` | Large, only if present and functional in the supported build | Convert actual native leather if present and functional |
| `eanimod:enhanced_chicken`, `enhanced_turtle`, `enhanced_axolotl` | No hide additions | Preserve native behavior |
| Eggs, projectiles, other non-livestock entities | No hide additions | Preserve native behavior |

`enhanced_horse` exists in the inspected registry, but registration does not prove a finished survival feature. Do not enable spawning or advertise unavailable horse content. Handle the registered entity safely and document its tested availability. Check inherited cow variants, mooshroom transformations, summoned animals, naturally replaced animals, and previously saved animals.

Use explicit IDs for GA coverage. Also add compatible plural tag aliases to generic Tanning definitions without removing existing singular entries. Do not populate a global animal tag with guessed IDs merely to make Tanning recognize them. Avoid substring heuristics such as matching every entity whose name contains `cow`.

## 5. Drop policy and data design

### 5.1 Configuration

Add server-owned, synced compatibility settings using the existing `ServerConfig` access pattern. Suggested names may be adapted to current conventions, but preserve these semantics:

| Setting | Default | Required behavior |
|---|---|---|
| `compat.geneticAnimals.enabled` | `true` | Enables the GA integration when `eanimod` is installed; absent mod is a normal inactive state. |
| `compat.geneticAnimals.hideMode` | `NATIVE` | `NATIVE` observes native skin output; `TANNING` grants the configured species hide to eligible adults. |
| `compat.geneticAnimals.leatherTackAtBench` | `true` | Routes eligible existing leather tack recipes through the bench. Does not unlock trade-only variants. |

When compatibility is disabled, explicitly bypass GA-specific hide handling in both entry points; do not accidentally reactivate it through newly added generic aliases. Existing global Binding Leather settings still apply. Keep this distinction visible in diagnostics.

Honor existing `replaceLeatherDrops`, `lootingAddsHides`, `replaceLeatherLoot`, `replaceLeatherInTrades`, `replaceLeatherEquipmentRecipes`, automatic detection settings, and exclusions. In particular, `replaceLeatherDrops=false` currently means retaining original skin items alongside hides; it is not a hide-disable switch. Document the additional yield this intentionally permits.

### 5.2 Native mode — default

Let GA generate its natural output once. Convert only the actual biological skin stacks identified by provenance:

- Each vanilla leather unit represents one eventual finished-leather unit. Use the existing small/medium/large hide yields of 1/2/3 to preserve the total without adding a new item or arbitrary yield NBT.
- Treat the species size in the coverage table as the maximum normal hide size. Decompose the leather amount into the fewest allowed hides, using smaller hides for the remainder. Example: a cow's 4 leather becomes one large and one small raw hide; 2 leather becomes one medium; a sheep's 1 leather becomes one small.
- Each actual rabbit-hide item becomes one small raw hide, matching Tanning's existing rabbit-hide mounting behavior. Do not reinterpret rabbit hides as finished vanilla leather units.
- Zero native skin means zero added hide, including a wool-only result, burning result, or pig with no skin output. Preserve all meat, wool, rabbit feet, scutes, eggs, and other output.
- Native eligibility, age thresholds, and original Looting effects belong to GA. Do not add a second Looting bonus in this mode. Explain that `lootingAddsHides` governs fixed Tanning yields, while native mode retains GA's own results.
- Preserve native amounts even when multiple stacks carry them. Use checked arithmetic and legal stack sizes. Apply the highest-priority source's explicit suppression first.

This policy intentionally means a cow is not guaranteed to yield a large hide, and a wool-bearing animal may yield none. Explain this in JEI and the compatibility guide so expected genetics do not look like another drop bug.

### 5.3 Standard Tanning mode

Produce one hide of the table's species size, or the winning datapack source's configured count, for each eligible adult death, even if GA supplies no skin or supplies wool instead. Preserve wool and meat. Do not use “contains vanilla leather” as the condition; this would leave pigs broken.

Use GA's actual `isBaby()` behavior and configured maturity, rather than fixed tick thresholds or vanilla negative-age assumptions. Never add these fixed hides to babies. In this mode, fire does not prevent the ordinary Tanning hide; preserve GA's cooked-meat output. State that this deliberately follows Tanning's hide progression rather than GA's skin availability.

Apply Tanning's optional Looting bonus once, using its existing rule. A verified carcass/skin-producing integration can replace the direct hide as described in section 7. Respect `doMobLoot`, canceled drops, and entity lifecycle constraints.

### 5.4 Datapack integration

Add GA-specific files under:

`src/main/resources/data/immersive_tanning/immersive_tanning/hide_sources/compat/eanimod/`

Reuse `entities`, `size`, `count`, `trait`, and `priority`. Give explicit GA defaults higher priority than generic aliases, while allowing pack authors to override them. Preserve deterministic tie handling. A higher-priority `size: none` or `count: 0` must stop hides without destroying unrelated/native items.

Retain the distinction between **no matching source** and **an explicit suppression**; `forEntity()` currently returns `null` for both. Add a resolved decision API if necessary while keeping old callers compatible. Do not let a compatibility fallback override a pack's deliberate suppression.

In `NATIVE` mode, `size` caps the decomposition above; positive fixed `count` is not a multiplier for native units. In `TANNING` mode, `size` and `count` determine the fixed output. Zero count suppresses both. Make these mode-dependent meanings explicit in documentation and diagnostics.

If adding a processing-stage field, such as `processing: death_event`, implement and validate it in the custom loader and retain the previous default for old files. Do not insert unparsed fields and assume they affect behavior. Likewise, Forge recipe conditions are not automatically evaluated by Tanning's custom `SimpleJsonResourceReloadListener`; explicitly implement conditional handling if using it there. Use `forge:mod_loaded` for supported recipe conditions and `required: false` for optional tag entries. [Forge conditional data][forge-conditional]

## 6. Implement one authoritative death-drop path for GA

### 6.1 Execution order

Keep ordinary non-GA behavior stable. For a recognized GA entity under the new integration:

1. Resolve its source, policy, optional-mod status, and explicit exclusion.
2. Defer Tanning hide generation to the real server death/drop transaction. The global loot modifier must not independently generate a GA hide.
3. Let GA calculate natural drops. Identify biological skin separately from cargo/equipment.
4. At the coordinated finalization stage, honor canceled events, the loot gamerule, genuine carcass handling, and the selected policy.
5. Consume only the eligible natural skin when `replaceLeatherDrops` is enabled; emit the planned raw hides once.
6. Apply Binding Leather conversion to eligible remaining ordinary leather after hide processing. Exclude protected cargo/equipment from the integration's processing.
7. Clear transient transaction state on every exit, including cancellation and exceptions.

Forge global loot modifiers are ordered and can transform earlier results. Keep `replace: false` in the modifier list, and preserve hide-before-binding behavior wherever table processing remains active. Do not “fix” this by adding Binding Leather globally to `replaced_by_hides`: it is a legitimate finished material and can be carried, traded, or created elsewhere. [Forge GLM behavior][forge-glm]

### 6.2 Provenance and inventory protection

A combined `LivingDropsEvent` item list alone does not reveal what came from the animal's skin. Implement a narrow GA adapter or optional mixin that records which captured item entities were emitted by natural death-loot code and which by `dropEquipment`/inventory code. Prefer method-scoped capture around known GA paths over broad interception of every item spawned in the world.

Requirements:

- Preserve carried stacks, their count, NBT, and capabilities. Test cargo containing exactly the same leather/rabbit-hide items as natural output.
- Preserve equipped saddles and other tack; respect GA/Minecraft's existing vanishing-curse behavior.
- A carried raw hide must not count as a generated hide. Avoid the current whole-list `contains(raw_hides)` shortcut for GA deduplication.
- Account for inherited or nested drop methods without marking a single production twice.
- Do not serialize temporary bookkeeping onto items or entities. Do not retain entities/worlds in static maps. Use scoped cleanup or bounded per-event state, with server-stop cleanup where applicable.
- Reprocessing the same death transaction must not produce another hide. If another mod intentionally removes a Tanning-generated hide, do not continually regenerate it.
- If a known supported-version adapter cannot establish provenance, preserve the existing items and report a bounded diagnostic; do not guess and destroy cargo. Such a failure is a failed compatibility test, not acceptable normal operation for the declared supported version.

The exact hook depends on the shipped Forge/GA classes. Verify descriptors and captured-drop behavior against the real JAR before implementing. Do not replace GA's whole death method or its genetic calculations.

### 6.3 Optional dependency and runtime safety

Keep registry-ID/data logic independent of GA classes. Isolate direct class references in an adapter loaded only when GA is available. If mixins are required for provenance, equipment, or offers, gate their application before target resolution using the repository's appropriate Forge loading-stage mechanism. Do not rely on a late `ModList` check after an optional target was already loaded.

Use narrowly scoped injections into the supported methods; avoid overwriting full methods. Required hooks for the declared supported version must be validated. Do not hide a broken injection behind permissive settings and still claim compatibility. An unknown version may disable a narrowly unsafe feature with a clear diagnostic; it must not silently corrupt inventory or prevent a Tanning-only server from starting.

Use a pinned development-only runtime dependency and compile-only dependency if needed. Do not bundle GA classes, assets, or its JAR in Tanning's release. Recheck mixin remapping in both development and reobfuscated production launches.

## 7. Butchery and other existing integrations

Preserve Tanning's existing carcass approach, but do not blindly add all GA species to `carcass_animals` or assume a cleaver guarantees a usable GA carcass.

For each supported third-party carcass path, verify the exact GA entity is recognized and actually yields a compatible skin. Only then suppress the direct hide. If no such integration exists, ordinary GA hide handling must continue; holding an unrelated cleaver must not delete the only skin source.

Where a real GA carcass supplies a separate skin item, add a conditional `hide_mounting` recipe for that actual registered item. Never invent `eanimod:cow_hide` or `eanimod:leather` IDs: the inspected natural mammal paths emit vanilla leather/rabbit hide. Verify other versions before adding new mappings.

Test GA + Butchery independently from Tanning + Butchery + vanilla livestock. Preserve existing Alex's Mobs, Lycanites, Livestock Overhaul, and other compatibility paths. Tanning should not start altering GA meat recipes or unrelated food tags.

## 8. Leatherworking and equipment

### 8.1 Route existing eligible recipes through the bench

Implement the following base recipes through the existing bench/detection system, with conditional data and the original costs:

| Output | Leather cost | Other materials | Existing acquisition |
|---|---:|---|---|
| `eanimod:collar_basic_leather` | 1 | None | Ordinary shapeless recipe |
| `eanimod:bridle_basic_leather` | 4 | 2 iron nuggets | Ordinary shaped recipe |
| `minecraft:saddle` | Existing Tanning cost | Existing Tanning auxiliaries | Already supported; ensure it works on GA animals |

Classify the actual leather equipment in `leatherwork_equipment`. Prefer existing automatic detection for these standard recipes; use explicit conditional recipes if needed to retain coverage when automatic detection is off. Avoid duplicate bench entries.

Inventory all registered leather/mixed-leather tack variants: basic, English, and western/pommel saddles; decorated bridles; ring/bell collars. Preserve their existing trade or transformation routes. Add bench equivalents for genuine existing base-material recipes only. Do not invent dozens of new recipes to make a coverage table appear complete.

Keep cloth-only equipment on its native acquisition path. Preserve dyeing, recoloring, naming, decoration, upgrade, and exchange operations. Do not flatten dynamic recipes to a fixed default output. Respect recipe IDs, item exclusions, namespace exclusions, and `replaceLeatherEquipmentRecipes=false` so pack makers can retain both crafting and bench recipes.

Use an appropriate existing seam or a small dedicated collar/bridle seam if the current system benefits from it. Verify completion with the actual item icon, not just recipe registration. Reuse the bench's escrow, cancellation, refunds, and server validation.

### 8.2 Preserve complete stacks when equipping

For confirmed metadata-losing GA paths, make the smallest optional adaptation that installs a one-count copy of the incoming stack instead of reconstructing an item from its type/color. Preserve:

- Tanning's entire `immersive_tanning` compound, including finish, integrity, workmanship, trait, crafter, and player-made identity.
- Name, dye, enchantments, damage where applicable, and other mod metadata/capabilities.
- Correct count consumption, the replaced equipment return, creative-mode behavior, offhand behavior, and server authority.

Cover both `getReplacementItemWithColour()` and the separate vanilla-saddle branch in `saddleAnimal()`. Exercise direct right-click equip, animal GUI insertion, replacement, removal, death, save/load, chunk reload, and client synchronization. Do not fix metadata only in a tooltip or only on the client.

### 8.3 Do not promise unsupported stat effects

GA tack in the inspected registration is generally nondamageable, and Tanning's armor effects inspect ordinary armor equipment slots. Storing craftsmanship on a bridle does not automatically give an animal speed or armor.

Preserve quality as item metadata and provenance; use truthful contextual tooltips where a generic finish description would imply a nonexistent effect. Do not add durability to nondamageable GA items merely to enable repairs. Damaged compatible items should use normal field repair only when genuinely damageable and supported.

New mount bonuses, genetic-quality perks, or durability systems are outside this compatibility change. Preserve existing player armor and horse-armor behavior; add explicit tests if any hook touches them.

## 9. Merchant compatibility

Adapt GA's actual offer factories or a similarly precise creation boundary, including `getEanimodTrade` and `getWanderingEanimodTrade`, after verifying the target JAR. Its direct additions must receive the same vanilla-leather-to-Binding-Leather behavior as ordinary listings when `replaceLeatherInTrades=true`.

- Convert both payment slots and results when they contain vanilla leather. Leave wool, emeralds, saddles, and other ingredients unchanged.
- Preserve all offer state: uses, maximum uses, XP, reward-XP flag, demand, multiplier, special price, and result color/name/NBT. Audit `BindingLeather.offer` rather than assuming its current reconstruction preserves every field.
- Preserve GA's randomized offerings, dye choices, profession/level rules, and its own trade enable/disable options. Do not reroll offers during normalization.
- Make conversion idempotent. Do not append duplicate offers or poll every villager each tick from Tanning.
- Handle already-saved affected offers lazily at an appropriate merchant-use/load boundary, without resetting trades or discounts. Keep migration narrowly scoped and respect the conversion setting.
- Preserve any existing Tanning village-made metadata. If attaching it to newly sold leather tack, use merchant provenance, never a player's crafting identity. Cloth-only merchandise must remain cloth.

Normal merchant exchanges may buy one item and sell a newly generated one; do not claim the player's saddle craftsmanship transfers through that transaction unless the existing mechanic actually supports it. Equipping/removing the same item, in contrast, must preserve it exactly.

Test vanilla villagers, wandering traders, GA trade options, and MCA Reborn if installed. Existing offers with unusual NBT or discounts must remain usable after save/reload.

## 10. Player guidance and diagnostics

Add localized JEI/help text that explains:

- Genetic Animals is detected automatically; raw hides use the normal rack → vat → finish/dry → bench loop.
- In default native mode, skin availability follows the animal's genetics, coat, age, and death circumstances. A wool-only result is intentional; native pigs may supply no hide.
- Native leather amount determines the combination of raw-hide sizes, so a cow can yield a smaller hide.
- Standard Tanning mode is available for predictable species-sized adult hides, including pigs.
- Binding Leather is for ordinary leather recipes, not a substitute raw hide or a material that should be “untanned.”
- Collars and bridles made at the bench retain their identity, dyeability, and ordinary GA use; do not advertise unsupported animal stat bonuses.

Extend `/immersive_tanning report` with a concise compatibility section. Add an operator-only targeted inspection command, for example `/immersive_tanning inspect <entity>`, that reports:

- Entity ID, detected GA version, selected mode, and whether the integration is active.
- Winning source ID/priority, matching tags, size/count, and any explicit suppression.
- Which processing stage owns hide generation and whether a carcass path is applicable.
- Native mode's dependency on actual skin output, without predicting or rerolling a future random drop.
- Whether required equipment/trade hooks are active for the supported version.

Use bounded debug logging for actual test kills: natural skin amounts, protected cargo count, chosen result, and skip reason. Avoid per-tick logs, entire genomes, UUID dumps, or unbounded event histories. Distinguish “integration disabled,” “native skin absent,” “explicit exclusion,” and “adapter failed.”

A higher-priority invalid datapack should produce a resource-specific useful error. Missing optional GA resources when the mod is absent should not flood logs.

## 11. Implementation order and suggested files

| Order | Work | Main touchpoints |
|---|---|---|
| 1 | Resolve release baseline; record runtime registry/recipe inventory; reproduce the original report | Build configuration, compatibility documentation, test fixtures |
| 2 | Add source matching, aliases, explicit suppression, mode configuration | `HideSourceLoader`, `ServerConfig`, `hide_sources/compat/eanimod/` |
| 3 | Coordinate death-only handling, provenance, and deduplication | `HideDropModifier`, `HideDrops`, a small `compat/geneticanimals/` adapter and narrowly optional mixins |
| 4 | Integrate leather recipes and preserve equipped stacks | `TanningData`, equipment tags/seams, optional GA equipment hooks |
| 5 | Normalize direct GA offers and existing affected offers | `BindingLeather`, `TanningVillagerTrades`, optional GA offer adapter |
| 6 | Complete help, JEI, diagnostics, and regression coverage | `TanningCommands`, JEI plugin, language files, GameTests, smoke/pack tests |

Keep helpers small and make transformation decisions testable without importing GA everywhere. Reuse existing station and recipe behavior. Preserve old datapack parsing and unrelated mod behavior. If a proposed data field or command is unnecessary after inspecting the current branch, implement the simpler equivalent while meeting every observable requirement.

## 12. Verification — required before declaring complete

### 12.1 Automated checks

Extend the existing GameTests with meaningful outcomes, not just assertions that a method was called. The existing synthetic tests stand vanilla entities in for other mods; keep those, but add a separate profile that actually loads the supported GA JAR.

Test at least:

1. Every mapped registered GA entity resolves the expected source; aliases do not produce duplicate matches. Generic sheep/llama support remains functional.
2. Explicit `size:none` / zero-count overrides win; a datapack reload updates the decision without restart or stale caches.
3. Native leather counts 0–5 decompose correctly into existing hide sizes, including stack splitting; rabbit hides retain their own conversion rule.
4. Native mode preserves zero-skin/wool results and does not double-apply Looting; standard mode grants one species hide even when a pig emits only pork.
5. All four combinations of leather-drop removal and Binding Leather loot conversion behave as documented.
6. Cargo and biological output can contain identical leather/rabbit-hide stacks without cargo loss. Carrying a raw hide does not suppress the generated one.
7. Repeated handling, nested callbacks, both loot entry points, and cancellation cannot duplicate output. Test another handler deliberately removing a generated hide.
8. GA absent, compatibility disabled, unsupported optional entity IDs, and dedicated-server classloading are safe.
9. Collar and bridle costs/output are correct; disabling recipe replacement retains native recipes; exclusions work; reload does not duplicate bench recipes.
10. Dye/name/enchantment/craftsmanship metadata survives equipment changes and persistence; one item is consumed and the old equipment returned exactly once.
11. Late-added GA trades use the configured currency item, preserve state, and remain stable on repeated normalization/reload.
12. Carcass suppression occurs only for a verified supported skin route. No usable skin route means no accidental loss of Tanning hides.

### 12.2 Real-game matrix

Use the packaged, reobfuscated Tanning JAR for at least one final client/server run, not only development classpaths.

| Scenario | Required evidence |
|---|---|
| Tanning alone | Startup, vanilla drops, full tanning loop, recipes and trades still work |
| Tanning + GA 0.11.12 | Baseline reproduction and corrected cow-family behavior; both hide policies |
| Sheep/llama/rabbit coat variants | Native wool and skin outcomes retained; standard policy explained and correct |
| Pigs; registered horse if usable | No hidden dependency on preexisting leather; honest coverage classification |
| Babies/juveniles/adults, changed GA adult-age settings | Native eligibility preserved; standard policy does not grant adult hides early |
| Normal, fire, projectile, Looting, non-player death | Policy-specific outputs and unrelated drops correct |
| `doMobLoot=false`, canceled death/drop events | No replacement drops spawned outside the controlled loot lifecycle |
| Loaded animal carrying leather, Binding Leather, rabbit hides, raw hides, finished leather, and named tack | Inventory survives unchanged except legitimate native death rules |
| Equipment via right-click and GUI; swapping, removal, death, restart | Metadata and counts preserved on both clients |
| Leatherworker/wandering trader, new and existing offers | Costs, discounts, uses, colors, and disabled trade settings preserved |
| Existing world and existing livestock | No conversion/re-spawn/reset requirement; settings and saved stations survive |
| Dedicated server with two players and a reconnect | No client-only classes, duplicate drops, stale recipes, or metadata disagreement |
| GA + Butchery, then the user's larger pack | Only verified carcasses suppress hides; existing compat remains intact |
| JEI/Jade present and absent | Useful integration display where installed; no mandatory dependency |

Initialize GA test animals through its normal spawn/genetics path. A raw entity constructor with uninitialized genes is not a valid runtime reproduction. Use controlled fixtures or repeatable seeds where possible; avoid flaky assertions that assume every random cow naturally drops leather.

For paired native-output tests, compare equivalent initialized animals/random state with and without the adapter. Do not simulate the “without” side by consuming the entity's random stream once before the actual kill. Preserve all non-skin results.

### 12.3 Build and release gates

Use the repository's documented Gradle wrapper/helper. Typical existing commands are:

```bash
./gradlew build
./gradlew runGameTestServer
./gradlew runClient -PsmokeTest
```

Add and document a reproducible GA integration-test profile, with its runtime JAR version/hash, rather than inventing a flag in documentation that does not exist. The existing Ultima pack-test runner can be extended for GA if available; also provide a minimal isolated test setup so the full pack is not a prerequisite.

Record commands, outcomes, tested versions, logs, representative before/after item counts, recipe checks, and equipment metadata comparisons. Compilation alone does not establish compatibility. If a runtime test cannot be performed, explicitly list the unverified requirement and do not label the integration fully validated.

## 13. Definition of done and implementation report

The change is complete when:

- The original Binding-Leather-only reproduction is resolved for the tested GA release under default settings whenever GA actually supplies skin.
- Both configured hide policies work as specified, including pigs, coat-dependent output, maturity, fire, and Looting.
- Biological materials and carried items are correctly separated; no duplication, cargo conversion, or unintended item loss occurs.
- The entire raw hide → finished leather → bench loop works with the integrated drops.
- Existing leather collar/bridle recipes are integrated; other tack retains its legitimate acquisition path and ordinary behavior.
- Equipped craftsmanship and item metadata survive all supported interactions and persistence paths.
- Direct GA merchant offers remain usable with Binding Leather and preserve their state.
- Old worlds, optional-mod absence, datapack overrides, multiplayer, and existing integrations pass their checks.
- Documentation, changelog, supported-version inventory, diagnostics, and automated/runtime evidence are included.

The coding agent's final report must state the source baseline used, exact files/behavior changed, tested mod versions, executed checks, and remaining limitations. Identify any source-to-release differences discovered. Do not claim a released fix or exhaustive compatibility across untested GA versions.

## 14. Primary sources

All links below were used for this review. Repository links are pinned to inspected commits where applicable. Runtime verification against the shipped JAR remains necessary.

- [Immersive Tanning source/version baseline][it-version] and [published release listing][it-release].
- [Genetic Animals source/version baseline][ga-version] and [official project/release listing][ga-release].
- [Tanning entity hide sources][it-hide-sources], [source loader][it-loader], [loot-table modifier][it-glm], and [death-drop fallback][it-drops].
- [Tanning Binding Leather processing][it-binding], [recipe detection][it-recipes], [hide sizes][it-sizes], and [existing compatibility tests][it-tests].
- [GA entity registry][ga-registry], [animal tag resources][ga-tags], and [mammal implementation directory][ga-entities].
- [GA base animal/inventory/equipment behavior][ga-base], [riding equipment behavior][ga-riding], and [configuration][ga-config].
- [GA equipment registrations][ga-items], [leather bridle recipe][ga-bridle], [leather collar recipe][ga-collar], and [other recipes][ga-recipes].
- [GA direct offer injection][ga-events] and [offer generation][ga-trades].
- [Forge 1.20.1 global loot modifiers][forge-glm] and [conditionally loaded data][forge-conditional].

[it-version]: https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/gradle.properties
[it-release]: https://www.curseforge.com/minecraft/mc-mods/immersive-tanning
[ga-version]: https://github.com/SaemonMoki/ExtendedAnimals/blob/32ecdbcec031f5f960c03eb24332288da6f1078d/gradle.properties
[ga-release]: https://www.curseforge.com/minecraft/mc-mods/genetic-animals
[it-hide-sources]: https://github.com/otectus/ImmersiveTanning/tree/06416690288ea07c6c7258d806762488823d8a7d/src/main/resources/data/immersive_tanning/immersive_tanning/hide_sources
[it-loader]: https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/src/main/java/com/otectus/immersivetanning/hide/HideSourceLoader.java
[it-glm]: https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/src/main/java/com/otectus/immersivetanning/loot/HideDropModifier.java
[it-drops]: https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/src/main/java/com/otectus/immersivetanning/loot/HideDrops.java
[it-binding]: https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/src/main/java/com/otectus/immersivetanning/leather/BindingLeather.java
[it-recipes]: https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/src/main/java/com/otectus/immersivetanning/recipe/TanningData.java
[it-sizes]: https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/src/main/java/com/otectus/immersivetanning/hide/HideSize.java
[it-tests]: https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/src/gameTest/java/com/otectus/immersivetanning/test/CompatGameTests.java
[ga-registry]: https://github.com/SaemonMoki/ExtendedAnimals/blob/32ecdbcec031f5f960c03eb24332288da6f1078d/src/main/java/mokiyoki/enhancedanimals/init/ModEntities.java
[ga-tags]: https://github.com/SaemonMoki/ExtendedAnimals/tree/32ecdbcec031f5f960c03eb24332288da6f1078d/src/main/resources/data/forge/tags/entity_types
[ga-entities]: https://github.com/SaemonMoki/ExtendedAnimals/tree/32ecdbcec031f5f960c03eb24332288da6f1078d/src/main/java/mokiyoki/enhancedanimals/entity
[ga-base]: https://github.com/SaemonMoki/ExtendedAnimals/blob/32ecdbcec031f5f960c03eb24332288da6f1078d/src/main/java/mokiyoki/enhancedanimals/entity/EnhancedAnimalAbstract.java
[ga-riding]: https://github.com/SaemonMoki/ExtendedAnimals/blob/32ecdbcec031f5f960c03eb24332288da6f1078d/src/main/java/mokiyoki/enhancedanimals/entity/EnhancedAnimalRideableAbstract.java
[ga-config]: https://github.com/SaemonMoki/ExtendedAnimals/blob/7c14eb9602655d05d5cce939c1dc1589ef8d26ad/src/main/java/mokiyoki/enhancedanimals/config/GeneticAnimalsConfig.java
[ga-items]: https://github.com/SaemonMoki/ExtendedAnimals/blob/32ecdbcec031f5f960c03eb24332288da6f1078d/src/main/java/mokiyoki/enhancedanimals/init/ModItems.java
[ga-bridle]: https://github.com/SaemonMoki/ExtendedAnimals/blob/32ecdbcec031f5f960c03eb24332288da6f1078d/src/main/resources/data/eanimod/recipes/bridle_basic_leather.json
[ga-collar]: https://github.com/SaemonMoki/ExtendedAnimals/blob/32ecdbcec031f5f960c03eb24332288da6f1078d/src/main/resources/data/eanimod/recipes/collar_basic_leather.json
[ga-recipes]: https://github.com/SaemonMoki/ExtendedAnimals/tree/32ecdbcec031f5f960c03eb24332288da6f1078d/src/main/resources/data/eanimod/recipes
[ga-events]: https://github.com/SaemonMoki/ExtendedAnimals/blob/32ecdbcec031f5f960c03eb24332288da6f1078d/src/main/java/mokiyoki/enhancedanimals/util/handlers/EventSubscriber.java
[ga-trades]: https://github.com/SaemonMoki/ExtendedAnimals/blob/7c14eb9602655d05d5cce939c1dc1589ef8d26ad/src/main/java/mokiyoki/enhancedanimals/util/EanimodVillagerTrades.java
[forge-glm]: https://docs.minecraftforge.net/en/1.20.1/resources/server/glm/
[forge-conditional]: https://docs.minecraftforge.net/en/1.20.1/resources/server/conditional/
