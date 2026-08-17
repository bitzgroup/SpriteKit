# SpriteKit

A Kotlin library for Android that implements the same API as Apple's
[SpriteKit](https://developer.apple.com/documentation/spritekit) framework — a scene graph
(`SKNode`/`SKScene`), sprites/shapes/labels, an action system, 2D physics, particles, tile maps,
camera/effects/constraints, transitions, and shaders.

The goal is API and behavioral parity with SpriteKit's types (`SKNode`, `SKScene`, `SKAction`,
`SKPhysicsBody`, and so on), expressed in idiomatic Kotlin rather than a literal port. See
[`docs/ROADMAP.md`](docs/ROADMAP.md) for the design principles behind that adaptation and the full
implementation progress checklist, and [`docs/API_COMPATIBILITY.md`](docs/API_COMPATIBILITY.md)
for a quick reference of exactly where (and why) this library's API shape differs from Apple's.

`SKView`'s core (`:spritekit`) is a plain `GLSurfaceView` subclass — zero third-party
dependencies, usable directly from XML/View apps. A separate `:spritekit-compose` module wraps it
in a `@Composable`, the documented, recommended way to use this library, since Jetpack Compose —
not the classic Android `View` system, which is in maintenance mode — is where Android's UI
toolkit investment goes now (Compose and `View` interoperate officially, so this is additive, not
a fork of the API). Android also splits SpriteKit's single main thread into a UI thread and a
separate render thread this library owns itself; `SKView` treats that render thread as the scene's
main thread and provides UI-thread ↔ render-thread bridge utilities to coordinate the two. See
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full hosting, threading, and rendering
design.

```kotlin
// recommended: :spritekit-compose
@Composable
fun MyGameScreen() {
    SKView(scene = myScene, modifier = Modifier.fillMaxSize())
}
```

```xml
<!-- also works: :spritekit only, classic View/XML -->
<jp.co.bitz.spritekit.SKView
    android:id="@+id/skView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

This is a sibling project to [GameplayKit for Android](https://github.com/bitzgroup/GameplayKit),
which follows the same porting philosophy for Apple's GameplayKit.

## Status

This project is under active development. Phase 0 (project scaffolding), Phase 1 (threading & view
foundation), Phase 2 (scene graph core — `SKNode`/`SKScene`), Phase 3 (textures & sprite rendering
— `SKTexture`, `SKTextureAtlas`, `SKSpriteNode`), Phase 4 (shapes & labels — `SKShapeNode`,
`SKLabelNode`), Phase 5 (actions — `SKAction`), Phase 6 (camera, effects, crop, constraints —
`SKCameraNode`, `SKEffectNode`, `SKCropNode`, `SKConstraint`), Phase 7 (physics —
`SKPhysicsWorld`/`SKPhysicsBody`, `SKPhysicsContact`/`SKPhysicsContactDelegate`, the
`SKPhysicsJoint` family, and `SKFieldNode`), Phase 8 (particles — `SKEmitterNode`,
`SKKeyframeSequence`), Phase 9 (tile maps — `SKTileSet`/`SKTileGroup`/`SKTileGroupRule`/
`SKTileDefinition`/`SKTileMapNode`), and Phase 10 (input — `SKNode` touch dispatch) are complete.
See [`docs/ROADMAP.md`](docs/ROADMAP.md) for the
full phased plan and progress checklist.

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
app/demo module — only library modules (`:spritekit`, and `:spritekit-compose` once Phase 1 lands)
and docs. A host app's own `settings.gradle.kts` includes them directly, e.g.:

```kotlin
include(":SpriteKit:spritekit", ":SpriteKit:spritekit-compose")
project(":SpriteKit:spritekit").projectDir = file("SpriteKit/spritekit")
project(":SpriteKit:spritekit-compose").projectDir = file("SpriteKit/spritekit-compose")
```

## License

MIT — see [`LICENSE`](LICENSE).
