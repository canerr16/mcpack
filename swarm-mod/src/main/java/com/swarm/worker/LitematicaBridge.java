package com.swarm.worker;

import com.swarm.SwarmMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;

/**
 * Litematica ile köprü: base64 .litematic'i diske yazar, yükler, verilen origin +
 * rotasyonda yerleştirir ve seçili placement yapar. Sonra Baritone 'litematica'
 * komutu bu seçili placement'ı inşa eder.
 *
 * Litematica API imzaları sürüme göre oynak olduğundan yansıma (reflection) ile
 * bağlanıyoruz: eksik/oynamış metod olursa mod çökmez, log basar. Oyun-içi testte
 * (Modül 3) bu dikişler pinlenen litematica_version'a göre kesinleşir.
 */
public final class LitematicaBridge {
    private LitematicaBridge() {}

    /** base64 gzip .litematic içeriğini schematics klasörüne yazar, dosyayı döner. */
    public static File saveSchematic(String base64, String name) throws Exception {
        MinecraftClient mc = MinecraftClient.getInstance();
        File dir = new File(mc.runDirectory, "schematics");
        dir.mkdirs();
        File file = new File(dir, name + ".litematic");
        byte[] bytes = Base64.getDecoder().decode(base64);
        Files.write(file.toPath(), bytes);
        SwarmMod.LOGGER.info("[worker] şema yazıldı: {} ({} bayt)", file, bytes.length);
        return file;
    }

    /**
     * Şemayı yükle, origin + rotationY ile yerleştir, seç.
     * @return başarılıysa true.
     */
    public static boolean loadPlaceAndSelect(File file, BlockPos origin, int rotationY) {
        try {
            Object schematic = createSchematicFromFile(file);
            if (schematic == null) {
                SwarmMod.LOGGER.error("[worker] şema yüklenemedi: {}", file);
                return false;
            }
            Object placement = createPlacement(schematic, origin, file.getName());
            if (placement == null) return false;

            applyRotation(placement, rotationY);
            addAndSelect(placement);
            SwarmMod.LOGGER.info("[worker] placement hazır origin={} rot={}",
                    origin, rotationY);
            return true;
        } catch (Throwable t) {
            SwarmMod.LOGGER.error("[worker] Litematica yerleştirme hatası", t);
            return false;
        }
    }

    private static Object createSchematicFromFile(File file) throws Exception {
        Class<?> cls = Class.forName("fi.dy.masa.litematica.schematic.LitematicaSchematic");
        // createFromFile(File dir, String fileName) veya (File file) imzası denenir.
        try {
            var m = cls.getMethod("createFromFile", File.class, String.class);
            String n = file.getName();
            if (n.endsWith(".litematic")) n = n.substring(0, n.length() - ".litematic".length());
            return m.invoke(null, file.getParentFile(), n);
        } catch (NoSuchMethodException ignore) {
            var m = cls.getMethod("createFromFile", File.class);
            return m.invoke(null, file);
        }
    }

    private static Object createPlacement(Object schematic, BlockPos origin, String name)
            throws Exception {
        Class<?> cls = Class.forName(
                "fi.dy.masa.litematica.schematic.placement.SchematicPlacement");
        // createFor(schematic, BlockPos origin, String name, boolean enabled, boolean mods)
        for (var m : cls.getMethods()) {
            if (m.getName().equals("createFor")
                    && m.getParameterCount() == 5) {
                return m.invoke(null, schematic, origin, name, true, true);
            }
        }
        // createFor(schematic, origin, name, enabled) alternatifi
        for (var m : cls.getMethods()) {
            if (m.getName().equals("createFor") && m.getParameterCount() == 4) {
                return m.invoke(null, schematic, origin, name, true);
            }
        }
        SwarmMod.LOGGER.error("[worker] SchematicPlacement.createFor bulunamadı");
        return null;
    }

    private static void applyRotation(Object placement, int rotationY) {
        BlockRotation rot = switch (((rotationY % 360) + 360) % 360) {
            case 90 -> BlockRotation.CLOCKWISE_90;
            case 180 -> BlockRotation.CLOCKWISE_180;
            case 270 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
        if (rot == BlockRotation.NONE) return;
        try {
            for (var m : placement.getClass().getMethods()) {
                if (m.getName().equals("setRotation")
                        && m.getParameterCount() >= 1
                        && m.getParameterTypes()[0] == BlockRotation.class) {
                    Object[] args = new Object[m.getParameterCount()];
                    args[0] = rot;
                    for (int i = 1; i < args.length; i++) args[i] = false; // rebuild flag
                    m.invoke(placement, args);
                    return;
                }
            }
            SwarmMod.LOGGER.warn("[worker] setRotation metodu bulunamadı, rotasyon atlandı");
        } catch (Throwable t) {
            SwarmMod.LOGGER.warn("[worker] rotasyon uygulanamadı", t);
        }
    }

    private static void addAndSelect(Object placement) throws Exception {
        Class<?> dataMgr = Class.forName("fi.dy.masa.litematica.data.DataManager");
        Object mgr = dataMgr.getMethod("getSchematicPlacementManager").invoke(null);
        // addSchematicPlacement(placement, boolean)
        try {
            mgr.getClass().getMethod("addSchematicPlacement",
                    placement.getClass().getSuperclass() != null
                            ? Class.forName("fi.dy.masa.litematica.schematic.placement.SchematicPlacement")
                            : placement.getClass(),
                    boolean.class).invoke(mgr, placement, true);
        } catch (NoSuchMethodException e) {
            for (var m : mgr.getClass().getMethods()) {
                if (m.getName().equals("addSchematicPlacement")) {
                    Object[] args = new Object[m.getParameterCount()];
                    args[0] = placement;
                    for (int i = 1; i < args.length; i++) args[i] = true;
                    m.invoke(mgr, args);
                    break;
                }
            }
        }
        // setSelectedSchematicPlacement(placement)
        try {
            mgr.getClass().getMethod("setSelectedSchematicPlacement",
                    Class.forName("fi.dy.masa.litematica.schematic.placement.SchematicPlacement"))
                    .invoke(mgr, placement);
        } catch (Throwable t) {
            SwarmMod.LOGGER.warn("[worker] setSelectedSchematicPlacement atlandı", t);
        }
    }

    public static void clearAll() {
        try {
            Class<?> dataMgr = Class.forName("fi.dy.masa.litematica.data.DataManager");
            Object mgr = dataMgr.getMethod("getSchematicPlacementManager").invoke(null);
            for (var m : mgr.getClass().getMethods()) {
                if (m.getName().equals("clear") || m.getName().equals("removeAllPlacements")) {
                    m.invoke(mgr);
                    return;
                }
            }
        } catch (Throwable t) {
            SwarmMod.LOGGER.debug("[worker] placement temizleme atlandı", t);
        }
    }
}
