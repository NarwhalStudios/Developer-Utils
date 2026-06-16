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
                removeAggro(store, commandBuffer, entityRef);
            }
            case SUSTAINED_IGNORE -> {
                long nowMs = store.getResource(TimeResource.getResourceType()).getNow().toEpochMilli();
                if (nowMs >= aggro.getDeadlineMs()) {
                    removeAggro(store, commandBuffer, entityRef);
                } else {
                    AggroUtil.suppressTargeting(store, entityRef, aggro.getRadius());
                }
            }
            case TAUNT -> {
                long nowMs = store.getResource(TimeResource.getResourceType()).getNow().toEpochMilli();
                if (nowMs >= aggro.getDeadlineMs()) {
                    removeAggro(store, commandBuffer, entityRef);
                } else {
                    AggroUtil.redirectAggro(store, entityRef, aggro.getRadius());
                }
            }
        }
    }

    /**
     * Remove the {@link AggroComponent} only if the entity's LIVE archetype still has it. The outer
     * query hands us entities by their CHUNK archetype, but an entity's live archetype can change
     * within the same tick (e.g. a taunted player dies and the engine drops the component) before this
     * deferred removal flushes. A raw {@code removeComponent} then throws
     * "Archetype doesn't contain ComponentType!", which crashed the world thread during teardown and
     * ejected the dying player to the overworld still dead. Re-checking the live store makes it a no-op.
     */
    private void removeAggro(@Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Ref<EntityStore> entityRef) {
        if (store.getComponent(entityRef, aggroComponentType) != null) {
            commandBuffer.removeComponent(entityRef, aggroComponentType);
        }
    }
}
