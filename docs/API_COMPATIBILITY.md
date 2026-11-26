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
  approach as GameplayKit's `GKRule`/`GKGameModel.copy()`. The one exception is nodes: `SKNode`
  and every node class have an explicit `copy()` (see "Scene graph").
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

- **`SKNode.copy()` is an `open fun` each node class overrides**, returning its own type, rather
  than `NSCopying`. What it copies follows Apple's, as recorded on iOS/tvOS simulators: a deep copy
  of the subtree with no parent; a new physics body (same settings and velocity) and a new
  `userData` map holding the same values; constraints, textures and shaders shared; running
  actions copied under their keys, starting over; a copied scene's camera and a crop node's mask
  that is also a descendant pointing at their copies; a scene's delegate and contact delegate not
  copied. An emitter's copy starts with no particles; a tile map's keeps its tiles. Apple archives
  the node through `NSCoding`, which keeps a subclass's class but drops its own stored
  properties; here **a subclass of your own is copied as the nearest library class unless it
  overrides `copy()`**, which it does with `copyInto(MyNode())`, copying its own properties after —
  so, unlike on Apple, it can keep them. A subclass that overrides `copyState` should check the
  target's type, since a subclass of it that doesn't override `copy()` hands it a superclass instance.

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
  value type, not `android.graphics.RectF` (see `docs/ROADMAP.md`'s
  "Phase 2 — Scene Graph Core" section for why). It's
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
  so `size` may still be the scene's initial size inside `didMove`. (Before 0.3.0, `.resizeFill` was
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
- **`SKViewDelegate.shouldRenderAtTime(view, time)`** is Apple's `view(_:shouldRenderAtTime:)`,
  renamed since Swift argument labels don't name Kotlin functions. As recorded on iOS/tvOS
  simulators, it is asked once a frame before `update` (never while the view is paused), and
  `false` skips the frame's `update`, actions and physics, the next frame that runs catching up on
  the skipped time. `time` is seconds on a monotonic clock; Apple passes the same value its
  `update(_:)` gets, which this library's `update(deltaTime)` has no counterpart for.
  **`shouldCullNonVisibleNodes` and `isAsynchronous`** default to `true` as on Apple and are
  accepted with no effect: culling only saves drawing work (this renderer draws every node), and
  `isAsynchronous` concerns Core Animation, which Android doesn't have.
- **`SKView` debug overlays** (`showsFPS`, `showsNodeCount`, `showsDrawCount`, `showsQuadCount`,
  `showsPhysics`) follow Apple's look as recorded on iOS/tvOS simulators: one statistics line at the
  bottom right, 5 points in from the right and 2 up from the bottom, in white 10-point monospaced
  text (Android's monospace font standing in for Menlo) over black at 30% opacity; physics outlines
  are hard-edged 1-pixel lines in opaque (0.4, 0.8, 0.8) above the scene. Differences: Apple's box
  hugs the glyphs' ink vertically, this one spans the font's ascent and descent; `draws` and `quads`
  count this renderer's OpenGL draw calls and triangle pairs, which batch and tessellate differently
  from Apple's Metal renderer, so they can read differently for the same scene; the frame rate is
  measured over half-second windows. **`showsFields` is accepted but draws nothing**: Apple's field
  visualization draws broken on iOS/tvOS simulators (a few lines at the scene's origin, wherever
  the field is), so there was no behavior to record and mirror.
- **`SKView.preferredFramesPerSecond`** defaults to 60 and reads back whatever was set, as Apple's
  does. The view runs at a whole fraction of the display's refresh rate (60, 30, 20, 15, ... at
  60 Hz), the one nearest the requested rate, the higher on a tie; a negative rate or one at or
  above the display's runs every refresh, and 0 runs once a second. On iOS/tvOS simulators at 60 Hz
  that matches Apple's choice for every rate from 11 to 60 except two: Apple runs 24 and 45 at 30,
  where this library runs 24 at 20 and 45 at 60. Apple's rule isn't documented and no simple rule
  fits all fifty. The display rate is rounded to whole hertz first, so 59.94 Hz counts as 60. **On a
  90/120 Hz display the default caps the view at 60 frames a second** — as on Apple, but unlike
  before this property existed, when the view drew every refresh; set a higher value to keep that.
  Below the display's rate the view stops rendering continuously and draws on request from a
  `Choreographer` callback every few refreshes.

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

- **`SKTextureAtlas` packs at runtime** rather than through Apple's build-time
  (Xcode-integrated) atlas compiler — there is no equivalent Android build step to hook into. It
  uses a classic shelf/next-fit bin-packing algorithm (simple, not space-optimal), with a
  one-pixel border of repeated edge pixels around each image so a stretched texture keeps clean
  edges, as Apple's do; Apple's own packing algorithm isn't documented, so the layout (and so each
  texture's `textureRect`) differs. Three ways in:
  - `SKTextureAtlas(named = "Sprites")` reads the images in the host app's
    `assets/Sprites.atlas/` folder (`.png`, `.jpg`, `.jpeg`, `.webp`; the `.atlas` extension may
    be given or left out). As on Apple (verified on iOS/tvOS simulators), `textureNames` keep their
    extensions (`"hero.png"`), and `textureNamed` finds a texture with or without one. A missing
    folder gives an empty atlas with a logged warning. No `@2x`/`@3x` variants, no `.spriteatlas`
    asset-catalog folders, and no trimming of transparent edges: every image is packed at its
    pixel size.
  - `SKTextureAtlas(dictionary = …)` keys are kept as given. Values may be a `Bitmap`, a `File`, a
    `Uri` (opened through the `ContentResolver`) or a `String` path — Apple takes an image, a path
    or a URL; a relative path is read from `assets/` here. Any other value type throws
    `IllegalArgumentException`.
  - `SKTextureAtlas.pack(images)` packs `Bitmap`s, with no Apple counterpart.
- **`SKTextureAtlas.textureNamed` returns the 128×128 missing-image placeholder for an unknown
  name**, as Apple's does, with a logged warning; before 0.5.0 it returned `null`.
- **Preloading** (`SKTexture.preload`, `SKTexture.preload(textures)`, `SKTextureAtlas.preload`,
  `preloadTextureAtlases`, `preloadTextureAtlasesNamed`) calls back on a background thread, as
  Apple's does, so hop back to the thread you need (`SKView.runOnGLThread` for scene work). Images
  are decoded when a texture or atlas is created and uploaded to the GPU the first time they're
  drawn, so only `preloadTextureAtlasesNamed` does real work first: it loads the atlases on that
  background thread. As on Apple, a missing atlas comes back empty rather than as an error.
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
  blur/glow shader pass, which this port doesn't implement. It does
  widen the frame, as on Apple.
- **`SKShapeNode.isAntialiased` outlines are antialiased the way Apple's are** (since 0.5.0;
  before that every outline was drawn hard-edged). Recorded on iOS/tvOS simulators: each edge of
  an outline fades linearly over 1.7 screen pixels, from opaque 1.12 pixels inside the edge to
  clear 0.58 outside, around joins and caps too; fills are never antialiased. The fade is built
  into the outline's geometry (a fringe with per-vertex alpha, no MSAA), sized in pixels at the
  node's own scale of `1` (one pixel being `1 / density` points, Apple's `1 / contentScaleFactor`)
  — so, as on Apple, scaling the node or the scene (its `scaleMode`) scales the fade with it, and a
  scene drawn smaller than its size draws nearly hard edges. Outlines thinner than about 2.4
  pixels are drawn fainter and that wide, approximating Apple's (whose thin-line profile differs
  by up to about 0.2 in alpha per pixel). A bevel join's fade is slightly steeper across the
  bevel than along the sides. `isAntialiased` also widens a drawn outline's frame by 0.85 points a
  side, as on Apple.
- **`SKShapeNode`'s frame includes a drawn outline**, as on Apple (since 0.5.0; before that it was
  the path's bounds only): when `lineWidth` is above `0` and `strokeColor` isn't fully
  transparent, the outline's segments and caps at `lineWidth + 2 × glowWidth` (plus the
  antialiasing padding) are added. Like Apple's, it leaves out sharp corners' miters. A round
  cap is counted as an exact half circle, where Apple's frame reaches about 13% further.
- **`SKShapeNode.lineWidth` defaults to `1`**, as on Apple (since 0.5.0; it was `0`, so an
  unstyled shape drew no outline). `blendMode` applies to both fill and outline, as on Apple.
- **`SKShapeNode.lineLength` is the path's real length.** Apple's iOS/tvOS simulators report `0`
  for every path, rendered or not, with or without a stroke shader; this library returns the
  documented value instead, measured on the flattened path.
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
  Exact weights need API 28; below that, weights of 600 and up render bold. (Before 0.3.0 the whole
  name went to `Typeface.create(name, NORMAL)`, so `"Helvetica-Bold"` silently rendered regular.)
- **`SKLabelNode.attributedText` is a `CharSequence`, styled with Android spans** instead of an
  `NSAttributedString` and its attributes: `ForegroundColorSpan` for `.foregroundColor`,
  `AbsoluteSizeSpan` (its size read as points)/`RelativeSizeSpan`/`TypefaceSpan`/`StyleSpan` for
  `.font`, and so on. The label keeps an immutable snapshot (`SpannedString`), as Apple copies the
  string. Its behavior follows Apple's as recorded on iOS/tvOS simulators: while set, it alone is
  drawn and sized, ignoring `text`/`fontName`/`fontSize`/`fontColor` (even when empty); setting
  `text` doesn't clear it; unstyled text is 12-point Helvetica in black, an `NSAttributedString`'s
  defaults; `color`/`colorBlendFactor`, the multi-line settings and the alignment modes apply as
  they do to `text`. `SKLabelNode(attributedText)` leaves `text` empty, where Apple's is `nil`.
- **`SKLabelNode.blendMode` is accepted but has no effect**, as on Apple: recorded on iOS/tvOS
  simulators, a label alpha-blends whatever its `blendMode` (even when `colorBlendFactor` tints
  it). `color`/`colorBlendFactor` tint the text exactly as they tint a sprite's texture.
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
- **`SKActionTimingMode` uses Apple's curves** (since 0.5.0, recorded on iOS/tvOS simulators):
  `easeIn` is `2t² − t³`, `easeOut` its mirror `t + t² − t³`, and `easeInEaseOut` `3t² − 2t³`.
  Before that, `easeIn` was `t²` and `easeOut` `2t − t²`; `easeInEaseOut` already matched.
- **`wait(duration:withRange:)` draws a new length on every run** — each pass of a `repeat`, and
  each node it runs on — and its `duration` reads as the base duration, as on Apple (since 0.5.0,
  verified on iOS/tvOS simulators; before that, the length was drawn once, when the action was
  created, and `duration` reported it).
- **`reversed()` only reverses relative ("by") actions and composites (`sequence`/`group`/
  `repeat`) built from them.** Absolute ("to") actions — `moveTo`, `scaleTo`, `rotateTo`,
  `resizeTo`, `fadeAlphaTo`, `colorize` — return an unreversed copy of themselves, since the value
  they started from isn't known until run time. Actions with no natural inverse (`wait`, `run`,
  `removeFromParent`, `customAction`, `animate`) do the same.
- **Single-axis and Kotlin-named factories.** Apple's label-only overloads get distinct names:
  `changeMassTo`/`changeMassBy`, `changeChargeTo`/`changeChargeBy`, `strengthTo`/`strengthBy`,
  `falloffTo`/`falloffBy`,
  `moveToX`/`moveToY` (`moveTo(x:)`/`moveTo(y:)`), `scaleXTo`/`scaleYTo` (`scaleX(to:)`/
  `scaleY(to:)`), `scaleTo(size)` (`scale(to: CGSize)`, sprites only, as on Apple),
  `resizeToWidth`/`resizeToHeight` (`resize(toWidth:)`/`resize(toHeight:)`; the two-dimension form
  is the existing `resizeTo(size)`), `speedTo`/`speedBy` (`speed(to:)`/`speed(by:)`), and
  `setTexture(texture, resize)`.
- **Apple's arithmetic, verified on iOS/tvOS simulators.** `scaleBy` and `speedBy` multiply (their
  `reversed()` divides). `rotateTo` interpolates straight to the target by default and turns the
  short way only with `shortestUnitArc = true` — either way ending exactly on the target angle.
  (Before 0.4.0, `scaleBy` added and `rotateTo` always took the short way.)
- **Physics actions, as Apple's run (since 0.5.0, verified on iOS/tvOS simulators).**
  `applyForce`/`applyImpulse` (each also with a scene-coordinate point `at`), `applyTorque`, and
  `applyAngularImpulse` change the body's velocity as each frame's actions run, before physics:
  each frame applies the impulse (for a force or torque, its amount times the duration) times how
  far the action's eased progress moved that frame, so a `timingMode` shapes the push. An
  impulse action with no duration applies nothing, and a reversed one pushes the same way again,
  as Apple's do. `changeMassTo`/`changeMassBy` and `changeChargeTo`/`changeChargeBy` (Apple's
  `changeMass(to:/by:)`/`changeCharge(to:/by:)`) interpolate linearly, adding for "by"; changing
  the mass changes the density, as setting `mass` does. `strengthTo`/`strengthBy` animate an
  `SKFieldNode`'s strength, and `strengthBy` multiplies. Apple's `falloff(to:)`/`falloff(by:)`
  leave a field's falloff unchanged on every field kind and never finish — even with no duration
  or on a node that isn't a field, holding up any `sequence` or `group` they're in — so
  `falloffTo`/`falloffBy` do the same; set `falloff` directly instead. The
  reversed charge, mass and strength-to actions do nothing — Apple's mass ones trap, as do mass
  actions that take the mass to `0` or below. All of them are no-ops on a node without a physics
  body (or, for strength and falloff, on a node that isn't a field).
- **`SKAction.duration` is settable**, as on Apple: on a single action it changes how long the
  action runs; on a `sequence`/`group`/`repeat` it changes only the reported value, which Apple's
  does too.
- **A `group` hands on the time its last action didn't need**, so in a `sequence` the next action
  starts exactly where it would after a plain `wait` of the same length, as on Apple (since 0.5.0,
  verified on iOS/tvOS simulators; before that, a finished `group` handed on no time).
- **`resizeTo`/`resizeBy`/`animate` are no-ops on any node that isn't an `SKSpriteNode`**, and
  `colorize` on any node that isn't an `SKSpriteNode` or `SKLabelNode` (the node types with a
  mutable `size`/`texture`, or `color`/`colorBlendFactor`), same as their underlying properties.
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
- **`SKConstraint.distance(_:to:in:)`/`orient(to:in:offset:)`** take the node as `node`, since
  `in` is a Kotlin keyword.
- **`SKConstraint.referenceNode`** matches Apple's (since 0.5.0; verified on iOS/tvOS simulators)
  for position and distance ranges: they're measured in its coordinates, including its scale and
  rotation, or the parent's when it's `nil`. A point target is in the parent's coordinates
  unless a node is given. Where Apple's rotation results with a reference node don't follow its
  coordinates, the port does: a `zRotation` range is measured relative to the reference node's
  rotation, and `orient` doesn't depend on the reference node at all. Apple also gives a NaN
  position when a distance constraint with a reference node puts the node exactly on its
  target; the port leaves the node there. See `PARITY_GAP_PLAN.md` C3.
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
- **Bodies turn as Apple's do** (since 0.5.0, verified on iOS/tvOS simulators):
  - A body turns about its **center of mass**, the shape's centroid. A node whose body's shape is
    off its origin (`circleOfRadius(r, center)`, an off-center rectangle or polygon) swings around
    that point, and `velocity` is the center of mass's.
  - `applyForce(force, at)` and `applyImpulse(impulse, at)` take the point in scene coordinates.
    A point off the center of mass turns the body by `(point − center of mass) × force` (or
    `× impulse`), with lengths in meters of 150 points, divided by the inertia.
  - `applyTorque` changes `angularVelocity` by `torque × dt / inertia`, and `applyAngularImpulse`
    by `impulse / inertia`, the inertia measured about the center of mass in kg·m². The public
    `inertia` (not part of Apple's API) is measured there too; before 0.5.0 it was measured about
    the node's origin, which differs only for an off-center shape.
- **The simulation runs in steps of 1/120 of a second** (since 0.5.0, verified on iOS/tvOS
  simulators), as Apple's does whatever the frame rate: each frame is split into the nearest whole
  number of such steps (two at 60 frames a second, six at 20, one at 90 or above), so a scene
  settles the same way at any frame rate. Forces and torques applied before a frame act through
  all of its steps, then clear. In each step a body moves at most 2 meters (300 points) and turns
  at most a quarter turn (π/2), so `velocity` reads 36,000 points a second and `angularVelocity`
  60π radians a second at most — Apple's (and Box2D's) limits. Before 0.5.0 a frame was one
  step, so a slow frame could let a falling body pass through a floor.
- **Restitution applies at any speed**, as on Apple (verified on iOS/tvOS simulators): a perfectly
  elastic ball bounces back at 50 points a second as at 400.
- **Mass/inertia formulas** are standard, well-known ones (a solid disk for circles, the classic
  polygon second-moment-of-area sum for convex polygons — hand-verified against the known
  `I = mass*(w²+h²)/12` square formula before implementation), not necessarily Apple's own
  (undocumented) computation.
- **Path-based bodies take an `android.graphics.Path`** (this library's `CGPath` stand-in, as for
  `SKShapeNode`): `polygonFrom(path)`, `edgeChainFrom(path)` and `edgeLoopFrom(path)` (since
  0.5.0). As on Apple (verified on iOS/tvOS simulators), each reads only the path's first subpath;
  a polygon closes back to its start, either winding works, and a concave outline keeps its full
  area and collides in its notches (the port splits it into a few convex pieces, as a compound
  body). A full circle's path makes an exact circle body, as on Apple. Other curves are flattened
  into straight edges to within a quarter point, where Apple's flattening is coarser on small
  curves (a 20×10 ellipse's polygon has 8 edges and 90% of the area), so curved bodies' areas
  differ slightly. The `polygonFrom(vertices)` overload, for a path of straight lines, also takes
  concave outlines since 0.5.0 (before, they were used as given, as if convex). The edge factories
  give static bodies, like `edgeFrom`/`edgeLoopFrom(rect)`.
- **`bodies(list)` combines bodies, as Apple's `init(bodies:)`** (since 0.5.0, verified on iOS/tvOS
  simulators): every part collides where it sits in its own body (gaps between them don't), the
  area is the parts' summed, and the compound turns about their shared center of mass. Its
  `density`, `restitution` and `friction` are the first body's — so its mass is that density times
  the whole area — and everything else starts at the defaults. A compound among the bodies adds
  its parts to collide with but no area. Two of Apple's results aren't mirrored: with an edge body
  first, Apple's density and mass come out NaN (the port uses the edge's density); with a compound
  first, Apple's density comes out 22,500 times too large (the port uses that compound's density).
- **`bodies(fromTexture:...)`** (texture-alpha-mask bodies) is not implemented.
- **`usesPreciseCollisionDetection` is accepted with no effect**: there is no swept collision
  detection, so a body fast enough to cross another within one 1/120-second step can pass
  through it.
- **Collisions turn bodies** (since 0.5.0, verified on iOS/tvOS simulators): polygons, and a
  polygon against an edge, touch at up to two points clipped as Box2D clips them, and the
  sequential-impulse solver pushes at each point — restitution along the normal, Coulomb friction
  along the tangent — so a box landing on a corner turns flat and settles, and a ball sliding along
  the floor starts rolling. `SKPhysicsContact.contactPoint` is the average of a contact's points.
  Before 0.5.0 the solver was linear-only and a collision alone never turned a body. The solver
  itself is this library's own, so the motion matches Apple's closely but not exactly.
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
- **The joint solver is linear-only**: joints correct relative *position*/*velocity* to satisfy
  their constraint but don't exchange torque, so a joint alone never turns a body (collisions,
  forces and impulses do).
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
- **`SKPhysicsBody.charge` is stored but has no effect** (since 0.5.0): the physics actions
  animate it, but there are no electric or magnetic fields for it to act on.
- **`SKFieldNode.region` isn't implemented** — every field is always unbounded, as if `region`
  were `null`, since this port has no `SKRegion` implementation (the same gap
  `SKConstraint` has). **`isExclusive`** is stored but not enforced — every enabled, bitmask
  matching field affects a body regardless of whether another exclusive field also does.
- **Gravity fields use Apple's units and falloff** (since 0.5.0, recorded on iOS/tvOS simulators):
  a `radialGravityField` or `linearGravityField` accelerates at `strength` meters per second
  squared (150 points/s², the same units as `SKPhysicsWorld.gravity`) divided by the distance from
  the field node in meters — never less than `minimumRadius`, which is in points — raised to
  `falloff`. A linear field's vector is used as given, not normalized. Defaults match Apple's:
  `falloff` is 2 for `radialGravityField` and 0 for the other kinds, and `minimumRadius` is
  `0.0045776367`. Before that, fields accelerated in points per second squared, every `falloff`
  started at 0, linear fields ignored it and normalized their vector, and `minimumRadius` started
  at 0. `SKEmitterNode` particles respond to fields through the same formula; that particles share
  it with physics bodies hasn't been recorded on Apple separately.
- **`dragField` is a plain `−strength × velocity` drag.** Apple's slows a body non-linearly and,
  from a strength of about 0.1, sends it to the 36000 points/s speed cap instead; that isn't
  mirrored (`docs/PARITY_GAP_PLAN.md` P7).
- **`velocityField` directly overrides a body's velocity** each step (matching Apple's documented
  "applies a velocity, not an acceleration" behavior) rather than integrating a force, and is
  applied after every force-based field for that step, so it wins whenever both affect the same
  body in the same frame.

## Particles (`SKEmitterNode`, `SKKeyframeSequence`)

- **`SKEmitterNode`** supports programmatic configuration only — Apple's `.sks` particle-editor
  archive format has no Android equivalent parser to build against.
- **`SKEmitterNode` follows what Apple's draws, as recorded on iOS/tvOS simulators** (the sample
  app's `SKEmitterParityTests`, which render one particle and read its pixels back):
  - Defaults: `emissionAngle` is `π/2`, `particleLifetime` `0` (a particle with no lifetime is
    never drawn) and `particleColor` transparent white.
  - `particleColorBlendFactor` mixes both color *and* opacity from opaque white toward
    `particleColor`'s; `particleColorSequence` replaces only the color, so the blend factor still
    decides how much of it shows.
  - `particleAlphaSequence`, `particleScaleSequence` and `particleColorBlendFactorSequence` replace
    their value, range and speed, sampled at the fraction of a particle's lifetime it has lived
    (keyframe times past `1` are never reached). Apple has no public rotation or speed sequence.
  - `targetNode`: particles are born where the emitter is, then live in the target's coordinates —
    they stay put when the emitter moves and move with the target, are drawn with the target's
    transform and opacity (not the emitter's, whose `alpha`/`isHidden` no longer apply to them),
    and take their emission angle, speed, accelerations and size in the target's coordinates,
    ignoring the emitter's rotation and scale. Removing the emitter removes its particles. Apple
    holds the target weakly; this library holds it strongly.
  - A newer particle is always drawn on top of an older one: `particleRenderOrder` is accepted
    but changes nothing, as on Apple. `particleZPosition` orders particles against other nodes;
    `particleZPositionSpeed` is accepted but changes nothing, as on Apple.
  - `particleAction` is accepted but runs on no particle: on simulators Apple's leaves every
    particle as it was (moves, scales, fades and colorizes alike).
  - `shader` isn't implemented (shaders run on `SKSpriteNode` only).
- **`SKKeyframeSequence` is generic and is given an `SKKeyframeInterpolator` for its value type**
  when it's created, rather than Apple's untyped (`[Any]`) version, which interpolates whatever
  numbers (`NSNumber`) and colors (`SKColor`) it holds and samples anything else as `nil`.
  `SKKeyframeInterpolator.FloatValues` and `ColorValues` (ARGB `Int`s, this library's color type)
  cover those two; a color whose spline overshoots is clamped to `0..1` per channel, which Apple's
  extended-range colors don't need. Otherwise it behaves as recorded on iOS/tvOS simulators (the
  sample app's `SKKeyframeSequenceParityTests`): `Linear`/`Clamp` by default; `Step` holds the
  latest keyframe reached; `Spline` is a cubic Hermite curve, flat at the end keyframes, whose
  other tangents weight the change on each side by the opposite side's time gap; `Loop` wraps the
  time by the last keyframe's time; times are never sorted, sampling limits the time to the *last*
  keyframe's time, and equal times sample as the later keyframe. Apple's `for:` argument labels
  are `index` parameters (`for` is a Kotlin keyword), and `count()` stays a function.
- **Particles respond to `SKFieldNode`s via `SKEmitterNode.fieldBitMask`**, reusing the physics
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
  `centerOfTile`/`tileColumnIndex`/`tileRowIndex` and `mapSize` (which feeds
  `calculateAccumulatedFrame`) all follow this same convention consistently, but Apple's own exact
  formulas for these aren't public, so this is *contract-conformant, not bit-identical*. (Recorded
  on iOS/tvOS simulators, Apple's hexagonal `mapSize` doesn't describe a box around the cells at
  all — a 1 × 1 map can report a negative width or height.) For `Grid`, `mapSize` matches Apple:
  `numberOfColumns` × `numberOfRows` tiles, not including the node's scale.
- **`SKTileMapNode.color`/`colorBlendFactor` are stored but not drawn**, as on Apple: pixel probes on
  iOS/tvOS simulators show Apple's tile maps ignore them at any blend factor. `blendMode` is drawn.
  The defaults are Apple's: white, `0`, `SKBlendMode.Alpha`.
- **`enableAutomapping` defaults to `true`**, as on Apple. `fill(with)` (and the `fillWith`
  constructor parameter, Apple's `init(tileSet:columns:rows:tileSize:fillWith:)`) never automaps:
  every cell gets a definition of the group's first rule, whatever `enableAutomapping` says
  (verified on iOS/tvOS simulators).
- **`SKTileGroup.empty()` is a new rule-less group each call**, and placing it (or any group with no
  rules) clears the cells, so `tileGroup` reads back `null` there — as on Apple.
- **`SKTileDefinition.placementWeight`** (default `1`) chooses among a rule's definitions in
  proportion to the weights, for `setTileGroup`, automapping and `fill` alike; a weight of `0` is
  never placed while another is positive, and when every weight is `0` each definition is equally
  likely — Apple's behavior, recorded on iOS/tvOS simulators.
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
- **A new `SKTileMapNode` is named `"Default Tile Map"`**, as Apple's is (verified on iOS/tvOS
  simulators).
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
  (Before 0.3.0 it was bounds-tested like any other node, so touches on the parts of the view showing
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

- Ships an extensibility hook plus one trivial built-in example rather than full
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
- **`SKTransformNode`** — 3D rotation of a 2D subtree (`xRotation`/`yRotation`, quaternions).
- **`SK3DNode`** — SceneKit content inside a scene; there is no SceneKit on Android.
- **`SKRenderer`** — rendering a scene into a caller-supplied Metal command buffer; this library
  draws only through `SKView`.
- **`SKMutableTexture`** — textures whose pixels are rewritten in place.
- **`SKReachConstraints`** / `SKAction.reach(to:…)` — inverse kinematics.
