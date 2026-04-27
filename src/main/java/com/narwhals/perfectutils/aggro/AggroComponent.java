package com.narwhals.perfectutils.aggro;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Per-player ECS marker for an active aggro-suppression / taunt window.
 * Drives {@link AggroTickSystem}, which re-applies targeting suppression
 * each tick until the deadline elapses, then removes the component.
 *
 * <p>{@link Mode#ONE_SHOT} requests are NOT persisted as a component — they
 * fire once during {@link com.narwhals.perfectutils.api.AggroAPI} drain and
 * exit. Only sustained-ignore and taunt windows live here.
 */
public class AggroComponent implements Component<EntityStore> {

    public enum Mode {
        ONE_SHOT,
        SUSTAINED_IGNORE,
        TAUNT
    }

    private Mode mode;
    private long deadlineMs;
    private double radius;

    public AggroComponent() {
        this(Mode.SUSTAINED_IGNORE, 0L, 0.0);
    }

    public AggroComponent(Mode mode, long deadlineMs, double radius) {
        this.mode = mode;
        this.deadlineMs = deadlineMs;
        this.radius = radius;
    }

    public AggroComponent(AggroComponent other) {
        this.mode = other.mode;
        this.deadlineMs = other.deadlineMs;
        this.radius = other.radius;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public long getDeadlineMs() {
        return deadlineMs;
    }

    public void setDeadlineMs(long deadlineMs) {
        this.deadlineMs = deadlineMs;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    @Nonnull
    @Override
    public AggroComponent clone() {
        return new AggroComponent(this);
    }
}
