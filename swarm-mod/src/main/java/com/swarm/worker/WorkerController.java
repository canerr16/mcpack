package com.swarm.worker;

import com.swarm.SwarmConfig;
import com.swarm.SwarmMod;
import com.swarm.net.Protocol;
import com.swarm.net.SwarmClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Worker rolü:
 *  - Sunucuya bağlanır, worker:hello yollar.
 *  - task:assign gelince .litematic'i yükleyip Baritone ile inşayı başlatır.
 *  - İnşa boyunca SNEAK basılı tutar (huni/smoker koyarken GUI açılmasın).
 *  - Bir malzeme eşiğin altına inince durur, need_material bildirir; malzeme
 *    gelince resumeDelayMs sonra devam eder.
 *  - Progress'i periyodik yollar; disconnect'te sunucu kaldığı yeri hatırlar.
 */
public class WorkerController {
    private final SwarmConfig cfg;
    private final SwarmClient client;

    private enum State { IDLE, BUILDING, WAITING_MATERIAL, DONE }
    private volatile State state = State.IDLE;

    // Atanan toplam malzeme (item id -> adet).
    private final Map<Identifier, Integer> assigned = new HashMap<>();
    private int assignedTotal = 0;
    private static final int PER_SLICE = 14;

    private long lastProgressSend = 0;
    private long materialReadySince = 0;  // malzeme yeniden yeterli olduğu an
    private Identifier waitingItem = null;

    public WorkerController(SwarmConfig cfg, SwarmClient client) {
        this.cfg = cfg;
        this.client = client;
    }

    public void init() {
        client.connect("worker");
        client.on(io.socket.client.Socket.EVENT_CONNECT, a -> sendHello());
        registerServerEvents();
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void sendHello() {
        MinecraftClient mc = MinecraftClient.getInstance();
        JSONObject o = new JSONObject();
        o.put("workerId", cfg.workerId);
        o.put("mcName", mc.player != null ? mc.player.getGameProfile().getName() : "worker");
        client.emit(Protocol.WORKER_HELLO, o);
        SwarmMod.LOGGER.info("[worker] hello gönderildi id={}", cfg.workerId);
    }

    private void registerServerEvents() {
        client.on(Protocol.TASK_ASSIGN, args -> {
            if (args.length == 0) return;
            handleAssign((JSONObject) args[0]);
        });
        client.on(Protocol.TASK_RESUME, args -> {
            SwarmMod.LOGGER.info("[worker] resume alındı");
            resumeBuild();
        });
        client.on(Protocol.TASK_ABORT, args -> {
            SwarmMod.LOGGER.info("[worker] abort alındı");
            BaritoneBridge.stop();
            LitematicaBridge.clearAll();
            state = State.IDLE;
        });
    }

    private void handleAssign(JSONObject task) {
        try {
            AntiCheatTuning.apply(cfg);

            String base64 = task.getString("litematic");
            int segmentId = task.getInt("segmentId");
            JSONObject o = task.getJSONObject("origin");
            BlockPos origin = new BlockPos(o.getInt("x"), o.getInt("y"), o.getInt("z"));
            int rotationY = task.optInt("rotationY", 0);

            // Malzeme listesini kaydet.
            assigned.clear();
            assignedTotal = 0;
            JSONArray ml = task.optJSONArray("materialList");
            if (ml != null) {
                for (int i = 0; i < ml.length(); i++) {
                    JSONObject m = ml.getJSONObject(i);
                    Identifier id = Identifier.tryParse(m.getString("item"));
                    int c = m.getInt("count");
                    if (id != null) assigned.merge(id, c, Integer::sum);
                    assignedTotal += c;
                }
            }

            File file = LitematicaBridge.saveSchematic(base64, "swarm_seg_" + segmentId);
            boolean ok = LitematicaBridge.loadPlaceAndSelect(file, origin, rotationY);
            if (!ok) {
                sendError("Litematica placement başarısız (segment " + segmentId + ")");
                return;
            }
            state = State.BUILDING;
            resumeBuild();
            SwarmMod.LOGGER.info("[worker] segment {} inşaya başladı origin={}",
                    segmentId, origin);
        } catch (Throwable t) {
            SwarmMod.LOGGER.error("[worker] assign işlenemedi", t);
            sendError("assign hatası: " + t.getMessage());
        }
    }

    private void resumeBuild() {
        // Baritone seçili litematica placement'ı inşa eder. Zaten konmuş blokları
        // atlar (buildIgnoreExisting=false), böylece kaldığı yerden devam eder.
        state = State.BUILDING;
        waitingItem = null;
        BaritoneBridge.buildSelectedLitematica();
    }

    private void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        // İnşa sırasında SNEAK basılı: huni/smoker koyarken arayüz açılmaz.
        boolean building = (state == State.BUILDING);
        if (cfg.sneakPlace) {
            mc.options.sneakKey.setPressed(building);
        }

        if (state == State.BUILDING) {
            checkMaterials(mc);
            maybeSendProgress(mc);
            // Baritone iş bitince aktif değildir -> done say.
            if (assignedTotal > 0 && placedCount(mc) >= assignedTotal - 1
                    && !BaritoneBridge.isActive()) {
                markDone();
            }
        } else if (state == State.WAITING_MATERIAL) {
            handleWaiting(mc);
        }
    }

    private void checkMaterials(MinecraftClient mc) {
        for (var e : assigned.entrySet()) {
            int have = countItem(mc, e.getKey());
            if (have < cfg.materialLowThreshold) {
                // Malzeme az -> dur ve bildir.
                waitingItem = e.getKey();
                state = State.WAITING_MATERIAL;
                materialReadySince = 0;
                BaritoneBridge.stop();
                if (cfg.sneakPlace) mc.options.sneakKey.setPressed(false);

                JSONObject o = new JSONObject();
                o.put("item", e.getKey().toString());
                o.put("count", Math.max(e.getValue(), 64));
                client.emit(Protocol.WORKER_NEED_MATERIAL, o);
                SwarmMod.LOGGER.info("[worker] malzeme bekleniyor: {} (var {})",
                        e.getKey(), have);
                return;
            }
        }
    }

    private void handleWaiting(MinecraftClient mc) {
        if (waitingItem == null) { resumeBuild(); return; }
        int have = countItem(mc, waitingItem);
        if (have >= cfg.materialLowThreshold) {
            long now = System.currentTimeMillis();
            if (materialReadySince == 0) {
                materialReadySince = now; // malzeme yeni geldi, sayaç başlat
            } else if (now - materialReadySince >= cfg.resumeDelayMs) {
                // Yeterli süre geçti -> devam.
                client.emit(Protocol.WORKER_MATERIAL_OK);
                SwarmMod.LOGGER.info("[worker] malzeme geldi, devam ediliyor");
                resumeBuild();
            }
        } else {
            materialReadySince = 0; // hâlâ yetersiz
        }
    }

    private void maybeSendProgress(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        if (now - lastProgressSend < 2000) return;
        lastProgressSend = now;
        int placed = placedCount(mc);
        JSONObject o = new JSONObject();
        o.put("placedCount", placed);
        o.put("placedZ", placed / PER_SLICE);
        client.emit(Protocol.WORKER_PROGRESS, o);
    }

    private void markDone() {
        state = State.DONE;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (cfg.sneakPlace) mc.options.sneakKey.setPressed(false);
        client.emit(Protocol.WORKER_DONE);
        SwarmMod.LOGGER.info("[worker] segment tamamlandı");
    }

    private void sendError(String message) {
        JSONObject o = new JSONObject();
        o.put("message", message);
        client.emit(Protocol.WORKER_ERROR, o);
        state = State.IDLE;
    }

    /** Kabaca yerleştirilen blok = atanan toplam - envanterde kalan atanan malzeme. */
    private int placedCount(MinecraftClient mc) {
        int remaining = 0;
        for (var e : assigned.entrySet()) {
            remaining += Math.min(countItem(mc, e.getKey()), e.getValue());
        }
        int placed = assignedTotal - remaining;
        return Math.max(0, placed);
    }

    private int countItem(MinecraftClient mc, Identifier id) {
        if (mc.player == null) return 0;
        int total = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && Registries.ITEM.getId(s.getItem()).equals(id)) {
                total += s.getCount();
            }
        }
        return total;
    }
}
