# Changelog

All notable changes to Perfect Utils are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.3] - 2026-06-18

### Fixed

- Crash on round teardown: `AggroTickSystem` (ONE_SHOT / SUSTAINED_IGNORE / TAUNT deadline removals) and `AggroAPI.drainPending` (CLEAR) called `commandBuffer.removeComponent` off the chunk-snapshot archetype without re-checking the entity's live archetype. An entity's live archetype can change within the same tick: a taunted player killed mid-tick has the aggro component dropped by their death, so the deferred removal threw `IllegalArgumentException: Archetype doesn't contain ComponentType!`. That uncaught exception crashed the world thread during instance teardown and ejected the dying player to the overworld still dead. All four removal sites now re-check `store.getComponent(ref, aggroComponentType) != null` first, so removal is a safe no-op when the live archetype no longer has the component.

### Changed

- `manifest.json` declares a `ServerVersion` range (`>=0.5.5 <0.6.0`) instead of the `*` wildcard, so Perfect Utils advertises the Hytale Update 5 (`0.5.x`) server line it is built and tested against rather than matching any server version.

## [1.0.2] - 2026-05-28

### Fixed

- Crash on Hytale Update 5 (server `0.5.x`): `AggroTickSystem` threw `NoSuchMethodError` on `TransformComponent.getPosition()`. Update 5 migrated Hytale's math vector types to JOML, so `getPosition()` now returns `org.joml.Vector3d` instead of `com.hypixel.hytale.math.vector.Vector3d`. A JVM method signature includes its return type, so the call compiled against the old type fails at runtime. Recompiled against the Update 5 server jar; the position field reads (`.x` / `.y` / `.z`) are source-compatible with JOML, so only the import changed.

### Changed

- `build.gradle` now reads `hytaleHome` from a gitignored `local.properties` (key `hytaleHome`), defaulting to the official launcher location when absent. Set `hytaleHome` to your install root (for example `D:/Games/Hytale`) to build against a non-default Hytale install.

## [1.0.1] - 2026-04-27

### Added

- `AggroAPI` — mob aggro / taunt controller, ported from Zephyr's `KunaiVanishTickSystem`.
  - `dropAggro(store, playerRef, radius)` — one-shot targeting reset within `radius`.
  - `suppress(store, playerRef, durationMs, radius)` — sustained `Attitude.IGNORE` window.
  - `taunt(store, tauntRef, durationMs, radius)` — pin nearby mobs to the taunter.
  - `clear`, `isSuppressed`, `isTaunting`, `getRemainingMs` queries.
- `AggroComponent`, `AggroTickSystem`, `AggroQueueDrainSystem` — per-player ECS state, swept once per tick. Drain stages components only; the tick system owns all `forEachChunk(NPCEntity, ...)` work to avoid nested-iteration errors on the store.
- `AggroUtil` — static helpers (`resetTargetingNearby`, `suppressTargeting`, `redirectAggro`) using `MarkedEntitySupport` + `WorldSupport.overrideAttitude` + `TargetMemory.knownHostiles`.
- `radius <= 0` iterates every NPC in the world (Zephyr-style true invisibility); positive values bound the sweep with a squared-distance filter against `TransformComponent`.

## [1.0.0] - 2026-04-26

### Added

- Initial release.
- `StunMobAPI` — mob stun primitive lifted from Perfect Parries.
  - `applyStun(store, target, durationMs, source)` — full stun: movement freeze + AI suppression + interaction lock.
  - `applyStagger(store, target, durationMs, source)` — lighter stagger; won't downgrade an active full stun.
  - `wakeUp(store, target)` — cancel an active stun early.
  - `isStunned`, `isFullStun`, `getRemainingMs` queries.
- `StunComponent`, `StunSystem`, `StunQueueDrainSystem`, `StunMobUtil` — per-entity ECS state, drained per-tick, with re-application each frame to outlast Hytale's short effect expiry.
- `StunConstants` — `STUN_END_THRESHOLD_SECONDS = 0.025f`, `STUN_WAKE_DELAY_MS = 250`, `STAGGER_WAKE_DELAY_MS = 100`.
- `EffectUtil` — minimal `EntityEffect` lookup + apply, lifted from Perfect Parries.
- Asset packs `DU_Entity_Stunned` / `DU_Entity_Staggered` (`HorizontalSpeedMultiplier: 0` + `MovementEffects.DisableAll: true`, 0.5s, re-applied per tick).
- Public APIs take `Store<EntityStore>` only — requests queue on a `ConcurrentLinkedQueue` and drain on the next world tick (~1 tick / 50 ms latency at 20 TPS).

[1.0.3]: https://github.com/narwhals/perfect-utils/releases/tag/v1.0.3
[1.0.2]: https://github.com/narwhals/perfect-utils/releases/tag/v1.0.2
[1.0.1]: https://github.com/narwhals/perfect-utils/releases/tag/v1.0.1
[1.0.0]: https://github.com/narwhals/perfect-utils/releases/tag/v1.0.0
