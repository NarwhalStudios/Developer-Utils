package com.narwhals.perfectutils.aggro;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Per-entity ticking system that runs all {@link AggroUtil} sweeps. Outer
 * query is {@link AggroComponent}, mirroring Zephyr's
 * {@code KunaiVanishTickSystem} — the only proven-safe outer query for
 * inner {@code store.forEachChunk(NPCEntity, ...)} calls.
 *
 * <p>{@code ONE_SHOT} fires the sweep once and removes the component the
 * same tick. {@code SUSTAINED_IGNORE} and {@code TAUNT} re-fire each tick
 * until the deadline passes, then remove.
 */
public class AggroTickSystem extends EntityTickingSystem<EntityStore> {

    private final ComponentType<EntityStore, AggroComponent> aggroComponentType;

    public AggroTickSystem(ComponentType<EntityStore, AggroComponent> aggroComponentType) {
        this.aggroComponentType = aggroComponentType;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return aggroComponentType;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        AggroComponent aggro = chunk.getComponent(index, aggroComponentType);
        if (aggro == null) return;

        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);

        switch (aggro.getMode()) {
            case ONE_SHOT -> {
                AggroUtil.resetTargetingNearby(store, entityRef, aggro.getRadius());
                commandBuffer.removeComponent(entityRef, aggroComponentType);
            }
            case SUSTAINED_IGNORE -> {
                long nowMs = store.getResource(TimeResource.getResourceType()).getNow().toEpochMilli();
                if (nowMs >= aggro.getDeadlineMs()) {
                    commandBuffer.removeComponent(entityRef, aggroComponentType);
                } else {
                    AggroUtil.suppressTargeting(store, entityRef, aggro.getRadius());
                }
            }
            case TAUNT -> {
                long nowMs = store.getResource(TimeResource.getResourceType()).getNow().toEpochMilli();
                if (nowMs >= aggro.getDeadlineMs()) {
                    commandBuffer.removeComponent(entityRef, aggroComponentType);
                } else {
                    AggroUtil.redirectAggro(store, entityRef, aggro.getRadius());
                }
            }
        }
    }
}
