# Architecture: Threading & Rendering

This document covers the parts of this library's design that have **no Apple equivalent to
mirror** — how it hosts a scene inside a modern Android UI, and how it reconciles SpriteKit's
single-main-thread model with Android's split between the UI thread and a dedicated render
thread. Everything else (API shape, node semantics, per-frame evaluation order) follows Apple's
documented SpriteKit behavior; this document exists because these two areas are where Android's
platform constraints force a genuinely new design, not just a Kotlin-idiomatic reshaping of an
existing Apple API.

## Hosting: a View core, a Compose wrapper

Apple's `SKView` is a `UIView`/`NSView` subclass, embedded the way any other platform view is.
Jetpack Compose and the classic Android `View` system are not mutually exclusive — `AndroidView`
lets Compose host a `View`, and `ComposeView` lets a `View` hierarchy host Compose content, both
officially supported, bidirectional interop paths. This library uses that interop rather than
picking one toolkit exclusively:

- **`SKView` (core) is a plain Android `View`** — a `GLSurfaceView` subclass, in the
  `:spritekit` module. It owns its `Renderer`/render thread and `EGLContext` the same well-worn
  way any `GLSurfaceView`-based Android graphics code does. It works directly in XML layouts or
  via plain `addView` calls, and **`:spritekit` has zero third-party runtime dependencies** —
  Compose is not required to use it.
- **`SKView` (Compose wrapper) is a `@Composable`** — a thin function in a separate
  `:spritekit-compose` module, implemented with `AndroidView(factory = { context -> SKView(context) })`.
  It additionally wires Compose's `LocalLifecycleOwner` to the underlying `GLSurfaceView`'s
  `onPause`/`onResume` automatically (something classic-View consumers have to do by hand from
  their `Activity`/`Fragment` callbacks), and exposes a `rememberSKViewState()` controller handle.
  A Kotlin class and a top-level function are allowed to share a name when their signatures don't
  collide (an established Kotlin idiom for "constructor-like factory functions"); the composable
  and the `View` class both being callable as `SKView(...)` is deliberate; it keeps SpriteKit's own
  `SKView` naming discoverable in both hosting contexts.

Google's ongoing UI investment is in Compose — the classic `View` system is in maintenance mode —
so **the Compose wrapper is the documented, recommended way to use this library**, and gets top
billing in the README. The `View` core exists so classic-View/XML apps aren't forced to adopt
Compose (even indirectly via an internal `ComposeView`) just to embed a scene, and so the render
thread/`EGLContext` lifecycle can be built on `GLSurfaceView`'s long-proven implementation instead
of the newer, less-battle-tested `AndroidEmbeddedExternalSurface` Compose surface-interop API. (An
earlier draft of this document explored making `AndroidEmbeddedExternalSurface` the core primitive
and dropping `View`/XML support entirely — reconsidered once it was clear Compose/View interop
covers the same ground with a more conservative implementation.)

```kotlin
// :spritekit-compose (recommended)
@Composable
fun MyGameScreen() {
    val viewState = rememberSKViewState()
    Box {
        SKView(scene = myScene, state = viewState, modifier = Modifier.fillMaxSize())
        MyComposeHud(modifier = Modifier.align(Alignment.TopEnd)) // draws over the scene
    }
}
```

```xml
<!-- :spritekit only, classic View/XML -->
<jp.co.bitz.spritekit.SKView
    android:id="@+id/skView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```
```kotlin
// classic View/XML — the app wires Activity lifecycle to SKView itself
override fun onPause() { super.onPause(); binding.skView.onPause() }
override fun onResume() { super.onResume(); binding.skView.onResume() }
```

For transition-driven scene swaps (Apple's `SKView.presentScene(_:transition:)`, Phase 11), both
the `View` and the Compose wrapper expose an imperative `presentScene(scene, transition)` call,
since animating between two scenes over time isn't naturally expressed as a single property/
parameter swap. On the Compose side this lives on `SKViewState`; the composable's `scene`
parameter itself is a plain state-hoisted convenience for the common "just show this scene" case.

## Thread ownership rules

- **The render thread — `GLSurfaceView`'s own `Renderer` thread — is the scene's main thread.**
  All `SKNode`/`SKScene`/`SKAction`/physics state is confined to it, mirroring SpriteKit's own
  single-thread-confinement contract but relocating "main" to Android's render thread instead of
  the UI thread.
- **The UI thread** owns `SKView`'s Android `View`/`Activity`/`Fragment` (or Compose composition)
  lifecycle and raw `MotionEvent` delivery. It never reads or writes scene graph state directly.
- Crossing threads always goes through one of the bridge utilities below — never direct field
  access, and no locking on the node graph itself.

## Bridge utilities

`SKView` (the `View`) exposes the primitives for crossing the UI-thread/render-thread boundary;
the Compose wrapper's `SKViewState` simply delegates to the same methods on the `SKView` instance
it wraps internally, so the two hosting paths behave identically underneath:

- **`SKView.runOnGLThread { block }`** — wraps `GLSurfaceView.queueEvent(Runnable)`. Queues
  `block` to run on the render thread before the next frame is drawn. Fire-and-forget,
  FIFO-ordered relative to other queued blocks.
- **`SKView.runOnUiThread { block }`** — wraps `Handler(Looper.getMainLooper()).post(Runnable)`.
  For render-thread code that needs to reach Android APIs that are themselves UI-thread-confined
  (e.g. triggering a platform dialog, or — from the Compose wrapper — mutating a `MutableState`
  that drives a Compose HUD overlay).

### Touch event routing

`SKView.onTouchEvent(MotionEvent)` runs on the UI thread, as required by the Android `View`
contract — this works unchanged whether `SKView` is placed via XML or wrapped by `AndroidView` in
the Compose module, since `AndroidView` forwards touch dispatch to the wrapped `View` transparently.
It snapshots the parts of the event the scene graph needs (pointer id, position in view space,
action) into an immutable value, then hands that snapshot to the render thread via
`runOnGLThread`. `SKNode`/`SKScene`'s `touchesBegan`/`touchesMoved`/`touchesEnded`/
`touchesCancelled` (Phase 10) always run on the render thread — the same thread as node mutation —
so hit-testing and touch handling need no synchronization with the render/update loop.

### What this deliberately does not provide

There is no general "read live node state from the UI thread" API in v1 — querying, say, a
sprite's current `position` from the UI thread (to sync a native Android overlay view to it, or a
Compose `MutableState`, for example) requires an explicit `runOnGLThread` round trip. Apple has no
equivalent split to design against here, so there's no precedent to mirror; this is a deliberate
v1 scope cut, not an oversight. If a real need for cheap cross-thread reads of specific "hot"
properties shows up later, the likely design is a double-buffered snapshot the render thread
publishes once per frame — not exposed yet.

## GPU resource lifecycle and context loss

Apple's SpriteKit never has to think about its GPU context disappearing out from under it mid-run.
Android's `GLSurfaceView` can destroy and recreate its `EGLContext` — and everything allocated
against it (textures, compiled shader programs, vertex buffers) — independently of this library's
Kotlin object graph: backgrounding the app, some configuration changes, and low-memory reclaim can
all trigger it, followed by a fresh `onSurfaceCreated` callback.

Every GPU-backed resource in this library (`SKTexture`, shader programs, sprite-batch VBOs) is
therefore split into two parts:

1. A **persistent CPU-side descriptor** — e.g. for `SKTexture`, the source `Bitmap`/resource
   reference and filtering mode. Survives context loss untouched; this is what user code holds a
   reference to.
2. A **lazily-(re)created GPU handle** — the actual `GLES20` texture/program/buffer name. Invalid
   after context loss until re-uploaded.

A render-thread-confined `SKResourceRegistry` tracks every live descriptor→handle mapping and
re-uploads GPU handles from their descriptors in `onSurfaceCreated`, transparent to library users —
code holding an `SKTexture` never needs to know whether its underlying GPU texture was just
recreated.

## Frame loop

`SKView`'s `GLSurfaceView.Renderer` runs in `RENDERMODE_CONTINUOUSLY`. Each `onDrawFrame` computes
`deltaTime` since the previous frame and executes SpriteKit's documented per-frame order:

```
update(deltaTime)
  → evaluate actions        (SKScene.didEvaluateActions())
  → simulate physics         (SKScene.didSimulatePhysics())
  → apply constraints         (SKScene.didApplyConstraints())
  → render
  → SKScene.didFinishUpdate()
```

Lifecycle mapping: classic-View consumers call `SKView.onPause()`/`onResume()` from their own
`Activity`/`Fragment` callbacks (plain `GLSurfaceView.onPause`/`onResume`, which suspend/resume the
render thread, plus `scene.isPaused`) — the same thing any `GLSurfaceView`-based Android code
already does. The Compose wrapper does this automatically via a `DisposableEffect` observing
`LocalLifecycleOwner`, so Compose consumers don't have to.

## Coordinate systems

SpriteKit's scene/node space is y-up, matching Apple's documented convention. Android
`MotionEvent`/`View` coordinate space is y-down, with the origin at the view's top-left in pixels.
The conversion happens exactly once, at the UI→render-thread touch bridge boundary described
above — `SKNode`/`SKScene` touch-handling code always sees Apple's y-up convention, never
Android's raw view-space coordinates.

## Rendering pipeline (summary)

Full detail lives with each subsystem's `docs/ROADMAP.md` phase; the cross-cutting rendering
design decisions are:

- **OpenGL ES 2.0** baseline via `GLSurfaceView`, chosen for the broadest device compatibility at
  this library's `minSdk 24`.
- Each frame, the scene graph is traversed and flattened into a draw list sorted by `zPosition`
  (ties broken by tree traversal order, per Apple's documented rule), then batched by texture and
  blend mode into as few draw calls as practical (a dynamic-VBO quad batcher).
- The renderer always dispatches each node's draw call through a "current shader program" concept
  — a default built-in program when no custom `SKShader` is set — so Phase 13's shader hook doesn't
  require re-architecting the batcher built in Phase 3.
