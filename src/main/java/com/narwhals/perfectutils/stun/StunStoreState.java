package com.narwhals.perfectutils.stun;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * The stun module's bookkeeping for one world, kept as a store resource so every world's thread
 * reads and writes only its own copy: the time of the world's last queue drain (one drain per tick,
 * however many players the drain system visits) and the entities given a stun component this tick
 * (several requests for one entity in one tick add the component once).
 *
 * <p>Not saved with the world: it is registered with a supplier and no codec.
 */
public class StunStoreState implements Resource<EntityStore> {

    private long lastDrainMs = -1L;
    private long additionsTickMs = -1L;
    private final Set<Ref<EntityStore>> additionsThisTick = new HashSet<>();

    public StunStoreState() {}

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

    /**
     * Records that {@code entityRef} gets a stun component this tick.
     *
     * @return {@code true} if it was not recorded yet this tick, {@code false} if it already was
     */
    public boolean markAddition(@Nonnull Ref<EntityStore> entityRef, long nowMs) {
        if (nowMs != additionsTickMs) {
            additionsThisTick.clear();
            additionsTickMs = nowMs;
        }
        return additionsThisTick.add(entityRef);
    }

    /** A fresh state: the bookkeeping is per tick, so a copy carries none of it. */
    @Nonnull
    @Override
    public Resource<EntityStore> clone() {
        return new StunStoreState();
    }
}
