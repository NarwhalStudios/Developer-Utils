# Perfect Utils

Lets other Hytale mods stun mobs, shake them off your trail, or pull them onto one player. Install it when a mod asks for it.

On its own it adds nothing you can see: no items, commands or settings. The mods that use it decide when it kicks in.

## What you see in game

- A stunned mob stops moving and attacking, can't use abilities, and shows a stun effect until it shakes it off. The same stun works on players, for PvP mods.
- Mobs near you can lose track of you, for a moment or for a few seconds while you slip away.
- A taunt pulls nearby mobs off everyone else and onto one player.

## Mods that use it

- [Kweebec Nightmare](https://www.curseforge.com/hytale/mods/kweebec-nightmares) needs it. A thrown Moonbloom stuns the hunter, and the hunter uses it to lock onto a survivor.
- [MMO Skill Tree](https://www.curseforge.com/hytale/mods/mmo-skill-tree) works better with it. Shield Bash, Shield Slam and Paralyzing Dart stop the target in place, Challenging Shout pulls mobs onto you, and Shadowstep and Vanish make nearby mobs lose track of you. Without it those abilities still hit, just without the stun.

## Install

Drop the jar into your server's `Mods/` folder. Version 1.3.0 is built for Hytale Update 7. On Update 6 use 1.2.0, and on Update 5 use 1.0.2 to 1.1.0: a build for one update won't load on another.

It runs alongside Perfect Parries without the two fighting over a stun.

## For mod authors

Perfect Utils gives your mod a stun (`StunMobAPI`) and an aggro controller (`AggroAPI`) that drops, suppresses or taunts, and you set the time and the radius. Both work on any mob with no setup per mob. The [README on GitHub](https://github.com/NarwhalStudios/Developer-Utils) shows how to call them.

## Credits

- **narwhals**: the original mob stun in Perfect Parries and the mob-aggro wipe in Zephyr.
- **ziggfreed**: turned both into this library and added the suppress and taunt modes.
