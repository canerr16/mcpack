package com.swarm.master;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.swarm.SwarmConfig;
import com.swarm.SwarmMod;
import com.swarm.net.Protocol;
import com.swarm.net.SwarmClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.json.JSONObject;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Master rolü:
 *  - Seçim aracı (varsayılan tahta balta) ile sol tık = Köşe1, sağ tık = Köşe2.
 *  - /swarm start smelter -> seçili alanı sunucuya gönderir.
 *  - Sunucudan swarm:status / need_material bildirimlerini oyuncuya yansıtır.
 */
public class MasterController {
    private final SwarmConfig cfg;
    private final SwarmClient client;

    private BlockPos corner1;
    private BlockPos corner2;
    private Identifier selectionItemId;

    public MasterController(SwarmConfig cfg, SwarmClient client) {
        this.cfg = cfg;
        this.client = client;
    }

    public void init() {
        selectionItemId = Identifier.tryParse(cfg.selectionItem);
        client.connect("master");
        registerServerEvents();
        registerSelection();
        registerCommands();
    }

    private void registerServerEvents() {
        client.on(Protocol.SWARM_STATUS, args -> {
            if (args.length > 0) SwarmMod.LOGGER.info("[master] status: {}", args[0]);
        });
        client.on(Protocol.SWARM_NEED_MATERIAL, args -> {
            if (args.length == 0) return;
            JSONObject o = (JSONObject) args[0];
            String item = o.optString("item");
            int count = o.optInt("count");
            String name = o.optString("mcName");
            msg(Formatting.GOLD, "WORKER " + name + " malzeme bekliyor: "
                    + item + " x" + count);
        });
        client.on(Protocol.SWARM_COMPLETE, a -> msg(Formatting.GREEN,
                "Swarm inşaatı TAMAMLANDI."));
        client.on(Protocol.SWARM_WORKER_ERROR, args -> {
            if (args.length == 0) return;
            JSONObject o = (JSONObject) args[0];
            msg(Formatting.RED, "Worker hata (" + o.optString("mcName") + "): "
                    + o.optString("message"));
        });
        client.on(Protocol.SWARM_WARN, args -> {
            if (args.length == 0) return;
            msg(Formatting.YELLOW, ((JSONObject) args[0]).optString("message"));
        });
    }

    /** Sol tık = Köşe1, sağ tık = Köşe2 (seçim aracı eldeyken). */
    private void registerSelection() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!holdingSelectionItem(player)) return ActionResult.PASS;
            corner1 = pos.toImmutable();
            msg(Formatting.AQUA, "Köşe1 (toplayıcı ucu): " + fmt(corner1));
            return ActionResult.SUCCESS; // bloğu kırma
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!holdingSelectionItem(player)) return ActionResult.PASS;
            if (!(hitResult instanceof BlockHitResult bhr)) return ActionResult.PASS;
            corner2 = bhr.getBlockPos().toImmutable();
            msg(Formatting.AQUA, "Köşe2: " + fmt(corner2));
            return ActionResult.SUCCESS; // blok kullanma/koyma iptal
        });
    }

    private boolean holdingSelectionItem(net.minecraft.entity.player.PlayerEntity player) {
        var stack = player.getMainHandStack();
        if (selectionItemId == null) return stack.isOf(Items.WOODEN_AXE);
        Identifier held = Registries.ITEM.getId(stack.getItem());
        return held.equals(selectionItemId);
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
            dispatcher.register(literal("swarm")
                .then(literal("pos1").executes(c -> { printCorners(c.getSource()); return 1; }))
                .then(literal("pos2").executes(c -> { printCorners(c.getSource()); return 1; }))
                .then(literal("status").executes(c -> {
                    client.emit("swarm:request_status");
                    return 1;
                }))
                .then(literal("stop").executes(c -> {
                    client.emit(Protocol.SWARM_STOP);
                    c.getSource().sendFeedback(Text.literal("Swarm durduruldu."));
                    return 1;
                }))
                .then(literal("start")
                    .then(argument("structure", StringArgumentType.word())
                        .executes(c -> start(c.getSource(),
                                StringArgumentType.getString(c, "structure"))))
                    .executes(c -> start(c.getSource(), "smelter")))
            ));
    }

    private int start(FabricClientCommandSource src, String structure) {
        if (corner1 == null || corner2 == null) {
            src.sendError(Text.literal("Önce iki köşeyi seç (sol/sağ tık)."));
            return 0;
        }
        if (!client.isConnected()) {
            src.sendError(Text.literal("Sunucuya bağlı değil."));
            return 0;
        }
        JSONObject payload = new JSONObject();
        payload.put("structure", structure);
        payload.put("corner1", posJson(corner1));
        payload.put("corner2", posJson(corner2));
        client.emit(Protocol.SWARM_START, payload);
        src.sendFeedback(Text.literal("swarm:start gönderildi (" + structure + ") "
                + fmt(corner1) + " -> " + fmt(corner2)));
        return 1;
    }

    private void printCorners(FabricClientCommandSource src) {
        src.sendFeedback(Text.literal("Köşe1=" + (corner1 == null ? "-" : fmt(corner1))
                + "  Köşe2=" + (corner2 == null ? "-" : fmt(corner2))));
    }

    private static JSONObject posJson(BlockPos p) {
        JSONObject o = new JSONObject();
        o.put("x", p.getX());
        o.put("y", p.getY());
        o.put("z", p.getZ());
        return o;
    }

    private static String fmt(BlockPos p) {
        return "[" + p.getX() + ", " + p.getY() + ", " + p.getZ() + "]";
    }

    private void msg(Formatting color, String text) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("[Swarm] " + text).formatted(color), false);
        }
        SwarmMod.LOGGER.info("[master] {}", text);
    }
}
