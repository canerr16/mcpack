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

        // === BAKMA / DÖNME PROBLEMLERİNE KARŞI ===
        // Anti-cheat uyum modu: rotasyonları sunucuyla tutarlı gönderir. Grim'de
        // "client baktı sandı ama server aynı fikirde değil" desync'ini önler ->
        // "havaya bakma / yetişemediği yere koymaya çalışma" bug'ının asıl çaresi.
        setBool(settings, "antiCheatCompatibility", true);
        // Serbest bakış: vücut sabit, sadece kafa hedefe döner -> 360 vücut dönmesi yok.
        setBool(settings, "freeLook", true);
        setBool(settings, "blockFreeLook", false);
        // Bakışı yumuşat: ani snap yerine kademeli, insani kafa hareketi.
        setBool(settings, "smoothLook", true);
        // Rastgele mikro-bakış kapalı: hedefi ıskalamasın (hava yumruklama sebebi).
        setBool(settings, "randomLooking", false);
        setBool(settings, "randomLooking113", false);

        // === HAVAYI YUMRUKLAMA / TAKILMAYA KARŞI ===
        // Blok KIRMA tamamen kapalı: worker sadece koyar. Yanlış/boş bloğa vurma yok.
        // (İnşa alanı boş olmalı; engel varsa worker bekler, kör kör vurmaz.)
        setBool(settings, "allowBreak", false);
        // Boşluğa/asılı blok koyma kapalı: her blok komşu yüzeye dayanır.
        setBool(settings, "allowPlacePistonOnBottom", false);
        setBool(settings, "buildIgnoreExisting", false); // konmuş bloğu tekrar koymaya çalışma
        setBool(settings, "okIfAir", false);

        // === REACH / HIZ (Grim: çok hızlı + uzak = flag) ===
        // Vanilla blok reach ~4.5; bunu aşma. maxReach config'ten (varsayılan 4.5).
        setDouble(settings, "blockReachDistance", cfg.maxReach);
        // Yerleştirme cezası: seri hızlı koymayı kırar, insani ritim.
        setDouble(settings, "blockPlacementPenalty", 25.0);

        // === HAREKET / DÜŞME (yerine yetişememe hissini azaltır) ===
        setBool(settings, "buildInLayers", true);     // katman katman, düzenli
        setBool(settings, "assumeSafeWalk", true);    // kenardan düşme riskini azalt
        setBool(settings, "allowParkour", false);     // riskli atlayış yok
        setBool(settings, "allowInventory", false);   // envantere Baritone dokunmasın

        SwarmMod.LOGGER.info("[worker] Baritone tuning uygulandı "
                + "(reach={}, allowBreak=false, antiCheatCompat=true).", cfg.maxReach);
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
