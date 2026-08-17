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

- **`SKLabelNode`** will render glyphs via `android.graphics.Paint`/`Typeface` into a cached
  texture — there is no CoreText equivalent on Android.

## Actions (`SKAction`)

*To be filled in when Phase 5 lands.*

## Physics (`SKPhysicsWorld`, `SKPhysicsBody`, `SKPhysicsJoint`, `SKFieldNode`)

- The physics engine will be a self-contained, from-scratch 2D rigid-body implementation (no
  external physics library dependency), matching GameplayKit's zero-runtime-dependency convention.
  *Contract-conformant, not bit-identical* — Apple's own physics engine internals are not public.

## Particles (`SKEmitterNode`, `SKKeyframeSequence`)

- **`SKEmitterNode`** will support programmatic configuration only — Apple's `.sks`
  particle-editor archive format has no Android equivalent parser to build against.

## Shaders (`SKShader`, `SKUniform`)

- Ships an extensibility hook plus one trivial built-in example (Phase 13) rather than full
  parity. Apple's shader-modifier snippet system (built-in symbols like `u_time`/`v_tex_coord`
  auto-injected per node type) is not publicly specified beyond its observable effect and is
  deferred, along with `SKAttribute` per-vertex custom attributes and `SKWarpGeometry` mesh warp
  rendering. See `docs/ROADMAP.md`'s "Explicitly Out of Scope" section.

## Not implemented

- **`SKEffectNode.filter`** — Core Image, no Android equivalent.
- **`SKVideoNode`** — no video-texture bridge in this library yet.
- **`SKLightNode`** / normal-map lighting and shadows — deferred with the rest of the shader system.
- **`SKReferenceNode`** file-based scene loading — no equivalent to Apple's `.sks` scene archive
  serialization format.
