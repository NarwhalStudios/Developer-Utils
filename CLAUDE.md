# CLAUDE.md

## What this repo is

**Perfect Utils**, a small Hytale library mod published by NarwhalStudios. It exposes two reusable gameplay primitives, a **mob stun** (`StunMobAPI`) and a **mob aggro / taunt controller** (`AggroAPI`), that other plugins consume (see "Consumer pattern"). No commands, no configs, no in-game UI.

The directory is named `Developer-Utils` for historical reasons: the on-disk path doesn't match the published mod name.

## Architecture

All under `com.narwhals.perfectutils`. Both APIs keep `Store`-only public surfaces and defer the ECS work to the next world tick through each API's own drain system (`StunQueueDrainSystem`, `AggroQueueDrainSystem`), since a `CommandBuffer` exists only inside a system's `tick`. Each drain queries `PlayerRef` and drains once per tick through its world's store state (`StunStoreState`, `AggroStoreState`).

- Never consolidate the two drain systems into one generic class: `registerSystem` keys by `Class`, so a second instance collides. A new drainable API gets its own `*QueueDrainSystem`.
- A drain system only stages components through its `CommandBuffer`; `AggroTickSystem` (outer query `AggroComponent`) does every sweep, because `store.forEachChunk(NPCEntity, ...)` from a `PlayerRef`-query system throws `Store is currently processing!`. Even a `ONE_SHOT` drop is a one-tick `AggroComponent`, so de-aggro lands 1-2 ticks after the call. The stun path may call `StunMobUtil.applyStun` from its drain, since it iterates no chunks.

## Code conventions

- Public API takes `long durationMs` (milliseconds). Internal `StunSystem` math is in `float seconds` (matches Hytale's tick `delta`). Convert at the boundary: `durationMs / 1000f`.
- Logger: `PerfectUtilsPlugin.LOGGER` (a `HytaleLogger.forEnclosingClass()` initialized once on the plugin class).
- Error handling: every CommandBuffer-side operation in `StunMobUtil` is wrapped in `try { ... } catch (Exception e) { LOGGER.atWarning().log(...) }` because a single mob's bad component state shouldn't break a whole tick.

## Consumer pattern

The family consumes this mod both ways, so a change to the api reaches both:

- The MMO (`mmo-family/mmo-skills`) loads it reflectively and runs without it: `integration/stun/adapters/PerfectUtilsStunAdapter` and `integration/aggro/adapters/PerfectUtilsAggroAdapter` probe for the API class and cache the handles. With Perfect Utils absent, its stun and aggro effects do nothing and its admin config page warns.
- Kweebec Nightmare (`mods/kweebec-nightmare`) compiles against it and hard-depends: `compileOnly files(perfectUtilsJar)`, the jar this repo builds (`repo.Developer-Utils` in `family.properties`, listed in `dep.kweebec-nightmare`) at the pinned `perfectUtilsVersion`, and `"narwhals:Perfect Utils"` in its manifest's `Dependencies`, so it does not load without it.

## Sibling repos

- `mods\Perfect-Parries`: original home of the stun primitive. Keeps its own in-house stun module (parry-specific crit window + stamina restoration) and does not consume Perfect Utils. Copy of the source lives there if comparing behavior is useful.
- `mmo-family\mmo-skills` (the MMO): the reflective consumer. Its adapters live in `integration/{stun,aggro}/adapters/`, and the `MmoStunInteraction`, `MmoTauntInteraction` and `MmoAggroSuppressInteraction` interaction types use them.
- `mods\Zephyr`: source of the aggro/targeting wipe pattern. `KunaiVanishTickSystem.suppressMobTargeting` is what `AggroUtil` was ported from, with the same `MarkedEntitySupport` + `WorldSupport.overrideAttitude` + `TargetMemory.knownHostiles` three-prong API surface and the same private-`attitudeOverrideMemory` reflection lazy-init.
