package com.narwhals.perfectutils.aggro;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * The aggro module's bookkeeping for one world, kept as a store resource so every world's thread
 * reads and writes only its own copy: the time of the world's last queue drain (one drain per tick,
 * however many players the drain system visits).
 *
 * <p>Not saved with the world: it is registered with a supplier and no codec.
 */
public class AggroStoreState implements Resource<EntityStore> {

    private long lastDrainMs = -1L;

    public AggroStoreState() {}

    /**
     * Claims this tick's drain for the world.
     *
     * @return {@code true} the first time it is called with a given {@code nowMs}, {@code false} after
     */
    public boolean claimDrain(long nowMs) {
        if (nowMs == lastDrainMs) return false;
        lastDrainMs = nowMs;
        return true;
    }

    /** A fresh state: the bookkeeping is per tick, so a copy carries none of it. */
    @Nonnull
    @Override
    public Resource<EntityStore> clone() {
        return new AggroStoreState();
    }
}
