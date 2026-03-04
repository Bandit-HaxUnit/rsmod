---
name: rsmod rev 233 to 236
overview: "Update rsmod from OSRS revision 233 to 236 across four phases: revision infrastructure, rsprot API migration (the largest change), Sailing skill release, and cache hash updates."
todos:
  - id: phase1-build
    content: "Update Build.kt: MAJOR=236, CACHE_URL and XTEA_URL to openrs2 id 2468"
    status: pending
  - id: phase1-toml
    content: "Update libs.versions.toml: rsprot version to 1.0.0-ALPHA-20250203, artifact IDs osrs-233-* to osrs-236-*"
    status: pending
  - id: phase1-readme
    content: Update README.md revision badge from 233 to 236
    status: pending
  - id: phase2-compile
    content: Attempt Gradle build after rsprot version change and note all compilation errors
    status: pending
  - id: phase2-rspcycle
    content: Migrate RspCycle.kt to new rsprot unified InfoPackets API (updateRootCoord, updateRootBuildAreaCenteredOnPlayer, getPackets())
    status: pending
  - id: phase2-networkscript
    content: "Migrate NetworkScript.kt: service.playerInfoProtocol/npcInfoProtocol → service.infoProtocols"
    status: pending
  - id: phase2-networkfactory
    content: "Migrate NetworkFactory.kt: alloc/dealloc calls to new infoProtocols API"
    status: pending
  - id: phase2-zone
    content: Fix any zone packet compilation errors in ZoneUpdateTransformer, PlayerZoneUpdateProcessor, SharedZoneEnclosedBuffers
    status: pending
  - id: phase3-sailing
    content: Remove unreleased = true from sailing in StatBuilds.kt
    status: pending
  - id: phase4-hashes
    content: Run server, collect hash mismatch errors, update stale hashes in Base* refs files
    status: pending
isProject: false
---

# rsmod: Revision 233 → 236

## Context

Three revisions passed between 233 and 236:

- **234** (Oct 2025): Grid Master minigame
- **235** (Nov 2025 – Jan 2026): **Sailing skill launched** (Nov 19, 2025), Christmas Event
- **236** (Feb 2026): Deadman: Annihilation, 25th Anniversary Event, Cow Boss

The hardest part is **not** the cache update — it's a **large-scale rsprot API refactor in rev 235** that changed how player/NPC info protocols are structured.

---

## Phase 1: Revision Infrastructure

**3 files, all mechanical changes.**

### `[api/core/src/main/kotlin/org/rsmod/api/core/Build.kt](api/core/src/main/kotlin/org/rsmod/api/core/Build.kt)`

```kotlin
MAJOR = 236
CACHE_URL = "https://archive.openrs2.org/caches/runescape/2468/disk.zip"
XTEA_URL  = "https://archive.openrs2.org/caches/runescape/2468/keys.json"
```

openrs2 ID `2468` is the most complete rev-236 live snapshot (Feb 25, 2026, 127,000 groups).

### `[gradle/libs.versions.toml](gradle/libs.versions.toml)`

```toml
rsprot = "1.0.0-ALPHA-20250203"           # was 1.0.0-ALPHA-20250909
rsprot-api    = { module = "net.rsprot:osrs-236-api",    ... }   # was osrs-233-api
rsprot-shared = { module = "net.rsprot:osrs-236-shared", ... }   # was osrs-233-shared
```

Note: despite the `20250203` version name, this was uploaded to Maven Central on 2026-02-03 (it is a newer version, not older).

### `[README.md](README.md)`

Update the revision badge URL: `revision-233-important` → `revision-236-important`.

---

## Phase 2: rsprot API Migration

**The rev 235 refactor is the largest change.** rsprot merged `PlayerInfo`, `NpcInfo`, and `WorldEntityInfo` into a unified `InfoPackets` system. The [rsprot README](https://github.com/blurite/rsprot) states: *"Revisions older than 235 have a significantly different API."*

The strategy: update `libs.versions.toml` first (Phase 1), then attempt a Gradle build and fix compilation errors. The files almost certain to require changes are:

### `[api/net/src/main/kotlin/org/rsmod/api/net/rsprot/RspCycle.kt](api/net/src/main/kotlin/org/rsmod/api/net/rsprot/RspCycle.kt)`

Currently holds separate `PlayerInfo` and `NpcInfo` references and calls them independently. The new API consolidates these. Key changes expected:

- Constructor likely changes from `(session, playerInfo, npcInfo, ...)` to accepting a unified info object
- `playerInfo.updateCoord(...)` + `npcInfo.updateCoord(...)` → `infos.updateRootCoord(level, x, z)`
- `playerInfo.updateBuildArea(...)` + `npcInfo.updateBuildArea(...)` → `infos.updateRootBuildAreaCenteredOnPlayer(x, z)`
- In `flush()`: `playerInfo.toPacket()` + `npcInfo.toPacket()` + `SetActiveWorldV2` + `SetNpcUpdateOrigin` → `infos.getPackets().rootWorldInfoPackets` with individual `.send()` calls

### `[api/net/src/main/kotlin/org/rsmod/api/net/rsprot/NetworkScript.kt](api/net/src/main/kotlin/org/rsmod/api/net/rsprot/NetworkScript.kt)`

- `service.playerInfoProtocol.update()` + `service.npcInfoProtocol.update()` → `service.infoProtocols.update()`

### `[api/net/src/main/kotlin/org/rsmod/api/net/rsprot/NetworkFactory.kt](api/net/src/main/kotlin/org/rsmod/api/net/rsprot/NetworkFactory.kt)`

- `service.playerInfoProtocol.alloc(slot, ...)` + `service.npcInfoProtocol.alloc(slot, ...)` → `service.infoProtocols.alloc(slot, ...)`

### Zone packet files (check on compile)

Rev 234 removed standalone `ObjAdd`/`ObjDel`/etc. packets — but rsmod already routes public obj packets through `UpdateZonePartialEnclosed` via `SharedZoneEnclosedBuffers`, which is the correct post-234 approach. These files may or may not need changes depending on whether rsprot renamed or restructured zone packet classes:

- `[api/registry/src/main/kotlin/org/rsmod/api/registry/zone/ZoneUpdateTransformer.kt](api/registry/src/main/kotlin/org/rsmod/api/registry/zone/ZoneUpdateTransformer.kt)`
- `[api/game-process/src/main/kotlin/org/rsmod/api/game/process/player/PlayerZoneUpdateProcessor.kt](api/game-process/src/main/kotlin/org/rsmod/api/game/process/player/PlayerZoneUpdateProcessor.kt)`
- `[api/utils/utils-zone/src/main/kotlin/org/rsmod/api/utils/zone/SharedZoneEnclosedBuffers.kt](api/utils/utils-zone/src/main/kotlin/org/rsmod/api/utils/zone/SharedZoneEnclosedBuffers.kt)`

**Approach:** let the Kotlin compiler report every broken call site — it will enumerate all changes needed.

---

## Phase 3: Sailing Skill

Sailing launched officially on **November 19, 2025** (rev 235). rsmod already has the skill defined but gated:

### `[api/config/src/main/kotlin/org/rsmod/api/config/builders/StatBuilds.kt](api/config/src/main/kotlin/org/rsmod/api/config/builders/StatBuilds.kt)`

```kotlin
// Before:
build("sailing") { unreleased = true }
// After:
build("sailing")
```

---

## Phase 4: Cache Hash Verification

After the server compiles and the new rev-236 cache downloads, run the server. The type resolver checks every `find("name", hash)` entry in the Base refs files against the live cache. If any named type changed between rev 233 and 236, the server will throw at startup:

```
The following reference hashes do not match their cache-computed hash (N found)
    - Invalid hash: <old>  | Cache hash: <new>  | Reference: ObjType(internalName='...')
```

Each error line already contains the correct new hash value. Collect all errors (up to 50 per run), then update the stale entries across:

- `[api/config/src/main/kotlin/org/rsmod/api/config/refs/BaseInterfaces.kt](api/config/src/main/kotlin/org/rsmod/api/config/refs/BaseInterfaces.kt)`
- `[api/config/src/main/kotlin/org/rsmod/api/config/refs/BaseComponents.kt](api/config/src/main/kotlin/org/rsmod/api/config/refs/BaseComponents.kt)`
- `[api/config/src/main/kotlin/org/rsmod/api/config/refs/BaseSeqs.kt](api/config/src/main/kotlin/org/rsmod/api/config/refs/BaseSeqs.kt)`
- `[api/config/src/main/kotlin/org/rsmod/api/config/refs/BaseSpotanims.kt](api/config/src/main/kotlin/org/rsmod/api/config/refs/BaseSpotanims.kt)`
- `[api/config/src/main/kotlin/org/rsmod/api/config/refs/BaseObjs.kt](api/config/src/main/kotlin/org/rsmod/api/config/refs/BaseObjs.kt)` (if any)
- `[api/config/src/main/kotlin/org/rsmod/api/config/refs/BaseNpcs.kt](api/config/src/main/kotlin/org/rsmod/api/config/refs/BaseNpcs.kt)` (if any)

You can also use `--skip-type-verification` as a server startup flag to bypass hash checks entirely and confirm the server runs, then circle back to fix hashes.

---

## Execution Order

```
Phase 1  →  Phase 2 (compile-driven)  →  Phase 3Execution failed for task ':content:other:commands:spotlessKotlinCheck'.
> The following files had format violations:
      src\main\kotlin\org\rsmod\content\other\commands\AdminCommands.kt
          @@ -1,16 +1,16 @@
           package org.rsmod.content.other.commands\r\n
           \r\n
          -import com.github.michaelbull.logging.InlineLogger\n
          -import jakarta.inject.Inject\n
          -import kotlin.math.max\n
          -import kotlin.math.min\n
          -import org.rsmod.annotations.InternalApi\n
          -import org.rsmod.api.config.constants\n
          -import org.rsmod.api.invtx.invAdd\n
          -import org.rsmod.api.invtx.invClear\n
          -import org.rsmod.api.player.output.MiscOutput\n
          -import org.rsmod.api.player.output.mes\n
          -import org.rsmod.api.player.protect.ProtectedAccessLauncher\n
          +import com.github.michaelbull.logging.InlineLogger\r\n
          +import jakarta.inject.Inject\r\n
          +import kotlin.math.max\r\n
          +import kotlin.math.min\r\n
          +import org.rsmod.annotations.InternalApi\r\n
          +import org.rsmod.api.config.constants\r\n
          +import org.rsmod.api.invtx.invAdd\r\n
          +import org.rsmod.api.invtx.invClear\r\n
          +import org.rsmod.api.player.output.MiscOutput\r\n
          +import org.rsmod.api.player.output.mes\r\n
          +import org.rsmod.api.player.protect.ProtectedAccessLauncher\r\n
           import org.rsmod.api.player.stat.PlayerSkillXP\r\n
           import org.rsmod.api.player.stat.stat\r\n
           import org.rsmod.api.player.stat.statAdvance\r\n
          @@ -94,15 +94,15 @@
                       invalidArgs = "Use as ::locadd duration locDebugNameOrId (ex: 100 bookcase)"\r\n
                   }\r\n
                   onCommand("locdel", "Remove loc", ::locDel) { invalidArgs = "Use as ::locdel duration" }\r\n
          -        onCommand("npcadd", "Spawn npc", ::npcAdd) {\n
          -            invalidArgs = "Use as ::npcadd duration npcDebugNameOrId (ex: 100 prison_pete)"\n
          -        }\n
          -        onCommand("invadd", "Spawn obj into inv", ::invAdd)\n
          -        onCommand("spawn", "Spawn obj into inv using item search", ::spawn)\n
          -        onCommand("invclear", "Remove all objs from inv", ::invClear)\n
          -        onCommand("varp", "Set varp value", ::setVarp) {\n
          -            invalidArgs = "Use as ::varp debugNameOrId value (ex: option_run 1)"\n
          -        }\n
          +        onCommand("npcadd", "Spawn npc", ::npcAdd) {\r\n
          +            invalidArgs = "Use as ::npcadd duration npcDebugNameOrId (ex: 100 prison_pete)"\r\n
          +        }\r\n
          +        onCommand("invadd", "Spawn obj into inv", ::invAdd)\r\n
          +        onCommand("spawn", "Spawn obj into inv using item search", ::spawn)\r\n
          +        onCommand("invclear", "Remove all objs from inv", ::invClear)\r\n
          +        onCommand("varp", "Set varp value", ::setVarp) {\r\n
          +            invalidArgs = "Use as ::varp debugNameOrId value (ex: option_run 1)"\r\n
      ... (100 more lines that didn't fit)
  Run 'gradlew.bat :content:other:commands:spotlessApply' to fix these violations.Execution failed for task ':content:other:commands:spotlessKotlinCheck'.
> The following files had format violations:
      src\main\kotlin\org\rsmod\content\other\commands\AdminCommands.kt
          @@ -1,16 +1,16 @@
           package org.rsmod.content.other.commands\r\n
           \r\n
          -import com.github.michaelbull.logging.InlineLogger\n
          -import jakarta.inject.Inject\n
          -import kotlin.math.max\n
          -import kotlin.math.min\n
          -import org.rsmod.annotations.InternalApi\n
          -import org.rsmod.api.config.constants\n
          -import org.rsmod.api.invtx.invAdd\n
          -import org.rsmod.api.invtx.invClear\n
          -import org.rsmod.api.player.output.MiscOutput\n
          -import org.rsmod.api.player.output.mes\n
          -import org.rsmod.api.player.protect.ProtectedAccessLauncher\n
          +import com.github.michaelbull.logging.InlineLogger\r\n
          +import jakarta.inject.Inject\r\n
          +import kotlin.math.max\r\n
          +import kotlin.math.min\r\n
          +import org.rsmod.annotations.InternalApi\r\n
          +import org.rsmod.api.config.constants\r\n
          +import org.rsmod.api.invtx.invAdd\r\n
          +import org.rsmod.api.invtx.invClear\r\n
          +import org.rsmod.api.player.output.MiscOutput\r\n
          +import org.rsmod.api.player.output.mes\r\n
          +import org.rsmod.api.player.protect.ProtectedAccessLauncher\r\n
           import org.rsmod.api.player.stat.PlayerSkillXP\r\n
           import org.rsmod.api.player.stat.stat\r\n
           import org.rsmod.api.player.stat.statAdvance\r\n
          @@ -94,15 +94,15 @@
                       invalidArgs = "Use as ::locadd duration locDebugNameOrId (ex: 100 bookcase)"\r\n
                   }\r\n
                   onCommand("locdel", "Remove loc", ::locDel) { invalidArgs = "Use as ::locdel duration" }\r\n
          -        onCommand("npcadd", "Spawn npc", ::npcAdd) {\n
          -            invalidArgs = "Use as ::npcadd duration npcDebugNameOrId (ex: 100 prison_pete)"\n
          -        }\n
          -        onCommand("invadd", "Spawn obj into inv", ::invAdd)\n
          -        onCommand("spawn", "Spawn obj into inv using item search", ::spawn)\n
          -        onCommand("invclear", "Remove all objs from inv", ::invClear)\n
          -        onCommand("varp", "Set varp value", ::setVarp) {\n
          -            invalidArgs = "Use as ::varp debugNameOrId value (ex: option_run 1)"\n
          -        }\n
          +        onCommand("npcadd", "Spawn npc", ::npcAdd) {\r\n
          +            invalidArgs = "Use as ::npcadd duration npcDebugNameOrId (ex: 100 prison_pete)"\r\n
          +        }\r\n
          +        onCommand("invadd", "Spawn obj into inv", ::invAdd)\r\n
          +        onCommand("spawn", "Spawn obj into inv using item search", ::spawn)\r\n
          +        onCommand("invclear", "Remove all objs from inv", ::invClear)\r\n
          +        onCommand("varp", "Set varp value", ::setVarp) {\r\n
          +            invalidArgs = "Use as ::varp debugNameOrId value (ex: option_run 1)"\r\n
      ... (100 more lines that didn't fit)
  Run 'gradlew.bat :content:other:commands:spotlessApply' to fix these violations.  →  Phase 4 (run-driven)
```

Phases 1, 3, and 4 are deterministic. Phase 2 is guided by compiler output after upgrading rsprot.