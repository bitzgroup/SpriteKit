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

This project is under active development. Implemented so far:

- **Scene graph and view:** `SKView` (and a Compose wrapper), `SKNode`/`SKScene`, threading
- **Rendering:** `SKTexture`, `SKTextureAtlas`, `SKSpriteNode`, `SKShapeNode`, `SKLabelNode`
- **Actions:** `SKAction`
- **Camera, effects, and constraints:** `SKCameraNode`, `SKEffectNode`, `SKCropNode`,
  `SKConstraint`
- **Physics:** `SKPhysicsWorld`/`SKPhysicsBody`, `SKPhysicsContact`/`SKPhysicsContactDelegate`, the
  `SKPhysicsJoint` family, and `SKFieldNode`
- **Particles:** `SKEmitterNode`, `SKKeyframeSequence`
- **Tile maps:** `SKTileSet`/`SKTileGroup`/`SKTileGroupRule`/`SKTileDefinition`/`SKTileMapNode`,
  including hexagonal tile sets (`SKTileSetType`)
- **Input:** `SKNode` touch dispatch, remote/game-controller `presses*` input, point queries, and
  view ↔ scene coordinate conversion
- **Transitions:** `SKTransition`, `SKView.presentScene(_:transition:)`
- **Audio:** `SKAudioNode`, the `play`/`pause`/`stop`/`changeVolume`/`changePlaybackRate`/
  `playSoundFileNamed` `SKAction`s
- **Shaders:** `SKShader`/`SKUniform` on `SKSpriteNode`, plus the built-in `SKShader.grayscale`
  example

Every public API has KDoc, and known differences from Apple's SpriteKit are recorded in
[`docs/API_COMPATIBILITY.md`](docs/API_COMPATIBILITY.md). API still missing is listed in
[`docs/PARITY_GAP_PLAN.md`](docs/PARITY_GAP_PLAN.md), with the plan for closing it; see
[`docs/ROADMAP.md`](docs/ROADMAP.md) for the full development plan.

## Requirements

- Android `minSdk` 24, `compileSdk` 37
- Kotlin 2.0+

## Building

```sh
./gradlew assemble          # build the library
./gradlew testDebugUnitTest # run unit tests
./gradlew ktlintCheck       # lint/format check
./gradlew detekt            # static analysis
```

See [`CLAUDE.md`](CLAUDE.md) for the full command reference and project structure, and
[`CONTRIBUTING.md`](CONTRIBUTING.md) before opening a pull request.

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

`SKTileSet(tileGroups = ..., type = SKTileSetType.HexagonalPointy)` (or `.HexagonalFlat`) lays
out that same map as regular hexagons instead of a grid — `centerOfTile`/`tileColumnIndex`/
`tileRowIndex` and automapping all follow suit; see `docs/API_COMPATIBILITY.md` for the exact
offset-coordinate convention.

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

    override fun touchesMoved(
        touches: Set<SKTouch>,
        event: SKEvent?,
    ) {
        val parent = parent ?: return
        position = touches.first().location(parent)
    }
}
```

Remote and game-controller buttons (Android TV, a D-pad, a gamepad) arrive as Apple-shaped
`presses*` callbacks on the scene — forwarded by the host, as a tvOS view controller forwards its
own `presses*` to its scene:

```kotlin
class GameScene : SKScene(size = Vector2(1920f, 1080f)) {
    override fun pressesBegan(
        presses: Set<SKPress>,
        event: SKPressesEvent?,
    ) {
        if (presses.any { it.type == SKPressType.Select }) fire()
    }
}

// In the host Activity:
override fun onKeyDown(keyCode: Int, event: KeyEvent) =
    skView.forwardKeyEvent(event) || super.onKeyDown(keyCode, event)

override fun onKeyUp(keyCode: Int, event: KeyEvent) =
    skView.forwardKeyEvent(event) || super.onKeyUp(keyCode, event)
```

### Transitions

```kotlin
skView.presentScene(nextLevelScene, transition = SKTransition.crossFade(duration = 0.5.seconds))
```

### Audio

```kotlin
// Plain file names resolve against the app's assets/ folder, like Apple's main-bundle lookup.
val music = SKAudioNode(fileNamed = "theme.mp3")
scene.addChild(music) // autoplayLooped defaults to true

ship.run(SKAction.playSoundFileNamed("laser.wav", waitForCompletion = false))
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
