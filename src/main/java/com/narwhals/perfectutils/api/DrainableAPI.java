package com.narwhals.perfectutils.api;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Marker interface for any Perfect Utils API singleton that buffers requests
 * off-thread and drains them once per world tick. Implemented by
 * {@link StunMobAPI}, {@link AggroAPI}, and any future API that needs
 * deferred {@link CommandBuffer} access.
 *
 * <p>The drain is wired by registering one
 * {@link com.narwhals.perfectutils.system.QueueDrainSystem} per
 * {@code DrainableAPI}, keyed off the singleton accessor so the system can
 * read the latest instance even if the API singleton is re-initialized.
 */
public interface DrainableAPI {

    /**
     * Drain queued requests against a fresh {@link CommandBuffer}. Called
     * exactly once per world tick from {@code QueueDrainSystem.tick}.
     */
    void drainPending(@Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, long nowMs);
}
