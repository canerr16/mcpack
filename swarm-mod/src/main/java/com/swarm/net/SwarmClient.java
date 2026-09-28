package com.swarm.net;

import com.swarm.SwarmConfig;
import com.swarm.SwarmMod;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.json.JSONObject;

import java.net.URISyntaxException;

/**
 * socket.io tabanlı sunucu bağlantısı. Master ve Worker ortak kullanır.
 * auth ile role + token gönderilir (server io.use ile doğrular).
 */
public class SwarmClient {
    private final SwarmConfig cfg;
    private Socket socket;

    public SwarmClient(SwarmConfig cfg) {
        this.cfg = cfg;
    }

    public void connect(String role) {
        try {
            IO.Options opts = new IO.Options();
            opts.reconnection = true;
            opts.reconnectionDelay = 1000;
            opts.forceNew = true;
            opts.auth = new java.util.HashMap<>();
            opts.auth.put("token", cfg.token);
            opts.auth.put("role", role);

            socket = IO.socket(cfg.serverUrl, opts);
            socket.on(Socket.EVENT_CONNECT, a ->
                    SwarmMod.LOGGER.info("[swarm] bağlandı ({})", role));
            socket.on(Socket.EVENT_CONNECT_ERROR, a ->
                    SwarmMod.LOGGER.warn("[swarm] bağlantı hatası: {}",
                            a.length > 0 ? a[0] : "?"));
            socket.on(Socket.EVENT_DISCONNECT, a ->
                    SwarmMod.LOGGER.warn("[swarm] bağlantı koptu"));
            socket.connect();
        } catch (URISyntaxException e) {
            SwarmMod.LOGGER.error("[swarm] geçersiz serverUrl: {}", cfg.serverUrl, e);
        }
    }

    public void on(String event, Emitter.Listener listener) {
        if (socket != null) socket.on(event, listener);
    }

    public void emit(String event, JSONObject payload) {
        if (socket != null && socket.connected()) {
            socket.emit(event, payload);
        } else {
            SwarmMod.LOGGER.warn("[swarm] emit atlandı (bağlı değil): {}", event);
        }
    }

    public void emit(String event) {
        emit(event, new JSONObject());
    }

    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
            socket.close();
        }
    }
}
