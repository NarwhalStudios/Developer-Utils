# Perfect Utils

A library mod for Hytale that exposes reusable gameplay primitives to other mods. Currently ships two primitives: a **mob stun** (`StunMobAPI`) and a **mob aggro / taunt** controller (`AggroAPI`).

This is part of narwhals' "Perfect" mod family (Perfect Parries, Perfect Dodges, Perfect Utils). Other narwhals mods may move shared utilities here over time.

## Features

### Mob stun primitive

`StunMobAPI` lets any plugin freeze a mob's movement, suppress its combat AI, and lock its interactions for a duration. Internally:

- Stun state lives on a per-entity ECS component (`StunComponent`).
- A ticking system (`StunSystem`) drains the duration each frame and re-applies the entity effect.
- Wake-up restores combat AI after a short buffer (250 ms full stun, 100 ms stagger) so the mob doesn't re-engage mid-recovery.
- Apply requests are queued and drained on the next world tick (`StunQueueDrainSystem`), so callers don't need a `CommandBuffer` of their own — pass a `Store` and a `Ref`, that's it. Worst-case latency is one tick (~50 ms at 20 TPS).

Two flavors:

- `applyStun` — full stun (`STUN_WAKE_DELAY_MS = 250`).
- `applyStagger` — lighter stagger (`STAGGER_WAKE_DELAY_MS = 100`); reapplications only refresh duration if the existing component is also a stagger, so a stagger never downgrades a full stun.

### Mob aggro / taunt primitive

`AggroAPI` exposes mob-targeting wipes and redirects, ported from Zephyr's `KunaiVanishTickSystem`. Three modes against the same in-mod state:

- `dropAggro(store, playerRef, radius)` — one-shot. Clears any current targeting on the player from nearby mobs and wipes the player from each mob's `TargetMemory`. Mobs may re-acquire normally on the next AI tick.
- `suppress(store, playerRef, durationMs, radius)` — sustained "ignore-me" window. For `durationMs`, mobs within `radius` treat the player as `Attitude.IGNORE` and have their target memory continuously wiped of the player. Backed by an ECS `AggroComponent` ticked every frame by `AggroTickSystem`.
- `taunt(store, tauntRef, durationMs, radius)` — taunt direction. Clears every prior target on nearby mobs (allies, the taunter, anything), sets `closestHostile` to the taunter, overrides `Attitude.HOSTILE` on the taunter. The mob's next AI evaluation locks onto the taunter.

`radius <= 0` iterates every NPC in the world (Zephyr-style true invisibility); positive values bound the sweep with a squared-distance filter against `TransformComponent`.

Internally the API uses the same per-tick queue-drain pattern as `StunMobAPI`: requests enqueue from any thread; the shared `QueueDrainSystem` calls `drainPending(store, commandBuffer, nowMs)` once per world tick. Worst-case latency is one tick.

## Consuming the API

Perfect Utils is meant to be a **soft dependency**. Don't add it to your `build.gradle` — probe for the API class reflectively at runtime so your mod still loads cleanly when Perfect Utils isn't installed.

### Detection

```java
PluginManager pm = PluginManager.get();
PluginIdentifier id = new PluginIdentifier("narwhals", "Perfect Utils");
if (!pm.hasPlugin(id, SemverRange.WILDCARD)) {
    // Perfect Utils not installed — skip the feature
    return;
}
```

### Resolving an API

Same pattern for both APIs — replace the FQN to pick which one you want:

```java
Object plugin = /* resolve the plugin instance via PluginManager */;
ClassLoader cl = plugin.getClass().getClassLoader();

// Stun:
Class<?> stunClass = Class.forName(
        "com.narwhals.perfectutils.api.StunMobAPI", false, cl);
Object stun = stunClass.getMethod("get").invoke(null);
Method applyStun = stunClass.getMethod(
        "applyStun", Store.class, Ref.class, long.class, Ref.class);

// Aggro:
Class<?> aggroClass = Class.forName(
        "com.narwhals.perfectutils.api.AggroAPI", false, cl);
Object aggro = aggroClass.getMethod("get").invoke(null);
Method dropAggro = aggroClass.getMethod(
        "dropAggro", Store.class, Ref.class, double.class);
Method suppress = aggroClass.getMethod(
        "suppress", Store.class, Ref.class, long.class, double.class);
Method taunt = aggroClass.getMethod(
        "taunt", Store.class, Ref.class, long.class, double.class);
```

Cache the `Method` handles + the `api` instance on first lookup; don't reflect on every call.

### Calling

```java
// Stun a mob for 2 seconds. sourceRef is optional (attribution).
applyStun.invoke(api, store, mobRef, 2000L, attackerRef);
```

The `StunMobAPI` surface:

| Method | Effect |
| --- | --- |
| `applyStun(store, target, durationMs, source)` | Full stun for `durationMs`. |
| `applyStagger(store, target, durationMs, source)` | Stagger for `durationMs`. Won't downgrade a full stun. |
| `wakeUp(store, target)` | Cancel an active stun early. |
| `isStunned(store, target)` | True while a stun or stagger is active. |
| `isFullStun(store, target)` | True only for full stuns. |
| `getRemainingMs(store, target)` | Remaining duration in milliseconds. |

The `AggroAPI` surface:

| Method | Effect |
| --- | --- |
| `dropAggro(store, playerRef, radius)` | One-shot reset. Mobs may re-acquire normally. |
| `suppress(store, playerRef, durationMs, radius)` | Sustained `Attitude.IGNORE` for `durationMs`. |
| `taunt(store, tauntRef, durationMs, radius)` | Pin nearby mobs to the taunter for `durationMs`. |
| `clear(store, playerRef)` | Cancel an active suppress/taunt early. |
| `isSuppressed(store, playerRef)` | True while a suppress window is active. |
| `isTaunting(store, playerRef)` | True while a taunt window is active. |
| `getRemainingMs(store, playerRef)` | Remaining duration in milliseconds. |

All apply/wake calls take `Store<EntityStore>` only — no `CommandBuffer` needed. Requests queue and drain on the next world tick.

### Example: hyMMO integrations

hyMMO ships reflective adapters in its `IntegrationRegistry` for both APIs:

- **Stun** — exposes a `STUN` on-hit type ability authors can attach to any ability:
  ```json
  "onHit": {"type": "STUN", "durationMs": 1500}
  ```
- **Aggro** — TELEPORT and DASH archetypes gained `aggroRadius` + `aggroDurationMs` params; a new TAUNT archetype calls `taunt(...)` directly. Shadowstep ships with `aggroRadius: 30.0` (one-shot drop on cast); the new `challenging_shout` ability is a 12-block taunt for tank specs.

When Perfect Utils is loaded, the calls dispatch through the adapter. When it isn't, every call is a silent no-op. See `hyMMO/src/main/java/com/ziggfreed/mmoskilltree/integration/{stun,aggro}/` for the full pattern.

## Building from source

Requires:

- JDK 25 (per `gradle.properties`).
- A local Hytale install via the official launcher (Windows: `%APPDATA%\Hytale`). The build references `HytaleServer.jar` from there. Build against the matching server version: Hytale Update 5 (server `0.5.x`) is required from 1.0.2 onward.

If your install is not in the default launcher location, set `hytaleHome` in a gitignored `local.properties` in the project root (for example `hytaleHome=D:/Games/Hytale`); `build.gradle` falls back to the launcher default when the file is absent.

```bash
./gradlew build
```

Output: `build/libs/Perfect Utils-1.1.0.jar`.

The build is self-contained — no external sibling-jar dependencies.

## Credits

- **narwhals** — original stun mechanic in Perfect Parries; original mob-vanish/targeting wipe in Zephyr's `KunaiVanishTickSystem` (the source of `AggroUtil`).
- **ziggfreed** — extracted the stun primitive into Perfect Utils; ported the aggro/taunt controller and added the `suppress` + `taunt` modes.

## License

MIT (see `LICENSE` if present, or the narwhals umbrella license).
