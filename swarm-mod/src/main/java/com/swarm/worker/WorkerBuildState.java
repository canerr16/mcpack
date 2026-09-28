package com.swarm.worker;

/**
 * Worker'ın "inşa halinde" olup olmadığını taşıyan global bayrak.
 * Mixin (SneakPlaceMixin) bunu okuyup, inşa sırasında blok yerleştirmenin
 * konteyner GUI'si açmasını engeller — karakteri fiilen ÇÖMELTMEDEN.
 * Böylece hareket yavaşlamaz, kenardan düşme/takılma riski artmaz (Grim dostu).
 */
public final class WorkerBuildState {
    private WorkerBuildState() {}

    private static volatile boolean building = false;

    public static boolean isBuilding() {
        return building;
    }

    public static void setBuilding(boolean value) {
        building = value;
    }
}
