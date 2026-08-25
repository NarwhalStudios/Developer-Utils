package com.narwhals.perfectutils.stun;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import com.hypixel.hytale.builtin.npccombatactionevaluator.evaluator.CombatActionEvaluator;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.CombatSupport;
import com.narwhals.perfectutils.PerfectUtilsPlugin;
import com.narwhals.perfectutils.util.EffectUtil;

/**
 * Generic mob-stun utility extracted from Perfect-Parries. Applies a movement
 * freeze + AI suppression + interaction lock for a configurable duration. The
 * stun is realized as an ECS component ({@link StunComponent}) ticked by
 * {@link StunSystem}; this class is the shared apply/wake entry point.
 *
 * <p>Public API ({@link com.narwhals.perfectutils.api.StunMobAPI}) exposes
 * a Store-only signature; this class still requires a {@link CommandBuffer}
 * because adding/removing components is a deferred operation in Hytale's ECS.
 */
public final class StunMobUtil {

    private static final HytaleLogger LOGGER = PerfectUtilsPlugin.LOGGER;

    private static ComponentType<EntityStore, StunComponent> stunComponentType;

    /** Per-tick dedup map — multiple apply requests in the same tick collapse to one. */
    private static final Map<Ref<EntityStore>, Long> pendingStunAdditions = new HashMap<>();
    private static long lastTickTime = -1;

    private StunMobUtil() {}

    public static void init(ComponentType<EntityStore, StunComponent> componentType) {
        stunComponentType = componentType;
    }

    public static void applyStun(Ref<EntityStore> entityRef, Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer, long durationMs,
            @Nullable Ref<EntityStore> sourceRef, long nowMs) {
        if (stunComponentType == null) {
            LOGGER.atWarning().log("StunMobUtil component type not initialized");
            return;
        }
        if (entityRef == null || !entityRef.isValid() || durationMs <= 0) return;

        float durationSeconds = durationMs / 1000f;

        if (nowMs != lastTickTime) {
            pendingStunAdditions.clear();
            lastTickTime = nowMs;
        }

        StunComponent existing = store.getComponent(entityRef, stunComponentType);
        if (existing != null) {
            existing.setTimeRemaining(durationSeconds);
            applyEffect(entityRef, store, commandBuffer, durationSeconds, true);
            setInteractionLock(entityRef, commandBuffer, durationSeconds, true);
            return;
        }

        if (pendingStunAdditions.containsKey(entityRef)) return;
        pendingStunAdditions.put(entityRef, nowMs);

        applyEffect(entityRef, store, commandBuffer, durationSeconds, true);
        setInteractionLock(entityRef, commandBuffer, durationSeconds, true);

        StunComponent stunComp = new StunComponent(durationSeconds, StunComponent.StunType.STUN);
        commandBuffer.addComponent(entityRef, stunComponentType, stunComp);
    }

    public static void applyStagger(Ref<EntityStore> entityRef, Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer, long durationMs, long nowMs) {
        if (stunComponentType == null) return;
        if (entityRef == null || !entityRef.isValid() || durationMs <= 0) return;

        float durationSeconds = durationMs / 1000f;

        if (nowMs != lastTickTime) {
            pendingStunAdditions.clear();
            lastTickTime = nowMs;
        }

        StunComponent existing = store.getComponent(entityRef, stunComponentType);
        if (existing != null) {
            if (existing.isStagger() && durationSeconds > existing.getTimeRemaining()) {
                existing.setTimeRemaining(durationSeconds);
                applyEffect(entityRef, store, commandBuffer, durationSeconds, false);
                setInteractionLock(entityRef, commandBuffer, durationSeconds, true);
            }
            return;
        }

        if (pendingStunAdditions.containsKey(entityRef)) return;
        pendingStunAdditions.put(entityRef, nowMs);

        applyEffect(entityRef, store, commandBuffer, durationSeconds, false);
        setInteractionLock(entityRef, commandBuffer, durationSeconds, true);

        StunComponent staggerComp = new StunComponent(durationSeconds, StunComponent.StunType.STAGGER);
        commandBuffer.addComponent(entityRef, stunComponentType, staggerComp);
    }

    public static void wakeUp(Ref<EntityStore> entityRef, Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer, boolean wasFullStun) {
        if (stunComponentType == null) return;
        if (entityRef == null || !entityRef.isValid()) return;

        StunComponent stunComp = store.getComponent(entityRef, stunComponentType);
        if (stunComp == null || stunComp.isWakingUp()) return;
        stunComp.setWakingUp(true);

        commandBuffer.removeComponent(entityRef, stunComponentType);
        AnimationUtils.stopAnimation(entityRef, AnimationSlot.Movement, true, commandBuffer);
        AnimationUtils.stopAnimation(entityRef, AnimationSlot.Action, true, commandBuffer);

        World world = store.getExternalData().getWorld();
        long delayMs = wasFullStun
                ? StunConstants.STUN_WAKE_DELAY_MS
                : StunConstants.STAGGER_WAKE_DELAY_MS;

        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            if (entityRef.isValid()) {
                world.execute(() -> resetCombatSupport(entityRef, store));
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    public static void enforceStun(Ref<EntityStore> entityRef, CommandBuffer<EntityStore> commandBuffer,
            float remainingDuration) {
        if (entityRef == null || !entityRef.isValid()) return;
        if (remainingDuration > 0) {
            setInteractionLock(entityRef, commandBuffer, remainingDuration, false);
        }
    }

    public static boolean isStunned(Ref<EntityStore> entityRef, Store<EntityStore> store) {
        if (stunComponentType == null) return false;
        if (entityRef == null || !entityRef.isValid()) return false;
        return store.getComponent(entityRef, stunComponentType) != null;
    }

    public static boolean isFullStun(Ref<EntityStore> entityRef, Store<EntityStore> store) {
        if (stunComponentType == null) return false;
        if (entityRef == null || !entityRef.isValid()) return false;
        StunComponent comp = store.getComponent(entityRef, stunComponentType);
        return comp != null && comp.isFullStun();
    }

    public static long getRemainingMs(Ref<EntityStore> entityRef, Store<EntityStore> store) {
        if (stunComponentType == null) return 0L;
        if (entityRef == null || !entityRef.isValid()) return 0L;
        StunComponent comp = store.getComponent(entityRef, stunComponentType);
        if (comp == null) return 0L;
        float remaining = comp.getTimeRemaining();
        return remaining <= 0f ? 0L : (long) (remaining * 1000f);
    }

    public static ComponentType<EntityStore, StunComponent> getStunComponentType() {
        return stunComponentType;
    }

    private static void applyEffect(Ref<EntityStore> entityRef, Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer, float duration, boolean isStunned) {
        if (entityRef == null || !entityRef.isValid()) return;

        EffectControllerComponent effectController = store.getComponent(entityRef,
                EffectControllerComponent.getComponentType());
        if (effectController == null) return;

        String effectName = isStunned ? StunConstants.STUN_EFFECT_ASSET : StunConstants.STAGGER_EFFECT_ASSET;
        EntityEffect effect = EffectUtil.getCachedEffect(effectName);
        if (effect == null) return;

        try {
            effectController.addEffect(entityRef, effect, 0.5f, OverlapBehavior.OVERWRITE, commandBuffer);
        } catch (Exception e) {
            LOGGER.atWarning().log("Exception while adding stun effect: " + e.getMessage());
        }
    }

    private static void setInteractionLock(Ref<EntityStore> entityRef, CommandBuffer<EntityStore> commandBuffer,
            float duration, boolean forceCancel) {
        if (entityRef == null || !entityRef.isValid()) return;

        if (forceCancel && duration > 0) {
            clearCombatActionEvaluator(entityRef, commandBuffer, duration);
        }

        InteractionManager interactionManager = commandBuffer.getComponent(entityRef,
                InteractionModule.get().getInteractionManagerComponent());
        if (interactionManager == null) return;

        try {
            if (forceCancel && duration > 0) {
                interactionManager.clear();
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Exception in setInteractionLock: " + e.getMessage());
        }
    }

    private static void clearCombatActionEvaluator(Ref<EntityStore> entityRef,
            CommandBuffer<EntityStore> commandBuffer, float stunDuration) {
        if (entityRef == null || !entityRef.isValid()) return;

        try {
            CombatActionEvaluator cae = commandBuffer.getComponent(entityRef,
                    CombatActionEvaluator.getComponentType());
            if (cae != null) {
                cae.completeCurrentAction(true, true);
                cae.clearPrimaryTarget();
            }

            ComponentType<EntityStore, NPCEntity> npcComponentType = NPCEntity.getComponentType();
            if (npcComponentType != null) {
                NPCEntity npcEntity = commandBuffer.getComponent(entityRef, npcComponentType);
                if (npcEntity != null) {
                    CombatSupport combatSupport = CombatSupport.get(entityRef, commandBuffer);
                    if (combatSupport != null) {
                        combatSupport.setExecutingAttack(null, false, stunDuration);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Exception clearing CAE/CombatSupport: " + e.getMessage());
        }
    }

    private static void resetCombatSupport(Ref<EntityStore> entityRef, Store<EntityStore> store) {
        if (entityRef == null || !entityRef.isValid()) return;

        try {
            ComponentType<EntityStore, NPCEntity> npcComponentType = NPCEntity.getComponentType();
            if (npcComponentType != null) {
                NPCEntity npcEntity = store.getComponent(entityRef, npcComponentType);
                if (npcEntity != null) {
                    CombatSupport combatSupport = CombatSupport.get(entityRef, store);
                    if (combatSupport != null) {
                        combatSupport.setExecutingAttack(null, false, 0.0);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Exception resetting CombatSupport: " + e.getMessage());
        }
    }
}
