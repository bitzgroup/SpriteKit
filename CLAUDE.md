# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

Phase 0 (project scaffolding), Phase 1 (threading & view foundation), Phase 2 (scene graph core:
`SKNode`/`SKScene`), Phase 3 (textures & sprite rendering: `SKTexture`, `SKTextureAtlas`,
`SKSpriteNode`), Phase 4 (shapes & labels: `SKShapeNode`, `SKLabelNode`), Phase 5 (actions:
`SKAction`'s factory surface plus the frame-stepped `stepAction` executor, wired into
`SKNode.run`/`SKView`'s frame loop), Phase 6 (camera, effects, crop, constraints: `SKCameraNode`,
`SKEffectNode`, `SKCropNode`, `SKConstraint`/`SKRange`), Phase 7 (physics, complete:
`SKPhysicsWorld`/`SKPhysicsBody` — a from-scratch semi-implicit-Euler/SAT rigid-body solver wired
into `SKView`'s frame loop; `SKPhysicsContact`/`SKPhysicsContactDelegate` — contact-test bitmasks
decoupled from collision bitmasks; the `SKPhysicsJoint` family — pin/spring/fixed/sliding/limit,
lazily-bound anchors, a two-stage velocity-then-position solver; `SKFieldNode` — radial/linear
gravity, drag, velocity fields), Phase 8 (particles: `SKEmitterNode`/`SKKeyframeSequence`,
stepped once per frame independent of `SKPhysicsWorld`, rendering through the same render-command
pipeline sprites/labels/shapes already use), Phase 9 (tile maps: `SKTileSet`/`SKTileGroup`/
`SKTileGroupRule`/`SKTileDefinition`/`SKTileMapNode`, grid-only, with adjacency-rule-based
automapping and its own per-map animation clock), Phase 10 (input: full `SKNode` touch
dispatch — `touchesBegan`/`touchesMoved`/`touchesEnded`/`touchesCancelled`, one `SKTouch` at a
time, hit-tested once on `touchesBegan` then tracked per pointer ID through `SKScene`), and Phase
11 (transitions: `SKTransition` fade/crossFade/moveIn/push/reveal/doorway/flip,
`SKView.presentScene(_:transition:)` — no offscreen-framebuffer support, so every effect reduces
to `glViewport` offset/size, a whole-scene alpha multiplier, and (`doorway` only) a scissor clip),
Phase 12 (audio: `SKAudioNode` — one persistent `android.media.MediaPlayer` per node, addressed
by a plain path/URL string rather than an app-bundle `fileNamed:` lookup, driven by
`play`/`pause`/`stop`/`changeVolume`/`changePlaybackRate` `SKAction`s reusing the existing
frame-stepped action machinery; `SKAction.playSoundFileNamed` is a fire-and-forget `MediaPlayer`
clip special-cased in the action executor since its real duration isn't known ahead of time; real
playback is isolated behind an `SKAudioPlaybackHandle`/`SKAudioPlaybackFactory` seam so
`SKAudioNode` itself stays pure/unit-testable), Phase 13 (shaders: `SKShader`/`SKUniform` on
`SKSpriteNode` only — `SKShader.source` is a complete GLSL ES fragment shader compiled in place of
the renderer's default one, rather than Apple's unspecified shader-modifier snippet system;
`SKUniform` is a Kotlin sealed-value shape covering `float`/`vector_float2`/`texture`; every
batched run of render commands now also shares a shader identity, resolved to a lazily-(re)compiled
`SKProgramBinding` the same way `SKTexture` lazily re-uploads; a shader that fails to compile falls
back to the default program rather than crashing the render thread; ships with one built-in example,
`SKShader.grayscale`), and Phase 14 (documentation: full public-API KDoc coverage,
`docs/API_COMPATIBILITY.md`, and README usage examples per subsystem) are complete — the internal
renderer (`SKSceneRenderer`) draws sprites/labels/shapes/particles/tile maps through one
generalized triangle-list pipeline,
camera-relative and crop-clipped via `glScissor` when applicable. See
[`docs/ROADMAP.md`](docs/ROADMAP.md) for the full phase-by-phase plan and progress checklist.

## Intent

This repo implements a **Kotlin library for Android that mirrors Apple's
[SpriteKit](https://developer.apple.com/documentation/spritekit) API** — a scene graph
(`SKNode`/`SKScene`), sprites/shapes/labels, an action system, 2D physics, particles, tile maps,
camera/effects/constraints, transitions, and shaders. `SKView`'s core (`:spritekit`) is a plain
`GLSurfaceView` subclass with zero third-party dependencies, usable directly from XML/View apps; a
separate `:spritekit-compose` module wraps it in a `@Composable` (via `AndroidView`) as the
documented, recommended way to use it, since Jetpack Compose — not the classic `View` system,
which is in maintenance mode — is where Android's UI toolkit investment goes now. See
`docs/ARCHITECTURE.md`'s "Hosting: a View core, a Compose wrapper" section.

The goal is API and behavioral parity with Apple's SpriteKit types (`SKNode`, `SKScene`,
`SKAction`, `SKPhysicsBody`, etc.), but expressed in **idiomatic Kotlin** rather than a literal
Obj-C/Swift transliteration (nullable types instead of Optional-as-comment, data/sealed classes,
extension functions, no `NSPredicate`/`NSCopying`/`NSCoding` equivalents). This repo is the sibling
project to [GameplayKit for Android](https://github.com/bitzgroup/GameplayKit), which follows the
same philosophy for Apple's GameplayKit; the two are not integrated with each other (no
`GKScene`-style binding — see `docs/ROADMAP.md`'s "Explicitly Out of Scope").

**Hosting and threading are the two parts of this design with no Apple equivalent to mirror.**
`SKView`'s core is a `GLSurfaceView` subclass (`:spritekit`), with a `@Composable` wrapper
(`:spritekit-compose`) built via `AndroidView` — see `docs/ARCHITECTURE.md`'s "Hosting: a View
core, a Compose wrapper" section. And where Apple's SpriteKit runs scene mutation, actions,
physics, and rendering on the same main thread that delivers UI/touch events, this library owns a
dedicated render thread itself (`GLSurfaceView`'s own `Renderer` thread) and treats **that thread
as the scene's main thread**, providing UI-thread ↔ render-thread bridge utilities
(`SKView.runOnGLThread`/`runOnUiThread`) to coordinate the two. See
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design — read this before touching
anything related to `SKView`, the frame loop, touch dispatch, or GPU resource lifecycle.

See `docs/ROADMAP.md` for the full implementation plan and progress checklist, organized by
SpriteKit subsystem.

## Commands

All commands run from the repo root.

- Build: `./gradlew assemble`
- Unit tests: `./gradlew testDebugUnitTest` (a single test: `./gradlew testDebugUnitTest --tests "jp.co.bitz.spritekit.SomeTest"`)
- Lint/format check: `./gradlew ktlintCheck` (auto-fix: `./gradlew ktlintFormat`)
- Static analysis: `./gradlew detekt`
- Full CI-equivalent check: `./gradlew ktlintCheck detekt assemble testDebugUnitTest`
- Publish to local Maven repo (sanity-check publishing config): `./gradlew publishToMavenLocal`

If `ANDROID_HOME`/`ANDROID_SDK_ROOT` is not set in the shell, create a `local.properties` (gitignored)
with `sdk.dir=/path/to/Android/sdk`.

Note: unlike a typical Android library, this project has **no `androidTest` (instrumented test)
source set and no emulator/device CI job** — GL-rendering code can't be exercised by a JVM unit
test. Each subsystem is designed so its *logic* (scene graph math, action interpolation, physics,
atlas packing) is pure-Kotlin and unit-testable independent of a live GL context; only the thin GL
draw-call layer itself goes unverified by automated tests. This is a deliberate, accepted gap for
now — see `docs/ROADMAP.md` Phase 0.

## Project structure

- `spritekit/` — the core library module (`jp.co.bitz.spritekit`), zero third-party dependencies;
  `SKView` here is a plain `GLSurfaceView` subclass. Namespace/group configured via
  `gradle.properties` (`GROUP`, `VERSION_NAME`) and `spritekit/build.gradle.kts`.
- `spritekit-compose/` — thin Jetpack Compose wrapper module, depends on `:spritekit` + Compose;
  exposes `SKView` as a `@Composable` via `AndroidView`. The documented, recommended way to use
  this library, but not the only way — see `docs/ARCHITECTURE.md`.
- `gradle/libs.versions.toml` — version catalog; add new dependencies/plugins here, not as
  hardcoded version strings in build files.
- `config/detekt/detekt.yml` — detekt rule overrides (builds upon detekt's default ruleset).
- `docs/ROADMAP.md` — phased implementation plan and progress checklist.
- `docs/ARCHITECTURE.md` — the Android-specific hosting/threading/rendering design (View core +
  Compose wrapper, render-thread-as-main-thread, UI↔render-thread bridge utilities, GPU resource
  lifecycle, frame loop, coordinate systems).
- `docs/API_COMPATIBILITY.md` — deviation log from Apple's SpriteKit API shape, filled in per
  subsystem as each roadmap phase lands.

## Git Branching Workflow

This repo follows a [Gitflow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow)-style
branching model.

| Branch | Branches from | Merges into | Naming |
|---|---|---|---|
| `main` | — | — | Always releasable. Direct pushes are blocked (branch protection); merge only from `release/*` or `hotfix/*`. Each merge is tagged with the corresponding `VERSION_NAME`. |
| `develop` | `main` | — | Integration branch for work heading to the next release. Also branch-protected. |
| `feature/<name>` | `develop` | `develop` | e.g. `feature/phase-1-threading`, `feature/phase-7-physics`. |
| `release/<version>` | `develop` | `main` **and** `develop` | e.g. `release/0.2.0`. Release-prep fixes only, no new features. |
| `hotfix/<name>` | `main` | `main` **and** `develop` | e.g. `hotfix/0.1.1-npe-fix`. Urgent fixes to a released `main`. |

- Every merge goes through a PR (no direct pushes to `main` or `develop`); CI
  (`ktlintCheck detekt assemble testDebugUnitTest`) must pass first.
- `release/*`/`hotfix/*` don't exist yet: the first release (`release/0.1.0`, tagged on `main`) is cut
  once the full `docs/ROADMAP.md` plan is complete. Until then, all work happens on `feature/*`
  branches merged into `develop` — `main` is not touched again until that first release; PRs #2 and
  #4 (bootstrapping `main` from an empty initial state early in Phase 0) were a one-time exception,
  not an ongoing pattern.

## Working in this repo

- **Documentation language:** all docs (README, KDoc, ROADMAP, etc.) must be written in **English**
  — this is an OSS project.
- **Documentation location:** project docs beyond the root `README.md` (roadmap, architecture
  notes, API compatibility notes, etc.) live under `docs/`.
- **No app/demo module, ever.** This repo is meant to be embedded into host apps as a **git
  submodule** — `settings.gradle.kts` must only ever include the `:spritekit` library module.
  Adding a sample/demo Android app module (even for internal testing) would confuse a host app's
  build, since it would look like part of what needs building/signing when this repo is embedded.
  Manual/visual verification of rendering-related work happens outside this repo.
- **`.gitignore`** covers macOS `.DS_Store` plus a standard Android/Gradle project (`.gradle/`, `build/`, `local.properties`, `*.apk`/`*.aab`, keystores, `google-services.json`, IntelliJ/Android Studio files).
- **Git operations:** branch per the workflow above (`feature/*` off `develop`, etc.); do not run `git commit` or `git push` unless explicitly requested by the user for that specific change.
