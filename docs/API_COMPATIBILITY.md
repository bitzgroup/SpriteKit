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
  this library confines the same access to Android's GL thread instead, with new (Apple has no
  equivalent) bridge utilities for coordinating with the UI thread. See `docs/ARCHITECTURE.md` for
  the full design — this is the one subsystem that isn't a reshaping of an existing Apple API, since
  there's nothing on Apple's side to mirror.

## Scene graph (`SKNode`, `SKScene`)

*To be filled in when Phase 2 lands.*

## Textures & sprites (`SKTexture`, `SKTextureAtlas`, `SKSpriteNode`)

- **`SKTextureAtlas`** will be a runtime atlas packer rather than Apple's build-time
  (Xcode-integrated) atlas compiler — there is no equivalent Android build step to hook into.

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
