package com.narwhals.perfectutils.stun;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.narwhals.perfectutils.api.StunMobAPI;

/**
 * Drains the {@link StunMobAPI} pending queue once per world tick, for that world's own targets.
 * Queries {@link PlayerRef} so the system fires whenever a player is in the world; the world's
 * {@link StunStoreState} gate makes it drain exactly once per tick regardless of player count.
 * One instance serves every world, each on its own thread, so the gate lives in the world's store,
 * never in a field here.
 */
public class StunQueueDrainSystem extends EntityTickingSystem<EntityStore> {

    private final ResourceType<EntityStore, StunStoreState> storeStateType;

    public StunQueueDrainSystem(@Nonnull ResourceType<EntityStore, StunStoreState> storeStateType) {
        this.storeStateType = storeStateType;
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
        StunStoreState state = store.getResource(storeStateType);
        if (state != null && !state.claimDrain(nowMs)) return;

        StunMobAPI api = StunMobAPI.get();
        if (api == null) return;
        api.drainPending(store, commandBuffer, nowMs);
    }
}
