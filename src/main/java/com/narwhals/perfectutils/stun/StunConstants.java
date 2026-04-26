package com.narwhals.perfectutils.stun;

public final class StunConstants {

    private StunConstants() {}

    /** Stun ends when remaining time falls below this threshold (avoids float drift). */
    public static final float STUN_END_THRESHOLD_SECONDS = 0.025f;

    /** Delay (ms) after a full stun ends before NPC combat AI is restored. */
    public static final long STUN_WAKE_DELAY_MS = 250;

    /** Delay (ms) after a stagger ends before NPC combat AI is restored. */
    public static final long STAGGER_WAKE_DELAY_MS = 100;

    /** Asset id of the entity effect applied to a fully stunned entity. */
    public static final String STUN_EFFECT_ASSET = "DU_Entity_Stunned";

    /** Asset id of the entity effect applied to a staggered entity. */
    public static final String STAGGER_EFFECT_ASSET = "DU_Entity_Staggered";
}
