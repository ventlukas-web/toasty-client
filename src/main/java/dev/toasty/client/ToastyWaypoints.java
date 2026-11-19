package dev.toasty.client;

import net.minecraft.client.MinecraftClient;

/** Wegpunkte: Koordinaten speichern, als Marker in der Welt und als Liste mit Richtungspfeil anzeigen. */
public final class ToastyWaypoints {
    private ToastyWaypoints() {}

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    public static String dim(MinecraftClient mc) {
        return mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
    }

    public static boolean add(String name, int x, int y, int z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        ToastyConfig c = ToastyConfig.get();
        c.waypoints.removeIf(w -> w.name.equalsIgnoreCase(name));
        ToastyConfig.Wp w = new ToastyConfig.Wp();
        w.name = name; w.x = x; w.y = y; w.z = z; w.dim = dim(mc);
        c.waypoints.add(w);
        c.save();
        return true;
    }

    public static boolean addHere(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        return add(name, (int) Math.floor(mc.player.getX()), (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ()));
    }

    public static boolean remove(String name) {
        ToastyConfig c = ToastyConfig.get();
        boolean r = c.waypoints.removeIf(w -> w.name.equalsIgnoreCase(name));
        if (r) c.save();
        return r;
    }

    /** Pfeil zum Wegpunkt relativ zur Blickrichtung (oben = geradeaus). */
    public static String arrow(MinecraftClient mc, ToastyConfig.Wp w) {
        double dx = w.x + 0.5 - mc.player.getX(), dz = w.z + 0.5 - mc.player.getZ();
        double yawTo = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = yawTo - mc.player.getYaw();
        rel = ((rel % 360) + 540) % 360 - 180;
        int idx = (int) Math.round(rel / 45.0);
        return ARROWS[((idx % 8) + 8) % 8];
    }

    public static double dist(MinecraftClient mc, ToastyConfig.Wp w) {
        double dx = w.x + 0.5 - mc.player.getX(), dy = w.y - mc.player.getY(), dz = w.z + 0.5 - mc.player.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
