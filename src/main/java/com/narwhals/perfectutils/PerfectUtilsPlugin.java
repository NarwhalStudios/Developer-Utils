package com.narwhals.perfectutils;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.narwhals.perfectutils.aggro.AggroComponent;
import com.narwhals.perfectutils.aggro.AggroQueueDrainSystem;
import com.narwhals.perfectutils.aggro.AggroTickSystem;
import com.narwhals.perfectutils.api.AggroAPI;
import com.narwhals.perfectutils.api.StunMobAPI;
import com.narwhals.perfectutils.stun.StunComponent;
import com.narwhals.perfectutils.stun.StunMobUtil;
import com.narwhals.perfectutils.stun.StunQueueDrainSystem;
import com.narwhals.perfectutils.stun.StunSystem;

/**
 * Umbrella plugin entry. Hosts every utility shipped from this repo:
 * <ul>
 * <li>Stun-mob primitive — ECS component/system + reflective
 * {@link StunMobAPI}</li>
 * <li>Aggro / taunt primitive — ECS component/system + reflective
 * {@link AggroAPI}</li>
 * </ul>
 *
 * <p>Each API has a dedicated queue-drain system. Hytale's ECS registry
 * keys systems by {@code Class}, so two instances of one generic drain
 * class would collide at registration time — keep them separate.
 */
public class PerfectUtilsPlugin extends JavaPlugin {

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static volatile PerfectUtilsPlugin instance;

    private ComponentType<EntityStore, StunComponent> stunComponentType;
    private ComponentType<EntityStore, AggroComponent> aggroComponentType;

    public PerfectUtilsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static PerfectUtilsPlugin get() {
        return instance;
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Perfect Utils setup");

        initComponents();
        initSystems();

        StunMobAPI.init(this);
        AggroAPI.init(this);
        AggroAPI.get().setAggroComponentType(aggroComponentType);

        LOGGER.atInfo().log("Perfect Utils setup complete");
    }

    @Override
    protected void shutdown() {
        StunMobAPI.clearInstance();
        AggroAPI.clearInstance();
        instance = null;
        LOGGER.atInfo().log("Perfect Utils shutdown");
    }

    public ComponentType<EntityStore, StunComponent> getStunComponentType() {
        return stunComponentType;
    }

    public ComponentType<EntityStore, AggroComponent> getAggroComponentType() {
        return aggroComponentType;
    }

    private void initComponents() {
        ComponentRegistryProxy<EntityStore> registry = this.getEntityStoreRegistry();
        stunComponentType = registry.registerComponent(StunComponent.class, StunComponent::new);
        aggroComponentType = registry.registerComponent(AggroComponent.class, AggroComponent::new);
        StunMobUtil.init(stunComponentType);
    }

    private void initSystems() {
        ComponentRegistryProxy<EntityStore> registry = this.getEntityStoreRegistry();
        registry.registerSystem(new StunSystem(stunComponentType));
        registry.registerSystem(new AggroTickSystem(aggroComponentType));
        registry.registerSystem(new StunQueueDrainSystem());
        registry.registerSystem(new AggroQueueDrainSystem());
    }
}
