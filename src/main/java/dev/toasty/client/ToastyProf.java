package dev.toasty.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Mini-Profiler: misst, wie viel Zeit die einzelnen Mod-Teile pro Frame/Tick brauchen. Auslesen mit /toasty perf. */
public final class ToastyProf {
    private static final Map<String, long[]> STATS = new TreeMap<>();
    private static long since = System.currentTimeMillis();

    private static final long[] FRAMES = new long[600];
    private static int fIdx = 0, fCount = 0;
    private static long lastFrame = 0;

    private ToastyProf() {}

    /** Einmal pro Frame aufrufen (HUD-Render). */
    public static void frame() {
        long now = System.nanoTime();
        if (lastFrame != 0) {
            long d = now - lastFrame;
            if (d < 2_000_000_000L) { FRAMES[fIdx] = d; fIdx = (fIdx + 1) % FRAMES.length; if (fCount < FRAMES.length) fCount++; }
        }
        lastFrame = now;
    }

    /** Komplette Lag-Analyse: Frameverlauf, Einstellungen, Speicher, Mod-Anteil, Empfehlungen. */
    public static List<String> analyze() {
        List<String> l = new ArrayList<>();
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (fCount < 30) { l.add("Zu wenig Daten – bitte einige Sekunden im Spiel bleiben und nochmal ausführen."); return l; }
        long[] f = java.util.Arrays.copyOf(FRAMES, fCount);
        long sum = 0, max = 0; int spikes = 0;
        for (long d : f) { sum += d; max = Math.max(max, d); if (d > 50_000_000L) spikes++; }
        double avgMs = sum / 1e6 / f.length;
        long[] sorted = f.clone(); java.util.Arrays.sort(sorted);
        double p99 = sorted[(int) Math.min(sorted.length - 1, Math.floor(sorted.length * 0.99))] / 1e6;
        l.add(String.format(java.util.Locale.ROOT, "FPS Ø %.0f · 1%%-Low %.0f · schlimmster Frame %.0f ms · Ruckler >50 ms: %d (von %d Frames)",
            1000.0 / avgMs, 1000.0 / p99, max / 1e6, spikes, f.length));

        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) >> 20, maxMem = rt.maxMemory() >> 20;
        int pct = (int) (used * 100 / Math.max(1, maxMem));
        int ents = 0;
        if (mc.world != null) for (Object o : mc.world.getEntities()) ents++;
        var o = mc.options;
        l.add("RAM " + used + "/" + maxMem + " MB (" + pct + "%) · Entities " + ents + " · Render " + o.getViewDistance().getValue()
            + " · Simulation " + o.getSimulationDistance().getValue() + " · Mods " + net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().size());

        // Anteil des Toasty-Mods
        double secs = Math.max(1, (System.currentTimeMillis() - since) / 1000.0);
        double modMs = 0; String top = null; double topMs = 0;
        for (Map.Entry<String, long[]> e : STATS.entrySet()) {
            double per = e.getValue()[0] / 1e6 / secs;
            modMs += per; if (per > topMs) { topMs = per; top = e.getKey(); }
        }
        double budget = 1000.0;
        l.add(String.format(java.util.Locale.ROOT, "Toasty-Mod gesamt: %.1f ms pro Sekunde (%.1f%% der Zeit)%s", modMs, modMs / budget * 100,
            top == null ? "" : " · größter Teil: " + top + " (" + String.format(java.util.Locale.ROOT, "%.1f", topMs) + " ms/s)"));

        l.add("--- Bewertung ---");
        boolean any = false;
        if (modMs > 60) { l.add("• Der Toasty-Mod frisst spürbar Zeit (" + top + "). Teste /toasty lite."); any = true; }
        if (pct > 85) { l.add("• RAM fast voll: im Launcher mehr Speicher zuweisen (z. B. 4–6 GB), sonst GC-Ruckler."); any = true; }
        if (spikes > 3 && pct > 70) { l.add("• Viele Ruckler + hoher RAM-Verbrauch: sehr wahrscheinlich Garbage Collection."); any = true; }
        if (o.getViewDistance().getValue() > 16) { l.add("• Render-Distanz über 16: kostet am meisten FPS. Auf 10–12 senken."); any = true; }
        if (o.getSimulationDistance().getValue() > 10) { l.add("• Simulations-Distanz hoch: auf 6–8 senken."); any = true; }
        if (ents > 250) { l.add("• Viele Entities (" + ents + "): Item-/Mob-Farmen oder Item-Haufen in der Nähe."); any = true; }
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium") == false)
            { l.add("• Sodium ist nicht installiert – das bringt oft das größte FPS-Plus."); any = true; }
        if (1000.0 / avgMs < 60 && modMs < 30) { l.add("• Niedrige FPS, aber der Toasty-Mod ist nicht schuld: Grafik-Einstellungen, Shader oder andere Mods prüfen."); any = true; }
        if (!any) l.add("• Nichts Auffälliges gefunden.");
        return l;
    }

    public static long begin() { return System.nanoTime(); }

    public static void end(String name, long t0) {
        long d = System.nanoTime() - t0;
        long[] s = STATS.computeIfAbsent(name, k -> new long[3]);
        s[0] += d; s[1]++; if (d > s[2]) s[2] = d;
    }

    public static void reset() { STATS.clear(); since = System.currentTimeMillis(); }

    public static List<String> report() {
        List<String> l = new ArrayList<>();
        double secs = Math.max(1, (System.currentTimeMillis() - since) / 1000.0);
        l.add("Messzeit: " + String.format(java.util.Locale.ROOT, "%.0f s", secs) + "  (Ø ms pro Aufruf · Summe ms pro Sekunde · Spitze ms)");
        STATS.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
            .forEach(e -> {
                long[] s = e.getValue();
                l.add(String.format(java.util.Locale.ROOT, "%s: Ø %.3f · %.1f ms/s · max %.2f", e.getKey(),
                    s[0] / 1e6 / Math.max(1, s[1]), s[0] / 1e6 / secs, s[2] / 1e6));
            });
        if (STATS.isEmpty()) l.add("Noch keine Daten – kurz spielen und nochmal /toasty perf.");
        return l;
    }
}
