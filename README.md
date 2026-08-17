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
`SKTileDefinition`/`SKTileMapNode`), Phase 10 (input — `SKNode` touch dispatch), Phase 11
(transitions — `SKTransition`, `SKView.presentScene(_:transition:)`), Phase 12 (audio —
`SKAudioNode`, the `play`/`pause`/`stop`/`changeVolume`/`changePlaybackRate`/`playSoundFileNamed`
`SKAction`s), Phase 13 (shaders — `SKShader`/`SKUniform` on `SKSpriteNode`, plus the built-in
`SKShader.grayscale` example), and Phase 14 (documentation — full public-API KDoc coverage,
`docs/API_COMPATIBILITY.md`, and this README's [Usage](#usage) section) are complete. See
[`docs/ROADMAP.md`](docs/ROADMAP.md) for the full phased plan and progress checklist.

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

## Usage

A quick tour of the API, subsystem by subsystem — every class here mirrors Apple's SpriteKit type
of the same name; see [`docs/API_COMPATIBILITY.md`](docs/API_COMPATIBILITY.md) for exactly where
each one's shape differs.

### Scenes and nodes

```kotlin
val scene = SKScene(size = Vector2(1080f, 1920f)).apply {
    scaleMode = SKSceneScaleMode.AspectFit
    backgroundColor = Color.BLACK
}

val player = SKNode().apply { position = Vector2(scene.size.x / 2f, 200f) }
scene.addChild(player)

skView.presentScene(scene)
```

### Sprites, shapes, and labels

```kotlin
val ship = SKSpriteNode(texture = shipTexture, size = Vector2(64f, 64f))

val marker = SKShapeNode(path = circlePath).apply {
    fillColor = Color.CYAN
    strokeColor = Color.WHITE
    lineWidth = 4f
}

val scoreLabel = SKLabelNode(text = "Score: 0").apply {
    fontSize = 48f
    fontColor = Color.WHITE
}

scene.addChild(ship)
scene.addChild(marker)
scene.addChild(scoreLabel)
```

### Actions

```kotlin
ship.run(
    SKAction.sequence(
        listOf(
            SKAction.moveTo(Vector2(500f, 800f), duration = 1.seconds),
            SKAction.rotateBy(Math.PI.toFloat(), duration = 0.5.seconds),
            SKAction.run { scoreLabel.text = "Score: 100" },
        ),
    ),
)
```

### Physics

```kotlin
scene.physicsWorld.gravity = Vector2(0f, -9.8f)
scene.physicsWorld.contactDelegate = myContactDelegate

ship.physicsBody =
    SKPhysicsBody.circleOfRadius(32f).apply {
        restitution = 0.4f
        categoryBitMask = 0x1
        contactTestBitMask = 0x2
    }
```

### Particles

```kotlin
val exhaust =
    SKEmitterNode().apply {
        particleTexture = sparkTexture
        particleSize = Vector2(8f, 8f)
        particleBirthRate = 200f
        particleLifetime = 0.6f
        particleSpeed = 120f
        particleSpeedRange = 40f
    }
ship.addChild(exhaust)
```

### Tile maps

```kotlin
val tileSet = SKTileSet(tileGroups = listOf(SKTileGroup(SKTileDefinition(grassTexture, Vector2(32f, 32f)))))
val map = SKTileMapNode(tileSet = tileSet, numberOfColumns = 20, numberOfRows = 12)
map.setTileGroup(tileSet.tileGroups.first(), column = 0, row = 0)
scene.addChild(map)
```

### Camera and crop

```kotlin
val camera = SKCameraNode()
scene.addChild(camera)
scene.camera = camera
camera.run(SKAction.moveTo(player.position, duration = 0.3.seconds))

val mask = SKShapeNode(path = viewportPath)
val cropped = SKCropNode(maskNode = mask).apply { addChild(mask) }
```

### Constraints

```kotlin
player.constraints = listOf(SKConstraint.positionX(SKRange.of(0f, scene.size.x)))
```

### Input

```kotlin
class Ship : SKSpriteNode(texture = shipTexture, size = Vector2(64f, 64f)) {
    init {
        isUserInteractionEnabled = true
    }

    override fun touchesMoved(touch: SKTouch) {
        position = touch.location
    }
}
```

### Transitions

```kotlin
skView.presentScene(nextLevelScene, transition = SKTransition.crossFade(duration = 0.5.seconds))
```

### Audio

```kotlin
val music = SKAudioNode(path = "file:///android_asset/theme.mp3")
scene.addChild(music) // autoplayLooped defaults to true

ship.run(SKAction.playSoundFileNamed("file:///android_asset/laser.wav", waitForCompletion = false))
```

### Shaders

```kotlin
ship.shader = SKShader.grayscale(intensity = 0.6f)
```

## Usage as a git submodule

This repository is intended to be embedded into host apps as a git submodule, so it contains no
app/demo module — only library modules (`:spritekit`, `:spritekit-compose`) and docs. A host app's
own `settings.gradle.kts` includes them directly, e.g.:

```kotlin
include(":SpriteKit:spritekit", ":SpriteKit:spritekit-compose")
project(":SpriteKit:spritekit").projectDir = file("SpriteKit/spritekit")
project(":SpriteKit:spritekit-compose").projectDir = file("SpriteKit/spritekit-compose")
```

## License

MIT — see [`LICENSE`](LICENSE).
