package com.narwhals.perfectutils.stun;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class StunComponent implements Component<EntityStore> {

    public enum StunType {
        STUN,
        STAGGER
    }

    private float timeRemaining;
    private final StunType stunType;
    private boolean wakingUp;

    public StunComponent() {
        this(0.0f, StunType.STAGGER);
    }

    public StunComponent(float duration, StunType type) {
        this.timeRemaining = duration;
        this.stunType = type;
        this.wakingUp = false;
    }

    public StunComponent(StunComponent other) {
        this.timeRemaining = other.timeRemaining;
        this.stunType = other.stunType;
        this.wakingUp = other.wakingUp;
    }

    public float getTimeRemaining() {
        return timeRemaining;
    }

    public void setTimeRemaining(float time) {
        this.timeRemaining = time;
    }

    public StunType getStunType() {
        return stunType;
    }

    public boolean isFullStun() {
        return stunType == StunType.STUN;
    }

    public boolean isStagger() {
        return stunType == StunType.STAGGER;
    }

    public boolean isWakingUp() {
        return wakingUp;
    }

    public void setWakingUp(boolean wakingUp) {
        this.wakingUp = wakingUp;
    }

    @Nonnull
    @Override
    public StunComponent clone() {
        return new StunComponent(this);
    }
}
