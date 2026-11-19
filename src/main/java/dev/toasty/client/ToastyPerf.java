package dev.toasty.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

/** FPS-Boost ueber Spieloptionen (kein Ersatz fuer Sodium/Optifine, aber spuerbar). */
public final class ToastyPerf {
    private ToastyPerf() {}

    @SuppressWarnings("unchecked")
    private static <T> T as(Object o, T witness) { return (T) o; }

    /** Enum-Konstante per Position (Namen sind im Spiel teils verschleiert, die Reihenfolge nicht). */
    private static Object enumAt(Object any, int index) {
        Object[] all = ((Enum<?>) any).getDeclaringClass().getEnumConstants();
        return all[Math.max(0, Math.min(all.length - 1, index))];
    }

    public static void apply() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        GameOptions o = mc.options;
        ToastyConfig c = ToastyConfig.get();

        // Partikel: ALL(0), DECREASED(1), MINIMAL(2) -> letzte Konstante = minimal
        var particles = o.getParticles();
        if (c.perfParticles) {
            if (c.origParticles == null) c.origParticles = Integer.toString(((Enum<?>) particles.getValue()).ordinal());
            particles.setValue(as(enumAt(particles.getValue(), Integer.MAX_VALUE), particles.getValue()));
        } else if (c.origParticles != null) {
            try {
                particles.setValue(as(enumAt(particles.getValue(), Integer.parseInt(c.origParticles)), particles.getValue()));
            } catch (Exception ignored) {}
            c.origParticles = null;
        }

        if (c.perfShadows) {
            if (c.origShadows == null) c.origShadows = o.getEntityShadows().getValue();
            o.getEntityShadows().setValue(false);
        } else if (c.origShadows != null) {
            o.getEntityShadows().setValue(c.origShadows);
            c.origShadows = null;
        }

        if (c.perfDistance) {
            if (c.origView == null) c.origView = o.getViewDistance().getValue();
            if (c.origSim == null) c.origSim = o.getSimulationDistance().getValue();
            if (c.origScaling == null) c.origScaling = o.getEntityDistanceScaling().getValue();
            o.getViewDistance().setValue(Math.min(c.origView, 8));
            o.getSimulationDistance().setValue(Math.min(c.origSim, 6));
            o.getEntityDistanceScaling().setValue(0.5);
        } else if (c.origView != null) {
            o.getViewDistance().setValue(c.origView);
            if (c.origSim != null) o.getSimulationDistance().setValue(c.origSim);
            if (c.origScaling != null) o.getEntityDistanceScaling().setValue(c.origScaling);
            c.origView = null; c.origSim = null; c.origScaling = null;
        }

        // Wolken aus (Konstante 0 = OFF)
        var clouds = o.getCloudRenderMode();
        if (c.perfClouds) {
            c.orig.putIfAbsent("clouds", Integer.toString(((Enum<?>) clouds.getValue()).ordinal()));
            clouds.setValue(as(enumAt(clouds.getValue(), 0), clouds.getValue()));
        } else if (c.orig.containsKey("clouds")) {
            try { clouds.setValue(as(enumAt(clouds.getValue(), Integer.parseInt(c.orig.get("clouds"))), clouds.getValue())); } catch (Exception ignored) {}
            c.orig.remove("clouds");
        }

        // Biom-Uebergang 0
        if (c.perfBlend) {
            c.orig.putIfAbsent("blend", Integer.toString(o.getBiomeBlendRadius().getValue()));
            o.getBiomeBlendRadius().setValue(0);
        } else if (c.orig.containsKey("blend")) {
            o.getBiomeBlendRadius().setValue(Integer.parseInt(c.orig.remove("blend")));
        }

        // weiches Licht (Ambient Occlusion) aus
        if (c.perfAo) {
            c.orig.putIfAbsent("ao", Boolean.toString(o.getAo().getValue()));
            o.getAo().setValue(false);
        } else if (c.orig.containsKey("ao")) {
            o.getAo().setValue(Boolean.parseBoolean(c.orig.remove("ao")));
        }

        // FPS-Limit aufheben, VSync aus
        if (c.perfFps) {
            c.orig.putIfAbsent("maxfps", Integer.toString(o.getMaxFps().getValue()));
            c.orig.putIfAbsent("vsync", Boolean.toString(o.getEnableVsync().getValue()));
            o.getMaxFps().setValue(260);
            o.getEnableVsync().setValue(false);
        } else if (c.orig.containsKey("maxfps")) {
            o.getMaxFps().setValue(Integer.parseInt(c.orig.remove("maxfps")));
            o.getEnableVsync().setValue(Boolean.parseBoolean(c.orig.getOrDefault("vsync", "true")));
            c.orig.remove("vsync");
        }
        c.save();
        o.write();
    }
}
