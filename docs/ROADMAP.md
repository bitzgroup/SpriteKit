# Implementation Roadmap

This document tracks progress implementing a Kotlin/Android library that mirrors Apple's
[SpriteKit](https://developer.apple.com/documentation/spritekit) API and behavior. `SKView`'s core
is a plain Android `View` (`:spritekit`, zero third-party dependencies), with a Jetpack Compose
wrapper (`:spritekit-compose`) as the documented, recommended way to use it — see
`docs/ARCHITECTURE.md`'s "Hosting: a View core, a Compose wrapper" section.

Check off items as they are implemented and tested. Items tied to Apple-platform-only concerns
(Core Image filters, Metal, CoreText, `.sks` archive files) are noted as **out of scope** since
there is no Android equivalent to bind to.

## Design Principles

### Kotlin-idiomatic, not a literal port

The public API follows SpriteKit's design and behavior, but is expressed in idiomatic Kotlin
rather than a literal Obj-C/Swift-to-Kotlin transliteration — the same principle
[GameplayKit for Android](https://github.com/bitzgroup/GameplayKit) follows:

- Nullability is modeled with Kotlin's type system (`?`), not Optionals-as-comments.
- Time intervals (Apple's `TimeInterval`, e.g. `update(_ currentTime:)`) are modeled as
  `kotlin.time.Duration` where the API takes a duration rather than an absolute timestamp.
- Prefer Kotlin constructs where they fit naturally: data classes, sealed classes/interfaces,
  extension functions, named/default arguments, property syntax over getter/setter methods.
- No `NSPredicate`/`NSCopying`/`NSCoding` equivalents — see `docs/API_COMPATIBILITY.md`.
- Class/member names follow SpriteKit naming (e.g. `SKNode`, `SKAction`, `SKPhysicsBody`) for
  discoverability by developers coming from Apple's docs; internals and supporting APIs use
  standard Kotlin conventions.
- Deviations from the Apple API shape are recorded as they happen — see
  `docs/API_COMPATIBILITY.md`.

### A dedicated render thread is the scene's main thread

Apple's SpriteKit runs scene mutation, action evaluation, physics, and rendering on the same
thread that also delivers UI/touch events (the main thread, driven by `CADisplayLink`). Android
splits these naturally: a `GLSurfaceView` owns a dedicated render thread for its `Renderer`
callbacks, separate from the UI thread that owns the `View`/`Activity` lifecycle and raw touch
events.

This library treats **that render thread as the scene's main thread** — all `SKNode`/`SKScene`/
`SKAction`/physics state is confined to it, mirroring SpriteKit's own single-thread-confinement
contract but relocating "main" away from Android's UI thread. See `docs/ARCHITECTURE.md` for the
full threading model and the UI-thread ↔ render-thread bridge utilities this requires (one of two
pieces of this library with no Apple equivalent to mirror).

### A View core, a Compose wrapper

Jetpack Compose and Android's classic `View` system aren't mutually exclusive — `AndroidView` and
`ComposeView` are officially supported, bidirectional interop points between them. This library's
`SKView` core (`:spritekit`) is a plain `GLSurfaceView` subclass, so it works directly in XML/View
apps with zero third-party dependencies, and its render-thread/`EGLContext` lifecycle is built on
`GLSurfaceView`'s long-proven implementation. A separate `:spritekit-compose` module wraps it in a
`@Composable` via `AndroidView` — since Google's ongoing UI investment is in Compose (the `View`
system is in maintenance mode), that wrapper is the documented, recommended way to use this
library, even though it isn't the only way. See `docs/ARCHITECTURE.md`'s "Hosting: a View core, a
Compose wrapper" section for the full rationale and API shape.

## Phase 0 — Project Setup

- [x] Scaffold Gradle Android library module (Kotlin DSL, `com.android.library` plugin)
- [x] Configure Kotlin, min/target/compile SDK versions (minSdk 24, compileSdk/targetSdk 34)
- [x] Configure unit test setup (JUnit / kotlin.test)
- [x] Configure `ktlint`/`detekt`
- [x] Set up CI (build + test on push/PR via GitHub Actions)
- [x] Set up Maven publishing configuration (`maven-publish` scaffold, verified with
      `publishToMavenLocal`); actual Maven Central/JitPack release credentials still TBD
- [x] No app/demo module anywhere in `settings.gradle.kts` — this repo is consumed as a git
      submodule by host apps, so only library module(s) exist (see `CLAUDE.md`'s "Working in this
      repo" section)
- [ ] Add the `:spritekit-compose` module (Phase 1) — a thin Jetpack Compose wrapper around
      `:spritekit`'s `View`-based `SKView`; `:spritekit` itself stays dependency-free (see
      `docs/ARCHITECTURE.md`'s "Hosting: a View core, a Compose wrapper" section)

## Phase 1 — Threading & View Foundation

*No GameplayKit precedent — this is the Android-specific infrastructure layer everything else is
built on. See `docs/ARCHITECTURE.md` for the full design.*

- [x] `SKScene` — minimal Phase 1 shell: just the per-frame lifecycle callbacks
      (`update`/`didEvaluateActions`/`didSimulatePhysics`/`didApplyConstraints`/`didFinishUpdate`)
      and `isPaused`. Apple's `SKScene` also extends `SKNode` for the scene graph (`size`/
      `scaleMode`/`anchorPoint`/background/children) — Phase 2 extends this class to subclass
      `SKNode` once it exists, rather than introducing a throwaway placeholder type
- [x] `SKView` (`:spritekit`) — `GLSurfaceView` subclass hosting a scene (`presentScene`), owns the
      `GLSurfaceView.Renderer` that drives the frame loop; works directly in XML/View apps
- [x] Frame loop: `RENDERMODE_CONTINUOUSLY`; per-frame order matches SpriteKit's documented
      sequence — `update(deltaTime)` → evaluate actions → simulate physics → apply constraints →
      render → `didFinishUpdate()` (render step is just a `glClear` until Phase 3's pipeline lands)
- [x] `SKView.runOnGLThread { }` / `SKView.runOnUiThread { }` — the UI-thread ↔ render-thread bridge
      utilities
- [x] Touch-event marshaling: UI-thread `MotionEvent` snapshotted into an immutable `SKTouchEvent`,
      handed to the render thread via `runOnGLThread` and delivered through a settable
      `SKView.onTouch` callback; real `SKNode`/`SKScene` touch dispatch replaces that callback in
      Phase 10 once nodes exist. Coordinates stay in view space (pixels, y-down) at this stage —
      converting to the scene's y-up space needs the scene size/scale Phase 2/3 add
- [x] `SKResourceRegistry` — render-thread-confined GPU resource lifecycle groundwork (reload hook
      for context loss, via `SKReloadableResource`); no actual GPU resources yet (those start in
      Phase 3)
- [x] Classic-View lifecycle: `SKView.onPause()`/`onResume()`, callable from `Activity`/`Fragment`
      callbacks (plain `GLSurfaceView.onPause`/`onResume` + `scene.isPaused`)
- [x] `:spritekit-compose` module — `@Composable fun SKView(scene, modifier, state)` via
      `AndroidView(factory = { context -> SKView(context) })`, `rememberSKViewState()` delegating
      to the wrapped `SKView`'s bridge utilities, and a `DisposableEffect` on `LocalLifecycleOwner`
      that calls the wrapped view's `onPause`/`onResume` automatically

## Phase 2 — Scene Graph Core

- [ ] `SKNode` — transform hierarchy (`position`, `zPosition`, `zRotation`, `xScale`/`yScale`,
      `alpha`, `isHidden`, `isPaused`, `name`, `userData`), `addChild`/`removeFromParent`,
      `childNode(withName:)`, `enumerateChildNodes(withName:)`, coordinate conversion
      (`convert(_:to:)`/`convert(_:from:)`), `calculateAccumulatedFrame()`, `intersects(_:)`
- [ ] `SKScene` — extend Phase 1's lifecycle-callback-only shell to subclass `SKNode` (now that it
      exists) and add `size`, `scaleMode` (`.fill`/`.aspectFill`/`.aspectFit`/`.resizeFill`),
      `anchorPoint`, background color
- [ ] Pure Kotlin, no GL dependency — fully unit-testable independent of a live GL context

## Phase 3 — Textures & Sprite Rendering

- [ ] `SKTexture` — from `Bitmap`/drawable resource/asset, `filteringMode`, rect subset
- [ ] GPU resource manager wired into Phase 1's `SKResourceRegistry`
- [ ] OpenGL ES 2.0 sprite renderer: default vertex/fragment shader program, draw list flattened
      from the scene graph and sorted by `zPosition` (ties broken by tree order, per Apple's
      documented rule), batched by texture + blend mode
- [ ] `SKSpriteNode` — `texture`, `color`/`colorBlendFactor`, `size`, `anchorPoint`, `blendMode`
- [ ] `SKTextureAtlas` — runtime atlas packer (Apple auto-packs atlases at Xcode build time; no
      Android equivalent, so this is a runtime alternative — see `docs/API_COMPATIBILITY.md`)

## Phase 4 — Shapes & Labels

- [ ] `SKShapeNode` — fill/stroke rendered from a triangulated `android.graphics.Path`,
      `strokeColor`/`fillColor`/`lineWidth`/`glowWidth`
- [ ] `SKLabelNode` — `text`/`fontName`/`fontSize`/`fontColor`/alignment modes, glyphs rendered via
      `android.graphics.Paint` into a cached texture (no CoreText equivalent on Android — see
      `docs/API_COMPATIBILITY.md`)

## Phase 5 — Actions

- [ ] `SKAction` — full factory surface: move/scale/rotate/fade/resize/wait/run/sequence/group/
      repeat/repeatForever/speed/reversed/customAction/colorize/follow-path/animate-with-textures/
      playSoundFileNamed
- [ ] `SKActionTimingMode` — linear/easeIn/easeOut/easeInEaseOut/custom timing function
- [ ] Frame-stepped executor (a per-node list of running action state machines evaluated each
      `update(deltaTime)`) — not coroutines/suspend, to match SpriteKit's exact per-frame
      evaluation timing and keep `speed`/pause semantics simple

## Phase 6 — Camera, Effects, Crop, Constraints

- [ ] `SKCameraNode` — viewport transform, `scene.camera`, `containsNode`
- [ ] `SKEffectNode` — `shouldEnableEffects`/`shouldRasterize` baseline only; Core Image `filter`
      is **out of scope** (no Android equivalent)
- [ ] `SKCropNode` — `maskNode`
- [ ] `SKConstraint`/`SKRange`/`SKRegion` — applied after physics, per Apple's documented
      per-frame order

## Phase 7 — Physics

Custom sequential-impulse 2D rigid-body engine, zero external dependencies (matches GameplayKit's
"self-contained" convention). Documented as *contract-conformant, not bit-identical* — SpriteKit's
own physics engine internals aren't public, same framing GameplayKit uses for its undocumented
algorithms (steering, noise, Gaussian sampling).

- [ ] **7a** — `SKPhysicsWorld`/`SKPhysicsBody` core: circle/rectangle/polygon/edge-loop shapes,
      gravity, dynamics (mass/density/friction/restitution/damping), semi-implicit Euler
      integration, SAT narrow-phase, category/collision/contactTest bitmasks
- [ ] **7b** — `SKPhysicsContact`/`SKPhysicsContactDelegate` (`didBegin`/`didEnd`)
- [ ] **7c** — `SKPhysicsJoint` family: pin, spring, fixed, sliding, limit
- [ ] **7d** — `SKFieldNode`, subset: radial gravity, linear gravity, drag, velocity (noise,
      turbulence, electric, magnetic fields deferred — see "Explicitly Out of Scope")

## Phase 8 — Particles

- [ ] `SKEmitterNode` — programmatic configuration only (no `.sks` particle-editor file format to
      parse — see `docs/API_COMPATIBILITY.md`)
- [ ] `SKKeyframeSequence` — used for particle color/scale/alpha ramps and other keyframed values
- [ ] Particle rendering reuses Phase 3's sprite batcher

## Phase 9 — Tile Maps

- [ ] `SKTileSet`/`SKTileGroup`/`SKTileGroupRule`/`SKTileDefinition`
- [ ] `SKTileMapNode`

## Phase 10 — Input

- [ ] Full `SKNode`/`SKScene` touch dispatch (`touchesBegan`/`touchesMoved`/`touchesEnded`/
      `touchesCancelled`) wired through Phase 1's UI→GL bridge
- [ ] Hit-testing via the scene graph's accumulated frame

## Phase 11 — Transitions

- [ ] `SKTransition` — fade/crossFade/moveIn/push/reveal/doorway/flip
- [ ] `SKView.presentScene(_:transition:)`

## Phase 12 — Audio

- [ ] `SKAudioNode` wrapping `SoundPool` (short sound effects) / `MediaPlayer` (music);
      positional/spatial audio is **out of scope** initially

## Phase 13 — Shaders

Shaders are the highest-difficulty subsystem to port faithfully — Apple's shader-modifier snippet
system injects built-in symbols (`u_time`, `v_tex_coord`, ...) per node type in ways that aren't
publicly specified. This phase ships the extensibility hook and one trivial example rather than
full parity; see `docs/ARCHITECTURE.md`.

- [ ] Renderer always dispatches through a "current shader program for this node" concept (default
      built-in program if none set), so `SKShader`/`SKUniform` slot in without re-architecting the
      renderer built in Phase 3
- [ ] `SKShader`/`SKUniform` — custom GLSL ES fragment shader source + uniform bindings
- [ ] One built-in example shader (grayscale/tint) demonstrating the extension point
- [ ] **Deferred** (see "Explicitly Out of Scope"): the shader-modifier snippet system,
      `SKAttribute` per-vertex custom attributes, `SKWarpGeometry`, lighting (`SKLightNode`)

## Phase 14 — Documentation

- [ ] KDoc for all public API surfaces
- [ ] `docs/API_COMPATIBILITY.md` fully filled in (per-subsystem deviation notes, as GameplayKit's
      equivalent document does)
- [ ] README usage examples per module
- **Not in this repo**: no sample/demo app — this repo is consumed as a git submodule by host
      apps, so it stays library code + docs only, same policy as GameplayKit

## Explicitly Out of Scope

- `SKEffectNode.filter` — Core Image, no Android equivalent
- `SKVideoNode` — video-texture playback; no immediate `SurfaceTexture`/`MediaPlayer` bridge
- `SKLightNode` / normal-map lighting and shadows — shader-dependent, deferred with the rest of
  the advanced shader system
- `SKWarpGeometry` — mesh warp rendering
- The shader-modifier snippet injection system and `SKAttribute` per-vertex custom attributes
- `SKReferenceNode` file-based scene loading — Apple's `.sks` scene archive format has no Android
  equivalent serialization; could be revisited with a custom Kotlin-serialization-based format
- Any `GKScene`-style binding to [GameplayKit for Android](https://github.com/bitzgroup/GameplayKit)
  — a separate integration concern, not part of this library
- Anything Metal-specific (obviously N/A)
