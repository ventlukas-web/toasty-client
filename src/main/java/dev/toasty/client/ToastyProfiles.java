package dev.toasty.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/** Profile: speichert ALLE Einstellungen (inkl. HUD-Positionen, Farben, Notizen, Wegpunkte, Tasten) unter einem Namen. */
public final class ToastyProfiles {
    private ToastyProfiles() {}

    private static Path dir() {
        Path d = FabricLoader.getInstance().getConfigDir().resolve("toastyclient-profiles");
        try { Files.createDirectories(d); } catch (Exception ignored) {}
        return d;
    }

    public static String clean(String n) {
        String s = n == null ? "" : n.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        return s.length() > 30 ? s.substring(0, 30) : s;
    }

    public static List<String> list() {
        List<String> r = new ArrayList<>();
        try (Stream<Path> st = Files.list(dir())) {
            st.forEach(p -> {
                String f = p.getFileName().toString();
                if (f.endsWith(".json")) r.add(f.substring(0, f.length() - 5));
            });
        } catch (Exception ignored) {}
        Collections.sort(r, String.CASE_INSENSITIVE_ORDER);
        return r;
    }

    public static boolean save(String name) {
        String n = clean(name);
        if (n.isEmpty()) return false;
        ToastyConfig c = ToastyConfig.get();
        c.keys.clear();
        for (KeyBinding k : ToastyKeysScreen.binds()) if (k != null) c.keys.put(k.getId(), k.getBoundKeyTranslationKey());
        try {
            Files.writeString(dir().resolve(n + ".json"), c.toJson());
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean load(String name) {
        String n = clean(name);
        Path f = dir().resolve(n + ".json");
        if (n.isEmpty() || !Files.exists(f)) return false;
        try {
            ToastyConfig nc = ToastyConfig.parse(Files.readString(f));
            if (nc == null) return false;
            ToastyConfig old = ToastyConfig.get();
            // gemerkte Original-Spieloptionen behalten, damit der FPS-Boost sauber zurueckgestellt werden kann
            nc.origParticles = old.origParticles; nc.origShadows = old.origShadows; nc.origView = old.origView;
            nc.origSim = old.origSim; nc.origScaling = old.origScaling; nc.orig = old.orig;
            ToastyConfig.replace(nc);
            MinecraftClient mc = MinecraftClient.getInstance();
            for (KeyBinding k : ToastyKeysScreen.binds()) {
                if (k == null) continue;
                String v = nc.keys.get(k.getId());
                if (v != null) k.setBoundKey(InputUtil.fromTranslationKey(v));
            }
            KeyBinding.updateKeysByCode();
            mc.options.write();
            ToastyTheme.reload(nc);
            ToastyPerf.apply();
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean delete(String name) {
        try { return Files.deleteIfExists(dir().resolve(clean(name) + ".json")); } catch (Exception e) { return false; }
    }
}
