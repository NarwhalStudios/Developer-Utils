package com.narwhals.perfectutils.stun;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.narwhals.perfectutils.api.StunMobAPI;

/**
 * Drains the {@link StunMobAPI} pending-stun queue once per world tick. Queries
 * {@link PlayerRef} so the system ticks for at least one entity whenever any
 * player is in the world (which is whenever stuns can be triggered). Multiple
 * players cause the tick to fire multiple times in the same frame; the
 * {@code lastDrainMs} gate ensures the queue is drained exactly once per tick.
 */
public class StunQueueDrainSystem extends EntityTickingSystem<EntityStore> {

    private long lastDrainMs = -1;

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

        StunMobAPI api = StunMobAPI.get();
        if (api == null) return;
        api.drainPending(store, commandBuffer, nowMs);
    }
}
