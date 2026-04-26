package com.narwhals.perfectutils.stun;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.narwhals.perfectutils.PerfectUtilsPlugin;
import com.narwhals.perfectutils.util.EffectUtil;

/**
 * Per-entity ticking system that drains stun durations and re-applies the entity
 * effect each tick (effects are short-lived in Hytale, so the system reapplies
 * with {@link OverlapBehavior#OVERWRITE} until the stun naturally ends).
 */
public class StunSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = PerfectUtilsPlugin.LOGGER;

    private final ComponentType<EntityStore, StunComponent> stunComponentType;

    public StunSystem(ComponentType<EntityStore, StunComponent> stunComponentType) {
        this.stunComponentType = stunComponentType;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return stunComponentType;
    }

    @Override
    public void tick(float delta, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        StunComponent stunComp = chunk.getComponent(index, stunComponentType);
        if (stunComp == null || stunComp.isWakingUp()) return;

        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);

        float remaining = stunComp.getTimeRemaining() - delta;
        stunComp.setTimeRemaining(remaining);

        if (remaining <= StunConstants.STUN_END_THRESHOLD_SECONDS) {
            endStun(entityRef, store, commandBuffer, stunComp.isFullStun());
            return;
        }

        try {
            String effectName = stunComp.isFullStun()
                    ? StunConstants.STUN_EFFECT_ASSET
                    : StunConstants.STAGGER_EFFECT_ASSET;
            EffectUtil.applyEffectWithBehavior(entityRef, store, commandBuffer, effectName, 0.5f,
                    OverlapBehavior.OVERWRITE);
        } catch (Exception e) {
            LOGGER.atWarning().log("Exception reapplying stun effect: " + e.getMessage());
        }

        StunMobUtil.enforceStun(entityRef, commandBuffer, remaining);
    }

    private void endStun(Ref<EntityStore> entityRef, Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer, boolean wasFullStun) {
        StunMobUtil.enforceStun(entityRef, commandBuffer, 0.0f);
        StunMobUtil.wakeUp(entityRef, store, commandBuffer, wasFullStun);
    }
}
