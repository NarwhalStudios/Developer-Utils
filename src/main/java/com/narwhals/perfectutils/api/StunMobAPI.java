package com.narwhals.perfectutils.api;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.narwhals.perfectutils.PerfectUtilsPlugin;
import com.narwhals.perfectutils.stun.StunMobUtil;

/**
 * Public, mod-facing API for the stun primitive. Other plugins resolve this
 * class reflectively (e.g., {@code Class.forName("com.narwhals.perfectutils.api.StunMobAPI")
 * .getMethod("get").invoke(null)}); a hard compile-time dependency on the
 * Developer-Utils jar is not required.
 *
 * <p>All apply/wake calls accept {@link Store} only — the underlying ECS work
 * needs a {@link CommandBuffer} which is not reachable outside a system tick,
 * so requests are enqueued and drained once per world tick by
 * {@code StunQueueDrainSystem}. Worst-case latency is one tick (~50ms at 20 TPS).
 *
 * <p>Query methods ({@link #isStunned}, {@link #isFullStun}, {@link #getRemainingMs})
 * read the ECS component directly via {@link Store}, so they reflect the state
 * as of the current call (subject to the 1-tick application delay above).
 *
 * <p><b>Entity-neutral.</b> The stun pipeline is entity-agnostic: it freezes
 * movement by pushing a stun {@code EntityEffect} (zero horizontal speed +
 * {@code MovementEffects.DisableAll}) through the target's
 * {@code EffectControllerComponent}, which a PLAYER carries just like a mob
 * (the vanilla {@code /player effect apply} path), so the client disables its
 * own movement input for the duration. The NPC-AI suppression
 * ({@code CombatActionEvaluator} / {@code CombatSupport}) is null-guarded and
 * simply no-ops on a player (a player has no NPC role). Use the
 * {@code *Entity} methods below to stun an arbitrary entity (mob OR player);
 * the {@code applyStun}/{@code applyStagger} methods are kept as the original
 * names and behave identically on any entity.
 */
public final class StunMobAPI {

    private enum RequestType { STUN, STAGGER, WAKE }

    private static final class PendingRequest {
        final RequestType type;
        final Ref<EntityStore> targetRef;
        final long durationMs;
        @Nullable final Ref<EntityStore> sourceRef;
        final boolean wakeAsFullStun;

        PendingRequest(RequestType type, Ref<EntityStore> targetRef, long durationMs,
                @Nullable Ref<EntityStore> sourceRef, boolean wakeAsFullStun) {
            this.type = type;
            this.targetRef = targetRef;
            this.durationMs = durationMs;
            this.sourceRef = sourceRef;
            this.wakeAsFullStun = wakeAsFullStun;
        }
    }

    private static volatile StunMobAPI instance;

    private final PerfectUtilsPlugin plugin;
    private final Queue<PendingRequest> pending = new ConcurrentLinkedQueue<>();

    private StunMobAPI(@Nonnull PerfectUtilsPlugin plugin) {
        this.plugin = plugin;
    }

    public static void init(@Nonnull PerfectUtilsPlugin plugin) {
        instance = new StunMobAPI(plugin);
    }

    @Nullable
    public static StunMobAPI get() {
        return instance;
    }

    public static void clearInstance() {
        if (instance != null) {
            instance.pending.clear();
        }
        instance = null;
    }

    /**
     * Apply a full mob stun: zero movement, AI suppression, interaction lock.
     * Realized within one world tick.
     */
    public void applyStun(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> targetRef,
            long durationMs, @Nullable Ref<EntityStore> sourceRef) {
        if (!targetRef.isValid() || durationMs <= 0) return;
        pending.add(new PendingRequest(RequestType.STUN, targetRef, durationMs, sourceRef, true));
    }

    /**
     * Apply a stagger — same mechanic as a stun but with a shorter wake delay
     * and the {@link com.narwhals.perfectutils.stun.StunComponent.StunType#STAGGER}
     * tag. Reapplications only refresh duration if the existing component is also
     * a stagger; otherwise the call is dropped to avoid downgrading a full stun.
     */
    public void applyStagger(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> targetRef,
            long durationMs, @Nullable Ref<EntityStore> sourceRef) {
        if (!targetRef.isValid() || durationMs <= 0) return;
        pending.add(new PendingRequest(RequestType.STAGGER, targetRef, durationMs, sourceRef, false));
    }

    /**
     * Apply a full stun to ANY entity (mob or player). Entity-neutral alias of
     * {@link #applyStun(Store, Ref, long, Ref)} with an explicit name for
     * consumers that stun players: a player's client disables its own movement
     * input for the duration via the stun {@code EntityEffect}; the NPC-AI
     * suppression simply no-ops on a player. Realized within one world tick.
     *
     * @param targetRef  the entity to stun (mob or player)
     * @param durationMs stun length in milliseconds; ignored if {@code <= 0}
     * @param sourceRef  optional source/attacker entity (may be {@code null})
     */
    public void stunEntity(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> targetRef,
            long durationMs, @Nullable Ref<EntityStore> sourceRef) {
        applyStun(store, targetRef, durationMs, sourceRef);
    }

    /**
     * Convenience overload of {@link #stunEntity(Store, Ref, long, Ref)} with no
     * source entity.
     */
    public void stunEntity(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> targetRef,
            long durationMs) {
        applyStun(store, targetRef, durationMs, null);
    }

    /** Cancel an active stun early. No-ops if the entity is not stunned. */
    public void wakeUp(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> targetRef) {
        if (!targetRef.isValid()) return;
        boolean wasFullStun = StunMobUtil.isFullStun(targetRef, store);
        pending.add(new PendingRequest(RequestType.WAKE, targetRef, 0, null, wasFullStun));
    }

    public boolean isStunned(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> targetRef) {
        return StunMobUtil.isStunned(targetRef, store);
    }

    public boolean isFullStun(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> targetRef) {
        return StunMobUtil.isFullStun(targetRef, store);
    }

    public long getRemainingMs(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> targetRef) {
        return StunMobUtil.getRemainingMs(targetRef, store);
    }

    /**
     * Drain pending requests. Called by
     * {@link com.narwhals.perfectutils.stun.StunQueueDrainSystem} once per
     * world tick with a fresh {@link CommandBuffer}.
     */
    public void drainPending(@Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, long nowMs) {
        PendingRequest req;
        while ((req = pending.poll()) != null) {
            if (!req.targetRef.isValid()) continue;
            try {
                switch (req.type) {
                    case STUN -> StunMobUtil.applyStun(req.targetRef, store, commandBuffer,
                            req.durationMs, req.sourceRef, nowMs);
                    case STAGGER -> StunMobUtil.applyStagger(req.targetRef, store, commandBuffer,
                            req.durationMs, nowMs);
                    case WAKE -> StunMobUtil.wakeUp(req.targetRef, store, commandBuffer,
                            req.wakeAsFullStun);
                }
            } catch (Throwable t) {
                PerfectUtilsPlugin.LOGGER.atWarning().log(
                        "StunMobAPI: drain request " + req.type + " threw: " + t.getMessage());
            }
        }
    }
}
