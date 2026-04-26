package com.narwhals.perfectutils.util;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.narwhals.perfectutils.PerfectUtilsPlugin;

public final class EffectUtil {

    private static final HytaleLogger LOGGER = PerfectUtilsPlugin.LOGGER;
    private static final Map<String, EntityEffect> effectCache = new HashMap<>();

    private EffectUtil() {}

    @Nullable
    public static EntityEffect getCachedEffect(String effectName) {
        if (effectCache.containsKey(effectName)) {
            return effectCache.get(effectName);
        }
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectName);
        if (effect == null) {
            LOGGER.atFine().log("EntityEffect '" + effectName + "' not found in asset map");
        }
        effectCache.put(effectName, effect);
        return effect;
    }

    public static void applyEffectWithBehavior(Ref<EntityStore> entityRef, Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer, String effectName, float intensity,
            OverlapBehavior overlapBehavior) {
        if (entityRef == null || !entityRef.isValid()) return;

        EffectControllerComponent effectController = store.getComponent(entityRef,
                EffectControllerComponent.getComponentType());
        if (effectController == null) return;

        EntityEffect effect = getCachedEffect(effectName);
        if (effect == null) return;

        try {
            effectController.addEffect(entityRef, effect, intensity, overlapBehavior, commandBuffer);
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to apply effect '" + effectName + "': " + e.getMessage());
        }
    }
}
