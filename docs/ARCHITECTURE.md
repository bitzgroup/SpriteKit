# Architecture: Threading & Rendering

This document covers the parts of this library's design that have **no Apple equivalent to
mirror** — how it reconciles SpriteKit's single-main-thread model with Android's split between the
UI thread and a `GLSurfaceView`'s dedicated GL thread. Everything else (API shape, node semantics,
per-frame evaluation order) follows Apple's documented SpriteKit behavior; this document exists
because the threading model is the one place Android's platform constraints force a genuinely new
design, not just a Kotlin-idiomatic reshaping of an existing Apple API.

## Why this is needed

On iOS/macOS, `SKView` is a regular `UIView`/`NSView`. SpriteKit's per-frame scene update,
action evaluation, physics simulation, and rendering all run on the main thread — the same thread
that delivers touch/mouse events and drives the rest of the UI, via `CADisplayLink`. A single
thread confinement rule covers everything: "touch this scene graph from the main thread."

On Android, `GLSurfaceView` spawns its own dedicated background thread per instance to own the
`EGLContext` and invoke `Renderer` callbacks (`onSurfaceCreated`/`onSurfaceChanged`/`onDrawFrame`).
The `View`/`Activity` lifecycle and raw touch input (`MotionEvent`) stay on the UI thread. There is
no single thread that naturally plays the role of SpriteKit's main thread.

## Thread ownership rules

- **The GL thread is the scene's main thread.** All `SKNode`/`SKScene`/`SKAction`/physics/
  constraint state is confined to it — this is where node mutation, the frame loop, and rendering
  all happen, mirroring SpriteKit's own single-thread-confinement contract but relocating "main"
  from Android's UI thread to the `GLSurfaceView` render thread.
- **The UI thread** owns `SKView`'s Android `View`/`Activity`/`Fragment` lifecycle plumbing and raw
  touch/input event delivery. It never reads or writes scene graph state directly.
- Crossing threads always goes through one of the bridge utilities below — never direct field
  access, and no locking on the node graph itself.

## Bridge utilities

`SKView` exposes two primitives for crossing the UI-thread/GL-thread boundary:

- **`SKView.runOnGLThread { block }`** — wraps `GLSurfaceView.queueEvent(Runnable)`. Queues
  `block` to run on the GL thread before the next frame is drawn. Fire-and-forget, FIFO-ordered
  relative to other queued blocks.
- **`SKView.runOnUiThread { block }`** — wraps `Handler(Looper.getMainLooper()).post(Runnable)`.
  For GL-thread code that needs to reach Android APIs that are themselves UI-thread-confined (e.g.
  triggering a platform dialog, updating a Jetpack Compose state holder that mirrors game state for
  a HUD overlay).

### Touch event routing

`SKView.onTouchEvent(MotionEvent)` runs on the UI thread, as required by the Android `View`
contract. It snapshots the parts of the event the scene graph needs (pointer id, position in view
space, action) into an immutable value type, then hands that snapshot to the GL thread via
`runOnGLThread`. `SKNode`/`SKScene`'s `touchesBegan`/`touchesMoved`/`touchesEnded`/
`touchesCancelled` (Phase 10) always run on the GL thread — the same thread as node mutation — so
hit-testing and touch handling need no synchronization with the render/update loop.

### What this deliberately does not provide

There is no general "read live node state from the UI thread" API in v1 — querying, say, a sprite's
current `position` from the UI thread (to sync a native Android overlay view to it, for example)
requires an explicit `runOnGLThread` round trip. Apple has no equivalent split to design against
here, so there's no precedent to mirror; this is a deliberate v1 scope cut, not an oversight. If a
real need for cheap cross-thread reads of specific "hot" properties shows up later, the likely
design is a double-buffered snapshot the GL thread publishes once per frame — not exposed yet.

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

A GL-thread-confined `SKResourceRegistry` tracks every live descriptor→handle mapping and
re-uploads GPU handles from their descriptors on `onSurfaceCreated`, transparent to library users —
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

Activity/Fragment lifecycle events map onto this as follows: `onPause`/`onResume` call through to
`GLSurfaceView.onPause`/`onResume` (which suspend/resume the GL thread itself) and additionally set
`scene.isPaused`, so a backgrounded scene neither burns CPU nor advances simulation time.

## Coordinate systems

SpriteKit's scene/node space is y-up, matching Apple's documented convention. Android's
`MotionEvent`/`View` coordinate space is y-down, with the origin at the view's top-left in pixels.
The conversion happens exactly once, at the UI→GL touch-event bridge boundary described above —
`SKNode`/`SKScene` touch-handling code always sees Apple's y-up convention, never Android's raw
view-space coordinates.

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
