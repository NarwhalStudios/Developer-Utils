# Perfect Utils

A small library mod for Hytale that other mods can build on top of. By itself it adds no commands, items, or configuration — it sits quietly in the background and lets the mods you actually care about do more interesting things.

## Why install it?

Install Perfect Utils only when another mod tells you to. If a mod's description says "requires Perfect Utils," drop this jar in your mods folder and you're done.

## What it currently provides

### **Mob stun primitive**
Mods can ask Perfect Utils to stun a mob for a configurable duration. While stunned, the mob:

- Stops moving (movement speed clamped to zero)
- Stops attacking (its current swing is interrupted, AI is suppressed)
- Cannot use abilities or interact (locked out for the duration)
- Plays a brief stun visual

When the stun runs out, the mob shakes it off and resumes whatever it was doing.

The same stun also works on players (not just mobs), so PvP mods can lock down an opponent for a short window. A stunned player's client disables its own movement input for the duration.

This is the same primitive that powers parry-stun in narwhals' Perfect Parries, lifted into a standalone library so any mod can use it — ability mods can build "stun on hit" effects, weapon mods can ship stun-on-crit, boss mods can apply long lockdowns during phases, and so on.

### **Mob aggro / taunt primitive** 
Mods can ask Perfect Utils to manipulate which target a mob is paying attention to. Three flavors against the same underlying state:

- **Drop aggro** — a one-shot reset. Pursuing mobs in a configurable radius lose track of the player and forget them from their target memory. The mob may pick them up again on the next AI tick if they're still in detection range — useful for "blink away and disappear from the chase," not for permanent invisibility.
- **Suppress** — a sustained "ignore me" window. For a configurable duration, mobs in radius treat the player as non-hostile and have their target memory continuously wiped. Useful for stealth abilities and short invulnerable phases.
- **Taunt** — the opposite direction. Drops aggro on every nearby mob (allies, the taunter, anything they were chasing) and pins them to the taunter for a configurable duration. Useful for tank-style "look at me" abilities that pull mobs off teammates.

All three modes work on any mob in the world — bosses, hostile fauna, summoned creatures — without per-mob configuration.

## Compatibility

- Updated for Hytale Update 5 (server 0.5.x) in version 1.0.2. Use 1.0.2 or newer on Update 5 servers; older builds will not load there.
- Plays nicely with Perfect Parries — they each track their own stun state and don't fight each other.
- The aggro and taunt windows are tracked per-player, so a mod can't accidentally double-apply them; the most recent request wins, the previous one is replaced.
- No configuration to manage; mods that use Perfect Utils set the durations and radii themselves.

## Credits

- **narwhals** — original mob stun in Perfect Parries; original mob-aggro / visibility wipe in Zephyr. Both primitives are lifted from their work and made reusable here.
- **ziggfreed** — extracted both into Perfect Utils as a standalone library, added the sustained-suppress and taunt modes alongside the existing one-shot drop.

