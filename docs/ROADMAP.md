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

- [x] `Vector2` — this library's `CGPoint`/`CGVector` stand-in (see `docs/API_COMPATIBILITY.md`);
      `Rect` — its `CGRect` stand-in, a plain Kotlin value type rather than
      `android.graphics.RectF` (whose instance methods aren't safe to call from plain JVM unit
      tests without Robolectric)
- [x] `SKNode` — transform hierarchy (`position`, `zPosition`, `zRotation`, `xScale`/`yScale`,
      `alpha`, `isHidden`, `isPaused`, `name`, `userData`), `addChild`/`removeFromParent`/
      `removeAllChildren`, `childNode`/`enumerateChildNodes` (direct-child exact-name match only;
      see `docs/API_COMPATIBILITY.md`), coordinate conversion (`convertTo`/`convertFrom`, renamed
      from Apple's `convert(_:to:)`/`convert(_:from:)` — see `docs/API_COMPATIBILITY.md`),
      `calculateAccumulatedFrame()`, `intersects(_:)`
- [x] `SKScene` — extended Phase 1's lifecycle-callback-only shell to subclass `SKNode` and added
      `size`, `scaleMode` (`.fill`/`.aspectFill`/`.aspectFit`/`.resizeFill`), `anchorPoint`,
      `backgroundColor`
- [x] Pure Kotlin, no GL dependency — fully unit-testable independent of a live GL context (28
      unit tests across `Vector2`/`Rect`-adjacent transform math, child management, coordinate
      conversion, and accumulated-frame/intersection logic)

## Phase 3 — Textures & Sprite Rendering

- [x] `SKTexture` — wraps a `Bitmap`, `filteringMode`, rect subset (`SKTexture(rect:in:)`, sharing
      GPU state with the texture it's carved from — see `docs/ARCHITECTURE.md`)
- [x] `SKBlendMode`, `SKTextureFilteringMode`
- [x] GPU resource manager: `SKResourceRegistry.generation`, a lazy-upload counter added alongside
      Phase 1's register/`reloadAll` callback list — see `docs/ARCHITECTURE.md`
- [x] OpenGL ES 2.0 sprite renderer (`SKSpriteRenderer`, internal): default vertex/fragment shader
      program, draw list flattened from the scene graph (`buildSpriteDrawList`) and sorted by
      `zPosition` (ties broken by tree order, per Apple's documented rule), batched by texture +
      blend mode. `SKScene.scaleMode` letterbox/crop math (`computeSceneProjection`) is pure
      Kotlin and unit-tested; the actual `GLES20`/`GLUtils`/`Matrix` calls are not — see this
      phase's testing note below
- [x] `SKSpriteNode` — `texture`, `color`/`colorBlendFactor`, `size`, `anchorPoint`, `blendMode`
- [x] `SKTextureAtlas` — runtime atlas packer (Apple auto-packs atlases at Xcode build time; no
      Android equivalent, so this is a runtime alternative — see `docs/API_COMPATIBILITY.md`); the
      packing layout algorithm (`packTextureAtlas`) is pure Kotlin and unit-tested separately from
      the actual bitmap compositing
- [x] Testing note: `SKTexture`/`SKSpriteRenderer`/`SKTextureAtlas.pack` all ultimately touch
      `android.graphics.Bitmap`/`Canvas` or `GLES20`/`GLUtils`, none of which are safe to call from
      plain JVM unit tests without Robolectric — consistent with `CLAUDE.md`'s documented testing
      gap. Everything reachable *without* constructing a real `Bitmap` (draw-list building,
      alpha/visibility inheritance, zPosition sorting, color-blend math, scaleMode projection math,
      atlas packing layout) is still pure Kotlin and unit-tested

## Phase 4 — Shapes & Labels

- [x] Generalized Phase 3's quad-only render pipeline into a flat-triangle-list one
      (`SKSpriteRenderer` → `SKSceneRenderer`, `buildSpriteDrawList` → `buildRenderCommands`) so
      shapes (arbitrary triangle meshes, no texture) interleave correctly by `zPosition` with
      sprites and labels (both still just textured quads) in one draw list — needed because all
      three node types must sort together, not as separate draw passes
- [x] `SKShapeNode` — `path` (an `android.graphics.Path`, this library's `CGPath` stand-in),
      `strokeColor`/`fillColor`/`lineWidth`/`glowWidth` (stored, not rendered — see
      `docs/API_COMPATIBILITY.md`). Fill via ear-clipping triangulation (`triangulateFill`),
      stroke via a per-segment quad ribbon (`triangulateStroke`) — both pure Kotlin and
      unit-tested; flattening the `Path`'s curves into line segments first (`flattenPath`, via
      `PathMeasure`) is not, for the same Android-API-safety reasons as everything else touching
      `Bitmap`/`Canvas`/`GLES20`
- [x] `SKLabelNode` — `text`/`fontName`/`fontSize`/`fontColor`/alignment modes, glyphs rendered via
      `android.graphics.Paint`/`Canvas` into a cached texture, regenerated only when the text or
      font actually changes (no CoreText equivalent on Android — see
      `docs/API_COMPATIBILITY.md`). The alignment-offset math (`labelQuadCorners`) is pure Kotlin
      and unit-tested separately from the `Paint`-based measurement/rendering it consumes

## Phase 5 — Actions

- [x] `SKAction` — factory surface: `moveTo`/`moveBy`, `scaleTo`/`scaleBy` (uniform and per-axis),
      `rotateTo`/`rotateBy` (shortest angular path), `resizeTo`/`resizeBy`, `fadeIn`/`fadeOut`/
      `fadeAlphaTo`/`fadeAlphaBy`, `hide`/`unhide`, `colorize`, `wait`/`wait(withRange:)`, `run`
      (block), `removeFromParent`, `sequence`/`group`/`repeat`/`repeatForever`, `customAction`,
      `animate` (texture list), `reversed()`, `speed`, `timingMode`/`timingFunction`. **Deferred**
      (see "Explicitly Out of Scope" below): `followPath`, `playSoundFileNamed`,
      `run(_:onChildWithName:)`
- [x] `SKActionTimingMode` — linear/easeIn/easeOut/easeInEaseOut, plus a custom `timingFunction`
      property
- [x] Frame-stepped executor (`SKActionState`/`stepAction`, per running action — not
      coroutines/suspend, to match SpriteKit's exact per-frame evaluation timing and keep
      `speed`/pause semantics simple), wired into `SKNode.run`/`removeAction`/`stepActions` and
      `SKView`'s frame loop (between `update(deltaTime)` and `didEvaluateActions()`). Correctly
      carries "leftover" time from a finished leaf action into the next step of an enclosing
      `sequence`/`repeat` within the same frame (important at low frame rates); a `group`'s
      children may each finish at a different point within a frame, but the group itself always
      reports finishing with zero leftover rather than tracking the exact remainder — a documented
      simplification, see `docs/API_COMPATIBILITY.md`. Pure Kotlin, no OpenGL/Android
      dependency — fully unit-tested (28 tests covering leaf interpolation, overflow/leftover
      carrying, sequence/group/repeat composition, `reversed()`, and `SKNode` integration)

## Phase 6 — Camera, Effects, Crop, Constraints

- [x] `SKCameraNode` — `scene.camera`, `containsNode`. The renderer expresses every render
      command's vertices relative to the camera (via `SKNode.convertTo`, from Phase 2) instead of
      the scene directly when one is set — moving/scaling the camera pans/zooms the view, reusing
      already-tested transform-conversion code rather than building separate view-matrix math
- [x] `SKEffectNode` — `shouldEnableEffects`/`shouldRasterize`/`shouldCenterFilter` baseline only
      (stored, no observable effect); Core Image `filter` is **out of scope** (no Android
      equivalent) — see `docs/API_COMPATIBILITY.md`
- [x] `SKCropNode` — `maskNode`, clipping to its *bounding box* via `glScissor` (not true
      per-pixel masking — see `docs/API_COMPATIBILITY.md`). The render command list carries an
      inherited (and, for nested crop nodes, progressively narrowed) `clipRect`; the renderer
      batches by (texture, blend mode, clip rect) and toggles `GL_SCISSOR_TEST` between runs
- [x] `SKConstraint`/`SKRange` — `positionX`/`positionY`/`position`/`zRotation`/`distance`/
      `orient`, applied after physics, per Apple's documented per-frame order. `SKRange`'s
      `SKRange(lowerLimit:)`/`SKRange(upperLimit:)` renamed to `atLeast`/`atMost` (proactively,
      to avoid the same Kotlin-overload-resolution collision `SKNode.convertTo`/`convertFrom` hit)
- [x] `SKRegion` — not implemented; every constraint Apple documents as taking one
      (`SKConstraint.positionX(_:y:)` and friends) actually takes an `SKRange`, so there was
      nothing in this phase's scope that needed it — see `docs/API_COMPATIBILITY.md`
- [x] Pure Kotlin, no OpenGL/Android dependency — fully unit-tested (27 new tests: constraint
      math, `SKCameraNode.containsNode`, `Rect.intersection`, and crop-node clip-rect
      propagation/nesting)

## Phase 7 — Physics

Custom sequential-impulse 2D rigid-body engine, zero external dependencies (matches GameplayKit's
"self-contained" convention). Documented as *contract-conformant, not bit-identical* — SpriteKit's
own physics engine internals aren't public, same framing GameplayKit uses for its undocumented
algorithms (steering, noise, Gaussian sampling).

- [x] **7a** — `SKPhysicsWorld`/`SKPhysicsBody` core: circle/rectangle/polygon/edge-loop shapes,
      gravity, dynamics (mass/density/friction/restitution/damping), semi-implicit Euler
      integration, SAT narrow-phase, category/collision/contactTest bitmasks. `SKPhysicsShape`
      (circle/convex-polygon/edge-chain) carries hand-verified mass/moment-of-inertia formulas
      (solid-disk for circles, the standard polygon second-moment sum for polygons — verified
      against the known `I = mass*(w²+h²)/12` square formula); `density` is the source of truth,
      so setting `.mass` back-computes it. `SKPhysicsBody` factories match Apple's:
      `circleOfRadius`/`rectangleOf`/`polygonFrom`/`edgeLoopFrom`/`edgeFrom` — `polygonFrom` takes
      a `List<Vector2>` (no `CGPath` equivalent), and `bodies(fromTexture:...)` texture-alpha-mask
      bodies aren't implemented. `SKWorldShape`/`narrowPhase` (`SKPhysicsCollision.kt`) is pure
      Kotlin and unit-tested independent of any node/scene: circle-circle, circle-polygon (inside
      and outside cases), polygon-polygon SAT, and circle/polygon-vs-edge-chain
      (segment-by-segment); two edge chains never collide with each other (both are static), and
      contact points for polygon-polygon/polygon-chain cases are an edge midpoint rather than a
      true clipped contact point. `SKPhysicsSimulation.kt`'s solver is **linear-only**: sequential
      impulses resolve normal (restitution) and tangential (Coulomb friction) velocity, but
      contact-point torque (spin from an off-center hit) is deferred — `applyTorque`/
      `applyAngularImpulse` still work, collision response just doesn't itself impart spin. O(n²)
      AABB broad phase, not scoped to large body counts; Baumgarte positional correction
      (20%/step, above a small penetration slop) resolves leftover overlap after the velocity
      solve. A node's world-space shape reuses `SKNode.convertTo` (the same trick Phase 6's camera
      support used); a circle's world radius is measured from where its local +x edge lands,
      exact under uniform scale/rotation, an ellipse-as-circle approximation under non-uniform
      scale. Adds `SKNode.physicsBody`/`SKScene.physicsWorld`; `SKView`'s frame loop now calls
      `simulatePhysics(scene, deltaTime)` between action evaluation and `didSimulatePhysics()`.
      Pure Kotlin, unit-tested without a live GL/Android context (36 new tests: mass/inertia
      formulas, collision manifolds, and full simulation steps — gravity, resting contacts,
      bitmask filtering, `isPaused`/`pinned` bodies)
- [x] **7b** — `SKPhysicsContact`/`SKPhysicsContactDelegate` (`didBegin`/`didEnd`). Contact
      notification is decoupled from physical collision response: every touching pair found by
      the narrow phase is checked against `contactTestBitMask` (Apple's documented rule — reported
      if either body's category is in the *other*'s contact-test mask) independent of whether
      `collisionBitMask` lets them physically collide, so a zero-`collisionBitMask` "sensor" body
      still reports contact without ever being pushed. `SKPhysicsWorld` tracks the set of
      currently-touching pairs (keyed by object identity, not equality) across frames to fire
      `didBegin` only on the first touching frame and `didEnd` only once they stop being observed
      touching — including when one body leaves the scene entirely, since it simply stops
      appearing in that frame's observations. `SKPhysicsContact.collisionImpulse` is always `0` —
      not threaded back from the solver in this port, see `docs/API_COMPATIBILITY.md`. 5 new tests
- [x] **7c** — `SKPhysicsJoint` family: pin, spring, fixed, sliding, limit. Constructed via
      idiomatic Kotlin constructors (`SKPhysicsJointPin(bodyA, bodyB, anchor)`, etc.) rather than
      Apple's `joint(withBodyA:bodyB:...)` class-method factories; added to a scene's simulation
      via `SKPhysicsWorld.add`/`remove`/`removeAllJoints`. Every joint kind's per-body anchor
      offset (and, for fixed/spring/limit, its other one-time state — relative rotation, spring
      rest length, limit's default `maxLength`) is bound lazily, from each body's *current*
      transform, the first frame the simulation actually processes it — not at construction time,
      since (unlike Apple) a body doesn't know its owning node, so the joint can't resolve
      world-to-local anchor conversion until a simulation step hands it that context; see
      `docs/API_COMPATIBILITY.md`. The solver is two-stage per step, mirroring contacts: a
      velocity-constraint pass (pin/fixed cancel all relative anchor-point velocity; sliding
      cancels only the perpendicular component; limit cancels the outward component once taut;
      spring applies a velocity-changing force instead, `-stiffness·stretch - damping·relative
      velocity` with `stiffness = (2π·frequency)²`) folded into the same iteration loop as contact
      resolution, followed by a Baumgarte position-correction pass — the velocity pass turned out
      to be load-bearing, not optional: pure position correction alone couldn't keep up with
      gravity's continuously-growing velocity error and drifted. `SKPhysicsJointFixed` additionally
      re-syncs relative rotation kinematically each step (not via torque). `SKPhysicsJointPin`'s
      angle-limit properties, `SKPhysicsJointSliding`'s axis (fixed in world space, doesn't rotate
      with either body), and `reactionForce`/`reactionTorque` are simplified/deferred — see
      `docs/API_COMPATIBILITY.md`. 9 new tests, one per joint kind's core behavior plus world
      joint-list management and a defensive "referenced body left the scene" case
- [x] **7d** — `SKFieldNode`, subset: radial gravity, linear gravity, drag, velocity (noise,
      turbulence, electric, magnetic, spring, vortex, and checkerboard-texture fields deferred —
      see "Explicitly Out of Scope"). One concrete class configured by factory functions
      (`radialGravityField`/`linearGravityField`/`dragField`/`velocityField`), matching Apple's own
      shape (a single `SKFieldNode` class with class-method factories, unlike `SKPhysicsJoint`'s
      genuinely-separate subclasses). Radial gravity's `strength`/`falloff`/`minimumRadius`
      formula (`strength / max(distance, minimumRadius)^falloff`, direction towards the field) is
      a standard inverse-power model, not necessarily Apple's own undocumented one — same
      *contract-conformant, not bit-identical* framing as the rest of physics. A field only affects
      a body if `(field.categoryBitMask and body.fieldBitMask) != 0`, the new
      `SKPhysicsBody.fieldBitMask`. Force-based fields (radial/linear gravity, drag) integrate into
      velocity like gravity does; `velocityField` instead directly overrides a body's velocity each
      step (matching Apple's documented "sets velocity, not acceleration" behavior), applied after
      the force-based fields so it wins when both affect the same body. `region` isn't implemented
      (every field is unbounded, as if `region` were `null` — this port has no `SKRegion`, the same
      gap Phase 6 documented for `SKConstraint`) and `isExclusive` is stored but not enforced. 8 new
      tests: each field kind's core behavior, `isEnabled`/bitmask gating, and non-dynamic bodies
      being unaffected

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

- `SKAction.follow(_:asOffset:orientToPath:duration:)` — path-following actions; would need
  path-length parameterization on top of `SKShapeNode`'s existing path-flattening machinery,
  deferred for scope
- `SKAction.playSoundFileNamed` — needs the audio system (Phase 12)
- `SKAction.run(_:onChildWithName:)` — a niche convenience over `childNode`/`enumerateChildNodes`
  plus a plain `run`
- `SKEffectNode.filter` — Core Image, no Android equivalent
- `SKVideoNode` — video-texture playback; no immediate `SurfaceTexture`/`MediaPlayer` bridge
- `SKLightNode` / normal-map lighting and shadows — shader-dependent, deferred with the rest of
  the advanced shader system
- `SKWarpGeometry` — mesh warp rendering
- `SKFieldNode`'s noise/turbulence/electric/magnetic/spring/vortex fields and texture-based
  (checkerboard) velocity fields — only the radial gravity/linear gravity/drag/velocity subset is
  implemented (Phase 7d)
- The shader-modifier snippet injection system and `SKAttribute` per-vertex custom attributes
- `SKReferenceNode` file-based scene loading — Apple's `.sks` scene archive format has no Android
  equivalent serialization; could be revisited with a custom Kotlin-serialization-based format
- Any `GKScene`-style binding to [GameplayKit for Android](https://github.com/bitzgroup/GameplayKit)
  — a separate integration concern, not part of this library
- Anything Metal-specific (obviously N/A)
