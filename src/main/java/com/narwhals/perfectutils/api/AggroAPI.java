package com.narwhals.perfectutils.api;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.narwhals.perfectutils.PerfectUtilsPlugin;
import com.narwhals.perfectutils.aggro.AggroComponent;
import com.narwhals.perfectutils.aggro.AggroUtil;

/**
 * Public, mod-facing API for the aggro-management primitive. Other plugins
 * resolve this class reflectively (e.g.,
 * {@code Class.forName("com.narwhals.perfectutils.api.AggroAPI").getMethod("get").invoke(null)});
 * a hard compile-time dependency on the Developer-Utils jar is not required.
 *
 * <p>Three behavioral modes:
 * <ul>
 * <li>{@link #dropAggro} — one-shot. Clear current targeting on the player +
 * wipe player from {@code TargetMemory}. Mobs may re-acquire normally.</li>
 * <li>{@link #suppress} — sustained. For the given duration, repeatedly clear
 * targeting + pin {@code Attitude.IGNORE} on the player so mobs cannot
 * re-acquire. Drains via {@link com.narwhals.perfectutils.aggro.AggroTickSystem}.</li>
 * <li>{@link #taunt} — taunt. Clears all marked-entity slots on nearby mobs,
 * sets {@code closestHostile} to the taunter, and overrides
 * {@code Attitude.HOSTILE} so the next AI evaluation locks onto the
 * taunter.</li>
 * </ul>
 *
 * <p>All apply calls accept {@link Store} only — the underlying ECS work
 * needs a {@link CommandBuffer} which is not reachable outside a system tick,
 * so requests are enqueued and drained once per world tick by
 * {@link com.narwhals.perfectutils.aggro.AggroQueueDrainSystem}. Worst-case
 * latency is one tick (~50ms at 20 TPS).
 *
 * <p>Query methods ({@link #isSuppressed}, {@link #isTaunting},
 * {@link #getRemainingMs}) read the ECS component directly via {@link Store},
 * so they reflect the state as of the current call (subject to the 1-tick
 * application delay above).
 */
public final class AggroAPI {

    private enum RequestType { DROP_AGGRO, SUPPRESS, TAUNT, CLEAR }

    private static final class PendingRequest {
        final RequestType type;
        final Ref<EntityStore> targetRef;
        final long durationMs;
        final double radius;

        PendingRequest(RequestType type, Ref<EntityStore> targetRef, long durationMs, double radius) {
            this.type = type;
            this.targetRef = targetRef;
            this.durationMs = durationMs;
            this.radius = radius;
        }
    }

    private static volatile AggroAPI instance;

    private final PerfectUtilsPlugin plugin;
    private final Queue<PendingRequest> pending = new ConcurrentLinkedQueue<>();
    private ComponentType<EntityStore, AggroComponent> aggroComponentType;

    private AggroAPI(@Nonnull PerfectUtilsPlugin plugin) {
        this.plugin = plugin;
    }

    public static void init(@Nonnull PerfectUtilsPlugin plugin) {
        instance = new AggroAPI(plugin);
    }

    @Nullable
    public static AggroAPI get() {
        return instance;
    }

    public static void clearInstance() {
        if (instance != null) {
            instance.pending.clear();
        }
        instance = null;
    }

    public void setAggroComponentType(@Nonnull ComponentType<EntityStore, AggroComponent> componentType) {
        this.aggroComponentType = componentType;
    }

    /**
     * One-shot aggro reset. Clears any current targeting on the player from
     * mobs within {@code radius} (or the entire world if {@code radius <= 0})
     * and wipes the player from their {@code TargetMemory}. Mobs may
     * re-acquire normally on next AI tick.
     */
    public void dropAggro(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef, double radius) {
        if (!playerRef.isValid()) return;
        pending.add(new PendingRequest(RequestType.DROP_AGGRO, playerRef, 0L, radius));
    }

    /**
     * Sustained "ignore-me" window. For {@code durationMs}, mobs within
     * {@code radius} (or world-wide if {@code radius <= 0}) treat the player
     * as {@code Attitude.IGNORE} and have their target memory continuously
     * wiped of the player.
     */
    public void suppress(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef,
            long durationMs, double radius) {
        if (!playerRef.isValid() || durationMs <= 0) return;
        pending.add(new PendingRequest(RequestType.SUPPRESS, playerRef, durationMs, radius));
    }

    /**
     * Taunt direction. Drops aggro on every mob within {@code radius} (or
     * world-wide if {@code radius <= 0}) and pins those mobs to the taunter
     * for {@code durationMs} via {@code Attitude.HOSTILE} override + closest
     * hostile redirect.
     */
    public void taunt(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> tauntRef,
            long durationMs, double radius) {
        if (!tauntRef.isValid() || durationMs <= 0) return;
        pending.add(new PendingRequest(RequestType.TAUNT, tauntRef, durationMs, radius));
    }

    /** Cancel an active suppress/taunt window early. No-op if none is active. */
    public void clear(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef) {
        if (!playerRef.isValid()) return;
        pending.add(new PendingRequest(RequestType.CLEAR, playerRef, 0L, 0.0));
    }

    public boolean isSuppressed(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef) {
        if (aggroComponentType == null || !playerRef.isValid()) return false;
        AggroComponent comp = store.getComponent(playerRef, aggroComponentType);
        return comp != null && comp.getMode() == AggroComponent.Mode.SUSTAINED_IGNORE;
    }

    public boolean isTaunting(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef) {
        if (aggroComponentType == null || !playerRef.isValid()) return false;
        AggroComponent comp = store.getComponent(playerRef, aggroComponentType);
        return comp != null && comp.getMode() == AggroComponent.Mode.TAUNT;
    }

    public long getRemainingMs(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef) {
        if (aggroComponentType == null || !playerRef.isValid()) return 0L;
        AggroComponent comp = store.getComponent(playerRef, aggroComponentType);
        if (comp == null) return 0L;
        long nowMs = System.currentTimeMillis();
        long remaining = comp.getDeadlineMs() - nowMs;
        return Math.max(0L, remaining);
    }

    /**
     * Drain pending requests. Called by
     * {@link com.narwhals.perfectutils.aggro.AggroQueueDrainSystem} once per
     * world tick with a fresh {@link CommandBuffer}.
     *
     * <p>Drain does NOT call {@link AggroUtil} directly — Hytale's Store
     * disallows {@code forEachChunk} from inside a system tick when the outer
     * iteration is on a different archetype (e.g. {@link
     * com.hypixel.hytale.server.core.universe.PlayerRef}). All actual mob
     * targeting work lives in {@link com.narwhals.perfectutils.aggro.AggroTickSystem},
     * which iterates the per-player {@link AggroComponent} archetype — Zephyr's
     * proven-safe pattern. Drain just stages components.
     */
    public void drainPending(@Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, long nowMs) {
        if (aggroComponentType == null) {
            // Drop everything quietly; the plugin hasn't finished setup yet.
            pending.clear();
            return;
        }
        PendingRequest req;
        while ((req = pending.poll()) != null) {
            if (!req.targetRef.isValid()) continue;
            try {
                switch (req.type) {
                    case DROP_AGGRO -> stageWindow(store, commandBuffer, req,
                            AggroComponent.Mode.ONE_SHOT, nowMs + 1L);
                    case SUPPRESS -> stageWindow(store, commandBuffer, req,
                            AggroComponent.Mode.SUSTAINED_IGNORE, nowMs + req.durationMs);
                    case TAUNT -> stageWindow(store, commandBuffer, req,
                            AggroComponent.Mode.TAUNT, nowMs + req.durationMs);
                    case CLEAR -> {
                        // Only remove if the live archetype still has it - removeComponent throws
                        // "Archetype doesn't contain ComponentType!" otherwise (e.g. clearing a player
                        // whose component was already dropped when they died).
                        if (store.getComponent(req.targetRef, aggroComponentType) != null) {
                            commandBuffer.removeComponent(req.targetRef, aggroComponentType);
                        }
                    }
                }
            } catch (Throwable t) {
                PerfectUtilsPlugin.LOGGER.atWarning().log(
                        "AggroAPI: drain request " + req.type + " threw: " + t.getMessage());
            }
        }
    }

    private void stageWindow(@Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull PendingRequest req,
            @Nonnull AggroComponent.Mode mode, long deadlineMs) {
        AggroComponent existing = store.getComponent(req.targetRef, aggroComponentType);
        if (existing != null) {
            existing.setMode(mode);
            existing.setDeadlineMs(deadlineMs);
            existing.setRadius(req.radius);
        } else {
            commandBuffer.addComponent(req.targetRef, aggroComponentType,
                    new AggroComponent(mode, deadlineMs, req.radius));
        }
    }
}
