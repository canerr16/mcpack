package com.swarm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * config/swarm.json dosyasından okunan çalışma zamanı ayarları.
 * Her Minecraft instance'ı kendi config'iyle master ya da worker olur.
 *
 * Örnek (worker 2):
 * {
 *   "role": "worker",
 *   "serverUrl": "http://127.0.0.1:8765",
 *   "token": "change-me-swarm-token",
 *   "workerId": "2",
 *   "materialLowThreshold": 8,
 *   "resumeDelayMs": 5000
 * }
 */
public class SwarmConfig {
    public String role = "worker";          // "master" | "worker"
    public String serverUrl = "http://127.0.0.1:8765";
    public String token = "change-me-swarm-token";
    public String workerId = "1";           // worker için benzersiz
    public String selectionItem = "minecraft:wooden_axe"; // master alan seçim aracı

    // Envanterde bir malzeme bu adedin altına inince "malzeme bekliyor" tetiklenir.
    public int materialLowThreshold = 8;
    // Malzeme geldikten sonra devam etmeden önce beklenecek süre (ms).
    public int resumeDelayMs = 5000;

    // Anti-cheat (Grim) uyumu için Baritone ayarları.
    public boolean sneakPlace = true;       // huni/smoker koyarken shift basılı
    public boolean allowAirPlace = false;   // asla boşluğa blok koyma
    public double maxReach = 4.0;           // insan reach sınırı
    public int placeMinDelayMs = 120;       // bloklar arası min gecikme
    public int placeMaxJitterMs = 180;      // + rastgele jitter (insani ritim)

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static SwarmConfig load() {
        Path dir = FabricLoader.getInstance().getConfigDir();
        Path file = dir.resolve("swarm.json");
        try {
            if (Files.exists(file)) {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                SwarmConfig cfg = GSON.fromJson(json, SwarmConfig.class);
                if (cfg != null) return cfg;
            }
        } catch (IOException | RuntimeException e) {
            SwarmMod.LOGGER.error("swarm.json okunamadı, varsayılanlar kullanılıyor", e);
        }
        // Yoksa varsayılanı yaz.
        SwarmConfig def = new SwarmConfig();
        try {
            Files.createDirectories(dir);
            Files.writeString(file, GSON.toJson(def), StandardCharsets.UTF_8);
            SwarmMod.LOGGER.info("Varsayılan swarm.json yazıldı: {}", file);
        } catch (IOException e) {
            SwarmMod.LOGGER.error("swarm.json yazılamadı", e);
        }
        return def;
    }

    public boolean isMaster() {
        return "master".equalsIgnoreCase(role);
    }
}
