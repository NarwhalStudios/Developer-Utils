package com.narwhals.perfectutils.system;

import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.narwhals.perfectutils.api.DrainableAPI;

/**
 * Generic per-tick drain for any {@link DrainableAPI}. Queries
 * {@link PlayerRef} so the system fires whenever a player is online (which
 * is whenever any apply/queue request can possibly arrive). Multiple players
 * cause the tick to fire multiple times per frame; the {@code lastDrainMs}
 * gate ensures the queue is drained exactly once per tick.
 *
 * <p>Constructed with a supplier so the system can pick up a fresh API
 * instance after re-init (the singletons under {@code api/} are {@code volatile}
 * and can be re-set by {@code PerfectUtilsPlugin.setup} on hot-reload paths).
 *
 * <p>Register one instance per drainable API in
 * {@code PerfectUtilsPlugin.initSystems} — there's no shared internal state,
 * each instance has its own {@code lastDrainMs}, so two instances ticking in
 * the same frame don't race.
 */
public class QueueDrainSystem extends EntityTickingSystem<EntityStore> {

    private final Supplier<? extends DrainableAPI> apiSupplier;
    private long lastDrainMs = -1L;

    public QueueDrainSystem(@Nonnull Supplier<? extends DrainableAPI> apiSupplier) {
        this.apiSupplier = apiSupplier;
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Override
    public void tick(float dt, int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        long nowMs = store.getResource(TimeResource.getResourceType()).getNow().toEpochMilli();
        if (nowMs == lastDrainMs) return;
        lastDrainMs = nowMs;

        DrainableAPI api = apiSupplier.get();
        if (api == null) return;
        api.drainPending(store, commandBuffer, nowMs);
    }
}
