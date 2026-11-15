# Hands-on stations (1.3.0)

Design record for the 1.3 overhaul that moved fleshing, finishing and leatherworking from screens into the world. It
covers the architecture and the dependency decision behind it, how saves migrate, which combinations were tested, and
what is still open. Player-facing behaviour is in the README ("The loop", "Working at a station"); the release notes
are in CHANGELOG.md.

## The dependency decision: a native Forge backend, not Diegesis

The brief recommended a thin integration of the Diegesis library (the in-world interface framework behind Hands-On
Delight), with a native adapter only if the prototype showed a concrete blocker. Diegesis 1.0.0-beta.9 (MIT, Forge
1.20.1 and Fabric) was evaluated from its source and documentation. These blockers decided it:

1. **No stationary or reduced-motion view.** Its public `CameraRig` always flies the camera to its shot; nothing in its
   public API or documentation keeps the player's own view. The brief requires stationary and reduced-motion modes that
   complete the whole craft through the same commands. Getting them from Diegesis would mean using its internal
   `impl`, `platform` and `mixin` packages, which the brief rules out as an integration contract.
2. **The work is continuous, not node clicks.** Scraping a curved hide cell by cell, cutting along a path from where it
   stopped, and judging a stitch by how far the thread is drawn across the seam need continuous surface hit tests,
   client prediction and server replay of every point. Diegesis models parts to click, grab and carry. The surface
   work would have lived in Immersive Tanning either way, on top of a second scene system.
3. **Footprint and pinning.** Its common mixin config adds 13 client mixins (camera, block hiding, input) to every
   client. Its Forge build is published on CurseForge only (its own getting-started guide uses a CurseMaven
   coordinate), as a beta. It would have been a mandatory beta download on both sides.

**Decision:** one production backend, native Forge, behind a small interface of our own (`StationBehavior`). Two
client mixins: `CameraMixin` (camera pose at the end of `Camera.setup`) and `GameRendererMixin` (no view bobbing while
focused). Neither Diegesis, Hands-On Delight nor Farmer's Delight is a dependency, and no code or assets were copied
from them.

## Architecture

Three kinds of state are kept apart:

| State | Where | Saved | Notes |
|---|---|---|---|
| **Work** | `FleshingWork` (rack), `LeatherworkJob` + `MaterialLedger` (bench), vat slots | Yes, on the block entity; carried by items when taken away | Authoritative, server-side; clients get a sanitized copy (`saveClient`) |
| **Leases** | `StationLease`, `StationLeases` | No | Who operates or watches a station; idle timeout, watchdog for a lost release |
| **Presentation** | `client/station/*`, renderers, `ClientWork` | No | Camera, pointer, prediction, sounds, hands; rebuilt from work state at any time |

- **Geometry.** `StationFrame` maps the north-facing model's local pixels to the world for every facing. Canonical
  surfaces live in `RackGeometry` (the drape over the beam, the frame plane, the lever), `VatGeometry` (the liquor
  surface, handles, opening, paddle, hook) and `BenchGeometry` (the work plane, book, output pad, scrap pile). The
  client's pointer ray uses the matrices of the frame that was drawn. The server checks the same geometry, so a stroke
  means the same thing on both sides.
- **Commands, not outcomes.** A client sends `StationCommandPacket`. It carries the position, the station's instance
  id, the lease id, the job id and a sequence number. Its action has the flags BEGIN, END, STACK and CANCEL, at most
  6 integers and at most 48 points quantized to 12 bits. The server checks:
  - reach (`maxStationDistance`), sight, the tool and the player's permission to interact;
  - a token-bucket rate of 24 commands per second (burst 36; releases are exempt);
  - a per-stroke path budget, so no stroke moves faster than a hand.

  It then replays the points into the work, answers with `StationAckPacket` (accepted or why not, and the work
  revision) and relays accepted strokes and poses to watchers. No packet carries a score or an item.
- **Prediction.** The rack predicts the hide from exactly the points it sends, in the same quantization, so the
  prediction and the server's replay are identical (`StitchInputGameTests.clientPredictionConvergesOnTheServersVerdict`).
  The bench predicts the next stitch for the moment between a thread's release and the next sync.
- **One way out for materials.** Bench materials sit in the job's `MaterialLedger`, and `drain()` is its only exit. Each
  stage has one route out:
  - a refund before the first cut;
  - an Unfinished Leatherwork item after it;
  - recovery when the recipe disappears or a gate now refuses it;
  - one drop route when the bench is broken;
  - the output pad for the finished piece, its scraps and component remainders. Collecting moves what fits and leaves
    the rest; a refund or workpiece that does not fit drops at the player's feet.

  The piece's events, experience, advancements and tool wear happen once, when it is tied off.
- **Work patterns.** `WorkPatterns` loads `immersive_tanning/work_patterns` (schema v1, validated with limits). A
  recipe resolves its pattern in this order:
  1. its explicit `work_pattern`;
  2. a pattern named like its seam;
  3. its 1.2 seam pattern, adapted;
  4. the generic wrap.

  A broken file is skipped with its reason and never breaks a station.

## Migration

Every conversion happens once, on load, in place. A converted station saves in the new format, and loading it again
changes nothing (`WorkGameTests.olderSavesMigrateOnceAndStably` checks each station).

| What | 1.2 save | 1.3 |
|---|---|---|
| Tanner's Rack (`RackVersion` 1 to 2) | Empty, raw hide, or drying hide with its finish | The same. A raw hide gains untouched fleshing work (a fresh seed; nothing lost or granted). |
| Tanner's Vat (`Version` 2 to 3) | Water, tannin, hides with their clocks | The same clocks. Every hide keeps its one paddle-tending credit. The vat gains a facing (old vats face north). |
| Leatherworker's Bench (`BenchVersion` 1 to 2) | An escrow held for an interrupted stitching session | The exact escrowed stacks move to the output pad, once. Sessions are not resumed. |
| Items | Hides, leather, finished pieces | Unchanged. |
| Server config | `minigames.*` | Still loads. New `work` section with defaults; the timing keys are kept but unused. |
| Client config | `accessibility.*` | Still loads. New `focus` section with defaults. |
| Datapacks | Bench recipes, `seam_patterns` | Load unchanged; seams are adapted into work patterns when no work pattern has their name. |
| API | Events, gates, assist sources | Same types and methods. `timedOut` is true only under challenge timing. |
| Network | Protocol 2 | Protocol 3: both sides must be 1.3. |

## Compatibility

"Tested" means run in this checkout with the version shown; "not tested" is not a claim of support or of failure.
Each row's evidence is in the next section.

| Area | Version | Result |
|---|---|---|
| Forge | 47.4.23 (Minecraft 1.20.1) | Tested: GameTests, smoke-test client, build |
| JEI | 15.20.0.135 | Tested in the smoke-test client: categories, "Prepare at bench" registration, recipe lookups |
| Genetic Animals | 0.11.12 (pinned) | Not run for 1.3 (its GameTests are behind `-PgeneticAnimals`) |
| Ultima pack (Butchery, Ice and Fire, Lycanites, Alex's Mobs, Dragn, magic, bench, trades) | The pack's own versions | Not run for 1.3 (needs the local Ultima instance; `tools/packtest/ultima_pack_test.py`) |
| Jade | - | Not tested at runtime (only compiled against its API) |
| Embeddium, Oculus (with and without shaders) | - | Not tested |
| Camera, combat and first-person-body mods | - | Not tested; `StationFocus.verifyCamera` falls back to the stationary view if another mod keeps moving the camera |
| Dedicated server | Forge 47.4.23, production jar | Tested: startup, report, stop; one dev client joined, worked a rack and left |
| Two real clients (operator and observer) | - | Not tested; leases, relays and late joiners are covered by server-side GameTests only |

## Verification

Run on 2026-11-14 in this checkout (Forge 47.4.23, JEI 15.20.0.135, Java 17). Gradle tasks were run through
`.mcmod-tools/gradlew-quiet.sh`.

| Check | Result | Evidence |
|---|---|---|
| `compileJava` before the work (baseline) | Pass | `/tmp/gradle-ImmersiveTanning-compileJava-20261114-142232.log` |
| `runGameTestServer` before the work (baseline) | Pass, 76 tests | `/tmp/itref/baseline_gametest.log` |
| `runGameTestServer` | Pass, all 95 required tests | `/tmp/gradle-ImmersiveTanning-runGameTestServer-20261114-163318.log` |
| `runClient -PsmokeTest` (focus tour, the remaining screens, the full playthrough) | Pass, 87 checks true, 0 false | `run/smoketest/smoketest-result.json`, `run/screenshots/smoketest_*.png` (41), `/tmp/gradle-ImmersiveTanning-runClient-20261114-163039.log` |
| `build` | Pass | `build/libs/immersive-tanning-1.3.0.jar` |
| Dedicated server, production jar (a fresh server directory reusing a local Forge 47.4.23 install's libraries) | Started, `/immersive_tanning report` listed 9 work patterns, stopped cleanly; no client class loaded | `build/server-check/console.log` |
| `runClient -PsmokeTest -PjoinServer=localhost:25599` against that server | Pass: joined, a rack placed by command, focus opened, one stroke scraped the hide on the server, left | `run/smoketest/server-join-result.json`, `build/server-check/console-join.log` |
| `tools/generate_textures.py --check`, `tools/generate_sounds.py --check`, `tools/generate_compat_recipes.py --check` | Pass | - |
| `.mcmod-tools/check_mod.py` | 0 errors, 0 warnings, 12 notes (the loop-registered items and sub-models listed in MODMAP) | - |

The smoke-test client runs before the passing one found these defects, all fixed in this release:

- Station renderers kept a vertex buffer across item, arm and text drawing, so the client crashed with
  `BufferBuilder not started`.
- An empty release batch (the tool had not moved since the last batch) was refused, so the thread stayed in hand.
- The bench view refused a worn knife or awl: the check compared the whole stack, tags included.
- The seat hub lay behind the lever handle, so clicking it moved the lever back to Harness and reset the treatment.
- The scripted vat sweep was shorter than one sweep.
- The bench's buttons sat over the work plane, where a stroke could press "Take the workpiece".
- Scrape flecks came from the server copy at the hide's centre and looked huge next to the camera.
- The detent labels were drawn too large and behind the post.
- Notices and the key footer covered the guide and the hotbar.

The visual follow-up aligns knife edges and awl tips to the held models, keeps arms at the work's depth,
maps panel grain continuously across triangles, and lights draped and submerged hides with outward normals. Lacing
attaches to the deformed pelt; panels have cut edges and slack thread curves. Contact flecks are short-lived and
rate-limited; vat wakes blend and stay inside the opening. Grain and contrast settings refresh existing hide textures.
Hover labels wrap above the guide, and finish labels sit outside the hide without showing through solid objects.

The follow-up client tour passed 91 checks (`/tmp/gradle-ImmersiveTanning-runClient-20261114-171403.log`), including
regressions for continuous panel UVs and hide lighting normals, with 44 screenshots. It includes left-handed and
tools-only bench views, the bench at 854x480, GUI scale 3, stationary/reduced-motion views and the full playthrough.
Before/after captures and the scoped diff are in `build/visual-review/`. The camera-motion assertion now uses an
explicit transition duration and samples the first focused tick; sampling the default short glide a tick later was
intermittent under startup stalls. The prior client settings are restored after the check. All 95 required GameTests
also passed (`/tmp/gradle-ImmersiveTanning-runGameTestServer-20261114-171608.log`). Their prediction check now compares
each accepted batch, including the completed hide's retained surface: some random layouts finish before its six
scripted strokes, at which point `work()` is deliberately cleared.

The arm and pattern refinement keeps every working arm at its vanilla dimensions; task and camera changes only
rotate and position it. Skin sleeves share the configured translucency, and the supporting palm rests on the active
panel or hide. Bench chalk arrows show grain direction, grooves identify each seam, and the HUD names the current
join. The nine authored patterns now use 8–15 stitches, with shaped contour cuts, rotated assembly pieces, a separate
boot heel counter and trouser waistband, both inseams, curved bindings, and reinforcing seams for tack. Cutting and
seating need more care; assisted interaction still widens its tolerances. Existing jobs retain their saved pattern.

Validation for this refinement: all 95 required GameTests passed and the repeated client tour passed 108 boolean
checks, including rigid wide/slim arm geometry, real input through cutting/rotating/sewing boots, every gear pattern
in cutting and stitching views, and an asserted 854×480 render target. The window manager may ignore native resize
requests; the smoke fixture now resizes the render target explicitly and restores it afterwards. Captures, results,
commands and the scoped patch are in `build/workshop-review/`.

The store screenshot mode (`-Dimmersive_tanning.screenshots.output`) also passed a live client run, capturing 21
native 1920×1080 images through fleshing, vat tending, Harness finishing, cutting, panel rotation and stitching.
The resulting tunic retained Integrity 90 and Workmanship 100. The curated gallery, captions and upload ZIP are
in `art/curseforge/`; raw captures and validation evidence are in `build/curseforge-gallery/`.

## Known issues and remaining work

- **Not run:**
  - the Ultima pack test (`tools/packtest/ultima_pack_test.py`);
  - the Genetic Animals GameTests (`-PgeneticAnimals`);
  - Embeddium and Oculus, camera and combat mods;
  - two real clients (operator and watcher);
  - frame-rate and latency variation;
- **Hands.** Working arms use rigid vanilla proportions (4×12×4, or 3×12×4 for slim skins), including sleeves and
  left-handed use. `focus.armOpacity` defaults to 0.35; tools remain opaque. Arms blend after the world so the vat
  liquor and work remain visible. Unusual fields of view, shader packs and additional skin/model mods still benefit
  from player testing.
- **Pacing targets** from the brief (fleshing time, finish spread) have not been measured with players.
- **Translations.** The four other locales retain their earlier keys; newer station and seam names fall back to English.
- **Sounds.** The older events (mount, dried, fill, hide, bubble, stitch, finish, strip, repair) still use vanilla
  sounds.
