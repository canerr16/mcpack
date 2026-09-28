package com.swarm;

import com.swarm.master.MasterController;
import com.swarm.net.SwarmClient;
import com.swarm.worker.WorkerController;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Swarm Smelter — client mod giriş noktası.
 * config/swarm.json içindeki "role" alanına göre Master ya da Worker davranır.
 * Aynı jar 5 instance'ta çalışır (1 master + 4 worker).
 */
public class SwarmMod implements ClientModInitializer {
    public static final String MOD_ID = "swarm_mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static SwarmConfig config;
    public static SwarmClient client;

    @Override
    public void onInitializeClient() {
        config = SwarmConfig.load();
        client = new SwarmClient(config);

        LOGGER.info("[swarm] rol: {} sunucu: {}", config.role, config.serverUrl);

        if (config.isMaster()) {
            new MasterController(config, client).init();
        } else {
            new WorkerController(config, client).init();
        }
    }
}
