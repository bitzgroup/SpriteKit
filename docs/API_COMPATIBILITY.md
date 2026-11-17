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
  `Double` of seconds, to prevent unit-confusion bugs. This includes `SKScene.update`, which is
  shaped differently from Apple's — see "Scene graph" below.
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

- **`SKView.allowsTransparency` puts the view above everything else in its window.** It's
  implemented (default `false`, as on Apple; also an `allowsTransparency` parameter on the Compose
  `SKView`), but a `GLSurfaceView`'s surface can only be composited entirely below its window (where
  nothing behind the view can show through) or entirely above it. Transparency needs the latter
  (`setZOrderOnTop`), so a transparent `SKView` is drawn over dialogs, popup menus and any other UI
  the app shows over it — on Apple, `SKView` is an ordinary layer and none of this applies. Use it only
  for a view nothing is shown on top of; to put a background behind a scene, draw it inside the scene.
  It must be set before the view is attached to a window. See `docs/PARITY_GAP_PLAN.md` section 2.9.

## Scene graph (`SKNode`, `SKScene`)

- **`convertTo`/`convertFrom` instead of `convert(_:to:)`/`convert(_:from:)`.** Swift argument
  labels aren't significant for Kotlin overload resolution, so both would collide as a single
  `convert(point: Vector2, node: SKNode): Vector2` overload — the same problem, and the same
  rename pattern, as GameplayKit-for-Android's `GKGraphNode.pathFrom`/`pathTo` (renamed from
  `findPath(from:)`/`findPath(to:)`).
- **Node search (`childNode`, `enumerateChildNodes`, `node[name]`) uses Apple's full search
  syntax** (since 0.4.0; before that, only an exact name among direct children): `/` and `//`
  steps, a leading `/` or `//` to start at the tree's root, `*` and `[a-z]` patterns, `.` and
  `..`. Apple's undocumented details are matched as recorded on iOS/tvOS simulators: `//` lists
  each node's matching children before searching deeper; the root's own name is never matched;
  `*` matches unnamed nodes; an empty search string throws, as Apple raises. Apple's
  `subscript(_:)` is the `get` operator. `enumerateChildNodes` drops the
  `UnsafeMutablePointer<ObjCBool>` "stop" out-parameter (no Kotlin equivalent); the callback is a
  plain `(SKNode) -> Unit`, and a `for` loop over `node[name]` with `break` stops early.
- **`Vector2` stands in for `CGPoint`, `CGVector`, and `CGSize`** (position vs. velocity/force/
  gravity vs. size in Apple's API) — one Kotlin type covers every role, since the split is a Core
  Graphics/Objective-C legacy this port doesn't need. A size-valued `Vector2` (e.g.
  `SKScene.size`) also exposes `width`/`height`, so `size.width` reads exactly as on Apple.
- **`Rect` stands in for `CGRect`**, returned by `calculateAccumulatedFrame()` — a plain Kotlin
  value type, not `android.graphics.RectF` (see `docs/ROADMAP.md`'s Phase 2 entry for why). It's
  edge-based (`left`/`top`/`right`/`bottom`) rather than origin-plus-size, but exposes
  `width`/`height` so `frame.height` reads exactly as on Apple.
- **`SKScene.scaleMode` `.aspectFit`/`.aspectFill` always center the scaled scene within the
  view, independent of `anchorPoint`** — `anchorPoint` only changes which local coordinate a node
  must use to sit at a given point of that (fixed, centered) rect, never where the rect itself
  renders. Verified against Apple's real `SKScene`/`SKView` on an iOS Simulator (default
  `anchorPoint` `(0, 0)`, `.aspectFit`, and swept against `(0.5, 0.5)`/`(1, 1)` too): the extra
  space `.aspectFit`/`.aspectFill` reveal or crop beyond `sceneSize` on the non-constraining axis
  always splits evenly, whichever `anchorPoint` is set. (Originally implemented as a plain
  `anchorPoint`-scaled offset into the letterboxed/cropped rect — correct for `.fill`/
  `.resizeFill`, where there's no such extra space, but wrong for `.aspectFit`/`.aspectFill`,
  which put *all* of it on the max-coordinate side for the default `anchorPoint (0, 0)`.
  `bitzgroup/tic-tac-toe`'s Android build visibly sat lower on screen than its iOS twin because of
  this — found comparing screenshots of the two apps, not from a written spec.)
- **`anchorPoint` is a no-op whenever `SKScene.camera` is set** — every render command and touch
  coordinate is already expressed relative to the camera (camera-relative `(0, 0)` *is* the
  camera's own position), so the projection is always centered on that reference point instead,
  regardless of `anchorPoint`. Verified against Apple's real `SKScene`/`SKCameraNode` on an iOS
  Simulator: a camera's position renders at the center of the view no matter what `anchorPoint` is
  set to (swept `(0, 0)`/`(0.5, 0.5)`/`(1, 1)`, pixel-identical each time) — unlike the no-camera
  case just above, where `anchorPoint` only relabels the still-centered rect's own coordinates,
  this makes it disappear from the equation entirely once a camera is active. `SKCameraNode
  .containsNode`'s viewport approximation follows the same rule. (Found building a downstream hex-
  grid wargame: with the default `anchorPoint (0, 0)` and a camera panned to frame the board, the
  board rendered pushed into one corner of the view instead of centered on the camera.)
- **`SKSceneScaleMode.ResizeFill` sizes the scene in density-independent pixels.** The presenting
  `SKView` keeps `SKScene.size` equal to its own size in dp — this library's stand-in for Apple's
  points, so one scene point covers about the same physical size on both platforms — and calls
  `didChangeSize(_:)` whenever it changes. As on Apple, the resize happens *after* `didMove(view)`,
  so `size` may still be the scene's initial size inside `didMove`. (Previously `.resizeFill` was
  rendered exactly like `.fill` — the declared size stretched to the view — and the scene was never
  resized.)
- **`didMove(view)`/`willMove(view)` instead of `didMove(to:)`/`willMove(from:)`** — Kotlin can't
  overload on argument labels alone, so each callback keeps its own name and a plain `view`
  parameter. Both run on the render thread, from `SKView.presentScene`.
- **`SKScene.sceneDidLoad()` runs on first presentation, not in the initializer.** Apple calls it
  once from inside the scene's initializer (verified on iOS/tvOS simulators: the size is already
  set, and presenting again or resizing never calls it again). A Kotlin superclass constructor
  can't safely call an override before the subclass's own properties are initialized, so here it
  runs once on the render thread, right before the first `didMove(view)` — a scene that's never
  presented never gets it. Presenting again or resizing doesn't call it again, as on Apple.
- **`SKScene.delegate` / `SKSceneDelegate`** match Apple's per-method replacement (verified on
  iOS/tvOS simulators): each frame step calls the delegate's method *instead of* the scene's own,
  and a step the delegate doesn't implement still reaches the scene's override. Apple's optional
  protocol methods become interface methods whose default bodies forward to the scene, so an
  override that calls `super` runs the scene's callback too. `update(deltaTime, scene)` passes the
  elapsed time like `SKScene.update` (see below). The delegate is held strongly, where Apple's is
  `weak`: the JVM collects reference cycles, and a weak reference would let an otherwise
  unreferenced `object :` delegate be collected and silently stop being called.
- **`SKScene.update(deltaTime: Duration)` instead of `update(_ currentTime: TimeInterval)`.**
  Apple passes an *absolute* timestamp and leaves every scene to subtract the previous frame's
  value itself; this library passes the time elapsed since the previous frame directly, as a
  `Duration`. Ported code that computes `currentTime - lastUpdateTime` drops that bookkeeping and
  uses `deltaTime` as is. The first frame after the GL surface is (re)created gets a zero
  `deltaTime`; deltas are not otherwise clamped.
- **`SKNode.isPaused`** behaves like Apple's: a paused node's whole subtree is skipped by action
  evaluation, constraint application, physics, particle emission, tile-map animation and audio
  stepping, and still renders. A paused `SKScene` additionally gets no `update`/`did…` callbacks.
- **`SKView.isPaused` and `SKView.scene`** match Apple's (behavior verified on an iOS simulator):
  setting `view.isPaused` sets the presented scene's `isPaused` to the same value, and while the
  view is paused nothing in the scene runs whatever the scene's own `isPaused` says.
  `presentScene(scene)` un-pauses both the incoming scene and the view itself, as Apple's does;
  `presentScene(scene, transition)` un-pauses the incoming scene but leaves a paused view paused
  (Apple's `SKTransition.pausesIncomingScene`/`pausesOutgoingScene` aren't implemented — see
  `PARITY_GAP_PLAN.md` X1).
  `SKView.onResume()` (the Android counterpart of returning to the foreground) un-pauses the scene
  even if the app had paused it through `scene.isPaused` — Apple's `SKView` does the same — but
  leaves a paused *view* paused. `scene` is `null` until the first `presentScene` has taken effect
  on the render thread.

- **`SKNode.nodes(point)`/`atPoint(point)`** are Apple's `nodes(at:)`/`atPoint(_:)` with the
  argument label dropped, and are top-level extension functions rather than members (call sites
  read the same). `frame` and `contains(_:)` are members. Semantics match Apple's, probed on
  macOS: `contains` tests the accumulated frame with `CGRect`'s half-open edges, while
  `nodes`/`atPoint` test it with all four edges inclusive; hidden and `alpha == 0` subtrees are
  skipped; results are frontmost first; `atPoint` falls back to the receiver.
- **View ↔ scene conversion** — Apple's `SKScene.convertPoint(fromView:)`/`convertPoint(toView:)`
  and `SKView.convert(_:to:)`/`convert(_:from:)` — are `convertPointFromView`/`convertPointToView`
  and `SKView.convertTo`/`convertFrom` (Kotlin can't overload on argument labels). View
  coordinates are Android view **pixels**, origin top-left, y-down, where Apple's are UIKit points.
- **Child-list API** — `insertChild(node, index)`, `removeChildren(nodes)`,
  `inParentHierarchy(parent)` and `moveToParent(parent)` (Apple's `insertChild(_:at:)`,
  `removeChildren(in:)`, `inParentHierarchy(_:)`, `move(toParent:)`; `moveToParent` because Kotlin
  can't take the `toParent:` label) match Apple's, verified on iOS/tvOS simulators:
  `insertChild` throws `IndexOutOfBoundsException` past the end, as Apple raises; `removeChildren`
  ignores nodes that aren't children; `inParentHierarchy` is `false` for the node itself;
  `moveToParent` keeps only the **position** in place — `zRotation` and the scale keep their local
  values — and carries it through every ancestor's transform on both sides, each tree's topmost
  node's own included.
- **`SKNode.speed`** multiplies how fast a node's actions run and, as on Apple, is inherited: a
  node's actions run at the product of its own and every ancestor's speed. `0` stops them while
  leaving them attached. A negative speed stops them too.

## Textures & sprites (`SKTexture`, `SKTextureAtlas`, `SKSpriteNode`)

- **`SKTextureAtlas.pack`** is a runtime atlas packer rather than Apple's build-time
  (Xcode-integrated) atlas compiler — there is no equivalent Android build step to hook into. It
  uses a classic shelf/next-fit bin-packing algorithm (simple, not space-optimal); Apple's own
  atlas-packing algorithm isn't documented, so there's nothing to match even if this library could
  hook into a build step.
- **`SKTexture(imageNamed:)`/`SKSpriteNode(imageNamed:)` read the host app's `assets/` folder**,
  the same stand-in for Apple's main bundle the audio API uses (see "Audio"). The extension may be
  left out (`"hero"` tries `.png`, `.jpg`, `.jpeg`, `.webp`), and a missing image gives Apple's
  128×128 placeholder (a red cross) plus a logged warning. The application `Context` this needs
  is captured at start-up by `SKBundleInitializer`, a no-data `ContentProvider` merged in from
  this library's manifest — so, as on Apple, textures can be loaded before any view exists. No
  `@2x`/`@3x` variants or asset catalogs: the file is used at its pixel size.
- **`SKSpriteNode.centerRect` takes a `Rect`** in unit texture space with Apple's bottom-left
  origin (`top` holds the lower edge, as for every `Rect`). Verified on iOS/tvOS simulators: the
  border keeps its texture size on screen whether the sprite grows through `size` or through its
  scale. When a sprite is smaller than its two borders, they shrink in proportion.
- **`SKTexture(rect:in:)`** doesn't compose nested sub-rects — `rect` is always interpreted
  relative to the passed-in texture's *underlying bitmap*, even if that texture is itself already
  a sub-rect. `SKTextureAtlas` (this constructor's only real use case in this library) never
  chains sub-rects, so this doesn't come up in practice.
- **`SKTexture.size()`** is the underlying `Bitmap`'s pixel dimensions (scaled by `textureRect`
  for a sub-region), the same as Apple reports for a texture made from a `CGImage` — an Android
  `Bitmap` carries no separate point/pixel scale factor the way a `UIImage` does. As on Apple,
  `SKSpriteNode(texture:)` sizes itself to that at construction, `SKEmitterNode.particleSize`
  defaults to `(0, 0)` meaning "use the texture's size", and `SKAction.animate`'s `resize: true`
  follows each frame's texture size.
- **`SKBlendMode.multiplyX2`** is not implemented (Apple's other cases —
  `.alpha`/`.add`/`.subtract`/`.multiply`/`.screen`/`.replace` — all are), a rarely-used blend mode
  this library didn't prioritize. Blending is implemented via standard `glBlendFunc`/
  `glBlendEquation` combinations — *contract-conformant, not bit-identical*, since Apple's own
  blending isn't independently documented beyond its observable effect.
- **`SKSpriteNode.size` includes the node's scale, as Apple's does** (verified on iOS/tvOS
  simulators): it reads as the unscaled size times `|xScale|`/`|yScale|`, and assigning it stores
  the value divided by them (ignored on an axis whose scale is `0`). The constructor's `size` is
  the unscaled size. (Before 0.4.0, `size` ignored the scale.) `scale(to)` is Apple's
  `scale(to:)`.
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
  producing garbage geometry. **Stroke triangulation** (`triangulateStroke`) builds a quad per
  segment plus join geometry at every corner (`lineJoin`: miter — falling back to bevel past
  `miterLimit`, as Core Graphics does — round, or bevel) and cap geometry at an open contour's ends
  (`lineCap`: butt, round, or square). Round caps/joins are polygonal (8 segments per half circle).
  Both are *contract-conformant, not bit-identical* with Apple's own (undocumented) shape
  rendering. (Joins were originally omitted, so polylines showed notches or gaps at their corners
  that Apple's default miter join doesn't have.)
- **`LineCap`/`LineJoin` stand in for `CGLineCap`/`CGLineJoin`** on `SKShapeNode.lineCap`/
  `lineJoin` — plain Kotlin enums rather than `android.graphics.Paint.Cap`/`Join`, for the same
  reason `Rect` stands in for `CGRect`: stroke geometry is built and unit-tested in pure Kotlin.
  Defaults match Apple's (`.butt`, `.miter`, `miterLimit` 10).
- **`SKShapeNode.circle(radius)`/`rect(size, cornerRadius)`/`ellipse(size)` instead of
  `SKShapeNode(circleOfRadius:)`/`SKShapeNode(rectOf:cornerRadius:)`/`SKShapeNode(ellipseOf:)`.**
  Companion factory functions rather than secondary constructors, since several Apple
  initializers differ only in argument label (`rectOf:` vs `ellipseOf:` both take one size), which
  Kotlin overload resolution can't tell apart. Each is centered on the node's origin, as on Apple.
  The rest of Apple's set follows the same pattern: `rect(rect, cornerRadius)` and `ellipseIn(rect)`
  (not centered), `path(path, centered)`, `points(points)` and `splinePoints(points)` (a `List`
  instead of a pointer and count). Their paths match Apple's element for element (verified on
  iOS/tvOS simulators), down to two quirks: `points` starts with a line from the first point to
  itself, and `splinePoints` — a Catmull-Rom spline drawn as cubic Béziers — starts with a
  zero-length curve from the first point to itself.
- **`SKShapeNode.glowWidth`** is stored for API parity but doesn't render a glow — that needs a
  blur/glow shader pass, deferred with the rest of the advanced shader work (Phase 13).
- **`SKShapeNode.fillTexture`** is stretched across the bounding box of the shape's (flattened,
  triangulated) fill geometry and multiplied by `fillColor` — so, as on Apple, `fillColor` must be
  set to something visible (typically white) for the texture to show. Texture coordinates are
  derived per fill vertex from its position within that box, so the texture is clipped to the
  shape's own outline exactly where the fill triangulation is. **`strokeTexture` is not
  implemented** — the stroke is still a flat `strokeColor` ribbon.
- **`SKLabelNode`** renders glyphs via `android.graphics.Paint`/`Typeface` into a cached texture —
  there is no CoreText equivalent on Android.
- **`SKLabelNode.fontName` takes Apple-style names and maps them onto Android families.** A
  PostScript-style `-Style` suffix (`"Helvetica-Bold"`, `"HelveticaNeue-LightItalic"`,
  `"AvenirNext-SemiBold"`) is read as a weight and italic flag and applied to the family Android
  resolves via `Typeface.create`; Apple's monospaced families (`Menlo`, `Courier`, ...) map to
  `monospace`, and other Apple families Android doesn't ship fall back to the default sans-serif
  (Android's closest match for Helvetica). Android family names (`"sans-serif-medium"`) also work.
  Exact weights need API 28; below that, weights of 600 and up render bold. (Previously the whole
  name went to `Typeface.create(name, NORMAL)`, so `"Helvetica-Bold"` silently rendered regular.)
- **Multi-line labels (`numberOfLines`, `preferredMaxLayoutWidth`, `lineBreakMode`) are laid out
  by `android.text.StaticLayout`** (since 0.4.0; before that, labels were single-line only). The
  rules match Apple's, as recorded on iOS/tvOS simulators: `numberOfLines = 1` (the default)
  ignores the width and line-break mode; otherwise the block wraps, its frame is as wide as its
  widest line, and `.baseline` vertical alignment acts like `.bottom`. Line breaks and glyph
  metrics are Android's, so exact sizes differ. `lineBreakMode` takes `SKLineBreakMode`, a
  stand-in for `NSLineBreakMode`: character wrapping falls back to word wrapping, and head/middle
  truncation shows no ellipsis on a multi-line block (Android's layout only does those on a single
  line).

## Actions (`SKAction`)

- **`fadeAlphaTo`/`fadeAlphaBy` instead of an overloaded `fadeAlpha(to:duration:)`/
  `fadeAlpha(by:duration:)`.** Same Kotlin-overload-resolution collision (and the same rename
  pattern) as `SKNode.convertTo`/`convertFrom`.
- **`run(_:onChildWithName:)` uses the full search syntax** of `SKNode.childNode` (see "Scene
  graph" above) and runs on the first match only, as on Apple (since 0.4.0; before that, direct
  children only).
- **`wait(duration:withRange:)` picks its random duration once, when the action is created.**
  Apple re-randomizes on every run of a reused action instance (e.g. inside a `repeatForever`);
  this library doesn't, for simplicity.
- **`reversed()` only reverses relative ("by") actions and composites (`sequence`/`group`/
  `repeat`) built from them.** Absolute ("to") actions — `moveTo`, `scaleTo`, `rotateTo`,
  `resizeTo`, `fadeAlphaTo`, `colorize` — return an unreversed copy of themselves, since the value
  they started from isn't known until run time. Actions with no natural inverse (`wait`, `run`,
  `removeFromParent`, `customAction`, `animate`) do the same.
- **Single-axis and Kotlin-named factories.** Apple's label-only overloads get distinct names:
  `moveToX`/`moveToY` (`moveTo(x:)`/`moveTo(y:)`), `scaleXTo`/`scaleYTo` (`scaleX(to:)`/
  `scaleY(to:)`), `scaleTo(size)` (`scale(to: CGSize)`, sprites only, as on Apple),
  `resizeToWidth`/`resizeToHeight` (`resize(toWidth:)`/`resize(toHeight:)`; the two-dimension form
  is the existing `resizeTo(size)`), `speedTo`/`speedBy` (`speed(to:)`/`speed(by:)`), and
  `setTexture(texture, resize)`.
- **Apple's arithmetic, verified on iOS/tvOS simulators.** `scaleBy` and `speedBy` multiply (their
  `reversed()` divides). `rotateTo` interpolates straight to the target by default and turns the
  short way only with `shortestUnitArc = true` — either way ending exactly on the target angle.
  (Before 0.4.0, `scaleBy` added and `rotateTo` always took the short way.)
- **`SKAction.duration` is settable**, as on Apple: on a single action it changes how long the
  action runs; on a `sequence`/`group`/`repeat` it changes only the reported value, which Apple's
  does too.
- **A `group`'s exact leftover time isn't tracked.** In an enclosing `sequence`, a finished
  `group` always hands zero leftover time to whatever runs next, even if its children actually
  finished partway through the available frame time — contract-conformant for "did the group
  finish," not bit-identical to Apple's own (undocumented) internal timing.
- **`resizeTo`/`resizeBy`/`colorize`/`animate` are no-ops on any node that isn't an
  `SKSpriteNode`** (the only node type with a mutable `size`/`color`/`colorBlendFactor`/
  `texture`), same as their underlying properties.
- **`customAction`'s block receives raw elapsed time** (`0` to the action's duration), not eased
  by `timingMode`/`timingFunction` — matches Apple's own documented behavior.
- **`follow` only follows a path's first contour** (via `android.graphics.PathMeasure`); a
  multi-contour `Path` (more than one `moveTo`) has its later contours ignored. `reversed()`
  doesn't reverse the `Path` object itself (`android.graphics.Path` has no public API for that) —
  it flips an internal "traverse back to front" flag instead, which produces the same visible
  motion. Like `flattenPath`, it touches real `Path`/`PathMeasure` APIs and isn't unit-tested.

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
- **`SKCameraNode.containsNode`/`containedNodeSet`** approximate the camera's viewport as
  `SKScene.size` centered on the camera — they don't account for `SKScene.scaleMode`'s letterbox/crop
  adjustment against the presenting `SKView`'s actual aspect ratio, since a node has no way to
  know that from the scene-graph layer alone. `containedNodeSet` otherwise matches Apple's
  (verified on iOS/tvOS simulators): every descendant whose own `frame` overlaps the viewport,
  hidden ones included, but not content-less nodes, the scene, or the camera.
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
- **Physics units match Apple's** (since 0.4.0, verified on iOS/tvOS simulators): `area`, `mass`
  and inertia measure lengths in meters of 150 points (a circle of radius 10 has an area of about
  `0.014` m²), and `gravity` is in meters per second squared (the default `-9.8` accelerates at
  `-1470` points/s²), while positions and velocities stay in points. An impulse changes the
  velocity by `impulse / mass` points per second, and a force accelerates at `force / mass` points
  per second squared, as on Apple. Before 0.4.0 lengths for mass were in points and gravity was
  applied as points/s², so mass was 22,500 times Apple's and bodies fell 150 times slower; host apps
  tuned to that need retuning.
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
- **`SKPhysicsWorld` queries** follow Apple's observed behavior (verified on iOS/tvOS
  simulators): `body(at:)` tests exact shapes and never finds an edge body; `body(in:)` (renamed
  `body(rect)`, since `in` is a Kotlin keyword) tests bounding boxes widened by 15 points (Box2D's
  0.1 m margin), so it also finds an edge loop around the rect; ray casts report hits nearest
  first, skip a body the ray starts inside, hit an edge from either side, and give normals of
  length 150 rather than 1, as Apple's do. The `enumerateBodies` callbacks have no `stop` flag;
  the single-body forms cover the common early exit. Queries see bodies where their nodes are now;
  Apple's see a moved body where it was at the last simulation step.
- **`SKPhysicsBody.isResting`** behaves as Apple's does, not as its name suggests: the simulation
  never sets it, and setting it `true` zeroes the velocity but is cleared by the next step or the
  next velocity assignment.
- **`allContactedBodies()`** lists bodies touching as of the last step where either body collides
  with the other (one-sidedly is enough) or tests for contact with it. Bodies rest exactly on each
  other here, where Box2D keeps about 1.5 points of skin between a body and an edge, so a contact
  can begin a little later than on Apple.
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
- **A joint's anchor points are fixed to its bodies when it's added to the world**, as on Apple,
  provided both bodies' nodes are already in the scene (since 0.4.0, through `SKPhysicsBody.node`).
  A joint added before that is bound on the first simulation step that finds both in the scene.
- **`SKPhysicsJointSpring` is a classic soft spring**: it oscillates at `frequency` hertz with
  `damping` as its damping ratio, whatever the bodies' masses. Apple doesn't document its formula.
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
- **`SKTileSetType` covers `Grid`/`HexagonalPointy`/`HexagonalFlat` — Apple's `.isometric` case
  isn't implemented.** No isometric layout math exists in this port; `SKTileSet.type` can't be set
  to it because the enum has no such case. `SKTileSet.type` defaults to `Grid`, matching this
  port's original (and, before this, only) behavior.
- **The two hexagonal layouts use fixed "offset coordinate" conventions**, not Apple's (largely
  undocumented) internal scheme: `HexagonalPointy` is "odd-r" (odd-numbered rows shifted right by
  half a tile width; rows spaced 3/4 of a tile height apart), `HexagonalFlat` is "odd-q" (odd-
  numbered columns shifted down by half a tile height; columns spaced 3/4 of a tile width apart).
  `centerOfTile`/`tileColumnIndex`/`tileRowIndex` and the map's overall layout size (which feeds
  `calculateAccumulatedFrame`) all follow this same convention consistently, but Apple's own exact
  formulas for these aren't public, so this is *contract-conformant, not bit-identical*.
- **`SKTileDefinition.textures` may be empty**, unlike Apple (which always requires at least one)
  — an empty-textures definition renders flat-colored, this port's usual "no texture" convention
  (matching untextured `SKSpriteNode`/`SKEmitterNode` particles). Chosen deliberately so
  `SKTileMapNode`'s grid/automapping logic stays fully unit-testable without ever constructing a
  real `SKTexture` (which wraps an `android.graphics.Bitmap`, unsafe to construct in a plain JVM
  test).
- **`SKTileAdjacencyMask` is a plain `Int`-bitmask `object`** (`SKTileAdjacencyMask.UP`, `.ALL`,
  etc.), matching this library's existing `categoryBitMask`-style bitmask convention, rather than
  a dedicated option-set type. It's shared across all three `SKTileSetType`s rather than each
  getting its own narrower mask type: a hexagonal tile group's rules simply never see 2 of the 8
  bits set (`UP`/`DOWN` for `HexagonalPointy`, `LEFT`/`RIGHT` for `HexagonalFlat`), since a hex
  cell only has 6 neighbors.
- **`SKTileMapNode.numberOfColumns`/`numberOfRows` are fixed at construction** — Apple allows
  resizing a live map (preserving existing tiles); this port doesn't.
- **Automapping's rule-matching algorithm is *contract-conformant, not bit-identical*** with
  Apple's own (undocumented) version: it scores each candidate `SKTileGroupRule` by how many
  adjacency bits it shares with the tile's actual same-group neighbor configuration (8 neighbors
  for `Grid`, 6 for the hexagonal types — an exact match always wins outright; otherwise the
  closest by Hamming distance), rather than Apple's unpublished matching/tie-breaking behavior.
  For the hexagonal types, which of the 8 `SKTileAdjacencyMask` bits corresponds to which actual
  neighbor cell depends on the tile's own row (`HexagonalPointy`) or column (`HexagonalFlat`)
  parity, since adjacent rows/columns are offset from each other by half a tile.
- **Placement/rotation/flip variants of `SKTileDefinition`** aren't implemented — every tile
  renders axis-aligned, unrotated.
- **Tile map rendering reuses the existing `SKRenderCommandList.kt` pipeline** — each non-empty
  cell contributes its own quad command (the same shape `SKSpriteNode`/`SKEmitterNode` particles
  already produce), sized by that cell's own `SKTileDefinition.size` (which need not match the
  map's `tileSize`) rather than a fixed per-map size, so `SKSceneRenderer` itself needed no
  changes. Every tile in one map shares that map node's own `zPosition` — a tile map is one flat
  layer, unlike particles' per-particle z-position.

## Input (`SKNode` touch dispatch)

- **`SKTouch`/`SKEvent` stand in for `UITouch`/`UIEvent`.** `touchesBegan(touches: Set<SKTouch>,
  event: SKEvent?)` and its siblings match Apple's `touchesBegan(_:with:)` shape: touches sharing a
  phase and target node arrive together in one call, each `SKTouch` persists for its finger's whole
  lifetime (so identity/`Set` membership is stable across callbacks, like `UITouch`), and its
  position is queried with `location(node)`/`previousLocation(node)` — Apple's
  `location(in:)`/`previousLocation(in:)`, renamed only because `in` is a Kotlin keyword. A
  pointer Android reports as moved without actually moving is left out of `touchesMoved`, like a
  stationary `UITouch`. Only `SKEvent.allTouches` is mirrored from `UIEvent`. There is no
  `.stationary` phase.
- **`SKTouch.tapCount` follows Apple's observed rules** (verified on an iOS simulator): quick taps
  close together count up, a pause or a tap far away starts over, each finger of a multi-finger tap
  counts on its own, and a touch that was dragged away or held too long ends with 0. The time and
  distance limits are Android's `ViewConfiguration` values (double-tap timeout and slop, touch
  slop, long-press timeout), since Apple doesn't document its own. `timestamp` is seconds on the
  uptime clock, as Apple's is, taken from the `MotionEvent`. `force` and `maximumPossibleForce` are
  always 0 — what Apple reports without force-sensing hardware (the simulator, every iPhone without
  3D Touch); Android's touch pressure is a different measure and isn't passed through.
- **Hit-testing uses each candidate node's own `localBounds`** (axis-aligned, un-rotated
  bounding-box containment in that node's local space) rather than Apple's (undocumented, possibly
  per-node-type/shape-aware) precise hit-testing — e.g. `SKShapeNode`'s actual path isn't tested,
  just its bounds.
- **The scene receives every touch no descendant takes, anywhere in its view** — it isn't
  bounds-tested, matching Apple, where the scene is the last responder for its whole `SKView`.
  (It used to be bounds-tested like any other node, so touches on the parts of the view showing
  content outside the scene's `(0, 0)..size` rect — routine once an `SKCameraNode` pans or the
  content is centered on the origin — were silently dropped, and a scene-level tap handler simply
  never fired there.)
- **`SKCropNode` clipping isn't considered during hit-testing** — a touch can still reach a node
  positioned somewhere an ancestor crop node would actually clip it from view.
- **A touch is hit-tested once, on `touchesBegan`**, then delivered to that same node for
  `touchesMoved`/`touchesEnded`/`touchesCancelled` regardless of where the pointer travels
  afterward (tracked per pointer ID), matching Apple's documented tracking behavior — not
  re-hit-tested every frame.
- **No responder-chain bubbling**: a node with `isUserInteractionEnabled == false` is never a hit
  candidate and never receives touches at all — no walking up to find an enabled ancestor.

## Input (`presses*` — remote and game-controller buttons)

- **`SKPress`/`SKPressesEvent` stand in for `UIPress`/`UIPressesEvent`.** `pressesBegan(presses:
  Set<SKPress>, event: SKPressesEvent?)` and its siblings match Apple's `pressesBegan(_:with:)`
  shape; each `SKPress` persists from key-down to key-up and carries Apple's `type`
  (`SKPressType`: `UpArrow`/`DownArrow`/`LeftArrow`/`RightArrow`/`Select`/`Menu`/`PlayPause`) and
  `phase`. Only `SKPressesEvent.allPresses` is mirrored; `UIPress`'s `force`/`key`/`timestamp`
  aren't, and `SKPress.keyCode` (the Android key code) is an addition.
- **Delivery is forwarded by the host, matching Apple.** Apple's SpriteKit never delivers presses to
  a scene by itself — not even with the `SKView` as first responder (verified on a tvOS simulator,
  recorded in the sample app's `SpriteKitParity` probes); a tvOS game overrides `presses*` on its
  view controller and forwards them. Here the host `Activity` calls
  `SKView.forwardKeyEvent(event)` from `onKeyDown`/`onKeyUp`, which delivers to the presented
  scene on the render thread. Nodes below the scene only see presses the scene passes on itself —
  there is no focus engine or responder chain.
- **Key mapping** (`skPressTypeForKeyCode`): D-pad arrows → arrows; D-pad center, Enter, numpad
  Enter and gamepad A → `Select`; Back and Escape → `Menu`; media play/pause → `PlayPause`. Other
  keys aren't forwarded (`forwardKeyEvent` returns `false`).
- **A held button is one `began` and one `ended`**, as on tvOS: Android's auto-repeat key-downs are
  consumed but not delivered. A key-up the system cancels (`KeyEvent.isCanceled`) is delivered as
  `pressesCancelled`. `pressesChanged` exists for shape parity but is never called — every mapped
  button is digital.
- **Back (`Menu`) is the host's call.** Forwarding it consumes it, so the system won't leave the
  screen — like a tvOS app whose view controller handles Menu instead of calling `super`. Because
  delivery is asynchronous (render thread), `forwardKeyEvent` can't report whether the scene
  handled it; the host decides up front whether to forward Back.

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

- **The host app's `assets/` folder stands in for Apple's main bundle.** `SKAudioNode(fileNamed:)`
  and `SKAction.playSoundFileNamed(_:waitForCompletion:)` resolve a plain file name (`"tap.mp3"`)
  — or a path relative to `assets/` (`"sounds/tap.mp3"`) — exactly as Apple resolves one against
  the app bundle, so the same string works on both platforms. An absolute file path or a URL
  (Apple's `SKAudioNode(url:)` case) is used as-is. Uncompressed assets (aapt's default for audio
  formats) play straight from the APK; a compressed one is copied into `cacheDir` once first.
  `SKAudioNode` has no public `path` property, matching Apple.
- **`MediaPlayer`-backed only, no `SoundPool`** — including for `SKAction.playSoundFileNamed`
  (typically a `SoundPool` use case on Apple/elsewhere, for short fire-and-forget sound effects).
  One `MediaPlayer` per `SKAudioNode` matches Apple's own persistent 1:1 node-to-player model more
  directly than `SoundPool`'s shared-pool model would.
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
