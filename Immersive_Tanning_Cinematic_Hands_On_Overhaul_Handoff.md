# Immersive Tanning — Cinematic, Hands-On Overhaul
## Coding-agent implementation handoff

**Prepared for Jade • 14 November 2026**

**Target:** [otectus/ImmersiveTanning](https://github.com/otectus/ImmersiveTanning)  
**Primary interaction reference:** [Pocky-l/handson-delight](https://github.com/Pocky-l/handson-delight)

> Rebuild Immersive Tanning around handling actual materials at its workstations. The camera approaches the work; the knife touches the hide; flesh comes away where the player scrapes; cords pull the hide taut; leather separates along cuts; thread passes through holes and draws panels together. These actions must cause the gameplay changes the player sees.

This is authorization for a substantial redesign of the current interaction model. Replace existing minigames, screens, camera handling, workstation rendering, and supporting architecture wherever necessary. Preserve useful crafting depth and existing worlds. A visual reskin of the current screens does not satisfy this brief.

This document contains **verified source observations** and **proposed implementation requirements**. All new mechanics, class names, schemas, timings, and performance budgets below are proposals unless explicitly described as existing. Research consisted of repository and source inspection; the mod and reference projects were not built or playtested for this handoff.

## 1. Establish the correct baseline

### 1.1 Inspected revisions

| Project | Inspected revision | Relevant facts |
|---|---|---|
| Immersive Tanning | `main`, `06416690288ea07c6c7258d806762488823d8a7d` | `gradle.properties`: mod 1.1.1, Minecraft 1.20.1, Forge 47.4.23; build uses Java 17 and official mappings. |
| Hands-On Delight | `main`, `54697d517938fa9a5876b4a65ab2a38abf331952` | Mod 1.4.1; includes Forge and Fabric source sets; depends on Diegesis 1.0.0-beta.9. |
| Diegesis | `main`, `7b9b343b49c31bbaf56e2b078668166fb7e5a068` | Inspected 1.20.1 source declares 1.0.0-beta.9; provides station scenes, camera rigs, direct input, state integration, and observer presentation. |

Start by reading the actual checkout's instructions, `CLAUDE.md`, `MODMAP.md`, `README.md`, and `docs/immersive_tanning.md`. Record its HEAD and compare it with the revision above. The supplied GitHub default branch may lag a local development version or a published release. Preserve any newer functionality found there; do not downgrade the project to match this research snapshot.

Keep this overhaul on the checkout's existing Minecraft/loader target. For the inspected baseline that means **Forge 1.20.1 / Java 17**. A NeoForge or Fabric port is a separate task.

The user's new direction supersedes the old brief's screen and rhythm-minigame prescriptions. Existing compatibility, inventory safety, progression contracts, and established finish identities remain requirements unless this document explicitly replaces their behavior.

### 1.2 What already exists

The mod already has more physical presentation than a blank inventory UI:

- `TannersRackRenderer` drapes a raw hide over a beam, renders finish-dependent tension, and changes wet leather toward its finished color during drying.
- `TannersVatBlockEntity` already has **no GUI**, supports water, tannin and hides in any order, charges each hide once, and pauses tanning when drained.
- `LeatherworkersBenchRenderer` shows a leather panel and the working result item.
- `FleshingSurface` already supplies deterministic scraping, directional grain, coverage, control, and overwork.
- `SessionManager` owns server-side scoring, tool/distance checks, progression events, and material transfers.
- The bench persists input escrow; the current interrupted-session recovery drops stale escrow after loading.
- Recipe detection, hide mounting, innate traits, finishes, Binding Leather, repair, Jade, JEI, and modpack compatibility already exist.

The principal gap is that important craftsmanship still occurs in `FleshingScreen`, `FinishSelectScreen`, and `LeatherworkScreen`. The world renderers do not themselves provide the full direct-manipulation craft described here.

**Reuse the sound underlying systems. Replace the interaction experience and the state model where necessary to support it.**

### 1.3 What to learn from Hands-On Delight

Inspect its station declarations, `BoardBehavior`, `ToolStroke`, recipe books, scene resources, and the Diegesis public API.

Carry over these principles:

| Reference principle | Tanning application |
|---|---|
| Interaction happens on recognizable block parts | Work the hide, pull a cord, select a leather roll, or take a finished piece. |
| Tools have a grip, working point, and contact pose | A scraping blade rides the hide; an awl punctures; thread tightens between actual holes. |
| Material handling has visible consequences | Wet hides sag, cuts separate panels, seams gather, bark changes the liquor. |
| Recipe assistance lives beside the work | A pattern book and physical pattern sheet support the bench. |
| Observers see the station's work | Another player can watch the hide being scraped and the seam being sewn. |
| Repetition has shortcuts | Keep the camera engaged between hides, repeat a pattern, and batch passive processing. |

Do not copy cooking interactions literally. Tanning needs scraping, tension, cutting, folding, and stitching rather than a reskinned stirring mechanic. Hands-On Delight is a reference, not a required dependency; Farmer's Delight must remain optional.

## 2. The intended experience

### 2.1 A complete craft

A player lays a raw hide across the rack's beam. Using the rack draws the view down and forward toward the material. One hand steadies it while the other holds the Tanner's Knife. The player draws the blade through broad, textured patches; removed material curls away and a clean surface appears along the blade's actual route.

The player lifts the scraped hide, puts it into the vat, adds bark, and pours water. The wet hide folds into the liquid and the bark disperses. The vat continues working while the player leaves. When ready, lifting a hide makes it droop and drip.

At the rack, the player attaches the wet hide and moves the tension controls to Supple, Harness, or Hardened. The hide visibly adopts that treatment. It dries without supervision.

At the bench, the player selects a pattern in a small book, places the chosen leather, draws the knife along a few meaningful cuts, seats the panels against guides, and stitches them together. Pulling thread closes each seam. The final object rises from the assembled leather and remains available to pick up.

Throughout this process, the player remains in the world. Work can be interrupted and resumed. A second player sees the same material state.

### 2.2 Priorities

1. **Direct causality:** the player's contact with a visible object changes that object.
2. **Readable craftsmanship:** state is apparent from the material before consulting numbers.
3. **Comfortable cinematography:** deliberate framing, short transitions, immediate escape, stable working shots.
4. **Persistent work:** closing the view does not erase labor or reroll a challenge.
5. **Low friction:** preparation and passive stages do not become a string of mandatory minigames.
6. **Reliable multiplayer:** inventory and quality are server-owned; observers see accepted actions.
7. **Complete compatibility:** old items, recipes, progression hooks, and valid modpack integrations retain a usable path.

Keep the existing three workstations as the core set. Add integrated props such as clamps, a stirring paddle, pattern paper, a burnishing block, and thread. These may be station equipment rather than new inventory items. Do not add mandatory chemistry, spoilage, maintenance meters, or several extra processing stations.

### 2.3 Pacing targets

These are initial playtest targets, not hard timers.

| Activity | Typical active time after learning | Rule |
|---|---:|---|
| Enter or leave focus | 0.25–0.45 seconds | Input escape must take effect immediately; visual settling may finish afterward. |
| Flesh one hide | 8–15 seconds; large hides up to roughly 20 | Skill comes from controlled coverage, not racing a countdown. |
| Load or collect a vat batch | 2–5 seconds | Stack operations remain available. |
| Set finish and tension | 2–5 seconds | A material decision with tactile feedback, not another graded test. |
| Make simple equipment | 15–25 seconds | One connected cutting/assembly/stitching interaction. |
| Make complex equipment | 25–40 seconds | More meaningful geometry, not dozens of tiny repeated holes. |

Keep current passive defaults initially: tanning `3600` world ticks and drying `2400` world ticks. At 20 TPS these are three and two minutes. Profile the full loop and tune separately from the visual overhaul. Never require standing beside a passive timer.

## 3. One coherent control scheme

Implement a central input router. Every physical press must resolve to one action, even when Minecraft, the scene system, and a held tool all see input.

| Context | Default action |
|---|---|
| Use a station normally | Enter its focused view; if the held item can be placed, place it once as part of the accepted interaction. |
| Sneak + use | Quick normal-world insertion, extraction, or last-finish application, chosen by targeted part and held item. |
| Point at a usable part | Subtle outline, meaningful cursor, and a short contextual verb. |
| Primary click | Pick a tool, operate a target, place a selected panel, puncture the next hole, or collect output. |
| Primary hold + drag | Scrape, cut, pull a panel, pull a cord, or draw thread. |
| Click-toggle option | Begin the same drag operation with a click and end it with another click. |
| Secondary click | Put down the active tool or cancel the uncommitted gesture. It must not also extract materials. |
| Escape | Immediately end capture and leave focus, preserving committed work. |
| Inventory key | Leave focus and open inventory without consuming a crafting action. |
| Rebindable material-drawer key | Briefly expose nearby inventory choices when needed; close it before working. |
| Next/previous station | Explicit rebindable navigation while idle; no navigation during an active gesture. |

Bind actions through key mappings and context, not hardcoded keys alone. Respect primary hand, swapped mouse buttons, GUI scale, and input remapping. Suppress mining, attacks, offhand use, and duplicate swings while a gesture owns input.

Disable automatic edge-hover station switching by default: a long scrape near the edge must never move the camera. Offer it as a client preference with a delay and gesture lockout.

The recipe book is a supporting tool. Do not make routine work depend on a persistent inventory grid, a fullscreen craft list, or a new floating control dashboard.

## 4. Camera and focus

### 4.1 Shot design

Create separate authored shots for each meaningful task, attached to the real block orientation:

| Task | Framing | Important visibility |
|---|---|---|
| Fleshing | Low, close, oblique view over the beam | Entire workable hide, blade contact, near edge, supporting hand. |
| Vat | Three-quarter view over the rim | Liquid surface, insertion opening, a readable batch, retrieval point. |
| Finishing | Front-oblique view of the rack | Hide silhouette, cords, three tension settings. |
| Cutting/assembly | Overhead-oblique bench view | Pattern, leather edges, tool grip, panel placement. |
| Stitching | Slightly closer seam view | Current hole pair and enough of the next seam to plan. |
| Result | Brief modest pullback | Completed object and material identity; immediately dismissible. |

Prefer a restrained vertical FOV around 50–60 degrees for authored shots; tune per station and preserve a user adjustment. Do not simulate a close-up only by narrowing FOV from the player's old position.

Move and orient the camera smoothly into place. Freeze the working shot during a gesture. A stage transition may reframe after release; never rotate the workspace underneath an active pointer.

Preserve the surrounding workshop, world lighting, nearby people, and spatial sound. Avoid black backgrounds, mandatory letterboxing, heavy depth of field, dramatic roll, or persistent camera shake.

### 4.2 Reach, obstruction, and lifecycle

The player's body stays where it actually stands. Camera movement does not teleport it, grant reach, disable damage, or open a station through a wall.

- Validate the player-to-station reach and usable face on the server.
- Check the camera path and destination against surrounding geometry. Use a compact fallback shot or the stationary mode when a low ceiling or wall blocks the preferred shot.
- Do not treat Diegesis's default `clear_view` behavior as collision avoidance: the inspected library can hide obstructing blocks and entities without moving the shot.
- For these stations, start with `clear_view.blocks=false` and `clear_view.entities=false`. Preserve hostile entities and danger cues. Add safe framing rather than revealing rooms through walls.
- Treat the whole near plane, not just the camera center, as relevant to clipping.
- Stop immediately on damage, death, dimension change, excessive displacement, lost reach, missing station, disconnect, or another screen taking control.
- On pause-menu/chat entry, release the active gesture and suspend input; no held-button work continues behind another screen.
- Restore camera owner, FOV, cursor grab, mouse sensitivity behavior, HUD, hands, view perspective, and relevant key states on every exit path.
- Give focus one owner. If another camera mod is active, negotiate or use a documented fallback; never run two competing camera updates.

Provide three presentation modes: **cinematic**, **reduced motion**, and **stationary world view**. All operate the same work state. Reduced motion removes camera travel, sway and result flourishes. Stationary view uses normal player sight and the same world targets; provide enlarged/selectable targets so it remains usable.

## 5. Tanner's Rack: physical fleshing

### 5.1 Mounting and tool contact

Use the existing beam as an actual work surface. Preserve hide size and trait identity through the presentation.

1. Place one raw hide, including valid foreign skins resolved by `HideItems.resolve`.
2. Animate it unfolding and settling over the beam. A small hide must look smaller; do not just change a label.
3. Pick up the Tanner's Knife from the player's actual eligible hand or reserved tool slot.
4. Let the blade travel over the hide while a supporting hand stabilizes it.
5. Lift the blade between passes. The surface relaxes slightly after contact.
6. Leave the completed scraped hide physically available on the rack; pickup is distinct from scoring.

The visual knife is a representation of a real tool, not a second inventory item. Define how its reservation survives hotbar changes and how one accepted job charges durability once.

### 5.2 Scraping model

Reuse and extend `FleshingSurface` where useful. The inspected model is a deterministic 64×48 surface with 12-bit coordinates; it is a reasonable starting point for authoritative material coverage.

Map its surface coordinates to a curved hide mesh:

- Remove opaque flesh patches along the accepted blade path.
- Show exposed leather, slight compression, and a restrained debris curl at the contact point.
- Align grain marks with the actual scoring direction.
- Good passes clear a broad useful strip. Cross-grain work takes additional passes.
- Repeated work on clean leather gradually produces visible roughness and a quality penalty.
- Leaving the hide with the blade down gives a small control penalty; it never deletes the hide.
- Hovering, repositioning with the blade lifted, low FPS, and pauses do not count as poor technique.
- Use a small forgiving edge margin and a clear contact indicator. Do not make the last isolated pixel a search puzzle.

Default to an **untimed** craft. Completion follows the cleaned area and required work, not a hidden deadline. Preserve an optional server-configured challenge timer for existing packs, including the existing assistance hook. Do not award speed bonuses.

Allow an explicit “Finish roughly” action after a meaningful minimum amount of work; show the expected lower quality and use the server's current score. It must not turn untouched hides into free high-quality outputs.

Keep the same seed, surface mask, quality history, and accepted stroke state through focus exit and resume. Cancelling and reopening must not generate easier patches.

### 5.3 Sequential hides

After pickup, offer a small “Next hide” prompt or let the player use the empty beam with another hide. Keep the camera in place and omit repeated establishing animations.

Work one active hide at a time. A gesture must not silently grant the same Integrity to an entire inventory stack. Efficient repeated crafting comes from short transitions and good material handling, while the vat continues to batch passive work.

## 6. Tanner's Vat: handle a real batch

The vat already works without a GUI. Improve its physical interaction and clarity while preserving its reliable processing rules.

### 6.1 Handling

- Pour water at the opening. Animate the bucket tipping, a short stream, surface displacement, and droplets on the rim.
- Put bark into the liquor. Show floating pieces briefly, dispersion, and a darker tannin tint.
- Lower scraped hides into the vat. They fold and sink partway; a large hide visibly displaces more liquid.
- Expose a few distinct hide edges or handles for inspection and retrieval. When capacity is increased, use a representative batch and an optional count, not sixteen overlapping full meshes.
- Lift completed hides from a clearly readable collection point. Animate damp folds and dripping; a stack pickup produces one short batch animation.
- Inspect a waiting hide to see its exact reason: “Needs water” or “Needs 1 more bark.” The visual cue must agree with the server's reason.
- Retain quick world interactions, hopper/pipe support, comparator behavior, and stack insertion/extraction.

A visual pouring stream is not a separate fluid transaction. For an ordinary bucket, commit one valid bucket exchange at the accepted pour action and animate that transfer. If partial-volume vessels are added later, they need a real volume ledger; do not invent partial vanilla buckets.

### 6.2 Optional hands-on tending

Provide a built-in paddle the player can pick up and make one slow sweep with. The hide and liquid respond to its real path.

The first accepted sweep for a started hide may credit **up to 10% of its original tanning duration**, bounded by its remaining duration. This is a proposed default, server-configurable down to zero. Record that credit per hide; repeated sweeps, new operators, draining, or reloading never grant it again. New hides added later have their own eligibility.

This action is optional. Untended hides finish at the existing baseline time with identical material quality. The vat is a place to touch and inspect the batch, not an obligation to stir continuously.

### 6.3 Preserve these exact invariants

1. Water, bark and hides work in every insertion order.
2. Each hide pays tannin once when starting; pausing or resuming never charges again.
3. A large hide waiting for sufficient tannin does not block a smaller affordable hide behind it.
4. Draining pauses started hides and removes the existing unspent liquor/tannin according to current rules; refill resumes remaining work.
5. Progress follows elapsed world `gameTime` while chunks are unloaded. Server downtime is not automatically real-world processing time.
6. Started, waiting, paused, and finished states remain distinguishable.
7. Automation simulation calls do not mutate anything.
8. A configured tannin limit below the largest hide cost still permits that hide to start.
9. New effects never create additional drops, consume bark twice, or treat a visual hide as a second inventory entry.

Reuse `startWaiting` as the central transition until an equivalent tested reducer replaces it. Route every new physical action and automation path through the same domain operations.

## 7. Rack finishing: choose by working the frame

Replace `FinishSelectScreen` with a visible tension mechanism and material treatment on the rack. Keep the familiar **Supple**, **Harness**, and **Hardened** names and mechanical identities.

### 7.1 Physical selection

Mount the wet hide on the upper frame. Two broad grips attach opposing edges; the remaining cords follow with an animation. Moving the rack's tension control previews one of three clearly labeled detents.

| Finish | Physical action and silhouette | Gameplay identity to retain |
|---|---|---|
| Supple | Set slack lacing, then make a short smoothing/flexing pass. Hide remains soft and draped. | Light, quiet leather; existing movement, detection, and boot vibration behavior. |
| Harness | Draw opposing cords to an even middle tension and seat the ties. Hide holds a firm, flexible shape. | Durability and field repair. |
| Hardened | Draw the frame taut and press/burnish the surface with an integrated block. Hide looks flatter and stiffer. | Protection and toughness within existing balance. |

This is a stylized Minecraft treatment, not a claim that frame tension alone reproduces real tanning chemistry. Do not introduce new mandatory oils, waxes, heaters, or water stages just to explain the visual metaphor.

Preview labels include the actual finish effect, not just a color. The player commits by seating the mechanism, after which drying starts. Prior to that, switching detents is free and reversible.

Use broad forgiving gestures. Finishing is a strategic material choice, not a third quality score or another time trial. A default last-finish shortcut should reproduce the short physical treatment without requiring a screen.

### 7.2 Drying and pickup

- Progress changes wetness, color, sag, and stiffness gradually.
- Cords visibly reflect the selected finish.
- Use small drips initially; stop them as drying progresses.
- A ready hide settles and makes one soft completion sound. It does not flash or spawn continuous particles.
- The yield remains derived from the hide's size and configured rules.
- Keep finished output on the station until collected; extracting twice must be impossible.
- Removing an unfinished drying hide preserves its selected finish and elapsed drying work, paused while off the rack. Remounting resumes it instead of restarting or completing it instantly.

A rack can remain one block entity with distinct beam and frame presentation. Do not quietly turn it into a multiblock or change its collision footprint without addressing existing placements and saves.

## 8. Leatherworker's Bench: cut, assemble, stitch

This is the largest gameplay replacement. Retire the rhythm lane and make the object visibly take shape on the bench.

### 8.1 Pattern selection and materials

Provide a small pattern book resting on or beside the bench:

- Show craftable patterns first, with search, favorites, category tabs, and a repeat-last choice available without covering the work.
- Selecting a pattern places a physical paper outline on the cutting mat.
- Show the result, required leather count, auxiliaries, chosen finish/trait, and any progression denial before taking materials.
- Let the player choose the material group explicitly. Never silently mix rare trophy leather with ordinary leather.
- Keep the existing one-finish/one-trait-per-piece rule and best-Integrity-first default within the selected group. Make that default visible.
- Support quick loading from inventory and direct placement into the staging area.
- A recipe click prepares the work; it must not also manufacture the finished output.

Keep logical item counts separate from visual panel counts. Five leather units in a recipe need not become five separate mandatory placement gestures. The pattern defines a small readable set of representative panels.

The existing Awl remains the real sewing tool. Needle, thread spool, pins, and paper may be integrated visual props. Consume only the auxiliaries declared by the recipe; an old recipe without string must not acquire an invisible string cost.

Books, Binding Leather conversions, and other generic ingredient recipes must not accidentally become equipment jobs. Preserve existing recipe routing and exclusions.

### 8.2 Cutting

1. Secure the selected material on the mat.
2. Trace two to four representative pattern cuts with the knife.
3. Separate the cut panel along the accepted route.
4. Slide offcuts into a visible scrap area.
5. Leave the freed panels available to assemble.

Cuts should be continuous and generous. Use a distance-to-path tolerance and progression along the path, not an exact pixel trace. The blade can leave the surface, reposition, and resume.

Wrong cuts produce rough edges and reduced workmanship within safe bounds. The recipe remains completable. Do not create arbitrary holes that destroy the materials, and do not reward repeated cutting of the same segment.

Visually staged scraps are not collectible rewards yet. Actual scrap output commits once with the finished item, using existing configured yields.

### 8.3 Assembly

Place a few panels against obvious guides or a modest station jig. Snap only when position and orientation are within a generous tolerance. Show a soft preview, then make the panel settle when accepted.

Offer meaningful pattern variation:

| Item family | Assembly identity |
|---|---|
| Boots | Fold upper around the sole; close one side and a cuff. |
| Helmet | Join cap sections and close the rim. |
| Chestpiece | Seat front and back panels; connect a shoulder and side. |
| Leggings | Fold leg panels and close the connecting seam. |
| Saddle | Seat the pad and body; attach a strap and hardware. |
| Horse armor | Arrange broad sections around a representative neck/body jig. |
| Modded equipment | Use explicit authored geometry or a reliable generic wrap/pouch pattern. |

Do not require a bespoke animated model for every modded output. The generic fallback must still involve cutting, alignment, and a real seam, and display the actual result item at completion.

### 8.4 Stitching and thread tension

A stitch is a physical sequence:

1. Bring the awl/needle to the next visible hole pair.
2. Press to puncture or pass through it.
3. Draw the thread in a broad direction away from the seam.
4. Release when slack is removed and the seam closes cleanly.

Show the needle entering, the thread following, slack diminishing, and the panels gathering. Excess pull puckers the edge; insufficient pull leaves slack. A successful stitch gives a small tactile sound and a visibly clean join.

Use **distance and material response**, not a moving beat marker. Tension must be legible through thread shape, edge displacement, and a discreet optional indicator. It cannot depend on color alone.

Group long seams into roughly four to eight meaningful stitch actions for ordinary equipment; allow more for complex patterns only when playtesting supports it. The animation may render intermediate stitches, but one gesture cannot complete an entire large garment without meaningful work.

A poor stitch can be loosened and redone before advancing. Record the actual repair so repeated redo cannot farm XP or quality increments. Never ask the player to count dozens of tiny holes.

When the seams are complete, pull or press to finish the final tie. Blend assembled panels into the result representation, play a short finishing sound, and leave the item on the output pad. The player can collect it immediately; camera flourish and pickup delay must not gate the reward.

### 8.5 Repeat crafting and repair

Keep the selected pattern and material preference between completed pieces. Offer “Make another” and a visible remaining-material count. Each new piece has its own input reservation and craftsmanship result.

Add a direct bench repair path: lay down a damaged compatible item, place scraps, then patch a short marked seam. Preserve the original finish, traits, quality, enchantments, dye, crafter data, and unrelated item NBT. Repair does not reroll quality or award full crafting XP.

Retain the existing fast field-repair routes. Bench repair adds a satisfying workshop interaction; it does not make emergency repair more cumbersome.

## 9. Quality without arcade overlays

Keep the established two quality axes and their existing item effects:

- **Integrity:** how well the hide was prepared; carried into leather and durability.
- **Workmanship:** how well the equipment was constructed; affects the established equipment/finish multipliers.

Do not add independent “vat skill,” “drying quality,” “tension level,” and “cinematic bonus” stats to every item.

### 9.1 Scoring requirements

For fleshing, begin with the existing coverage/control/overwork model. Its inspected weighting is 70% coverage, 15% control, and 15% avoiding excess overwork. Retune only after recording comparable stroke traces on the new world surface.

For bench work, start with:

`Workmanship = round(0.25 × cutting + 0.15 × assembly + 0.60 × stitching)`

Each component is normalized to 0–100. These weights are proposed tuning values. Use path accuracy/coverage for cutting, accepted panel alignment for assembly, and completed joins/tension quality for stitching. Evaluate all results on the server.

- Count distinct completed work, not frame count, raw mouse distance, or packet quantity.
- Missing required work cannot be disguised as a high average over a few successful actions.
- Completion bonuses, XP, advancement criteria, and scraps fire once per job.
- Avoid rerollable randomness and time-based score bonuses.
- Preserve the current Integrity quantization on stackable hides/leather and exact equipment Workmanship unless an explicit migration proves a change worthwhile.
- Preserve current grade boundaries and finish multiplier defaults initially.
- Reuse the existing events before final item data is written, honoring their clamped score modifications.

Show a short readable result summary only when useful: grade, finish, and one concrete explanation such as “Even seams; rough cut edge.” Numerical details remain available through existing tooltip preferences. Do not cover the work with stars, streak counters, combo meters, or a permanent scoreboard.

### 9.2 Progression API continuity

Preserve `HideFleshedEvent`, `LeatherworkStitchedEvent`, `ILeatherworkRecipeGate`, and `ITanningAssistSource`.

The existing assist interface lengthens a fleshing time allowance. With untimed default work, keep it as a documented no-op unless challenge timing is enabled; do not silently reinterpret it as free quality. If future progression assistance needs new semantics, add a new additive interface.

For untimed completions, pass `timedOut=false` through the existing events. Attribute crafting rewards and the crafter stamp to the authorized completing player; a deliberate handoff transfers that responsibility. Paused or unattended bench jobs never synthesize a player completion event.

Recipe gates must run before material reservation and again when committing work if their policy can change. Event-adjusted results must drive XP and advancements as today.

## 10. Visual and audio production requirements

### 10.1 Material language

Use Minecraft-scale geometry and textures with clear silhouettes. Improve close-up readability without making the workstations look imported from a photorealistic game.

Build these material states deliberately:

| Material | Visible properties |
|---|---|
| Raw hide | Irregular silhouette, thickness, coarse grain, removable patches. |
| Scraped hide | Cleaner surface, directional scrape evidence, softened folds. |
| Wet tanned hide | Darker color, weight, drip points, pronounced sag. |
| Supple leather | Soft folds and flexible edges. |
| Harness leather | Even grain and a firm but bendable shape. |
| Hardened leather | Flatter surface, stiff edges, reduced deformation. |
| Worked panels | Cut edges, hole positions, visible thread, progressive joins. |

Use restrained fantasy accents for traits. Insulating may suggest a pale/furred edge; Tough a thicker grain; Heatproof darker patterned patches. Preserve resource-pack overrides and use fallbacks for unknown traits. Do not encode trait identity solely with glowing particles.

### 10.2 Hands, tools, and observer presentation

Render a supporting hand and a working hand with contact poses appropriate to the current action. Respect skin, sleeve, slim/wide arm model, primary hand, and a tools-only preference. No severed-looking floating arm segments.

If a camera/body/animation mod cannot supply compatible hand rendering, fall back to tools-only while keeping the physical interaction complete. Avoid a new mandatory animation framework just for hands.

Define tool grip and working-point offsets explicitly. Follow the useful pattern in Hands-On Delight's `ToolStroke`: move the contact point to the work and derive the tool pose around that point. A knife hovering centimeters above the hide while it clears material is a failed visual test.

Observers see accepted tool motion, contact effects, cut panels, cord tension, thread state, and finished outputs. A detailed first-person hand rig may remain local; simplified remote arm poses and a correctly positioned world tool are sufficient.

### 10.3 Sound and effects

Create or appropriately source distinct sounds for mounting hide, clean scraping, rough scraping, wet leather, pouring, bark scatter, lacing, cutting, needle passage, thread tightening, and tying off. Keep source/attribution records.

Use positional sound at the contact point with subtitles. Vary pitch and intensity gently by action; do not make every tick play a new sound. Fade continuous scraping loops on release, exit, unload, and disconnect.

Particles communicate contact: a few bark fragments, drops, scrap flecks, or fibers. Avoid clouds of particles, blood/gore requirements, screen flashes, forced bloom, and per-frame item entities.

Cinematic quality comes from well-framed contact, material response, lighting, and sound. Shader packs and post-processing must not be required.

## 11. Implementation architecture

### 11.1 Prefer a thin Diegesis integration

The reference's camera and station machinery are substantially supplied by Diegesis. Evaluate and prototype against the inspected public API before writing a competing framework.

**Recommended route:** use a pinned, tested Forge 1.20.1 Diegesis dependency for focused scenes, input capture, node interaction, and shared presentation. Keep all tanning rules, inventories, work progress, quality, and progression in Immersive Tanning.

Do not depend on Hands-On Delight or Farmer's Delight. Do not copy their entire client behavior classes. If source or assets are reused, preserve applicable notices; inspect the actual license files rather than treating this brief as legal clearance.

The first implementation milestone must establish:

1. A rack opens into a correctly oriented scene and restores the normal view cleanly.
2. Surface coordinates correspond to the real curved hide.
3. The scene can read the existing block entity without duplicating inventory ownership.
4. A nearby observer sees accepted scraping and can join mid-job.
5. A dedicated server starts without client classes being loaded.
6. Reduced-motion and stationary interaction can use the same domain commands.
7. Camera obstruction, clear-view settings, and other camera mods have a workable integration path.

Resolve the exact published Forge artifact from official project metadata and pin it. The inspected reference uses beta.9; that is evidence for the prototype baseline, not permission to guess a Maven coordinate or accept every future beta. Declare matching client/server dependency requirements.

Use only `com.pockyl.diegesis.api` unless a small, documented compatibility shim is unavoidable. The internal `impl`, `platform`, and `mixin` packages are not a stable integration contract.

If the prototype demonstrates a concrete blocker, implement a focused native Forge adapter behind the same interface and record the reason. Do not leave the task at an architectural recommendation or ship two half-finished camera systems. Complete one production backend.

### 11.2 Separate responsibilities

The names below are **proposed**, not existing repository classes.

| Area | Suggested responsibility |
|---|---|
| `work/WorkOrder` | Job identity, recipe/material snapshot, stage, owner, quality state, result state. |
| `work/FleshingWork` | Persistent seed, mask, scrape history, accepted path and scoring. |
| `work/LeatherworkJob` | Cutting, panel placement, seams, tension, and completion. |
| `work/WorkCommandHandler` | One server entry point for validated actions and state transitions. |
| `work/MaterialLedger` | Reservation, actual inputs, container remainders, output, and recovery. |
| `station/TanningStations` | Station registration and model/interaction anchors. |
| `station/*StationLogic` | Bridge station actions to the domain layer. |
| `client/station/*Behavior` | Tool/hand animation, hover feedback, prediction, and observer effects. |
| `client/station/StationFocusAdapter` | Camera ownership, exit behavior, comfort modes, and integration boundaries. |
| `client/render/HideSurfaceMesh` | Cached geometry and authoritative surface-to-mesh mapping. |
| `client/render/LeatherworkAssembly` | Panels, thread segments, jigs, and result transition. |
| `data/WorkPatternLoader` | Versioned patterns, validation, fallback, reload. |

Evolve the current block entities into the persistent owners of work. Reduce the scope of `SessionManager` to operator/session coordination or replace it with equivalent services. Do not add thousands of lines of drawing, camera, and inventory code to it.

Distinguish three kinds of state:

- **Work state:** persists in the station/item and survives a player leaving.
- **Operator lease:** temporary permission for one player to manipulate a workpiece.
- **Presentation state:** local camera, interpolation, hover, and effects.

Closing focus releases the lease and presentation; it does not implicitly delete work.

### 11.3 Avoid duplicate inventories and clocks

The same paid input must never exist both in a Diegesis-owned slot and in an independent escrow copy that can also be extracted.

Bind scene slots to canonical station storage or provide read-only projections whose actions go through the ledger. Inspect `StationHost`, `SlotStorage`, `kept()`, and close/refund behavior. The library's default transient slots are not a substitute for persistent work storage.

Keep the vat's existing water/tannin/slot state authoritative. Do not create a second library tank that can disagree with `TannersVatBlockEntity.water()`.

Any client-side idle scene logic must be a read-only projection of synchronized state. It must not advance tanning, consume supplies, release escrow, or call server completion routines.

Passive progress uses server world time. Active manipulation uses accepted work geometry and bounded server validation. Camera animation uses local elapsed time. These clocks must not be interchangeable.

### 11.4 World coordinates and hit testing

Create one shared transform from station-local space to world space for each block facing. Test all four horizontal facings.

Map:

`pointer ray → actual work surface → canonical UV/task coordinates → validated command`

The inspected Diegesis `SceneEvent.Stroke` reports points on a camera-facing plane. **Do not use those raw coordinates as hide UVs.** Project the ray or reported point onto the canonical surface through a tested adapter, and use the same mapping for the blade contact and scoring.

For a deformed hide, maintain a stable parametric surface. Visual bending must not move the scoring target away from the visible patch. Rebuild only the affected mesh data as needed.

Reject hits behind the work surface, outside valid regions, through blocking geometry, or on disabled nodes. A resource pack may change appearance but cannot expand authoritative reach or manufacture new accepted task targets.

## 12. Networking, multiplayer, and authority

### 12.1 Commands, not outcomes

The client sends intent and bounded samples. It never sends the resulting item, final score, claimed inventory contents, or a “job completed” verdict.

A proposed command envelope includes:

- Station dimension and position.
- Station-instance generation, so a broken/replaced block at the same position is different.
- Job ID and operator-lease ID.
- Monotonic sequence number and expected stage/revision.
- Action kind and bounded payload.
- For strokes: begin/end flags and a bounded list of quantized local samples.

The server resolves the real player, held/reserved tool, recipe, inventory, station, and task state. Each action checks:

1. Loaded chunk and correct dimension/block entity, without force-loading.
2. Live player, allowed game mode, reach, line of sight, and protection permission.
3. Correct operator lease and work stage.
4. Valid tool/material references.
5. Monotonic sequence and appropriate revision.
6. Finite, bounded coordinates and legal path lengths.
7. Legal target order and prerequisites.
8. Rate and payload limits.

Protecting the generic slot schema is not enough: Diegesis's custom-action hook explicitly requires validation of its payload.

Do not demand that the server player's look vector exactly matches the cinematic pointer. Validate the allowed workstation interaction volume and body-to-work reach instead; the view is intentionally offset.

### 12.2 Sampling and reconciliation

- Predict local tool motion and provisional contact immediately.
- Batch continuous samples at approximately 10 sends/second initially; sample/resample by elapsed time and path length, not every rendered frame.
- Preserve begin/end boundaries and sharp turns when coalescing.
- Cap samples per message, coordinate range, cumulative path length, and outstanding work.
- Budget total library actions below its documented 40 actions/second limit. Begin/end, clicks, and material actions share that budget.
- Never let a throttled final-release packet leave a tool permanently pressed; use release acknowledgement and a short lease/gesture watchdog.
- Server replies include accepted sequence, authoritative revision, stage, and the relevant changed state.
- On disagreement, correct the material and score; blend the visual tool correction where possible.
- Replays and late packets from old jobs or leases are ignored safely.

Test at 30, 60, and high frame rates, with 0–250 ms latency and artificial jitter. Similar physical paths must produce comparable quality independent of rendering speed. Delayed packets cannot make the server award extra work.

Use the existing fixed-point approach where practical. With spatial scoring, avoid introducing new precision timing windows merely to “secure” a drag.

### 12.3 Operator and observer behavior

Allow one active operator per hide or bench job initially. Multiple players may inspect and watch. Vats may accept concurrent independent inserts through serialized transactions.

- The lease is acquired server-side and has a bounded idle timeout.
- A lost client or abandoned view releases it without deleting the job.
- Another player cannot replace the operator's tools, extract their active materials, or steal a reserved output through the focused interface.
- Persist ownership and honor applicable protection rules. Default unfinished jobs to their creator; deliberate handoff is an explicit station action.
- Ownership is interaction policy, not an unbreakable anti-grief claim. Block-break/drop behavior must remain compatible with the world's protection rules.
- Send public material/pose snapshots to chunk observers and operator-specific guidance only to the operator.
- A late observer receives the current material state, not the history of every particle or stroke.
- Pause remote active animations when the lease ends. Completed material stays visible.

Do not broadcast whole inventories or unrestricted custom NBT just to draw the tool. Sanitize a render snapshot to what the scene actually needs.

## 13. Persistent work and material safety

### 13.1 Ownership model

At every point, each input or result has exactly one canonical owner:

| Stage | Owner | Valid exit behavior |
|---|---|---|
| Pattern preview | Player inventory | Close without consumption. |
| Reserved, no physical work | Station ledger | Explicit cancellation returns exact inputs once. |
| Work started | Persistent workpiece/job | Pause or return a resumable workpiece. |
| Completed, uncollected | Station output storage | Pickup transfers it once; no further crafting awards. |
| Collected | Player inventory or normal dropped-item transfer | The station no longer owns it. |

A result animation is never a source of items. Tool animation completion is never sufficient to complete a recipe.

For equipment, add a single generic `unfinished_leatherwork` carrier if a persistent station cannot retain the job during extraction/breaking. Store stage, material ledger, recipe/task snapshot, quality history, and schema version under the mod's own NBT root. This is a recoverable in-progress item, not another mandatory crafting ingredient.

Partly scraped raw hides can carry their work state in their own NBT; wet hides can carry finishing/drying progress. Do not add unique job IDs to completed stackable leather.

### 13.2 Transactions and edge cases

- Reserve exact input stacks atomically, including all relevant NBT and auxiliary container remainders.
- Make inventory-full behavior explicit: finished outputs stay on the station; an already accepted job does not destroy its result.
- Delay scraps and XP until the final commit. No refund plus scraps, refund plus result, or repeated result plus XP.
- Material pickup uses the exact stored output stack, not a fresh reconstruction that loses dye or external NBT.
- Preserve tool identity and damage rules; swapping an unrelated tool must not charge or satisfy the job.
- On ordinary close/disconnect/death, persist committed work and release interaction.
- On block replacement/break, choose exactly one drop route: the in-progress workpiece or its recovery contents, never both.
- Replace the current “drop stale escrow after load” behavior for new-format resumable jobs.
- On unload, persist canonical state and discard transient leases without force-loading chunks.
- On restart, recreate work from saved state and require a new lease.
- Track result commit and reward eligibility separately from the camera/result animation.

Define and test save/load checkpoints around reservation, work progress, completion, output pickup, and workpiece extraction. Do not claim universal crash-proof transactions across arbitrary filesystem rollbacks or Minecraft's separately saved player/chunk files. Preserve normal save/reload behavior and document any remaining abrupt-crash window found in testing.

### 13.3 Recipe reload and invalid work

Snapshot the accepted task definition, recipe result, input ledger, and recipe version/hash when the job is committed. Do not let a client resource reload redefine scoring midway.

On datapack reload, pause affected jobs and revalidate IDs and gates. Default to finishing a valid paid snapshot when only presentation/tuning changed. If the recipe was removed or permission revoked, keep the workpiece and offer a clear recovery action returning the exact original inputs once, with no result, scraps, quality reward, or XP.

Do not make players lose rare materials because a pack removed a pattern. Log one actionable diagnostic rather than spamming every tick.

## 14. Save, item, config, and API migration

Introduce explicit schemas for new rack/bench work state. Do not infer “new save” from whether one optional key happens to be missing.

| Existing data | Migration requirement |
|---|---|
| Raw hide mounted on rack | Preserve hide size and trait; create untouched work state only if no progress existed. |
| Drying rack | Preserve chosen finish, hide identity, start/duration, and completed state. Do not require old hides to repeat the new finishing gestures. |
| Vat save format 2 | Preserve `Started`, duration/start, tannin paid, and `PausedElapsed`; initialize new tending credit without granting repeated bonuses. |
| Older vat formats | Retain the existing dry-vat and waiting-hide migration behavior. |
| Bench escrow without a resumable task snapshot | Recover exact old inputs once. Do not invent an almost-finished job from its display item. |
| Existing finished leather/equipment | Keep IDs, quality, finish, trait, crafter, dye, damage, enchantments, and external NBT. |
| Existing recipe and seam JSON | Continue loading through the legacy adapter described below. |
| Existing timing and assistance configs | Retain compatibility keys with documented effects; a new explicit untimed default supersedes the old mandatory timer. |
| Public API/events | Preserve binary/source compatibility where practical; add new APIs rather than silently changing old meanings. |

Preserve registry IDs for all existing blocks and items. Existing worlds should load without missing-registry prompts.

The inspected protocol is `ModNetwork.VERSION = "2"`. Bump the protocol when packet layouts or semantics change, and reject incompatible client/server versions clearly. Do not assume a camera library's own version negotiation replaces the mod's.

Back up representative test saves before migration testing. Upgrade disposable copies through at least two load/save cycles to catch migrations that accidentally re-run.

## 15. Data-driven work patterns

### 15.1 Extend rather than discard recipe data

Keep `immersive_tanning:leatherworking`, existing `leather`, `auxiliary`, `result`, and `seam` fields, hide sources, traits, mounting recipes, and equipment detection.

Add an optional `work_pattern` field referencing:

`data/<namespace>/immersive_tanning/work_patterns/<name>.json`

A new pattern defines panel shapes, representative cuts, assembly anchors, seam joins, and physical interaction tolerances. Keep actual input cost and output in the recipe; visual geometry must not determine ingredient consumption.

Separate visual scene assets from authoritative task definitions:

- Server datapacks define allowed work and quality rules.
- Client resources define meshes, textures, sounds, camera framing, and decorative props.
- Sync the resolved authoritative task for the active job to the client.
- Resource packs cannot redefine completion conditions.

### 15.2 Proposed schema example

This example describes a small two-panel wrap; it is a schema to implement, not a file already supported by the current mod. Coordinates are normalized to each panel's local surface, with x right and y down.

~~~json
{
  "schema_version": 1,
  "panels": [
    {
      "id": "front",
      "outline": [[0.1, 0.1], [0.9, 0.1], [0.9, 0.9], [0.1, 0.9]]
    },
    {
      "id": "back",
      "outline": [[0.1, 0.1], [0.9, 0.1], [0.9, 0.9], [0.1, 0.9]]
    }
  ],
  "cuts": [
    {
      "id": "front_trim",
      "panel": "front",
      "path": [[0.1, 0.1], [0.9, 0.1]],
      "tolerance": 0.06
    },
    {
      "id": "back_trim",
      "panel": "back",
      "path": [[0.1, 0.1], [0.9, 0.1]],
      "tolerance": 0.06
    }
  ],
  "assembly": [
    {
      "panel": "front", "anchor": "jig_front",
      "target": [0.35, 0.5], "rotation_deg": 0,
      "position_tolerance": 0.08, "rotation_tolerance_deg": 15
    },
    {
      "panel": "back", "anchor": "jig_back",
      "target": [0.65, 0.5], "rotation_deg": 0,
      "position_tolerance": 0.08, "rotation_tolerance_deg": 15
    }
  ],
  "joins": [
    {
      "id": "side",
      "a": {"panel": "front", "path": [[0.85, 0.2], [0.85, 0.8]]},
      "b": {"panel": "back", "path": [[0.15, 0.2], [0.15, 0.8]]},
      "actions": 5,
      "pull": {"min": 0.18, "ideal": 0.25, "max": 0.35}
    }
  ],
  "quality_weights": {"cutting": 0.25, "assembly": 0.15, "stitching": 0.60},
  "presentation": "immersive_tanning:generic_wrap"
}
~~~

Define the pull values as normalized gesture travel in the work surface's coordinate system. Assembly targets use normalized bench work-plane coordinates; their accepted position and angle come from this server-side task data. The named anchors attach presentation geometry to those same targets. Client presentation resources cannot move an authoritative placement target or redefine its tolerance.

### 15.3 Legacy patterns and validation

Adapt existing `seam_patterns`:

- Reuse panel polygons and seam paths.
- Derive a small number of representative cuts and placements.
- Convert existing stitch counts into sensible grouped actions without multiplying workload.
- Continue accepting `rhythm`, `beat_ms`, `window_ms`, `perfect_ms`, and `lead_ms` for compatibility; they no longer force rhythm timing in the new default interaction.
- Preserve explicit seam selection and equipment/tag-based fallback.
- Use an authored generic pattern when an automatically detected item lacks custom geometry.

Bound total panels, vertices, cuts, seams, actions, and serialized snapshot size. Reject negative counts, NaN/infinite numbers, invalid references, impossible tolerances, degenerate paths, and unsupported schema versions with file-specific errors.

Reload patterns atomically. A bad file should produce a clear diagnostic and a documented fallback or disable only that recipe, not crash every workstation.

Ship authored patterns for all current vanilla bench outputs and representative modded equipment. The generic path must be tested with unknown items and custom NBT outputs.

## 16. Compatibility that must survive

Treat the existing README's integration list as a regression checklist, not proof that every combination works after the overhaul. Validate the actual installed versions available to the implementation agent.

| Area | Required behavior |
|---|---|
| Binding Leather | Preserve the vanilla-leather replacement, conversion recipes, generic ingredient uses, loot/trade handling, and anvil behavior. |
| Modded hides and pelts | Keep hide-source priorities, entity tags, hide-mounting recipes, and `HideItems.resolve` as the consistent acceptance path. |
| Butchery | Preserve carcass-versus-hide suppression and skinning-knife tags. Do not reintroduce duplicate animal material drops. |
| Bark | Continue accepting configured/tagged tannin sources and existing knife/log behavior. No recipe hardcoding to one bark item. |
| Traits and damage effects | Preserve trait IDs, strengths, damage tags, and their propagation through every stage. |
| Automatic equipment recipes | Preserve exclusions, recipe/mod blacklists, source-recipe association, auxiliary requirements, and generic pattern fallback. |
| JEI | Show the whole material flow and physical work steps; transfer chooses/prepares a pattern and revalidates server-side. |
| Jade | Show stage, owner/busy state where appropriate, waiting reason, and passive progress. Suppress redundant text in focus if configured. |
| Village leatherwork | Preserve quality, crafter labels, trades, existing items, and tannery recognition where supplied by integrations. |
| Runic Skills/other progression | Retain public events, recipe gates, XP behavior, and assist compatibility without a mandatory dependency. |
| Automation | Supply and collect passive stages according to config; never synthesize player craftsmanship or bypass a reserved job. |
| Performance/render mods | Test Embeddium and Oculus, with shaders off and on; do not merely inherit a compatibility claim from the reference. |
| Camera/combat/animation mods | Test clean ownership, hands, hotkeys, and fallback behavior with representative camera and combat setups. |
| Resource packs | Replace textures/models/sounds without changing the server's recipe cost or work rules. |
| Multiplayer protection | Check interaction and extraction permissions on every actionable path, including focused custom packets. |

If the actual checkout contains later features such as additional leather types, scale-backed materials, new finish rules, or expanded compatibility, carry them into the new physical presentation and migration matrix. Do not invent their presence based on this 1.1.1 snapshot.

Maintain a compatibility result table with tested version numbers and outcomes. “Not tested” must remain distinct from “supported.”

## 17. Accessibility, configuration, and performance

### 17.1 Accessibility

Ship these with the overhaul, not as postponed polish:

- Reduced-motion and stationary-view options.
- Hold or click-toggle gestures.
- Rebindable inputs and keyboard-driven target cycling/confirmation.
- Keyboard equivalents for moving along a work path and pulling thread; the same server rules apply.
- Adjustable local pointer sensitivity and tool smoothing.
- Large target indicators, readable text, high-contrast outlines, and non-color tension cues.
- Sound subtitles and visual confirmation for every required audio cue.
- Tools-only hand rendering and optional supporting-hand visibility.
- Persistent contextual instructions while an action is available.
- No required rapid clicking, sustained high precision, or rhythm timing in default play.

The existing `PressGate` and its tests embody the useful “one physical action, one accepted press” rule. Preserve that principle across all input modes.

Teach the first craft with a few contextual hints on the relevant object. Let players revisit a compact illustrated workflow through the pattern book or existing recipe viewer. Avoid a forced tutorial screen, timed reading, or repeatedly resetting experienced players' hints.

Presentation preferences must not secretly change server scoring. When a motor-assistance option needs different authoritative tolerances, represent it as an explicit server-supported interaction profile. Make it available without lowering the possible quality ceiling, and validate its actions just as carefully.

### 17.2 Configuration ownership

| Server/world settings | Client settings |
|---|---|
| Passive timings and capacity | Camera mode, transition intensity, FOV preference |
| Tending credit and automation permissions | Hold/toggle controls and pointer sensitivity |
| Crafting thresholds and quality weights | Target size, contrast, captions, numeric readouts |
| Challenge timer and authoritative assist policy | Hand/tool visuals, sway, particles, sound mix |
| Reach, interaction profiles, lease timeout | Inventory drawer and edge-navigation preference |
| Recipe replacement and compatibility policies | Render distance and visual detail |

Keep sensible defaults. Expose a concise set of meaningful controls rather than every easing coefficient and mesh constant. Use translation keys for player-facing text.

Introduce an explicit server challenge-timing switch, off by default. Retained timer/assistance values apply when that switch is on. Document this changed default prominently for existing modpacks instead of implying that the old timer values still impose a deadline.

Do not place developer terms such as “packet revision,” “escrow,” or “snapshot hash” into normal crafting messages.

### 17.3 Performance design

- Cache base meshes and pattern geometry. Do not rebuild every panel, vertex array, and thread list every frame.
- Update only changed material regions or state-derived mesh portions.
- Use restrained vertex deformation, not a general cloth/soft-body solver.
- Reuse tool and item render data where possible.
- Use level of detail: focused work gets the full surface and thread; nearby observers get simplified geometry; distant idle stations get static state.
- Cull particles and offscreen effects; stop all loops when the station unloads.
- Do not create one entity per scrap, stitch, bark piece, or droplet.
- Do not sync full surface masks or all recipe data every frame. Send bounded changes and periodic recovery snapshots.
- Keep skin/model lookups cached and asynchronous loading out of render loops.

Initial measurement targets, to revise only with recorded evidence:

| Scenario | Initial budget/goal |
|---|---|
| 32 visible idle stations | Under roughly 1 ms additional client CPU frame time on the reference test system. |
| One focused station | Under roughly 2 ms additional client CPU frame time; record GPU cost separately. |
| Four simultaneous operators | Under roughly 2 ms added server tick time at the 95th percentile. |
| Active stroke stream | Around 10 application messages/second plus bounded discrete actions; no per-frame network traffic. |
| Idle completed station | No continuous progress packets or repeated completion effects. |
| Long session | No growing mesh, listener, sound, session, or packet-history collections. |

These are profiling targets, not claims about current performance. Record resolution, hardware, mod list, shader settings, TPS, frame-time percentiles, and network traffic. If available, include an i7/RTX 4050 6 GB class machine as a practical test target.

## 18. File-by-file implementation map

All Java paths below are relative to `src/main/java/com/otectus/immersivetanning/`.

| Existing area | Work to perform |
|---|---|
| `block/TannersRackBlock.java`, `TannersVatBlock.java`, `LeatherworkersBenchBlock.java` | Route focus and quick use once; preserve permissions, normal interactions, and drop paths. |
| `blockentity/StationBlockEntity.java` | Reconcile existing locks with temporary operator leases; keep ownership separate from saved work. |
| `blockentity/TannersRackBlockEntity.java` | Persist fleshing/finish state, paused drying, and collectible output. |
| `blockentity/TannersVatBlockEntity.java` | Preserve the start/pause core; add physical action adapters and per-hide tending credit. |
| `blockentity/LeatherworkersBenchBlockEntity.java` | Replace transient-run assumptions with persistent job, ledger, workpiece, and output storage. |
| `minigame/FleshingSurface.java` | Retain deterministic surface math where useful; add persistence and stable world mapping. |
| `minigame/SessionManager.java` and session classes | Separate work progression from view lifetime; centralize validation and idempotent completion. |
| `minigame/StitchRun.java`, `StitchPattern.java`, `SeamPatterns.java` | Replace default beat scoring with physical seam work; retain legacy data parsing/adaptation. |
| `minigame/PressGate.java` | Reuse or generalize edge-triggered input suppression. |
| `client/screen/FleshingScreen.java`, `FinishSelectScreen.java`, `LeatherworkScreen.java` | Remove from ordinary crafting routes after functional parity; do not leave hidden dependencies on their widget state. |
| `client/ClientEvents.java`, `ClientSetup.java`, `ClientPacketHandler.java` | Register focus/input and rendering behind client-only boundaries; remove obsolete screen handling. |
| `client/renderer/*Renderer.java`, `MeshUtil.java` | Make material state, active tools, panels, and seams visible in both focused and ordinary world rendering. |
| `network/ModNetwork.java`, `network/packet/` | Version the protocol; route commands/acks and remove obsolete timing/screen packets only after migration. |
| `recipe/LeatherworkingRecipe*.java`, `TanningData.java` | Add work-pattern references and fallback while retaining recipe discovery and suppression. |
| `recipe/LeatherStock.java`, `AuxInventory.java` | Audit exact stack selection, reservations, remainders, NBT preservation, and rollback. |
| `api/`, `leather/`, `effect/` | Preserve public contracts and item effects; apply final quality through established hooks. |
| `config/`, `client/StationNotices.java` | Add comfort/server policy settings; keep useful readable feedback. |
| `client/compat/jei/`, `compat/jade/` | Present new stages and precise waiting/ownership information. |
| `assets/immersive_tanning/` | Add authored scenes, hand/tool poses, material states, pattern props, subtitles, and translations. |
| `data/*/immersive_tanning/` | Add work patterns and maintain existing trait/source/seam data. |
| `art/tanning_art.py`, `tools/generate_textures.py` | Keep generated art and its editable source in agreement; update checks for new assets. |

Pick one rendering owner for each surface. If a focused scene replaces a block-entity renderer, hide the original only when the replacement is actually drawing. Restore it on close, resource reload failure, and fallback. Never leave doubled hides, doubled tools, or an invisible station after the camera exits.

## 19. Implementation sequence and completion gates

Do the complete overhaul through reviewable milestones. Each milestone must leave a working build; the first successful prototype is not the final deliverable.

### Milestone 1 — Baseline and camera proof

Record the real HEAD, run existing checks, and preserve representative saves. Build a focused rack scene with one material surface, one tool, one validated action, observer state, and clean escape.

**Gate:** dedicated server starts; close/reopen/damage/low ceiling all recover; dependency/backend decision is recorded.

### Milestone 2 — Persistent work and transactions

Implement job state, material ownership, output storage, lease handling, save schemas, and recovery. Connect this to simple placeholder geometry before detailed animation.

**Gate:** reservation, pause, reload, block break, output pickup, and full inventory cannot duplicate or lose ordinary inputs in the tested lifecycle.

### Milestone 3 — Finish the fleshing experience

Map real strokes to the hide, add contact tools/hands, material removal, untimed scoring, contextual help, and next-hide flow.

**Gate:** one complete raw-hide-to-scraped-hide loop works with no fullscreen crafting screen and can be watched by another player.

### Milestone 4 — Vat and finishing rack

Implement physical fill/add/insert/take, restrained tending, tension detents, drying transitions, and quick actions.

**Gate:** the existing vat regressions still pass; a hide can be processed and finished through all input orders without a menu.

### Milestone 5 — Bench craftsmanship

Implement pattern preparation, exact material reservation, representative cutting, panel assembly, thread-pull stitching, actual output collection, repeat craft, and bench repair.

**Gate:** boots and a saddle demonstrate distinct work patterns; a detected modded item completes through the generic fallback.

### Milestone 6 — Compatibility and migration

Preserve old items/data, legacy patterns, recipe routing, progression APIs, Jade/JEI, automation, and applicable pack integrations.

**Gate:** old saves upgrade twice without repeated migration or lost material; integration tests and a dedicated multiplayer session pass.

### Milestone 7 — Art, audio, accessibility, and performance

Replace placeholders with authored final assets, verify hand/tool contact and observer scenes, finish comfort controls, and profile busy workshops.

**Gate:** the complete normal and accessible flows work at multiple resolutions and frame rates; no render-mode-specific crafting advantage.

### Milestone 8 — Release readiness

Remove dead screen/minigame routes and obsolete assets only after replacements pass. Update documentation, changelog, dependency declarations, screenshots, pack notes, and migration notes.

**Gate:** every acceptance criterion below is either demonstrated or listed as an explicit unresolved blocker. Do not label a partial visual prototype as the completed overhaul.

## 20. Verification plan

### 20.1 Build and existing checks

The inspected repository documents these commands:

~~~bash
./gradlew build
./gradlew runGameTestServer
./gradlew runClient -PsmokeTest
python3 tools/generate_textures.py --check
~~~

The client smoke workflow is graphical and must be run where a suitable client environment exists. Headless GameTests do not prove camera quality or visual correctness.

For the existing optional Ultima pack harness, inspect its requirements and run it only against an isolated disposable copy when the pack is available:

~~~bash
./gradlew build smokeTestJar
python3 tools/packtest/ultima_pack_test.py --expect dragn,lycanites,alexsmobs,butchery,magic,bench,trades
~~~

Also boot a dedicated Forge server with the production jar and connect real clients. Never substitute an integrated-server test for this check.

### 20.2 Extend the meaningful regression suites

| Existing suite | Preserve or evolve |
|---|---|
| `VatRegressionGameTests` | Every resource order, mixed sizes, one-time tannin, automation simulation, drain/refill, save/load, old saves, low tannin limit. |
| `StationGameTests` | Complete station flow, material ownership, finish output, normal/quick interactions; adapt obsolete cancel/refund expectations to new workpiece behavior. |
| `MinigameGameTests` | Deterministic surface math and grain behavior; replace default rhythm assertions with spatial seam/cut assertions. |
| `StitchInputGameTests` | One physical action per command, early/unready input rejection, prediction convergence. |
| `ApiGameTests` | Recipe gate behavior, event score clamping, timed-mode assistance, additive API compatibility. |
| `BindingLeatherGameTests`, `CompatGameTests`, `DataGameTests` | Existing item/recipe/trait/integration contracts. |

Do not simply delete tests because the old UI disappeared. Retain the underlying invariant or replace it with the new intended behavior.

### 20.3 New automated scenarios

- Deterministic results for the same accepted gesture path at different client frame rates and packet batch sizes.
- Start/end gesture ordering, lost release, duplicate sequence, stale lease, and block replacement at the same coordinates.
- No progress from hover-only input, repeated completed segments, absurd path jumps, NaN/infinite coordinates, or oversized payloads.
- Same-quality possibility with mouse, toggle, and keyboard interaction.
- Correct canonical transforms for every station facing and each hide size.
- Work stage and quality preserved after pause, death, logout, chunk unload, restart, extraction, and remounting.
- Input reservation failures and changed recipes/gates leave a recoverable state.
- No simultaneous refund/result/scrap route; reward events and XP cannot fire twice.
- Exact preservation of modded item NBT, auxiliary remainders, dye, enchantments, and repair metadata.
- Vat tending credit cannot be repeated by reload, draining, second operator, or a newly opened view.
- Resource reload reconstructs the scene from current state without replaying old rewards/effects.
- Malformed work patterns fail safely and valid legacy patterns still produce usable jobs.
- All existing save migration paths are idempotent.

### 20.4 Manual playtest matrix

| Scenario | Evidence to capture |
|---|---|
| Full vanilla craft | Raw hide through finished boots, entirely through the new world interactions. |
| Complex pattern | Saddle or horse armor, showing different assembly geometry. |
| Modded input/output | Foreign pelt and detected equipment through a generic pattern. |
| Four station orientations | Blade contact, camera, cursor, output pickup. |
| Confined workshop | Wall behind station, low ceiling, slab floor, neighboring blocks; no unintended wall hiding. |
| Dangerous interruption | Damage, knockback, death, entity crossing the view; input/camera restore. |
| Two real clients | Operator, observer, late join, attempted concurrent use, handoff, reconnect. |
| Frame rate and latency | 30/60/high FPS; 0/100/250 ms and jitter; comparable physical results. |
| Accessibility | Reduced motion, stationary view, toggle, keyboard controls, left hand, sound off, high contrast. |
| Display sizes | 854×480, 1080p, 1440p, ultrawide, multiple GUI scales; low-resolution UI must remain escapable and usable. |
| Renderer combinations | Base Forge, Embeddium, Oculus without/with shaders, representative resource pack. |
| Lifecycle | Save at each stage, reload, break station, fill inventory, remove recipe, resource reload. |
| Long session | Repeated jobs and idle workshop; stable memory, sounds, listeners, and frame time. |

Capture short videos for active interaction; still screenshots alone cannot demonstrate contact timing, camera comfort, or double-input behavior. Include a side-by-side operator/observer example.

### 20.5 Playtest judgments

After a few attempts, ask testers to perform an ordinary craft without guidance. Observe:

- Can they identify the next action by looking at the workstation?
- Does the blade feel attached to the hide?
- Can they explain why a seam was loose or puckered?
- Can they stop instantly and return without frustration?
- Does repeated crafting remain satisfying after ten pieces?
- Does the passive batch processing let them do other things?
- Does the camera feel like leaning toward a task?
- Are results equally understandable without sound or color distinctions?

Tune mechanics from these observations. A technically correct mouse gesture that feels disconnected from the object is not finished.

## 21. Definition of done

The overhaul is complete only when:

- [ ] Fleshing, finish selection, and bench crafting use real workstation interactions by default.
- [ ] The old rhythm-based stitch screen is no longer the ordinary crafting path.
- [ ] Camera movement meaningfully frames each workstation and always restores cleanly.
- [ ] Scraping, cutting, lacing, and stitching visibly change the material where the tool touches it.
- [ ] All three finishes remain distinct and useful.
- [ ] Work is resumable and does not reset on ordinary focus exit.
- [ ] Inputs, workpieces, outputs, scraps, tool wear, and XP have one tested ownership/commit path.
- [ ] Nearby players see a coherent version of the work, including mid-job arrival.
- [ ] Passive timers, automation, existing recipes, traits, Binding Leather, and progression hooks retain their intended behavior.
- [ ] Existing worlds/items migrate without replacing registry IDs or losing valid material.
- [ ] Unknown modded equipment has a working generic pattern.
- [ ] Reduced-motion, stationary, toggle, and keyboard paths can complete the full craft.
- [ ] Rendering works without shaders and within recorded performance budgets.
- [ ] New sounds, models, textures, subtitles, and translations are finished.
- [ ] The build, server tests, client inspection, and multiplayer tests have recorded results.
- [ ] Documentation describes the implemented controls and behavior rather than the retired minigames.

The coding agent's final delivery must include changed files, the architectural/dependency decision, migration behavior, commands run and results, compatibility versions tested, visual evidence, and remaining issues. Be precise about anything not run.

## 22. Source index

All linked source snapshots below were inspected for this handoff. New designs in this document remain proposals.

### Immersive Tanning

- [Pinned repository](https://github.com/otectus/ImmersiveTanning/tree/06416690288ea07c6c7258d806762488823d8a7d)
- [README: loop, compatibility, API, datapacks, and build commands](https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/README.md)
- [Project conventions](https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/CLAUDE.md)
- [Original design brief](https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/docs/immersive_tanning.md)
- [Version/dependency baseline](https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/gradle.properties) and [build configuration](https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/build.gradle)
- [Session validation, scoring, material handling, and events](https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/src/main/java/com/otectus/immersivetanning/minigame/SessionManager.java)
- [Deterministic fleshing surface](https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/src/main/java/com/otectus/immersivetanning/minigame/FleshingSurface.java)
- [Vat state and save migration](https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/src/main/java/com/otectus/immersivetanning/blockentity/TannersVatBlockEntity.java)
- [Bench escrow and recovery](https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/src/main/java/com/otectus/immersivetanning/blockentity/LeatherworkersBenchBlockEntity.java)
- [Rack material rendering](https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/src/main/java/com/otectus/immersivetanning/client/renderer/TannersRackRenderer.java)
- [Current bench rendering](https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/src/main/java/com/otectus/immersivetanning/client/renderer/LeatherworkersBenchRenderer.java)
- [Recipe serializer](https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/src/main/java/com/otectus/immersivetanning/recipe/LeatherworkingRecipeSerializer.java) and [legacy stitch data](https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/src/main/java/com/otectus/immersivetanning/minigame/StitchPattern.java)
- [Server defaults](https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/src/main/java/com/otectus/immersivetanning/config/ServerConfig.java) and [protocol registration](https://github.com/otectus/ImmersiveTanning/blob/06416690288ea07c6c7258d806762488823d8a7d/src/main/java/com/otectus/immersivetanning/network/ModNetwork.java)
- [Existing regression suites](https://github.com/otectus/ImmersiveTanning/tree/06416690288ea07c6c7258d806762488823d8a7d/src/gameTest/java/com/otectus/immersivetanning/test)

### Hands-On Delight and Diegesis

- [Hands-On Delight pinned repository](https://github.com/Pocky-l/handson-delight/tree/54697d517938fa9a5876b4a65ab2a38abf331952)
- [Reference gameplay and controls](https://github.com/Pocky-l/handson-delight/blob/54697d517938fa9a5876b4a65ab2a38abf331952/README.md)
- [Reference dependency versions](https://github.com/Pocky-l/handson-delight/blob/54697d517938fa9a5876b4a65ab2a38abf331952/gradle.properties)
- [Cutting-board station schema and actions](https://github.com/Pocky-l/handson-delight/blob/54697d517938fa9a5876b4a65ab2a38abf331952/common/src/main/java/com/pockyl/handson_delight/station/CuttingBoardStation.java)
- [Board interaction behavior](https://github.com/Pocky-l/handson-delight/blob/54697d517938fa9a5876b4a65ab2a38abf331952/common/src/main/java/com/pockyl/handson_delight/client/BoardBehavior.java)
- [Tool contact poses](https://github.com/Pocky-l/handson-delight/blob/54697d517938fa9a5876b4a65ab2a38abf331952/common/src/main/java/com/pockyl/handson_delight/client/ToolStroke.java)
- [Diegesis pinned repository](https://github.com/Pocky-l/diegesis/tree/7b9b343b49c31bbaf56e2b078668166fb7e5a068)
- [Dependency and setup guidance](https://github.com/Pocky-l/diegesis/blob/7b9b343b49c31bbaf56e2b078668166fb7e5a068/docs/getting-started.md)
- [Station authority, storage, actions, and limits](https://github.com/Pocky-l/diegesis/blob/7b9b343b49c31bbaf56e2b078668166fb7e5a068/docs/stations.md)
- [Camera shots, clear view, scenes, and inventory presentation](https://github.com/Pocky-l/diegesis/blob/7b9b343b49c31bbaf56e2b078668166fb7e5a068/docs/scenes.md)
- [Client behavior, strokes, shapes, and observer reconstruction](https://github.com/Pocky-l/diegesis/blob/7b9b343b49c31bbaf56e2b078668166fb7e5a068/docs/client.md)




