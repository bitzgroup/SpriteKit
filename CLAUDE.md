# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

Phase 0 (project scaffolding) is complete: a Gradle Android library project builds, lints, and has
a `maven-publish` scaffold. No `SK*` source classes are implemented yet. See
[`docs/ROADMAP.md`](docs/ROADMAP.md) for the full phase-by-phase plan and progress checklist.

## Intent

This repo implements a **Kotlin library for Android that mirrors Apple's
[SpriteKit](https://developer.apple.com/documentation/spritekit) API** — a scene graph
(`SKNode`/`SKScene`), sprites/shapes/labels, an action system, 2D physics, particles, tile maps,
camera/effects/constraints, transitions, and shaders — built on `GLSurfaceView`.

The goal is API and behavioral parity with Apple's SpriteKit types (`SKNode`, `SKScene`,
`SKAction`, `SKPhysicsBody`, etc.), but expressed in **idiomatic Kotlin** rather than a literal
Obj-C/Swift transliteration (nullable types instead of Optional-as-comment, data/sealed classes,
extension functions, no `NSPredicate`/`NSCopying`/`NSCoding` equivalents). This repo is the sibling
project to [GameplayKit for Android](https://github.com/bitzgroup/GameplayKit), which follows the
same philosophy for Apple's GameplayKit; the two are not integrated with each other (no
`GKScene`-style binding — see `docs/ROADMAP.md`'s "Explicitly Out of Scope").

**Threading is the one part of this design with no Apple equivalent to mirror.** Apple's SpriteKit
runs scene mutation, actions, physics, and rendering on the same main thread that delivers UI/touch
events. Android splits that: `GLSurfaceView` owns a dedicated GL thread separate from the UI
thread. This library treats **the GL thread as the scene's main thread** and provides UI-thread ↔
GL-thread bridge utilities (`SKView.runOnGLThread`/`runOnUiThread`) to coordinate the two. See
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design — read this before touching
anything related to `SKView`, the frame loop, touch dispatch, or GPU resource lifecycle.

See `docs/ROADMAP.md` for the full implementation plan and progress checklist, organized by
SpriteKit subsystem.

## Commands

All commands run from the repo root.

- Build: `./gradlew assemble`
- Unit tests: `./gradlew testDebugUnitTest` (a single test: `./gradlew testDebugUnitTest --tests "jp.co.bitz.spritekit.SomeTest"`)
- Lint/format check: `./gradlew ktlintCheck` (auto-fix: `./gradlew ktlintFormat`)
- Static analysis: `./gradlew detekt`
- Full CI-equivalent check: `./gradlew ktlintCheck detekt assemble testDebugUnitTest`
- Publish to local Maven repo (sanity-check publishing config): `./gradlew publishToMavenLocal`

If `ANDROID_HOME`/`ANDROID_SDK_ROOT` is not set in the shell, create a `local.properties` (gitignored)
with `sdk.dir=/path/to/Android/sdk`.

Note: unlike a typical Android library, this project has **no `androidTest` (instrumented test)
source set and no emulator/device CI job** — GL-rendering code can't be exercised by a JVM unit
test. Each subsystem is designed so its *logic* (scene graph math, action interpolation, physics,
atlas packing) is pure-Kotlin and unit-testable independent of a live GL context; only the thin GL
draw-call layer itself goes unverified by automated tests. This is a deliberate, accepted gap for
now — see `docs/ROADMAP.md` Phase 0.

## Project structure

- `spritekit/` — the library module (`jp.co.bitz.spritekit`), namespace/group configured via
  `gradle.properties` (`GROUP`, `VERSION_NAME`) and `spritekit/build.gradle.kts`.
- `gradle/libs.versions.toml` — version catalog; add new dependencies/plugins here, not as
  hardcoded version strings in build files.
- `config/detekt/detekt.yml` — detekt rule overrides (builds upon detekt's default ruleset).
- `docs/ROADMAP.md` — phased implementation plan and progress checklist.
- `docs/ARCHITECTURE.md` — the Android-specific threading/rendering design (GL thread as main
  thread, UI↔GL bridge utilities, GPU resource lifecycle, frame loop, coordinate systems).
- `docs/API_COMPATIBILITY.md` — deviation log from Apple's SpriteKit API shape, filled in per
  subsystem as each roadmap phase lands.

## Git Branching Workflow

This repo follows a [Gitflow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow)-style
branching model.

| Branch | Branches from | Merges into | Naming |
|---|---|---|---|
| `main` | — | — | Always releasable. Direct pushes are blocked (branch protection); merge only from `release/*` or `hotfix/*`. Each merge is tagged with the corresponding `VERSION_NAME`. |
| `develop` | `main` | — | Integration branch for work heading to the next release. Also branch-protected. |
| `feature/<name>` | `develop` | `develop` | e.g. `feature/phase-1-threading`, `feature/phase-7-physics`. |
| `release/<version>` | `develop` | `main` **and** `develop` | e.g. `release/0.2.0`. Release-prep fixes only, no new features. |
| `hotfix/<name>` | `main` | `main` **and** `develop` | e.g. `hotfix/0.1.1-npe-fix`. Urgent fixes to a released `main`. |

- Every merge goes through a PR (no direct pushes to `main` or `develop`); CI
  (`ktlintCheck detekt assemble testDebugUnitTest`) must pass first.
- `release/*`/`hotfix/*` don't exist yet: the first release (`release/0.1.0`, tagged on `main`) is cut
  once the full `docs/ROADMAP.md` plan is complete. Until then, all work happens on `feature/*`
  branches merged into `develop`.

## Working in this repo

- **Documentation language:** all docs (README, KDoc, ROADMAP, etc.) must be written in **English**
  — this is an OSS project.
- **Documentation location:** project docs beyond the root `README.md` (roadmap, architecture
  notes, API compatibility notes, etc.) live under `docs/`.
- **No app/demo module, ever.** This repo is meant to be embedded into host apps as a **git
  submodule** — `settings.gradle.kts` must only ever include the `:spritekit` library module.
  Adding a sample/demo Android app module (even for internal testing) would confuse a host app's
  build, since it would look like part of what needs building/signing when this repo is embedded.
  Manual/visual verification of rendering-related work happens outside this repo.
- **`.gitignore`** covers macOS `.DS_Store` plus a standard Android/Gradle project (`.gradle/`, `build/`, `local.properties`, `*.apk`/`*.aab`, keystores, `google-services.json`, IntelliJ/Android Studio files).
- **Git operations:** branch per the workflow above (`feature/*` off `develop`, etc.); do not run `git commit` or `git push` unless explicitly requested by the user for that specific change.
