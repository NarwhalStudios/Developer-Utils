package com.narwhals.perfectutils.aggro;

import java.lang.reflect.Field;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.builtin.npccombatactionevaluator.memory.TargetMemory;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.hypixel.hytale.server.npc.util.AttitudeMemoryEntry;
import com.narwhals.perfectutils.PerfectUtilsPlugin;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * Mob-targeting wipes and redirects, ported from Zephyr's
 * {@code KunaiVanishTickSystem.suppressMobTargeting}. Three modes:
 *
 * <ul>
 * <li>{@link #resetTargetingNearby}: one-shot. Clear marked-entity slots,
 * remove player from {@link TargetMemory#getKnownHostiles}, null out
 * {@code closestHostile} if it's the player. No attitude override — mobs may
 * re-acquire normally if still in detection range.</li>
 *
 * <li>{@link #suppressTargeting}: sustained-tick variant. Same as above
 * <b>plus</b> {@link Attitude#IGNORE} override on the player, pinning mobs to
 * non-hostile until the override clears.</li>
 *
 * <li>{@link #redirectAggro}: taunt. Clears all marked-entity slots (drops
 * every prior target, including allies), sets {@code closestHostile} to the
 * taunter, and overrides {@link Attitude#HOSTILE} on the taunter — mobs'
 * next AI evaluation will lock onto the taunter.</li>
 * </ul>
 *
 * <p>All methods accept an optional positive {@code radius}; if &lt;= 0,
 * iterates every NPC in the world (Zephyr-style true invisibility). Distance
 * test uses squared comparison against the source entity's
 * {@link TransformComponent}.
 *
 * <p>Reflection trick: {@code WorldSupport.attitudeOverrideMemory} is a
 * private field that may be {@code null} on a freshly-constructed role.
 * {@link #ensureAttitudeOverrideMemory} lazy-initializes it once per JVM via
 * a cached {@link Field} handle.
 */
public final class AggroUtil {

    private static final HytaleLogger LOGGER = PerfectUtilsPlugin.LOGGER;

    private static volatile Field attitudeOverrideMemoryField;

    private AggroUtil() {}

    /**
     * One-shot reset. Clears any current targeting on the player from nearby
     * mobs and wipes the player from their {@link TargetMemory}. Mobs may
     * naturally re-acquire on next AI tick if the player remains in detection
     * range.
     */
    public static void resetTargetingNearby(@Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerRef, double radius) {
        if (!playerRef.isValid()) return;
        Vector3d origin = readPosition(store, playerRef);
        forEachNpc(store, origin, radius, (npcRef, role, targetMemory) ->
                wipePlayerFromMob(playerRef, role, targetMemory, false));
    }

    /**
     * Sustained-ignore reset. Same as {@link #resetTargetingNearby} plus an
     * {@link Attitude#IGNORE} override on the player so mobs don't re-acquire
     * even when in detection range. Intended to be called every tick from
     * {@link AggroTickSystem} for the duration of the suppression window.
     */
    public static void suppressTargeting(@Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerRef, double radius) {
        if (!playerRef.isValid()) return;
        Vector3d origin = readPosition(store, playerRef);
        forEachNpc(store, origin, radius, (npcRef, role, targetMemory) ->
                wipePlayerFromMob(playerRef, role, targetMemory, true));
    }

    /**
     * Taunt direction. Clears all marked-entity slots on nearby mobs (drops
     * every prior target — allies, the taunter, anything), sets
     * {@code closestHostile = tauntRef}, and overrides
     * {@link Attitude#HOSTILE} on the taunter. The mob's next AI evaluation
     * will lock onto the taunter.
     */
    public static void redirectAggro(@Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> tauntRef, double radius) {
        if (!tauntRef.isValid()) return;
        Vector3d origin = readPosition(store, tauntRef);
        int tauntIndex = tauntRef.getIndex();

        forEachNpc(store, origin, radius, (npcRef, role, targetMemory) -> {
            try {
                MarkedEntitySupport markedSupport = role.getMarkedEntitySupport();
                Ref<EntityStore>[] targets = markedSupport.getEntityTargets();
                for (int slot = 0; slot < targets.length; slot++) {
                    Ref<EntityStore> target = targets[slot];
                    if (target != null && target.isValid() && target.getIndex() != tauntIndex) {
                        markedSupport.clearMarkedEntity(slot);
                    }
                }

                WorldSupport worldSupport = role.getWorldSupport();
                ensureAttitudeOverrideMemory(worldSupport);
                worldSupport.overrideAttitude(tauntRef, Attitude.HOSTILE, 1.0);

                if (targetMemory != null) {
                    targetMemory.setClosestHostile(tauntRef);
                }
            } catch (Exception e) {
                LOGGER.atFine().log("redirectAggro failed for one mob: " + e.getMessage());
            }
        });
    }

    private static void wipePlayerFromMob(@Nonnull Ref<EntityStore> playerRef,
            @Nonnull Role role, @Nullable TargetMemory targetMemory, boolean overrideIgnore) {
        try {
            int playerIndex = playerRef.getIndex();

            MarkedEntitySupport markedSupport = role.getMarkedEntitySupport();
            Ref<EntityStore>[] targets = markedSupport.getEntityTargets();
            for (int slot = 0; slot < targets.length; slot++) {
                Ref<EntityStore> target = targets[slot];
                if (target != null && target.isValid() && target.getIndex() == playerIndex) {
                    markedSupport.clearMarkedEntity(slot);
                }
            }

            if (overrideIgnore) {
                WorldSupport worldSupport = role.getWorldSupport();
                ensureAttitudeOverrideMemory(worldSupport);
                worldSupport.overrideAttitude(playerRef, Attitude.IGNORE, 1.0);
            }

            if (targetMemory != null) {
                if (targetMemory.getKnownHostiles().containsKey(playerIndex)) {
                    targetMemory.getKnownHostiles().remove(playerIndex);
                    List<Ref<EntityStore>> hostileList = targetMemory.getKnownHostilesList();
                    hostileList.removeIf(ref -> ref.getIndex() == playerIndex);
                }
                Ref<EntityStore> closest = targetMemory.getClosestHostile();
                if (closest != null && closest.getIndex() == playerIndex) {
                    targetMemory.setClosestHostile(null);
                }
            }
        } catch (Exception e) {
            LOGGER.atFine().log("wipePlayerFromMob failed for one mob: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface NpcVisitor {
        void visit(@Nonnull Ref<EntityStore> npcRef, @Nonnull Role role, @Nullable TargetMemory targetMemory);
    }

    private static void forEachNpc(@Nonnull Store<EntityStore> store, @Nullable Vector3d origin,
            double radius, @Nonnull NpcVisitor visitor) {
        ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
        ComponentType<EntityStore, TargetMemory> targetMemoryType = TargetMemory.getComponentType();
        ComponentType<EntityStore, TransformComponent> transformType = TransformComponent.getComponentType();
        boolean radiusFilter = origin != null && radius > 0.0;
        double radiusSq = radius * radius;

        store.forEachChunk(npcType, (npcChunk, commandBuffer) -> {
            int size = npcChunk.size();
            for (int i = 0; i < size; i++) {
                NPCEntity npcEntity = npcChunk.getComponent(i, npcType);
                if (npcEntity == null) continue;

                Role role = npcEntity.getRole();
                if (role == null) continue;

                Ref<EntityStore> npcRef = npcChunk.getReferenceTo(i);

                if (radiusFilter) {
                    TransformComponent npcTransform = npcChunk.getComponent(i, transformType);
                    if (npcTransform == null) continue;
                    Vector3d npcPos = npcTransform.getPosition();
                    double dx = npcPos.x - origin.x;
                    double dy = npcPos.y - origin.y;
                    double dz = npcPos.z - origin.z;
                    if ((dx * dx + dy * dy + dz * dz) > radiusSq) continue;
                }

                TargetMemory targetMemory = npcChunk.getComponent(i, targetMemoryType);
                visitor.visit(npcRef, role, targetMemory);
            }
        });
    }

    @Nullable
    private static Vector3d readPosition(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        return transform == null ? null : transform.getPosition();
    }

    private static void ensureAttitudeOverrideMemory(@Nonnull WorldSupport worldSupport) {
        try {
            if (attitudeOverrideMemoryField == null) {
                synchronized (AggroUtil.class) {
                    if (attitudeOverrideMemoryField == null) {
                        Field field = WorldSupport.class.getDeclaredField("attitudeOverrideMemory");
                        field.setAccessible(true);
                        attitudeOverrideMemoryField = field;
                    }
                }
            }
            Object current = attitudeOverrideMemoryField.get(worldSupport);
            if (current == null) {
                attitudeOverrideMemoryField.set(worldSupport, new Int2ObjectOpenHashMap<AttitudeMemoryEntry>());
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.atWarning().log("Failed to init attitudeOverrideMemory: " + e);
        }
    }
}
