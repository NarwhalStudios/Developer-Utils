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
 * Per-entity ticking system that drains {@link AggroComponent} windows.
 * SUSTAINED_IGNORE windows re-call {@link AggroUtil#suppressTargeting} every
 * tick; TAUNT windows re-call {@link AggroUtil#redirectAggro}. The component
 * is removed once the deadline passes.
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

        long nowMs = store.getResource(TimeResource.getResourceType()).getNow().toEpochMilli();
        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);

        if (nowMs >= aggro.getDeadlineMs()) {
            commandBuffer.removeComponent(entityRef, aggroComponentType);
            return;
        }

        switch (aggro.getMode()) {
            case SUSTAINED_IGNORE -> AggroUtil.suppressTargeting(store, entityRef, aggro.getRadius());
            case TAUNT -> AggroUtil.redirectAggro(store, entityRef, aggro.getRadius());
            case ONE_SHOT -> commandBuffer.removeComponent(entityRef, aggroComponentType);
        }
    }
}
