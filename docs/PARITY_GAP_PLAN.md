# Apple Parity Gap Audit & Remediation Plan

Audit date: 2026-11-15, against `develop` at `3a6db0b` (0.2.0 plus the parity fixes merged since).

`docs/ROADMAP.md` (Phases 0–15) is complete, and `docs/API_COMPATIBILITY.md` records the
deviations that were decided on purpose while each phase landed. This document records what
neither of them covers: **Apple SpriteKit API that is simply absent from this port without ever
having been written down as a deviation.** These are the gaps a host app only discovers when a
line of its Apple code has nothing to be ported to.

The trigger was exactly such a discovery: while planning Android TV support for a host app,
`SKNode` turned out to have `touches*` but no `presses*`
(`pressesBegan(_:with:)` and siblings), which Apple's SpriteKit has on iOS/tvOS. Rather than
recording that one item, the whole public surface was audited.

## How the audit was done, and its limits

- Every public declaration under `spritekit/src/main` and `spritekit-compose/src/main` was listed
  and compared, class by class, with Apple's documented SpriteKit API.
- The comparison is **from the source and from Apple's documentation only**. Nothing here was
  re-verified against the real framework on a simulator, and no build or test was run as part of
  the audit. Before implementing an item, confirm the Apple behavior it mirrors on a real
  simulator/device first — the practice that already caught the `aspectFit` anchoring and
  `GKMonteCarloStrategist` assumptions.
- Items already listed in `docs/API_COMPATIBILITY.md` or ROADMAP's "Explicitly Out of Scope" are
  not repeated, except in section 5 where this audit recommends changing their status.
- Member-level completeness is high for the classes that exist; it is lower for rarely used
  corners (e.g. every `SKEmitterNode` property). Treat a missing mention as "not checked", not as
  "confirmed present".

Priorities:

| | Meaning |
| --- | --- |
| **P0** | Bug, or a gap that blocks a straightforward port of ordinary Apple code |
| **P1** | Commonly used API; small, pure-Kotlin, unit-testable to add |
| **P2** | Less commonly used API, or needs moderate design work |
| **P3** | Needs renderer infrastructure that does not exist yet (FBO, stencil, per-pixel passes) |

## 1. Bugs and stale documentation

| # | Item | Detail | Pri |
| --- | --- | --- | --- |
| ~~B1~~ | ~~Textures lost after GL context recreation~~ | **Fixed.** Cause: each `SKView`'s `SKResourceRegistry.generation` counted from 0, so a view recreated with its `Activity` reused the old view's generation numbers and textures kept from the old view (a retained scene, cached `SKTexture`s) looked already uploaded, drawing with texture names from a dead context. Generations are now unique process-wide, and a texture re-generates its GL name on any generation change. | P0 |
| ~~B2~~ | ~~`SKView.onResume()` force-unpauses the scene~~ | **Not a bug — matches Apple.** Verified on an iOS simulator: Apple's `SKView` also un-pauses a scene paused via `scene.isPaused` when the app returns to the foreground; only a paused *view* stays paused. `onResume` now leaves a paused view (V1) paused, and the behavior is documented. | P0 |
| ~~B3~~ | ~~`API_COMPATIBILITY.md` is stale on `SKNode.isPaused`~~ | **Done** — entry rewritten. | P1 |
| ~~B4~~ | ~~`SKScene.update` shape is undocumented and contradicts the docs~~ | **Done** — `update(deltaTime: Duration)` stays and is documented as a deviation; the contradicting "Durations" bullet is fixed. | P1 |

## 2. Missing API, by subsystem

### 2.1 Input

| # | Missing | Notes | Pri |
| --- | --- | --- | --- |
| ~~I1~~ | ~~`SKNode.pressesBegan/Changed/Ended/Cancelled(_:with:)` and `UIPress`/`UIPressesEvent` stand-ins~~ | **Done**, per the design verified on a tvOS simulator (recorded in the sample app's `SpriteKitParity` probes): Apple's SpriteKit never delivers presses to a scene by itself, so `SKView.forwardKeyEvent(KeyEvent)` is the host's forwarding hook — the counterpart of a tvOS view controller forwarding its `presses*` — delivering `SKPress`es (Apple-shaped `type`/`phase`) to the presented scene on the render thread. One `began` and one `ended` per press, also when held (auto-repeats consumed, not delivered); a cancelled key-up is `pressesCancelled`; whether Back (`Menu`) is forwarded is the host's decision. `SKNode`'s `TooManyFunctions` budget was raised to 34 for the four overridable callbacks. | P0 |
| I2 | `SKNode.focusBehavior` (`SKNodeFocusBehavior`) | tvOS focus-engine participation. Android's focus system is `View`-based, so node-level focus needs its own design; do only if a host app needs in-scene focus. | P3 |
| ~~I3~~ | ~~`SKNode.atPoint(_:)`, `nodes(at:)`, `contains(_:)`, `frame`~~ | **Done**, with Apple's semantics probed on macOS (accumulated-frame test with closed edges, hidden/`alpha == 0` subtrees skipped, frontmost first, falls back to `self`). `nodes`/`atPoint` are extensions for the `TooManyFunctions` budget. | P0 |
| ~~I4~~ | ~~`SKScene.convertPoint(fromView:)` / `convertPoint(toView:)`, `SKView.convert(_:to:)` / `convert(_:from:)`~~ | **Done** as `convertPointFromView`/`convertPointToView` and `SKView.convertTo`/`convertFrom`, sharing the touch-dispatch projection math. | P1 |
| ~~I5~~ | ~~`SKTouch.tapCount`, `timestamp`, `force`~~ | **Done**, verified on an iOS simulator: quick nearby taps count up per finger, and a drag or long press ends with `tapCount` 0. Android's tap limits stand in for Apple's undocumented ones; `force` is 0, as on Apple without force-sensing hardware. | P2 |

### 2.2 Scene and view lifecycle

| # | Missing | Notes | Pri |
| --- | --- | --- | --- |
| ~~S1~~ | ~~`SKScene.sceneDidLoad()`~~ | **Done**, verified on iOS/tvOS simulators: Apple runs it once, inside the initializer (size already set), never again on presenting or resizing. A Kotlin superclass constructor can't safely call an override, so it runs once right before the first `didMove` instead (recorded deviation). | P2 |
| ~~S2~~ | ~~`SKSceneDelegate` (`SKScene.delegate`)~~ | **Done**, verified on iOS/tvOS simulators: the delegate replaces the scene's own callbacks *per method* (one it doesn't implement still reaches the scene), in the scene's order. The interface's default methods forward to the scene to get the same effect; held strongly (Apple: `weak`). | P2 |
| ~~V1~~ | ~~`SKView.isPaused`, `SKView.scene` (public getter)~~ | **Done**, with Apple's semantics verified on an iOS simulator: setting the view's `isPaused` sets the scene's too; a paused view runs nothing whatever the scene says; `presentScene` un-pauses the incoming scene, and (without a transition) the view too. These Apple behaviors are recorded as tests in the sample app's `SpriteKitParityTests` target. | P0 |
| ~~V2~~ | ~~`SKView.ignoresSiblingOrder`~~ | **Done.** The renderer already drew Apple's default (`false`) order — effective `zPosition`, then tree order — verified by pixel probes on iOS/tvOS simulators. `true` only allows any order for equal-`zPosition` nodes, so the flag is accepted and tree order kept. | P1 |
| ~~V3~~ | ~~`SKView.preferredFramesPerSecond`~~ | **Done**, verified on iOS/tvOS simulators: the view runs at the whole fraction of the display rate nearest the requested one (all of 11–60 match Apple's but 24 and 45, recorded), 0 runs once a second. The default 60 now caps 90/120 Hz displays, as on Apple. | P2 |
| ~~V4~~ | ~~`SKView.showsFPS`, `showsNodeCount`, `showsDrawCount`, `showsQuadCount`, `showsPhysics`, `showsFields`~~ | **Done**, recorded on iOS/tvOS simulators: the statistics share one line at the bottom right (`nodes:7 draws:3 quads:334  58.9 fps`, 10-point monospaced white text over 30% black); the node count includes the scene and hidden/plain nodes; physics outlines are 1-pixel opaque (0.4, 0.8, 0.8) lines above the content. Draw/quad counts are this renderer's own. `showsFields` is accepted but draws nothing: Apple draws it broken on simulators, so there was nothing to record. | P2 |
| ~~V5~~ | ~~`shouldCullNonVisibleNodes`, `isAsynchronous`, `SKViewDelegate.view(_:shouldRenderAtTime:)`~~ | **Done**, verified on iOS/tvOS simulators: the delegate is asked once a frame before `update` (not while the view is paused); `false` skips the frame, and the next frame that runs catches up on the skipped time. `shouldCullNonVisibleNodes`/`isAsynchronous` default to `true` and are accepted with no effect. (`allowsTransparency` is implemented, with an Android limitation — see section 2.9.) | P2 |
| V6 | `SKView.texture(from:)`, `texture(from:crop:)` | Render-to-texture; needs FBO support (R1). | P3 |

### 2.3 Scene graph (`SKNode`)

| # | Missing | Notes | Pri |
| --- | --- | --- | --- |
| ~~N1~~ | ~~`insertChild(_:at:)`, `removeChildren(in:)`, `move(toParent:)`, `inParentHierarchy(_:)`~~ | **Done**, verified on iOS/tvOS simulators. Apple's `move(toParent:)` keeps only the *position* in place — rotation and scale keep their local values — so that is what `moveToParent` does. | P1 |
| ~~N2~~ | ~~`SKNode.speed`~~ | **Done**, verified on iOS/tvOS simulators (inherited multiplicatively; `0` stops actions without removing them). | P1 |
| ~~N3~~ | ~~`childNode(withName:)` search syntax (`//`, `/`, `..`, `*`, `[a-z]`), `subscript(_:)`~~ | **Done**, including Apple's result order (`//` lists a node's children before searching deeper), verified on iOS/tvOS simulators. `subscript` is the `get` operator. | P1 |
| ~~N4~~ | ~~`copy()`~~ | **Done** as an `open fun copy()` per node class (no general `NSCopying`), verified on iOS/tvOS simulators: a deep copy, detached; a new physics body and `userData` map, shared constraints and textures; running actions start over; references within the copy (a scene's camera, a crop node's mask) point at the copies. A subclass of your own overrides `copy()` via `copyInto` to keep its class. | P2 |
| N5 | `SKReachConstraints`, `SKNode.reachConstraints`, `SKAction.reach(to:…)` | Inverse kinematics. | P3 |

### 2.4 Sprites, textures, shapes, labels

| # | Missing | Notes | Pri |
| --- | --- | --- | --- |
| ~~T1~~ | ~~`SKTexture(imageNamed:)`, `SKSpriteNode(imageNamed:)`~~ | **Done**, from `assets/`; a missing image gives Apple's 128×128 placeholder. The `Context` is captured at start-up by a manifest-merged `ContentProvider`, so textures load before any view exists. Drawable resources aren't searched. | P1 |
| ~~T2~~ | ~~`SKTextureAtlas(named:)`, `SKTextureAtlas(dictionary:)`, `preload` / `SKTexture.preload`~~ | **Done**, verified on iOS/tvOS simulators: `named` packs an `assets/<name>.atlas/` folder at runtime; names keep their extensions and are found with or without one; an unknown name gives the 128×128 placeholder and a missing atlas is empty; preloads call back on a background thread. Recording it also fixed packed textures bleeding into each other when stretched: each image now gets a one-pixel border of its own edge pixels. `textureNamed` no longer returns `null`. | P2 |
| T3 | `SKTexture.usesMipmaps`, `SKTexture(data:size:)`, `SKMutableTexture`, `SKTexture.generatingNormalMap`, noise-texture initializers | Normal maps only matter once lighting exists. | P3 |
| ~~T4~~ | ~~`SKSpriteNode.centerRect`~~ | **Done**, verified by pixel probes on iOS/tvOS simulators: the border keeps its texture size on screen through `size` or scale. | P1 |
| ~~T5~~ | ~~`SKSpriteNode.scale(to:)`~~ | **Done** as `scale(to)`. Recording it showed Apple's `SKSpriteNode.size` *includes* the scale, which this library's didn't — fixed along with it. | P1 |
| ~~H1~~ | ~~Remaining `SKShapeNode` convenience initializers: `init(rect:)` / `init(rect:cornerRadius:)` (an arbitrary, non-centered rect), `init(ellipseIn:)`, `init(path:centered:)`, `init(points:count:)`, `init(splinePoints:count:)`~~ | **Done** as companion factories, matching Apple's paths element for element (verified on iOS/tvOS simulators). | P1 |
| ~~H2~~ | ~~`SKShapeNode.isAntialiased`, `blendMode`, `lineLength`~~ | **Done**, recorded on iOS/tvOS simulators. `blendMode` applies to fill and outline. `isAntialiased` widens a drawn outline's frame as Apple's does (and, since H3, antialiases it). Apple reports `lineLength` as `0` on simulators; this returns the real length. Recording it also fixed two gaps: `lineWidth` defaulted to `0` instead of Apple's `1`, and the frame left out the outline. | P2 |
| ~~H3~~ | ~~Antialiased `SKShapeNode` outlines~~ | **Done**, recorded on iOS/tvOS simulators: each outline edge fades linearly over 1.7 screen pixels (1.12 inside, 0.58 outside), scaling with the node and the scene; fills stay hard-edged. Built as a per-vertex-alpha fringe on the stroke geometry, no MSAA. | P2 |
| ~~L1~~ | ~~`SKLabelNode.color`/`colorBlendFactor`, `blendMode`~~ | **Done**, verified by pixel probes on iOS/tvOS simulators: `color` tints the text exactly as a sprite's tints its texture, and `colorize` animates it. Apple ignores a label's `blendMode` (it always alpha-blends), so this library keeps it for API parity only. Recording it also fixed two sprite bugs: an untextured sprite drew white unless `colorBlendFactor` was raised, and `color`'s alpha was ignored. | P2 |
| ~~L2~~ | ~~`SKLabelNode.numberOfLines`, `preferredMaxLayoutWidth`, `lineBreakMode`~~ | **Done** with `StaticLayout`, following Apple's layout rules as recorded on iOS/tvOS simulators (one line ignores the width; a multi-line block aligns `.baseline` like `.bottom`). | P1 |
| ~~L3~~ | ~~`SKLabelNode.attributedText`~~ | **Done**, as a `CharSequence` styled with Android spans, verified on iOS/tvOS simulators: while set it alone is drawn and sized (`text` and the font properties are ignored, setting `text` doesn't clear it); unstyled text is 12-point Helvetica in black; `color` still tints; line and alignment settings apply. | P2 |

### 2.5 Actions

Missing `SKAction` factories (everything else in Apple's list is present):

| # | Missing | Pri |
| --- | --- | --- |
| ~~A1~~ | ~~`moveTo(x:duration:)`, `moveTo(y:duration:)`, `scaleX(to:duration:)`, `scaleY(to:duration:)`, `scale(to: CGSize, duration:)`, `resize(toWidth:duration:)`, `resize(toHeight:duration:)`, `resize(toWidth:height:duration:)`~~ — **Done** (`moveToX`/`moveToY`, `scaleXTo`/`scaleYTo`, `scaleTo(size)`, `resizeToWidth`/`resizeToHeight`). | P1 |
| ~~A2~~ | ~~`rotate(toAngle:duration:shortestUnitArc:)`~~ — **Done**, verified: the default interpolates straight to the target; both end exactly on it. Found along the way: `scaleBy` was additive, Apple's multiplies — fixed. | P1 |
| ~~A3~~ | ~~`setTexture(_:)`, `setTexture(_:resize:)`~~ — **Done**. | P1 |
| ~~A4~~ | ~~`speed(to:duration:)`, `speed(by:duration:)` (depends on N2)~~ — **Done** as `speedTo`/`speedBy`; `speedBy` multiplies, as Apple's does. | P1 |
| ~~A5~~ | ~~`run(_:onChildWithName:)` with the full name-search syntax~~ — **Done** with N3; runs on the first match only, as on Apple. | P2 |
| ~~A6~~ | ~~`applyForce`/`applyTorque`/`applyImpulse`/`applyAngularImpulse` actions, `changeCharge`, `changeMass`, `strength(to:/by:)`, `falloff(to:/by:)`~~ — **Done**, verified on iOS/tvOS simulators: force and impulse actions change the velocity as actions run, following the eased progress; a reversed one pushes the same way; `strength(by:)` multiplies; Apple's `falloff` actions change nothing and never finish, and the port's do the same. Adds a stored `SKPhysicsBody.charge`. | P2 |
| ~~A7~~ | ~~`wait(forDuration:withRange:)` re-randomizing per run, `group` leftover time (both recorded deviations)~~ — **Done**, verified on iOS/tvOS simulators: a ranged wait draws a new length on every run (each pass of a `repeat`, each node) and reports its base `duration`; a `group` hands on the time its last action didn't need, so the next action in a `sequence` starts exactly where it would after a plain wait. | P2 |
| ~~A8~~ | ~~`SKAction.duration` as a public, settable property (verify current visibility)~~ — **Done** — settable; on a composite it changes only the reported value, as on Apple. | P1 |
| A9 | `stereoPan`, `changeObstruction`, `changeOcclusion`, `changeReverb`, `warp(to:)`, `animate(withNormalTextures:)`, `setNormalTexture`, `SKAction(named:)` | Out of scope with their subsystems |
| A10 | Apple's timing curves. Recording A6 showed Apple's `easeIn` is `2t² − t³` (a quarter of the way: 7/64; halfway: 3/8), where the port's is `t²`; record `easeOut`/`easeInEaseOut` too and match them. | P2 |

### 2.6 Physics

| # | Missing | Notes | Pri |
| --- | --- | --- | --- |
| ~~P1~~ | ~~`SKPhysicsWorld.body(at:)`, `body(in:)`, `body(alongRayStart:end:)`, `enumerateBodies(at:/in:/alongRayStart:end:)`~~ | **Done**, verified on iOS/tvOS simulators, including Apple's padded bounding boxes for `body(in:)` and length-150 ray normals. Recording them found that the port's physics units were not Apple's — mass in points² and gravity in points/s² — fixed along with them. | P1 |
| ~~P2~~ | ~~`SKPhysicsBody.node`, `isResting`, `area`, `joints`, `allContactedBodies()`~~ | **Done**, verified on iOS/tvOS simulators; joint anchors are now fixed when a joint is added. | P1 |
| ~~P3~~ | ~~`applyForce(_:at:)`, `applyImpulse(_:at:)`, torque from off-centre contacts~~ | **Done**, verified on iOS/tvOS simulators: an off-center force or impulse turns the body by `r × F` with lengths in meters; bodies turn about their center of mass (an off-center shape swings its node around it); contacts touch at up to two clipped points and push at each, so a box landing on a corner turns flat and a sliding ball starts rolling. Recording it showed Apple simulates in steps of 1/120 of a second whatever the frame rate, moving at most 300 points and turning at most π/2 a step; the port now does too, so scenes settle the same way at any frame rate. Joints are still linear-only. | P2 |
| P4 | `SKPhysicsBody(bodies:)`, `init(edgeChainFrom:)`, `init(edgeLoopFrom: path)`, `init(polygonFrom: path)` | Compound and path-based bodies. | P2 |
| P5 | ~~`SKPhysicsBody.charge`~~ (stored since A6, with no field to act on it), `SKPhysicsWorld.sampleFields(at:)`, `SKRegion`, remaining field kinds, `SKPhysicsContact.collisionImpulse`, joint limits/reaction forces | Already recorded; grouped here as one physics-completeness work item. | P3 |
| P6 | `SKFieldNode` default `falloff` per field kind | Recording A6 showed Apple's `radialGravityField()` starts with a falloff of `2`; the port's fields all start at `0`. Record each kind's defaults and match them. | P2 |

### 2.7 Camera, constraints, particles, tile maps, transitions, audio

| # | Missing | Pri |
| --- | --- | --- |
| ~~C1~~ | ~~`SKCameraNode.containedNodeSet()`~~ — **Done**, verified on iOS/tvOS simulators. | P1 |
| C2 | `SKConstraint.referenceNode`; the point-based overloads `distance(_:to: CGPoint)`, `distance(_:to:in:)`, `orient(to: CGPoint, …)`, `orient(to:in:offset:)` | P2 |
| E1 | `SKEmitterNode.particleAction`, `particleRenderOrder`, `shader`; `targetNode` and the scale/alpha/rotation sequences (recorded) | P2 |
| E2 | `SKKeyframeSequence.interpolationMode`, `repeatMode`, mutation (`addKeyframeValue`, `setKeyframeValue`, …) | P2 |
| M1 | `SKTileMapNode.fill(with:)`, `mapSize`, `color`/`colorBlendFactor`/`blendMode`; `SKTileGroup.empty()`, `SKTileGroup(tileDefinition:)`; `SKTileDefinition.placementWeight` | P2 |
| X1 | `SKTransition.doorsOpenHorizontal/Vertical`, `doorsCloseHorizontal/Vertical`, `doorway` true geometry; `pausesIncomingScene`, `pausesOutgoingScene` | P2 |
| U1 | `SKAudioNode.isPositional`, `SKScene.listener` (recorded as "no positional audio") | P3 |

### 2.8 Whole classes that are absent and unrecorded

| Class | Recommendation |
| --- | --- |
| ~~`SKSceneDelegate`~~, `SKViewDelegate` | ~~Add (S2)~~ done; add `SKViewDelegate` (V5) |
| `SKReachConstraints` | P3 (N5) |
| `SKMutableTexture` | P3 (T3) |
| `SKTransformNode`, `SK3DNode`, `SKRenderer` | Record as out of scope (3D transform / SceneKit / Metal) |
| `SKRegion`, `SKAttribute`/`SKAttributeValue`, `SKWarpGeometry`, `SKLightNode`, `SKVideoNode`, `SKReferenceNode` | Already recorded as out of scope |

### 2.9 Finding: `allowsTransparency` vs. the `GLSurfaceView` core (2026-11-15)

A host app tried to show a background drawn by its own UI (a Compose composable behind the
`SKView`) through a transparent scene, the way Apple's `SpriteView(scene:options: [.allowsTransparency])`
lets SwiftUI content show through. A prototype added `SKView.allowsTransparency` (an RGBA EGL
config, a premultiplied clear color, `PixelFormat.TRANSLUCENT`). It was **not merged**, because:

- A `GLSurfaceView` renders into its own surface, which the system composites either entirely
  **below** its window (the default: the window punches a hole where the view is, so anything the
  app draws behind the view is erased, and a transparent frame shows black) or entirely **above**
  it (`setZOrderOnTop(true)`, needed for the content behind to show through).
- Above the window, it covers everything else the app draws over it: on a phone emulator, a
  Material `AlertDialog` showed the board through it and a `DropdownMenu` didn't appear at all.
  Returning from the home screen also left textures black (compare B1). There is no z-order that
  is both "above what's behind the view" and "below what's in front of it".
- On Apple, `SKView` is an ordinary layer in Core Animation's tree, so the OS composites it in
  order with the views around it, transparency included. Android's equivalent path is a
  `TextureView` (rendered as part of the window), which would mean owning the render thread and
  EGL lifecycle instead of relying on `GLSurfaceView`'s.

**Decision (the maintainers, 2026-11-15): keep `GLSurfaceView`.** Its render-thread/EGL lifecycle
and its fit with the rest of the Android stack are worth more than lifting this limitation. Hosts
that need a background behind the board should draw it **inside the scene** (e.g. a full-scene
sprite below the content) — no transparency needed. Revisit only if a `TextureView` core is wanted
for other reasons (e.g. drawing app UI over the scene in the same window).

**Follow-up (same day):** API parity stays a goal, so `SKView.allowsTransparency` (and the matching
`allowsTransparency` parameter of the Compose `SKView`) was implemented anyway, default `false` as on
Apple, with the limitation above stated in its KDoc and in `API_COMPATIBILITY.md`. It works for a
view nothing is ever shown on top of. The `SKBlendMode.Alpha` blend now also accumulates the frame's
alpha in premultiplied form (`glBlendFuncSeparate`), which is required for correct translucency and
changes nothing visible on an opaque view.

## 3. Renderer infrastructure that gates the P3 items

Several recorded deviations share two missing renderer capabilities. Building them once unblocks
the whole group, so they are tracked as infrastructure rather than per-API work.

| # | Capability | Unblocks |
| --- | --- | --- |
| R1 | Offscreen framebuffer (render a subtree to a texture) | `SKView.texture(from:)`, true transition compositing (cross-fade of two live scenes, real flips/doors), `SKEffectNode.shouldRasterize`, a filter/shader pass on `SKEffectNode`/`SKScene`, `SKShapeNode.glowWidth` |
| R2 | Stencil or alpha-mask clipping | `SKCropNode` per-pixel masking (currently bounding-box `glScissor`) |
| R3 | Per-pixel colorize in the default fragment shader | Exact `colorBlendFactor` on partially transparent textures, `SKBlendMode.multiplyX2`/`multiplyAlpha` |
| R4 | Shader on more node types + Apple's built-in symbols (`u_time`, `v_tex_coord`, `u_texture`, `SKDefaultShading()`) | `SKShapeNode.fillShader`/`strokeShader`, `SKEmitterNode.shader`, `SKScene.shader`, ported shader source working unchanged |

## 4. Proposed work order

Each step is one `feature/*` branch and PR against `develop`, with the matching
`API_COMPATIBILITY.md` update in the same PR.

1. **Docs and bugs first** — B3, B4 (documentation), then B1 (investigate and fix), B2 + V1.
2. **Input parity** — I1 (`presses*`), I3 (point queries), I4 (coordinate conversion).
3. ~~**Lifecycle** — S1 (`sceneDidLoad`), S2.~~ Done.
4. ~~**Everyday API, batch 1** — H1 (shape initializers), N1, N2, A1–A4, A8, T5, C1.~~ Done,
   plus two Apple-parity fixes it surfaced: `SKSpriteNode.size` includes the scale, and
   `scaleBy` multiplies.
5. ~~**Everyday API, batch 2** — T1 (named textures), T4 (`centerRect`), L2, N3 + A5, V2.~~
   Done.
6. ~~**Physics queries** — P1, P2.~~ Done, plus the physics-units fix it surfaced.
7. **Second tier** — the P2 rows above, driven by what host apps actually hit.
8. **Renderer infrastructure** — R1–R4, then the P3 rows they unblock.

Steps 1–2 are what a host app adding Android TV support touches; steps 3–6 are what any
ordinary SpriteKit port will reach for next.

## 5. Suggested changes to recorded scope decisions

- `SKAction.run(_:onChildWithName:)` is implemented but still listed under ROADMAP's "Explicitly
  Out of Scope"; remove it from that list.
- ~~`SKNode.copy()`: revisit the blanket "no `NSCopying`" rule for nodes specifically (N4).~~ Done:
  nodes have `copy()`; the general rule stays.
- Add `SKTransformNode`, `SK3DNode`, `SKRenderer`, `SKMutableTexture`, `SKReachConstraints` to
  `API_COMPATIBILITY.md`'s "Not implemented" list so that their absence is at least recorded.

## 6. Keeping this from recurring

The gaps above accumulated because phases were scoped by *class* ("`SKShapeNode` done") while
parity was only ever checked for the members a host app happened to use.

- When a phase or PR declares a class done, diff its public members against Apple's documentation
  page for that class and record every omission in `API_COMPATIBILITY.md`, even trivial ones.
- When a host app finds a gap, check the sibling members of the same class in the same PR.
- Update this file as items land (strike the row, link the PR); delete it once sections 1–2 are
  empty and everything left lives in `API_COMPATIBILITY.md`.
