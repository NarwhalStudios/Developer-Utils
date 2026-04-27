package com.narwhals.perfectutils.aggro;

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
import com.narwhals.perfectutils.api.AggroAPI;

/**
 * Drains the {@link AggroAPI} pending queue once per world tick. Queries
 * {@link PlayerRef} so the system fires whenever any player is online; the
 * {@code lastDrainMs} gate ensures the queue is drained exactly once per tick
 * regardless of player count.
 */
public class AggroQueueDrainSystem extends EntityTickingSystem<EntityStore> {

    private long lastDrainMs = -1L;

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

        AggroAPI api = AggroAPI.get();
        if (api == null) return;
        api.drainPending(store, commandBuffer, nowMs);
    }
}
