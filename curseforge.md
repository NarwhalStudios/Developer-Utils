# Perfect Utils

A small library mod for Hytale that other mods can build on top of. By itself it adds no commands, items, or configuration — it sits quietly in the background and lets the mods you actually care about do more interesting things.

## Why install it?

Install Perfect Utils only when another mod tells you to. If a mod's description says "requires Perfect Utils," drop this jar in your mods folder and you're done.

## What it currently provides

**Mob stun primitive.** Mods can ask Perfect Utils to stun a mob for a configurable duration. While stunned, the mob:

- Stops moving (movement speed clamped to zero)
- Stops attacking (its current swing is interrupted, AI is suppressed)
- Cannot use abilities or interact (locked out for the duration)
- Plays a brief stun visual

When the stun runs out, the mob shakes it off and resumes whatever it was doing.

This is the same primitive that powers parry-stun in narwhals' Perfect Parries, lifted into a standalone library so any mod can use it — ability mods can build "stun on hit" effects, weapon mods can ship stun-on-crit, boss mods can apply long lockdowns during phases, and so on.

## Compatibility

- Works on any Hytale server.
- Plays nicely with Perfect Parries — they each track their own stun state and don't fight each other.
- No configuration to manage; mods that use Perfect Utils set the stun durations themselves.

## Reporting bugs

Open an issue on the GitHub repo or post in the narwhals modding community. Include a server log and a description of which other mods were active.
