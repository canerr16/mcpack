package com.swarm.worker;

import com.swarm.SwarmMod;

/**
 * Baritone'a komut geçişi. Baritone API'sine yansıma ile bağlanır ki farklı fabric
 * build'lerinde derleme kırılmasın.
 *
 * Litematica yerleştirme seçiliyken `litematica` komutu, seçili placement'ı inşa eder.
 */
public final class BaritoneBridge {
    private BaritoneBridge() {}

    private static Object primaryBaritone() {
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            Object provider = api.getMethod("getProvider").invoke(null);
            return provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
        } catch (Throwable t) {
            SwarmMod.LOGGER.error("[worker] Baritone bulunamadı", t);
            return null;
        }
    }

    /** #litematica — seçili Litematica placement'ını inşa et. */
    public static boolean buildSelectedLitematica() {
        return execute("litematica");
    }

    public static boolean stop() {
        return execute("stop");
    }

    /** Baritone komut string'ini çalıştırır (prefix'siz). */
    public static boolean execute(String command) {
        Object baritone = primaryBaritone();
        if (baritone == null) return false;
        try {
            Object cmdManager = baritone.getClass().getMethod("getCommandManager").invoke(baritone);
            // ICommandManager.execute(String) -> boolean (sürüme göre değişebilir)
            var m = cmdManager.getClass().getMethod("execute", String.class);
            Object result = m.invoke(cmdManager, command);
            SwarmMod.LOGGER.info("[worker] baritone '{}' -> {}", command, result);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            SwarmMod.LOGGER.error("[worker] baritone komut hatası: {}", command, t);
            return false;
        }
    }

    /** Baritone o an aktif bir işlem (build/path) yürütüyor mu? */
    public static boolean isActive() {
        Object baritone = primaryBaritone();
        if (baritone == null) return false;
        try {
            Object pathing = baritone.getClass()
                    .getMethod("getPathingBehavior").invoke(baritone);
            Object active = pathing.getClass().getMethod("isPathing").invoke(pathing);
            return Boolean.TRUE.equals(active);
        } catch (Throwable t) {
            return false;
        }
    }
}
