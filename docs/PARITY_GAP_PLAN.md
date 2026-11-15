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
| I1 | `SKNode.pressesBegan/Changed/Ended/Cancelled(_:with:)` and `UIPress`/`UIPressesEvent` stand-ins | The gap that started this audit. **Design corrected by verification on a tvOS simulator** (recorded as tests in the sample app's `SpriteKitParity` target): Apple's SpriteKit never delivers presses to a scene by itself — not even with the `SKView` as first responder; a tvOS game overrides `presses*` on its view controller and forwards them to the scene. So: add the four `presses*` callbacks to `SKNode` (no-ops, overridable, Apple-shaped: a `Set<SKPress>` plus an event), an `SKPress` with an Apple-shaped `type` (`upArrow`/`downArrow`/`leftArrow`/`rightArrow`/`select`/`menu`/`playPause`) and `phase`, and — instead of automatic delivery — an `SKView` helper that converts a `KeyEvent` and forwards it to the presented scene on the render thread, for the host `Activity` to call from `onKeyDown`/`onKeyUp` the way a tvOS view controller forwards. Verified Apple delivery shape: one `began` and one `ended` per press, also when held (no repeats); whether the app handles `menu` (Back) is the host's decision, as on tvOS. | P0 |
| I2 | `SKNode.focusBehavior` (`SKNodeFocusBehavior`) | tvOS focus-engine participation. Android's focus system is `View`-based, so node-level focus needs its own design; do only if a host app needs in-scene focus. | P3 |
| ~~I3~~ | ~~`SKNode.atPoint(_:)`, `nodes(at:)`, `contains(_:)`, `frame`~~ | **Done**, with Apple's semantics probed on macOS (accumulated-frame test with closed edges, hidden/`alpha == 0` subtrees skipped, frontmost first, falls back to `self`). `nodes`/`atPoint` are extensions for the `TooManyFunctions` budget. | P0 |
| ~~I4~~ | ~~`SKScene.convertPoint(fromView:)` / `convertPoint(toView:)`, `SKView.convert(_:to:)` / `convert(_:from:)`~~ | **Done** as `convertPointFromView`/`convertPointToView` and `SKView.convertTo`/`convertFrom`, sharing the touch-dispatch projection math. | P1 |
| I5 | `SKTouch.tapCount`, `timestamp`, `force` | Already noted as absent in `API_COMPATIBILITY.md`; `tapCount` is the one ports commonly need (double-tap). | P2 |

### 2.2 Scene and view lifecycle

| # | Missing | Notes | Pri |
| --- | --- | --- | --- |
| S1 | `SKScene.sceneDidLoad()` | `didMove`/`willMove`/`didChangeSize` exist; this is the one lifecycle callback still absent. On Apple it runs once after the scene is initialized or decoded — with no archive loading here, define it as "once, before the first `didMove`". | P2 |
| S2 | `SKSceneDelegate` (`SKScene.delegate`) | `update(_:for:)`, `didEvaluateActions(for:)`, `didSimulatePhysics(for:)`, `didApplyConstraints(for:)`, `didFinishUpdate(for:)`. The subclass hooks exist; the delegate forwarding does not. | P2 |
| ~~V1~~ | ~~`SKView.isPaused`, `SKView.scene` (public getter)~~ | **Done**, with Apple's semantics verified on an iOS simulator: setting the view's `isPaused` sets the scene's too; a paused view runs nothing whatever the scene says; `presentScene` un-pauses the incoming scene, and (without a transition) the view too. These Apple behaviors are recorded as tests in the sample app's `SpriteKitParityTests` target. | P0 |
| V2 | `SKView.ignoresSiblingOrder` | Check what order equal-`zPosition` siblings currently draw in and which Apple mode that corresponds to; then expose the switch. | P1 |
| V3 | `SKView.preferredFramesPerSecond` | Frame-rate cap (battery; TV). | P2 |
| V4 | `SKView.showsFPS`, `showsNodeCount`, `showsDrawCount`, `showsQuadCount`, `showsPhysics`, `showsFields` | Debug overlays. `showsFPS`/`showsNodeCount`/`showsPhysics` are the useful three. | P2 |
| V5 | `shouldCullNonVisibleNodes`, `isAsynchronous`, `SKViewDelegate.view(_:shouldRenderAtTime:)` | `allowsTransparency` is implemented, with an Android limitation — see section 2.9. | P2 |
| V6 | `SKView.texture(from:)`, `texture(from:crop:)` | Render-to-texture; needs FBO support (R1). | P3 |

### 2.3 Scene graph (`SKNode`)

| # | Missing | Notes | Pri |
| --- | --- | --- | --- |
| N1 | `insertChild(_:at:)`, `removeChildren(in:)`, `move(toParent:)`, `inParentHierarchy(_:)` | `move(toParent:)` must preserve the node's scene-space transform. | P1 |
| N2 | `SKNode.speed` | Per-node action speed multiplier, inherited by descendants. Only `SKAction.speed` and `SKPhysicsWorld.speed` exist. | P1 |
| N3 | `childNode(withName:)` search syntax (`//`, `/`, `..`, `*`, `[a-z]`), `subscript(_:)` | Already recorded as a deviation; listed here because ported code using `"//name"` fails silently (returns `null`). | P1 |
| N4 | `copy()` | Deliberately excluded with `NSCopying`, but node copying (stamping out configured prototypes) is common SpriteKit usage. Reconsider as an explicit `open fun copy(): SKNode` per node class. | P2 |
| N5 | `SKReachConstraints`, `SKNode.reachConstraints`, `SKAction.reach(to:…)` | Inverse kinematics. | P3 |

### 2.4 Sprites, textures, shapes, labels

| # | Missing | Notes | Pri |
| --- | --- | --- | --- |
| T1 | `SKTexture(imageNamed:)`, `SKSpriteNode(imageNamed:)` | The only public constructor takes a `Bitmap`, so every host app writes its own loader. Resolve names against `assets/` the same way `SKAudioNode(fileNamed:)` already does (and consider drawable resources). Needs a `Context`, like the audio path. | P1 |
| T2 | `SKTextureAtlas(named:)`, `SKTextureAtlas(dictionary:)`, `preload` / `SKTexture.preload` | `named:` can map to an `assets/<name>.atlas/` folder packed by the existing runtime packer. | P2 |
| T3 | `SKTexture.usesMipmaps`, `SKTexture(data:size:)`, `SKMutableTexture`, `SKTexture.generatingNormalMap`, noise-texture initializers | Normal maps only matter once lighting exists. | P3 |
| T4 | `SKSpriteNode.centerRect` | 9-slice stretching — common for UI panels and buttons. | P1 |
| T5 | `SKSpriteNode.scale(to:)` | Trivial. | P1 |
| H1 | Remaining `SKShapeNode` convenience initializers: `init(rect:)` / `init(rect:cornerRadius:)` (an arbitrary, non-centered rect), `init(ellipseIn:)`, `init(path:centered:)`, `init(points:count:)`, `init(splinePoints:count:)` | The centered `circle`/`rect`/`ellipse` factories exist; these are the rest of Apple's set. Add as further companion factories. | P1 |
| H2 | `SKShapeNode.isAntialiased`, `blendMode`, `lineLength` | `lineCap`/`lineJoin`/`miterLimit` exist. No antialiasing switch or MSAA configuration exists in the renderer; Apple's default is `isAntialiased = true`, so check how shape edges compare. | P2 |
| L1 | `SKLabelNode.color`/`colorBlendFactor`, `blendMode` | | P2 |
| L2 | `SKLabelNode.numberOfLines`, `preferredMaxLayoutWidth`, `lineBreakMode` | Recorded as a deviation; `android.text.StaticLayout` can do the layout. Needed for localized text. | P1 |
| L3 | `SKLabelNode.attributedText` | Map to `android.text.Spanned`. | P2 |

### 2.5 Actions

Missing `SKAction` factories (everything else in Apple's list is present):

| # | Missing | Pri |
| --- | --- | --- |
| A1 | `moveTo(x:duration:)`, `moveTo(y:duration:)`, `scaleX(to:duration:)`, `scaleY(to:duration:)`, `scale(to: CGSize, duration:)`, `resize(toWidth:duration:)`, `resize(toHeight:duration:)`, `resize(toWidth:height:duration:)` | P1 |
| A2 | `rotate(toAngle:duration:shortestUnitArc:)` | P1 |
| A3 | `setTexture(_:)`, `setTexture(_:resize:)` | P1 |
| A4 | `speed(to:duration:)`, `speed(by:duration:)` (depends on N2) | P1 |
| A5 | `run(_:onChildWithName:)` with the full name-search syntax — exists for direct children only; follows N3 | P2 |
| A6 | `applyForce`/`applyTorque`/`applyImpulse`/`applyAngularImpulse` actions, `changeCharge`, `changeMass`, `strength(to:/by:)`, `falloff(to:/by:)` | P2 |
| A7 | `wait(forDuration:withRange:)` re-randomizing per run, `group` leftover time (both recorded deviations) | P2 |
| A8 | `SKAction.duration` as a public, settable property (verify current visibility) | P1 |
| A9 | `stereoPan`, `changeObstruction`, `changeOcclusion`, `changeReverb`, `warp(to:)`, `animate(withNormalTextures:)`, `setNormalTexture`, `SKAction(named:)` | Out of scope with their subsystems |

### 2.6 Physics

| # | Missing | Notes | Pri |
| --- | --- | --- | --- |
| P1 | `SKPhysicsWorld.body(at:)`, `body(in:)`, `body(alongRayStart:end:)`, `enumerateBodies(at:/in:/alongRayStart:end:)` | Spatial queries and ray casts — very common in gameplay code. | P1 |
| P2 | `SKPhysicsBody.node`, `isResting`, `area`, `joints`, `allContactedBodies()` | `node` back-reference also unblocks eager joint anchors (a recorded deviation). | P1 |
| P3 | `applyForce(_:at:)`, `applyImpulse(_:at:)`, torque from off-centre contacts | The solver is linear-only (recorded); these need the angular half. | P2 |
| P4 | `SKPhysicsBody(bodies:)`, `init(edgeChainFrom:)`, `init(edgeLoopFrom: path)`, `init(polygonFrom: path)` | Compound and path-based bodies. | P2 |
| P5 | `SKPhysicsBody.charge`, `SKPhysicsWorld.sampleFields(at:)`, `SKRegion`, remaining field kinds, `SKPhysicsContact.collisionImpulse`, joint limits/reaction forces | Already recorded; grouped here as one physics-completeness work item. | P3 |

### 2.7 Camera, constraints, particles, tile maps, transitions, audio

| # | Missing | Pri |
| --- | --- | --- |
| C1 | `SKCameraNode.containedNodeSet()` | P1 |
| C2 | `SKConstraint.referenceNode`; the point-based overloads `distance(_:to: CGPoint)`, `distance(_:to:in:)`, `orient(to: CGPoint, …)`, `orient(to:in:offset:)` | P2 |
| E1 | `SKEmitterNode.particleAction`, `particleRenderOrder`, `shader`; `targetNode` and the scale/alpha/rotation sequences (recorded) | P2 |
| E2 | `SKKeyframeSequence.interpolationMode`, `repeatMode`, mutation (`addKeyframeValue`, `setKeyframeValue`, …) | P2 |
| M1 | `SKTileMapNode.fill(with:)`, `mapSize`, `color`/`colorBlendFactor`/`blendMode`; `SKTileGroup.empty()`, `SKTileGroup(tileDefinition:)`; `SKTileDefinition.placementWeight` | P2 |
| X1 | `SKTransition.doorsOpenHorizontal/Vertical`, `doorsCloseHorizontal/Vertical`, `doorway` true geometry; `pausesIncomingScene`, `pausesOutgoingScene` | P2 |
| U1 | `SKAudioNode.isPositional`, `SKScene.listener` (recorded as "no positional audio") | P3 |

### 2.8 Whole classes that are absent and unrecorded

| Class | Recommendation |
| --- | --- |
| `SKSceneDelegate`, `SKViewDelegate` | Add (S2, V5) |
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
3. **Lifecycle** — S1 (`sceneDidLoad`), S2.
4. **Everyday API, batch 1** — H1 (shape initializers), N1, N2, A1–A4, A8, T5, C1.
5. **Everyday API, batch 2** — T1 (named textures), T4 (`centerRect`), L2, N3 + A5, V2.
6. **Physics queries** — P1, P2.
7. **Second tier** — the P2 rows above, driven by what host apps actually hit.
8. **Renderer infrastructure** — R1–R4, then the P3 rows they unblock.

Steps 1–2 are what a host app adding Android TV support touches; steps 3–6 are what any
ordinary SpriteKit port will reach for next.

## 5. Suggested changes to recorded scope decisions

- `SKAction.run(_:onChildWithName:)` is implemented but still listed under ROADMAP's "Explicitly
  Out of Scope"; remove it from that list.
- `SKNode.copy()`: revisit the blanket "no `NSCopying`" rule for nodes specifically (N4).
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
