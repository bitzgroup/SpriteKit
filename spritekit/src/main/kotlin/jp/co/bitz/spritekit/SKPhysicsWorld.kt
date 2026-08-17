package jp.co.bitz.spritekit

/**
 * Owns the physics simulation for an [SKScene] (via [SKScene.physicsWorld]) — mirrors Apple's
 * `SKPhysicsWorld`. [SKView]'s frame loop advances the simulation once per frame for the
 * presented scene; there's no public step function here, matching Apple (the scene drives
 * simulation implicitly).
 *
 * `SKPhysicsContact`/`SKPhysicsContactDelegate` (Phase 7b) and joints (Phase 7c) aren't
 * implemented yet — see `docs/ROADMAP.md`.
 */
public class SKPhysicsWorld {
    /**
     * Acceleration applied to every [SKPhysicsBody] with [SKPhysicsBody.affectedByGravity] set.
     * Defaults to `(0, -9.8)`, matching Apple.
     */
    public var gravity: Vector2 = Vector2(0f, -9.8f)

    /** Simulation rate multiplier — `2` runs physics twice as fast as real time, `0` freezes it. Defaults to `1`. */
    public var speed: Float = 1f
}
