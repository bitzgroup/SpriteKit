# SpriteKit

A Kotlin library for Android that implements the same API as Apple's
[SpriteKit](https://developer.apple.com/documentation/spritekit) framework — a scene graph
(`SKNode`/`SKScene`), sprites/shapes/labels, an action system, 2D physics, particles, tile maps,
camera/effects/constraints, transitions, and shaders — built on `GLSurfaceView`.

The goal is API and behavioral parity with SpriteKit's types (`SKNode`, `SKScene`, `SKAction`,
`SKPhysicsBody`, and so on), expressed in idiomatic Kotlin rather than a literal port. See
[`docs/ROADMAP.md`](docs/ROADMAP.md) for the design principles behind that adaptation and the full
implementation progress checklist, and [`docs/API_COMPATIBILITY.md`](docs/API_COMPATIBILITY.md)
for a quick reference of exactly where (and why) this library's API shape differs from Apple's.

Unlike Apple's platforms, Android splits SpriteKit's single main thread into a UI thread and a
separate GL thread; this library treats the GL thread as the scene's main thread and provides
UI-thread ↔ GL-thread bridge utilities to coordinate the two. See
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full threading and rendering design.

This is a sibling project to [GameplayKit for Android](https://github.com/bitzgroup/GameplayKit),
which follows the same porting philosophy for Apple's GameplayKit.

## Status

This project is under active development. Phase 0 (project scaffolding) is complete; no `SK*`
classes are implemented yet. See [`docs/ROADMAP.md`](docs/ROADMAP.md) for the full phased plan and
progress checklist.

## Requirements

- Android `minSdk` 24, `compileSdk`/`targetSdk` 34
- Kotlin 2.0+

## Building

```sh
./gradlew assemble          # build the library
./gradlew testDebugUnitTest # run unit tests
./gradlew ktlintCheck       # lint/format check
./gradlew detekt            # static analysis
```

See [`CLAUDE.md`](CLAUDE.md) for the full command reference and project structure.

## Usage as a git submodule

This repository is intended to be embedded into host apps as a git submodule, so it contains no
app/demo module — only the `:spritekit` library module (and docs). A host app's own
`settings.gradle.kts` includes it directly, e.g.:

```kotlin
include(":SpriteKit:spritekit")
project(":SpriteKit:spritekit").projectDir = file("SpriteKit/spritekit")
```

## License

MIT — see [`LICENSE`](LICENSE).
