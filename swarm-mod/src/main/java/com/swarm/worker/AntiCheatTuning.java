package com.swarm.worker;

import com.swarm.SwarmConfig;
import com.swarm.SwarmMod;

/**
 * Grim anti-cheat uyumlu Baritone ayarları.
 * Kural (kullanıcı teyidi): "çok hızlı + uzak reach + air-place" olmadıkça sorun yok.
 *
 * NOT: Baritone ayar alan adları sürüme göre değişebilir. Yansıma (reflection) ile
 * güvenli şekilde set ediyoruz; olmayan ayarı sessizce atlıyoruz. Böylece farklı
 * Baritone fabric build'lerinde derleme/çalışma kırılmaz.
 */
public final class AntiCheatTuning {
    private AntiCheatTuning() {}

    public static void apply(SwarmConfig cfg) {
        Object settings = getBaritoneSettings();
        if (settings == null) {
            SwarmMod.LOGGER.warn("[worker] Baritone settings bulunamadı, tuning atlandı.");
            return;
        }

        // Anti-cheat uyum modu: Baritone rotasyon/tıklama paternini insana yaklaştırır.
        setBool(settings, "antiCheatCompatibility", true);
        // Vücut yerine sadece kafa/serbest bakış -> 360 dönme yok.
        setBool(settings, "freeLook", true);
        setBool(settings, "blockFreeLook", false);
        // Bakışı yumuşat (ani snap yerine kademeli).
        setBool(settings, "smoothLook", true);
        // Envantere Baritone dokunmasın (biz yöneteceğiz).
        setBool(settings, "allowInventory", false);
        // Air-place / sketchy yerleştirme kapalı.
        setBool(settings, "allowPlacePistonOnBottom", false);
        // Reach sınırı — insanüstü uzaklıktan koyma.
        setDouble(settings, "blockReachDistance", cfg.maxReach);
        // Blok koyma cezası (yavaşlat) — çok hızlı seri koymayı kırar.
        setDouble(settings, "blockPlacementPenalty", 25.0);
        // Katman katman inşa: aşağıdan yukarı, düzenli ilerleme.
        setBool(settings, "buildInLayers", true);
        // Yerleştiremediğinde takılıp havayı yumruklamasın diye:
        setBool(settings, "buildIgnoreExisting", false);
        setBool(settings, "okIfAir", false);

        SwarmMod.LOGGER.info("[worker] Baritone anti-cheat tuning uygulandı "
                + "(reach={}, sneakPlace={}).", cfg.maxReach, cfg.sneakPlace);
    }

    private static Object getBaritoneSettings() {
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            Object settings = api.getMethod("getSettings").invoke(null);
            return settings;
        } catch (Throwable t) {
            return null;
        }
    }

    // Baritone Setting<T> alanları: settings.<name>.value = ...
    private static void setBool(Object settings, String field, boolean value) {
        setSetting(settings, field, value);
    }

    private static void setDouble(Object settings, String field, double value) {
        setSetting(settings, field, value);
    }

    private static void setSetting(Object settings, String field, Object value) {
        try {
            var f = settings.getClass().getField(field);
            Object setting = f.get(settings); // Setting<T>
            var valueField = setting.getClass().getField("value");
            valueField.set(setting, value);
        } catch (NoSuchFieldException e) {
            SwarmMod.LOGGER.debug("[worker] Baritone ayarı yok (atlandı): {}", field);
        } catch (Throwable t) {
            SwarmMod.LOGGER.debug("[worker] Baritone ayarı set edilemedi: {}", field, t);
        }
    }
}
