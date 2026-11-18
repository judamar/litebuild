package dev.litebuild;

/** Interruptores de World Properties que consultan los mixins (el mundo del jugador, no se guardan). */
public final class Rules {
    public static volatile boolean blockGravity = true;
    public static volatile boolean trampleFarmland = true;

    private Rules() {
    }
}
