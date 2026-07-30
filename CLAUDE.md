# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

RS Mod is a RuneScape (OldSchool-era, revision-based) game-server emulator written in Kotlin, aiming to be as
mechanically accurate to the original client/server as possible. It targets Java 21 and is built with Gradle.
[RSProx](https://github.com/blurite/rsprox) is the recommended compatible client for manual verification of
game mechanics against real packet traffic.

## Common commands

First-time / environment setup (downloads the game cache and generates required artifacts):
```sh
./gradlew install --console=plain
```

Run the game server:
```sh
./gradlew run --console=plain
```

Build everything (compiles all modules):
```sh
./gradlew build
```

Format code (Spotless + ktfmt, kotlinlang style, 100 col width):
```sh
./gradlew spotlessApply     # auto-format
./gradlew spotlessCheck     # verify formatting (run by `build`)
```

Unit tests (all modules):
```sh
./gradlew test
```

Run a single test class or method:
```sh
./gradlew :api:game-process:test --tests "org.rsmod.api.game.process.player.PlayerMovementMapFlagTest"
./gradlew :api:game-process:test --tests "*PlayerMovementMapFlagTest.someMethodName"
```

Meta tests (architecture/style rules via Konsist, and KDoc `@see`-tag validation) — run in CI as "Meta Tests":
```sh
./gradlew konsistTest --rerun-tasks
./gradlew docTest
```

Integration tests (require the cache from `install`; slow, run nightly in CI rather than on every PR):
```sh
./gradlew install
./gradlew integration
```

Other useful root tasks (see `build.gradle.kts`): `cleanInstall`, `downloadCache`, `packCache`, `generateRsa`,
`setupLogbackNovice`, `setupLogbackAdvanced`.

## Architecture

The codebase is split into four top-level Gradle module trees, each recursively including every subdirectory
that contains a `build.gradle.kts` (see `settings.gradle.kts`):

- **`engine/`** — the low-level, content-agnostic simulation core: entity/type model (`engine/game`), map/zone/
  collision representation (`engine/map`), route-finding (`engine/routefinder`), the event bus (`engine/events`),
  coroutine support for suspendable game logic (`engine/coroutine`), object-transaction primitives
  (`engine/objtx`), and the plugin/DI scaffolding (`engine/plugin`).
- **`api/`** — Guice-wired services and domain APIs built on top of the engine: cache decoding
  (`api/cache`, `api/cache-enricher`), type-safe config references (`api/config`), networking (`api/net`),
  per-tick game processing (`api/game-process`), and many focused domain modules (`api/player`, `api/npc`,
  `api/combat*`, `api/inv-plugin`, `api/stats*`, `api/shops`, `api/repo`, etc.).
- **`content/`** — actual game content, organized by feature rather than by technical layer: `content/skills`
  (woodcutting, fishing, cooking, magic), `content/areas`, `content/interfaces`, `content/travel`,
  `content/other`, `content/generic`. This is almost always where new gameplay behavior belongs.
- **`server/`** — process bootstrap and wiring: `server/app` (entry point, `GameBootstrap`/`GameServer`, and the
  top-level Guice modules), `server/install` (cache download/pack/RSA-key generation, invoked by the root
  `install`/`downloadCache`/`packCache`/`generateRsa` tasks), `server/services` (service lifecycle management),
  `server/shared` (shared wiring helpers used by `server/app`).

### Dependency injection and plugin discovery

Wiring uses Guice (`jakarta.inject`). `GameServerModule` (in `server/app`) installs the top-level modules:
`CacheStoreModule`, `GameModule`, `ParserModule`, `ScannerModule`, `ServiceModule`, `SymbolModule`.

Content and API modules are **not** manually registered anywhere. `ScannerModule` uses ClassGraph to scan the
`org.rsmod.api` and `org.rsmod.content` packages (see `PluginConstants.searchPackages`) and auto-discovers:
- subclasses of `PluginModule` (Guice bindings for a feature, e.g. `WoodcuttingModule`)
- subclasses of `PluginScript` (event-handler registration for a feature, e.g. `Woodcutting`)

This means adding new content generally just means adding classes under `org.rsmod.content.*` (or
`org.rsmod.api.*`) — no central registry needs updating.

### The `PluginScript` pattern

Content scripts extend `PluginScript` and override `ScriptContext.startup()`, registering event handlers
against type-safe content references (from `api/config/refs`, e.g. `content.tree`, `controllers.*`) rather than
raw ids:
```kotlin
class Woodcutting @Inject constructor(...) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(content.tree) { attempt(it.loc, it.type) }
        onOpLoc3(content.tree) { cut(it.loc, it.type) }
        onOpLocU(content.tree, content.woodcutting_axe) { cut(it.loc, it.type) }
        onAiConTimer(controllers.woodcutting_tree_duration) { controller.treeDespawnTick() }
    }
}
```
State mutation goes through repositories (`LocRepository`, `PlayerRepository`, `ControllerRepository`, etc. in
`api/repo`) rather than direct field mutation.

### Type-safe configs

Game config (objs, locs, npcs, params, varbits, seqs, etc.) is represented as type-safe reference classes
(`ObjType`, `LocType`, `NpcType`, ...) decoded from the cache (`api/cache/**/*TypeDecoder.kt`) and exposed as
named constants under `api/config/src/main/kotlin/org/rsmod/api/config/refs/` (`BaseObjs`, `BaseNpcs`,
`BaseVarBits`, etc.), avoiding magic numbers and letting references be checked for existence in the cache.
Note: the `id` field on these types is intentionally internal, mutable, and nullable — a deliberate tradeoff
documented in `docs/quirks.md`.

### Test source sets

Beyond the standard `test` source set, most modules also have:
- an `integration` source set (via `integration-test-suite.gradle.kts`) for slower tests that depend on
  `api:testing` and a real cache — run with `./gradlew integration` after `./gradlew install`.
- `konsistTest`/`docTest` suites live only in `api/meta` and assert architecture/style rules and KDoc
  conventions across the whole codebase.

### Notable quirks and conventions worth knowing before changing engine/API behavior

The full, evolving list is in `docs/quirks.md`; a few that are easy to get wrong:
- `delay(...)` (`p_delay`) does **not** follow the official convention: `delay(1)` delays one tick, and
  `delay(0)` is a no-op.
- Teleport functions intentionally take a `CollisionFlagMap` argument instead of relying on a singleton/pub-sub,
  because teleporting must update the collision map instantly.
- All game-related randomness should go through `GameRandom`, not raw `kotlin.random`/`java.util.Random`
  (enforced by `StdRandomKonsistTest`).
- Zone update processing (and thus outgoing `ZoneProt` updates) iterates zones in a fixed bottom-left-to-top-right
  order, not the order updates actually occurred.
- Tests involving object transactions (`org.rsmod.objtx`) are not thread-safe; mark such test classes
  `@Execution(ExecutionMode.SAME_THREAD)`.
