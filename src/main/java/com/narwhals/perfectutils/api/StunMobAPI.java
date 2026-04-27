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
