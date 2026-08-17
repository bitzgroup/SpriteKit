# API Compatibility Notes

This library mirrors Apple's [SpriteKit](https://developer.apple.com/documentation/spritekit) API
and behavior, but is written in idiomatic Kotlin rather than a literal Obj-C/Swift-to-Kotlin
transliteration (see `docs/ROADMAP.md`'s "Design Principles" section). This document is a quick
reference, organized by subsystem, for developers who already know Apple's SpriteKit and want to
know exactly where — and why — this library's shape differs. It does not restate behavior that
matches Apple's docs; only intentional deviations, omissions, and additions are listed.

Per-subsystem sections below are filled in as each `docs/ROADMAP.md` phase lands. Two deviation
categories recur throughout and are called out once here rather than per item:

- **Contract-conformant, not bit-identical.** Several SpriteKit subsystems (physics, particle
  emission, noise-driven fields) are not documented by Apple beyond their observable contract.
  Where noted, this library implements a standard/reference algorithm that satisfies the same
  contract, but its output is not guaranteed to match Apple's internal implementation bit-for-bit —
  the same framing [GameplayKit for Android](https://github.com/bitzgroup/GameplayKit) uses for its
  own undocumented-internals cases (steering behaviors, noise, the Gaussian sampler).
- **No Foundation/Obj-C runtime equivalents.** Kotlin has no `NSPredicate`, `NSCopying`,
  `NSCoding`, or Core Graphics/Core Image types. Every place SpriteKit's API surface depends on one
  of these is called out below with its Kotlin/Android replacement.

## General conventions

- **Nullability** uses Kotlin's `?` type system throughout, not Optional-as-comment.
- **Durations.** Where SpriteKit's API takes an elapsed/relative time interval (e.g.
  `SKAction.wait(forDuration:)`), this library uses `kotlin.time.Duration` rather than a raw
  `Double` of seconds, to prevent unit-confusion bugs — SpriteKit's `update(_ currentTime:
  TimeInterval)` callback (an *absolute* timestamp, not a duration) is the one place this library
  still exposes a raw value, since wrapping an absolute clock reading in `Duration` would be
  misleading; see the "Scene lifecycle" section once Phase 2 lands.
- **No SIMD/Core Graphics vector or path types.** `CGPoint`/`CGVector`/`CGSize` map to plain
  Kotlin data classes or existing Android types where one already fits (e.g. `android.graphics.Path`
  for `SKShapeNode`'s `path`, rather than introducing a parallel path type).
- **No `NSPredicate`/`NSCopying`/`NSCoding` equivalents.** Anywhere SpriteKit's API would take one
  of these, this library takes a plain Kotlin lambda or Kotlin-native replacement instead — same
  approach as GameplayKit's `GKRule`/`GKGameModel.copy()`.
- **Property syntax over getter/setter methods**, named/default arguments, data/sealed classes, and
  extension functions are used where they fit naturally; class/member names still follow
  SpriteKit's naming (`SKNode`, `SKAction`, `SKPhysicsBody`, ...) for discoverability.
- **Threading.** SpriteKit assumes all scene-graph access happens on Apple's single main thread;
  this library confines the same access to a dedicated render thread it owns instead, with new
  (Apple has no equivalent) bridge utilities for coordinating with the UI thread. See
  `docs/ARCHITECTURE.md` for the full design — this is one of two subsystems (with `SKView`
  hosting, below) that isn't a reshaping of an existing Apple API, since there's nothing on Apple's
  side to mirror.
- **`SKView` has two forms: a `View` core and a `@Composable` wrapper.** Apple's `SKView` is a
  single `UIView`/`NSView` subclass. This library's `SKView` core (`:spritekit`) is a
  `GLSurfaceView` subclass usable directly in XML/View apps with zero third-party dependencies; a
  separate `:spritekit-compose` module wraps it in a `@Composable` (via `AndroidView`) as the
  documented, recommended way to use it, since Jetpack Compose — not the classic `View` system —
  is where Android's UI toolkit investment goes now. Compose and the `View` system are officially
  interoperable (`AndroidView`/`ComposeView`), so this is additive, not a fork of the API surface.
  See `docs/ARCHITECTURE.md`'s "Hosting: a View core, a Compose wrapper" section.

## Scene graph (`SKNode`, `SKScene`)

- **`convertTo`/`convertFrom` instead of `convert(_:to:)`/`convert(_:from:)`.** Swift argument
  labels aren't significant for Kotlin overload resolution, so both would collide as a single
  `convert(point: Vector2, node: SKNode): Vector2` overload — the same problem, and the same
  rename pattern, as GameplayKit-for-Android's `GKGraphNode.pathFrom`/`pathTo` (renamed from
  `findPath(from:)`/`findPath(to:)`).
- **`childNode`/`enumerateChildNodes` only support an exact-name match among *direct* children.**
  Apple's `childNode(withName:)`/`enumerateChildNodes(withName:using:)` additionally support a
  `/`-separated path syntax with `//` for recursive descent and `*` wildcards — not implemented.
  `enumerateChildNodes` also drops the `UnsafeMutablePointer<ObjCBool>` "stop" out-parameter from
  its callback (no Kotlin/Obj-C runtime equivalent); the callback is a plain `(SKNode) -> Unit`.
- **`Vector2` stands in for both `CGPoint` and `CGVector`** (position vs. velocity/force/gravity in
  Apple's API) — one Kotlin type covers both roles, since the split is a Core Graphics/Objective-C
  legacy this port doesn't need. **`Rect` stands in for `CGRect`**, returned by
  `calculateAccumulatedFrame()` — a plain Kotlin value type, not `android.graphics.RectF` (see
  `docs/ROADMAP.md`'s Phase 2 entry for why).
- **`SKNode.isPaused`** exists as a property, but per-node pause propagation to descendants during
  action evaluation/physics simulation isn't implemented yet — there's no action or physics system
  to propagate to until later phases. Only `SKScene.isPaused` (inherited from here) is currently
  honored, by `SKView`'s render loop.

## Textures & sprites (`SKTexture`, `SKTextureAtlas`, `SKSpriteNode`)

- **`SKTextureAtlas.pack`** is a runtime atlas packer rather than Apple's build-time
  (Xcode-integrated) atlas compiler — there is no equivalent Android build step to hook into. It
  uses a classic shelf/next-fit bin-packing algorithm (simple, not space-optimal); Apple's own
  atlas-packing algorithm isn't documented, so there's nothing to match even if this library could
  hook into a build step.
- **`SKTexture(rect:in:)`** doesn't compose nested sub-rects — `rect` is always interpreted
  relative to the passed-in texture's *underlying bitmap*, even if that texture is itself already
  a sub-rect. `SKTextureAtlas` (this constructor's only real use case in this library) never
  chains sub-rects, so this doesn't come up in practice.
- **`SKSpriteNode.size`** always defaults to `Vector2.Zero`, even when a `texture` is set. Apple
  auto-sizes a sprite to its texture's pixel dimensions (adjusted by scale factor) at construction
  time; matching that would mean calling `Bitmap.getWidth()`/`getHeight()` from inside
  `SKSpriteNode`'s own logic, which — like this library's approach throughout — stays out of code
  paths meant to be pure-Kotlin/unit-testable. Set `size` explicitly.
- **`SKBlendMode.multiplyX2`** is not implemented (Apple's other cases —
  `.alpha`/`.add`/`.subtract`/`.multiply`/`.screen`/`.replace` — all are), a rarely-used blend mode
  this library didn't prioritize. Blending is implemented via standard `glBlendFunc`/
  `glBlendEquation` combinations — *contract-conformant, not bit-identical*, since Apple's own
  blending isn't independently documented beyond its observable effect.
- **`SKSpriteNode.colorBlendFactor`** is applied as a CPU-computed-per-sprite vertex-color
  multiplier (`mix(white, color, colorBlendFactor)`, scaled by the node's accumulated alpha) rather
  than a per-pixel shader `mix` between the sampled texture color and `color` — a modulate
  approximation of Apple's per-pixel blend, chosen because `color`/`colorBlendFactor` are per-node
  scalars, not textures, so precomputing them once per sprite (rather than per-fragment) is both
  simpler and cheaper. Reasonable for the common case; not pixel-identical to Apple's for textures
  with partial transparency.

## Shapes & labels (`SKShapeNode`, `SKLabelNode`)

- **`SKShapeNode.path`** is a plain `android.graphics.Path` — this library's `CGPath` stand-in,
  chosen because it already fits the role well (see the "reuse existing Android types" convention
  under "General conventions" above). Its points are interpreted directly as this node's local
  (y-up) coordinate space, the same as every other node's local geometry (e.g. `SKSpriteNode.size`)
  — no y-flip, since `Path` itself has no inherent "up" direction until something renders it, and
  this library never renders it via `Canvas`.
- **Fill triangulation** (`triangulateFill`) is classic ear-clipping: doesn't support holes, and
  stops early (returning whatever was triangulated so far) on self-intersecting input rather than
  producing garbage geometry. **Stroke triangulation** (`triangulateStroke`) doesn't generate
  miter/bevel/round joins between segments — adjacent quads simply meet (or gap slightly, at sharp
  angles) without extra join geometry. Both are *contract-conformant, not bit-identical* with
  Apple's own (undocumented) shape rendering.
- **`SKShapeNode.glowWidth`** is stored for API parity but doesn't render a glow — that needs a
  blur/glow shader pass, deferred with the rest of the advanced shader work (Phase 13).
- **`SKLabelNode`** renders glyphs via `android.graphics.Paint`/`Typeface` into a cached texture —
  there is no CoreText equivalent on Android.
- **`SKLabelNode` is single-line only** — Apple's `numberOfLines`/`preferredMaxLayoutWidth`
  multi-line wrapping isn't implemented.

## Actions (`SKAction`)

- **`fadeAlphaTo`/`fadeAlphaBy` instead of an overloaded `fadeAlpha(to:duration:)`/
  `fadeAlpha(by:duration:)`.** Same Kotlin-overload-resolution collision (and the same rename
  pattern) as `SKNode.convertTo`/`convertFrom`.
- **`wait(duration:withRange:)` picks its random duration once, when the action is created.**
  Apple re-randomizes on every run of a reused action instance (e.g. inside a `repeatForever`);
  this library doesn't, for simplicity.
- **`reversed()` only reverses relative ("by") actions and composites (`sequence`/`group`/
  `repeat`) built from them.** Absolute ("to") actions — `moveTo`, `scaleTo`, `rotateTo`,
  `resizeTo`, `fadeAlphaTo`, `colorize` — return an unreversed copy of themselves, since the value
  they started from isn't known until run time. Actions with no natural inverse (`wait`, `run`,
  `removeFromParent`, `customAction`, `animate`) do the same.
- **A `group`'s exact leftover time isn't tracked.** In an enclosing `sequence`, a finished
  `group` always hands zero leftover time to whatever runs next, even if its children actually
  finished partway through the available frame time — contract-conformant for "did the group
  finish," not bit-identical to Apple's own (undocumented) internal timing.
- **`resizeTo`/`resizeBy`/`colorize`/`animate` are no-ops on any node that isn't an
  `SKSpriteNode`** (the only node type with a mutable `size`/`color`/`colorBlendFactor`/
  `texture`), same as their underlying properties.
- **`animate`'s `resize` parameter isn't implemented** — same reason `SKSpriteNode.size` doesn't
  auto-size from a texture in the first place (see the "Textures & sprites" section above).
- **`customAction`'s block receives raw elapsed time** (`0` to the action's duration), not eased
  by `timingMode`/`timingFunction` — matches Apple's own documented behavior.

## Camera, effects, crop, constraints (`SKCameraNode`, `SKEffectNode`, `SKCropNode`, `SKConstraint`)

- **`SKScene`/`SKCropNode` don't extend `SKEffectNode`**, unlike Apple's inheritance chain
  (`SKScene : SKEffectNode : SKNode`). With no Core Image filter pipeline behind it,
  `SKEffectNode` here is a plain passthrough grouping node — there's nothing for `SKScene`/
  `SKCropNode` to meaningfully inherit from it, so retrofitting that inheritance isn't worth the
  churn unless a real filter/offscreen-rendering pipeline lands later.
- **`SKEffectNode.filter`** (Core Image) isn't implemented — no Android equivalent.
  `shouldEnableEffects`/`shouldRasterize`/`shouldCenterFilter` are stored for API parity but have
  no observable effect without a filter.
- **`SKCropNode` clips to `maskNode`'s bounding box**, via `glScissor` — not true per-pixel alpha
  masking. A non-rectangular or partially-transparent mask (e.g. a circular `SKShapeNode`) clips
  to its rectangular bounds, not its actual silhouette. Nested crop nodes still intersect
  correctly (each narrows the inherited clip rect further).
- **`SKCameraNode.containsNode`** approximates the camera's viewport as `SKScene.size` (centered
  per `SKScene.anchorPoint`) — it doesn't account for `SKScene.scaleMode`'s letterbox/crop
  adjustment against the presenting `SKView`'s actual aspect ratio, since a node has no way to
  know that from the scene-graph layer alone.
- **`SKRange.atLeast`/`atMost`** replace Apple's overloaded `SKRange(lowerLimit:)`/
  `SKRange(upperLimit:)` initializers — the same Kotlin-overload-resolution collision (and rename
  pattern) as `SKNode.convertTo`/`convertFrom`.
- **`SKRegion` isn't implemented.** Every constraint Apple documents as taking one
  (`SKConstraint.positionX(_:y:)` and its siblings) actually takes an `SKRange`; nothing in this
  port's constraint API needed a region.
- **`SKConstraint`'s built-in kinds are *contract-conformant, not bit-identical*** with Apple's
  own (undocumented) constraint-solving internals — in particular, `orient(to:offset:)`'s exact
  algorithm for resolving the allowed angular deviation from facing the target isn't documented by
  Apple beyond its observable effect.

## Physics (`SKPhysicsWorld`, `SKPhysicsBody`, `SKPhysicsJoint`, `SKFieldNode`)

- The physics engine is a self-contained, from-scratch 2D rigid-body implementation (no external
  physics library dependency), matching GameplayKit's zero-runtime-dependency convention.
  *Contract-conformant, not bit-identical* — Apple's own physics engine internals are not public.
- **Mass/inertia formulas** are standard, well-known ones (a solid disk for circles, the classic
  polygon second-moment-of-area sum for convex polygons — hand-verified against the known
  `I = mass*(w²+h²)/12` square formula before implementation), not necessarily Apple's own
  (undocumented) computation.
- **`polygonFrom(_:)`** takes a `List<Vector2>` rather than a `CGPath` — this library has no path
  type. Concave input isn't validated or corrected, matching Apple's own "must be convex" contract.
- **`bodies(fromTexture:...)`** (texture-alpha-mask bodies) is not implemented.
- **The narrow phase's contact point is approximate** for polygon-polygon and polygon-vs-edge-chain
  cases: an edge midpoint rather than a true Sutherland-Hodgman-clipped contact point/manifold.
- **Collision response is linear-only**: sequential impulses resolve normal (restitution) and
  tangential (Coulomb friction) velocity, but don't derive torque from an off-center contact
  point, so a collision alone never imparts spin (`applyTorque`/`applyAngularImpulse` still do).
  Apple doesn't document its own solver's exact behavior here either.
- **Broad phase is O(n²)** (every body pair's AABBs are tested each step) — not scoped to scale to
  very large body counts; a spatial partition (grid/quadtree) is a possible future optimization.
- **A body's world-space shape ignores non-uniform scale for circles**: the world radius is
  measured from where the local shape's +x edge lands after the node's transform, which is exact
  under uniform scale/rotation but treats a non-uniformly-scaled circle as a (slightly wrong-sized)
  circle rather than the ellipse it should become.
- **`SKPhysicsContact.collisionImpulse`** is always `0` — the resolved impulse magnitude isn't
  threaded back out of the solver to the notification path in this port. Apple's own docs are
  vague about its exact precision/derivation too.
- **Contact notification (`contactTestBitMask`) is decoupled from collision response
  (`collisionBitMask`)**, matching Apple's documented contract: a pair can report contact via
  `SKPhysicsContactDelegate` without ever physically colliding (a zero-`collisionBitMask` sensor
  body), and vice versa. Both are independent of the narrow phase itself, which runs once per
  candidate pair regardless of either bitmask.
- **`SKPhysicsJoint` subclasses are idiomatic Kotlin constructors** (`SKPhysicsJointPin(bodyA,
  bodyB, anchor)`, etc.), not Apple's `joint(withBodyA:bodyB:...)` class-method factories.
- **A joint's anchor point(s) are bound lazily**, from each body's *current* transform, the first
  frame the simulation processes that joint — not at construction/`add(_:)` time like Apple. A
  body has no reference back to its owning node in this port (unlike Apple's engine, which does),
  so a joint can't resolve a world-space anchor into each body's local space until a simulation
  step hands it the node lookup it needs. In practice this only matters if a body moves between
  constructing the joint and the next simulation step — construct/add joints and let a step run
  before relying on the anchor tracking a specific point, rather than moving bodies in between.
- **The joint solver is linear-only**, the same simplification collision response makes: joints
  correct relative *position*/*velocity* to satisfy their constraint but don't exchange torque, so
  a body's rotation is otherwise still driven only by its own `angularVelocity`/`applyTorque`.
  `SKPhysicsJointFixed` is the one exception — it kinematically re-syncs `bodyB`'s rotation to
  `bodyA`'s each step (a direct rotation overwrite, not a torque-based coupling).
- **`SKPhysicsJointPin`'s `shouldEnableLimits`/`lowerAngleLimit`/`upperAngleLimit`/
  `frictionTorque`** are stored for API parity but not enforced — the solver doesn't model
  relative rotation between a pin's two bodies at all.
- **`SKPhysicsJointSliding`'s `axis`** is fixed in world space for the joint's lifetime; it
  doesn't rotate along with either body (Apple's own axis-rotation behavior isn't documented).
- **`SKPhysicsJoint.reactionForce`/`reactionTorque`** (read-only, post-simulation) aren't
  implemented.
- **`SKFieldNode` is one concrete class configured by factory functions**
  (`radialGravityField`/`linearGravityField`/`dragField`/`velocityField`), matching Apple's own
  shape here — unlike `SKPhysicsJoint`, which really is a family of separate subclasses. Only this
  subset of Apple's field kinds is implemented; noise, turbulence, electric, magnetic, spring,
  vortex, and checkerboard-texture-based velocity fields aren't — see `docs/ROADMAP.md`'s
  "Explicitly Out of Scope".
- **`SKFieldNode.region` isn't implemented** — every field is always unbounded, as if `region`
  were `null`, since this port has no `SKRegion` implementation (the same gap Phase 6 documented
  for `SKConstraint`). **`isExclusive`** is stored but not enforced — every enabled, bitmask
  matching field affects a body regardless of whether another exclusive field also does.
- **`radialGravityField`'s `strength`/`falloff`/`minimumRadius` formula** —
  `strength / max(distance, minimumRadius)^falloff`, directed towards the field node's position —
  is a standard inverse-power model, not necessarily Apple's own (undocumented) one.
- **`velocityField` directly overrides a body's velocity** each step (matching Apple's documented
  "applies a velocity, not an acceleration" behavior) rather than integrating a force, and is
  applied after every force-based field for that step, so it wins whenever both affect the same
  body in the same frame.

## Particles (`SKEmitterNode`, `SKKeyframeSequence`)

- **`SKEmitterNode`** supports programmatic configuration only — Apple's `.sks` particle-editor
  archive format has no Android equivalent parser to build against.
- **`SKEmitterNode.particleSize`** has no Apple equivalent. Apple auto-sizes each particle from
  `particleTexture`'s pixel dimensions; this port can't do that without reading a `Bitmap`'s
  dimensions from inside otherwise-pure-Kotlin node/config classes — the same reason
  `SKSpriteNode.size` must be set explicitly instead of inferred from its texture. Set
  `particleSize` explicitly (defaults to `32x32`).
- **`SKEmitterNode.targetNode`** isn't implemented — every particle stays in the emitting node's
  own local space for its whole life (so moving the emitter drags its existing particles along,
  unlike Apple's default of reparenting particles into the emitter's *parent* so they don't).
- **The scale/rotation/alpha `SKKeyframeSequence` properties** Apple documents as siblings of
  `particleColorSequence` aren't implemented — only `particleColorSequence` is.
- **`SKKeyframeSequence` is generic and takes an explicit `interpolate` function at `sample`
  time**, rather than Apple's untyped (`[Any]`) version, which infers how to interpolate via
  runtime reflection on the value's type (`CGFloat`, `SKColor`, `CGPoint`, ...). More boilerplate
  per call site, but type-safe, and with no implicit "which types does interpolation actually
  support" contract to document.
- **Particles respond to `SKFieldNode`s via `SKEmitterNode.fieldBitMask`**, reusing Phase 7d's
  field-force formulas (refactored to operate on a world position/velocity pair rather than an
  `SKPhysicsBody`, so both physics bodies and particles — which have no physics body — can share
  the same code). Defaults to `0` (unaffected by any field), matching Apple.
- **Particle rendering reuses the existing `SKRenderCommandList.kt` pipeline** — each living
  particle contributes its own quad command (the same "flat triangle list, texture, blend mode,
  vertex color" shape `SKSpriteNode`/`SKLabelNode`/`SKShapeNode` already produce), so
  `SKSceneRenderer` itself needed no changes to support particles.

## Tile maps (`SKTileSet`, `SKTileGroup`, `SKTileGroupRule`, `SKTileDefinition`, `SKTileMapNode`)

- **Configured programmatically only** — no `.sks` tile-set archive format to parse, and no
  bundled/built-in tile sets (Apple ships several) to draw from.
- **Only grid-shaped maps are supported.** Apple's `SKTileSetType` (and the isometric/hexagonal
  variants it selects) isn't implemented, so `SKTileSet` has no corresponding property at all.
- **`SKTileDefinition.textures` may be empty**, unlike Apple (which always requires at least one)
  — an empty-textures definition renders flat-colored, this port's usual "no texture" convention
  (matching untextured `SKSpriteNode`/`SKEmitterNode` particles). Chosen deliberately so
  `SKTileMapNode`'s grid/automapping logic stays fully unit-testable without ever constructing a
  real `SKTexture` (which wraps an `android.graphics.Bitmap`, unsafe to construct in a plain JVM
  test).
- **`SKTileAdjacencyMask` is a plain `Int`-bitmask `object`** (`SKTileAdjacencyMask.UP`, `.ALL`,
  etc.), matching this library's existing `categoryBitMask`-style bitmask convention, rather than
  a dedicated option-set type.
- **`SKTileMapNode.numberOfColumns`/`numberOfRows` are fixed at construction** — Apple allows
  resizing a live map (preserving existing tiles); this port doesn't.
- **Automapping's rule-matching algorithm is *contract-conformant, not bit-identical*** with
  Apple's own (undocumented) version: it scores each candidate `SKTileGroupRule` by how many
  adjacency bits it shares with the tile's actual same-group 8-neighbor configuration (an exact
  match always wins outright; otherwise the closest by Hamming distance), rather than Apple's
  unpublished matching/tie-breaking behavior.
- **Placement/rotation/flip variants of `SKTileDefinition`** aren't implemented — every tile
  renders axis-aligned, unrotated.
- **Tile map rendering reuses the existing `SKRenderCommandList.kt` pipeline** — each non-empty
  cell contributes its own quad command (the same shape `SKSpriteNode`/`SKEmitterNode` particles
  already produce), sized by that cell's own `SKTileDefinition.size` (which need not match the
  map's `tileSize`) rather than a fixed per-map size, so `SKSceneRenderer` itself needed no
  changes. Every tile in one map shares that map node's own `zPosition` — a tile map is one flat
  layer, unlike particles' per-particle z-position.

## Input (`SKNode` touch dispatch)

- **Touches are delivered one `SKTouch` at a time** (`pointerId` plus `location`, already
  converted into the *receiving* node's own local space) per `touchesBegan`/`touchesMoved`/
  `touchesEnded`/`touchesCancelled` call, rather than Apple's batched `Set<UITouch>`. Idiomatic
  Kotlin given this library's per-pointer `SKTouchEvent` model (Phase 1) and Android's own
  per-pointer `MotionEvent` API — Apple's batching is largely an iOS multitouch-coalescing
  artifact, not essential to mirror.
- **Hit-testing uses each candidate node's own `localBounds`** (axis-aligned, un-rotated
  bounding-box containment in that node's local space) rather than Apple's (undocumented, possibly
  per-node-type/shape-aware) precise hit-testing — e.g. `SKShapeNode`'s actual path isn't tested,
  just its bounds.
- **`SKCropNode` clipping isn't considered during hit-testing** — a touch can still reach a node
  positioned somewhere an ancestor crop node would actually clip it from view.
- **A touch is hit-tested once, on `touchesBegan`**, then delivered to that same node for
  `touchesMoved`/`touchesEnded`/`touchesCancelled` regardless of where the pointer travels
  afterward (tracked per pointer ID), matching Apple's documented tracking behavior — not
  re-hit-tested every frame.
- **No responder-chain bubbling**: a node with `isUserInteractionEnabled == false` is never a hit
  candidate and never receives touches at all — no walking up to find an enabled ancestor.

## Transitions (`SKTransition`)

- **No offscreen-framebuffer (FBO) support** — this renderer never draws a scene to a texture, so
  every transition effect is built purely from `glViewport` offset/size, a whole-scene alpha
  multiplier, and (for `doorway` only) a plain rectangular clip, rather than true compositing.
- **`flipHorizontal`/`flipVertical` approximate Apple's true 3D flip as a 2D squash-then-grow**
  (the outgoing scene narrows to nothing, then the incoming scene grows back out) — this renderer
  has no 3D perspective transform to do an actual flip with.
- **`doorway`'s split is a plain vertical line down the middle**, each half sliding straight
  sideways — not Apple's (undocumented) exact panel geometry/easing.
- **The outgoing scene is frozen for the whole transition** — only the *incoming* scene's
  `update`/actions/physics/etc. continue running; the outgoing scene is drawn purely as a visual
  snapshot of whatever state it was in when the transition began.
- **Transition progress is linear** — no easing curve (Apple's transitions may use one; exact
  timing isn't documented).

## Audio (`SKAudioNode`, audio `SKAction`s)

- **No app-bundle `fileNamed:` lookup** — `SKAudioNode.path` and
  `SKAction.playSoundFileNamed(fileNamed:)` take a plain path/URL string, exactly as
  `android.media.MediaPlayer.setDataSource(String)` accepts: an absolute file path, an
  `http(s)://` URL, or a bundled asset via `"file:///android_asset/..."`. There's no Android
  equivalent of an app's bundled `.caf`/`.mp3` resource resolved by filename alone, so the caller
  resolves whatever path is appropriate.
- **`MediaPlayer`-backed only, no `SoundPool`** — including for `SKAction.playSoundFileNamed`
  (typically a `SoundPool` use case on Apple/elsewhere, for short fire-and-forget sound effects).
  `SoundPool`/`MediaPlayer.create()` both need a `Context`, which isn't threaded through this
  library's scene graph; `MediaPlayer.setDataSource(String)` doesn't. One `MediaPlayer` per
  `SKAudioNode` also matches Apple's own persistent 1:1 node-to-player model more directly than
  `SoundPool`'s shared-pool model would.
- **No positional/spatial audio** — no distance attenuation, panning, or `SKNode` position
  influencing playback; deferred, see `docs/ROADMAP.md`.
- **`SKAction.playSoundFileNamed`'s reported `duration` is always `0`** — the real clip length
  isn't known until `MediaPlayer` actually starts playing it, so unlike every other `SKAction`,
  its progress isn't duration-driven; `waitForCompletion` instead polls the underlying player's
  `isPlaying` state each frame.
- **Playback failures are silently absorbed**, not surfaced as errors — every `MediaPlayer` call
  (`setDataSource`, `prepare`, `start`, ...) is wrapped in a catch-and-ignore, so a bad path or a
  player in the wrong state can't crash the render thread; there's no delegate/callback equivalent
  of Apple's own (rare) audio error reporting.

## Shaders (`SKShader`, `SKUniform`)

- Ships an extensibility hook plus one trivial built-in example (Phase 13) rather than full
  parity. Apple's shader-modifier snippet system (built-in symbols like `u_time`/`v_tex_coord`
  auto-injected per node type) is not publicly specified beyond its observable effect and is
  deferred, along with `SKAttribute` per-vertex custom attributes and `SKWarpGeometry` mesh warp
  rendering. See `docs/ROADMAP.md`'s "Explicitly Out of Scope" section.
- **`SKShader.source` is a complete GLSL ES fragment shader**, not an Apple-style modifier
  snippet — since that snippet-injection system isn't publicly specified, this port compiles
  `source` whole, in place of the renderer's default fragment shader, against the same
  varyings/uniforms the default shader already exposes (`varying vec2 v_TexCoord`,
  `varying vec4 v_Color`, `uniform sampler2D u_Texture`) plus every custom `SKUniform`.
- **Only `SKSpriteNode.shader` exists** — Apple also exposes `shader` on `SKShapeNode`,
  `SKEmitterNode`, and `SKScene` (a whole-scene shader); none of those are ported yet, deferred
  along with the rest of the advanced shader system.
- **`SKUniform` is a Kotlin sealed-value shape** (`SKUniformValue.FloatValue`/`Vector2Value`/
  `TextureValue`, set via `SKUniform.value`) rather than Apple's one-class-many-typed-properties
  shape — matches this library's existing `SKActionKind`/`SKConstraintKind` convention. Only
  `float`/`vector_float2`/`texture` uniform types are ported; `vector_float3`/`vector_float4`/
  matrix types aren't — no SIMD types in this library (see "General conventions" above), and
  they're not needed by this phase's scope.
- **A shader that fails to compile/link falls back to the renderer's default program**, rather
  than crashing the render thread — there's no delegate/callback equivalent of Apple's own shader
  compile-error reporting; a failure is silent (matching this library's audio-playback-failure
  convention, see "Audio" above) and isn't retried every frame until `SKShader.source` is edited
  again.

## Not implemented

- **`SKEffectNode.filter`** — Core Image, no Android equivalent.
- **`SKVideoNode`** — no video-texture bridge in this library yet.
- **`SKLightNode`** / normal-map lighting and shadows — deferred with the rest of the shader system.
- **`SKReferenceNode`** file-based scene loading — no equivalent to Apple's `.sks` scene archive
  serialization format.
