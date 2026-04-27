# Perfect Utils

A library mod for Hytale that exposes reusable gameplay primitives to other mods. The current build ships one primitive: a mob stun.

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

## Consuming the API

Perfect Utils is meant to be a **soft dependency**. Don't add it to your `build.gradle` — probe for the API class reflectively at runtime so your mod still loads cleanly when Perfect Utils isn't installed.

### Detection

```java
PluginManager pm = PluginManager.get();
PluginIdentifier id = new PluginIdentifier("narwhals", "Perfect Utils");
if (!pm.hasPlugin(id, SemverRange.WILDCARD)) {
    // Perfect Utils not installed — skip stun feature
    return;
}
```

### Resolving the API

```java
Object plugin = /* resolve the plugin instance via PluginManager */;
ClassLoader cl = plugin.getClass().getClassLoader();
Class<?> apiClass = Class.forName(
        "com.narwhals.perfectutils.api.StunMobAPI", false, cl);

Object api = apiClass.getMethod("get").invoke(null);
Method applyStun = apiClass.getMethod(
        "applyStun", Store.class, Ref.class, long.class, Ref.class);
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

All apply/wake calls take `Store<EntityStore>` only — no `CommandBuffer` needed. Requests queue and drain on the next world tick.

### Example: hyMMO integration

hyMMO ships a reflective `PerfectUtilsStunAdapter` in its `IntegrationRegistry` and exposes a `STUN` on-hit type that ability authors can attach to any ability:

```json
"onHit": {"type": "STUN", "durationMs": 1500}
```

When Perfect Utils is loaded, the on-hit dispatches through the adapter and the mob stuns. When it isn't, the on-hit is a silent no-op. See `hyMMO/src/main/java/com/ziggfreed/mmoskilltree/integration/stun/` for the full pattern.

## Building from source

Requires:

- JDK 25 (per `gradle.properties`).
- A local Hytale install via the official launcher (Windows: `%APPDATA%\Hytale`). The build references `HytaleServer.jar` from there.

```bash
./gradlew build
```

Output: `build/libs/Perfect Utils-1.0.0.jar`.

The build is self-contained — no external sibling-jar dependencies.

## Credits

- **narwhals** — original stun mechanic in Perfect Parries.
- **ziggfreed** — extracted the stun primitive into Perfect Utils.

## License

MIT (see `LICENSE` if present, or the narwhals umbrella license).
