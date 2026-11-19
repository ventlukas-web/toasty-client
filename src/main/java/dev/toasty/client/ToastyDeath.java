package dev.toasty.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Death-Aufzeichnung: speichert die letzten Sekunden (Position, Leben, Gegner, Schaden) und zeigt sie nach dem Tod als Karten-Replay. */
public final class ToastyDeath {
    public static final long KEEP_MS = 15000;

    public record Other(double x, double z, char kind, String name) {}
    public record Frame(long t, double x, double y, double z, float yaw, float hp, List<Other> others) {}
    public record Hit(long t, float amount, String src) {}

    private static final List<Frame> BUF = new ArrayList<>();
    private static final List<Hit> HITS = new ArrayList<>();
    private static float prevHp = -1;
    private static boolean captured;

    public static volatile List<Frame> lastFrames = new ArrayList<>();
    public static volatile List<Hit> lastHits = new ArrayList<>();
    public static volatile long lastDeathAt;

    private ToastyDeath() {}

    public static boolean hasReplay() { return !lastFrames.isEmpty(); }

    /** Todes-Bildschirm: Button "Death-Replay". */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof DeathScreen && hasReplay()) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Death-Replay ansehen"),
                    b -> client.setScreen(new ToastyDeathScreen(screen)))
                    .dimensions(w / 2 - 100, h / 4 + 120, 200, 20).build());
            }
        });
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) { BUF.clear(); HITS.clear(); prevHp = -1; return; }
        if (mc.currentScreen instanceof DeathScreen || mc.player.isDead() || mc.player.getHealth() <= 0) {
            if (!captured && !BUF.isEmpty()) {
                captured = true;
                lastFrames = new ArrayList<>(BUF);
                lastHits = new ArrayList<>(HITS);
                lastDeathAt = System.currentTimeMillis();
                String last = HITS.isEmpty() ? "" : "Letzter Schaden: " + String.format(java.util.Locale.ROOT, "%.1f", HITS.get(HITS.size() - 1).amount()) + " von " + HITS.get(HITS.size() - 1).src();
                ToastyNotify.push("Du bist gestorben", last.isEmpty() ? "Death-Replay bereit (/toasty replay)" : last, 0xFFFB7185);
                BUF.clear(); HITS.clear(); prevHp = -1;
            }
            return;
        }
        captured = false;
        if ((mc.world.getTime() & 3) != 0) return;
        long now = System.currentTimeMillis();
        float hp = mc.player.getHealth();
        List<Other> others = new ArrayList<>();
        List<LivingEntity> near = mc.world.getEntitiesByClass(LivingEntity.class, mc.player.getBoundingBox().expand(24), e -> e != mc.player && e.isAlive());
        near.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(mc.player)));
        for (int i = 0; i < Math.min(8, near.size()); i++) {
            LivingEntity e = near.get(i);
            char kind = e instanceof PlayerEntity ? 'P' : e instanceof HostileEntity ? 'H' : 'O';
            others.add(new Other(e.getX(), e.getZ(), kind, kind == 'P' ? e.getName().getString() : ""));
        }
        if (prevHp >= 0 && hp < prevHp - 0.01f) {
            LivingEntity at = mc.player.getAttacker();
            HITS.add(new Hit(now, prevHp - hp, at != null ? at.getName().getString() : "Umgebung"));
            if (HITS.size() > 40) HITS.remove(0);
        }
        prevHp = hp;
        BUF.add(new Frame(now, mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), hp, others));
        while (!BUF.isEmpty() && BUF.get(0).t() < now - KEEP_MS) BUF.remove(0);
        HITS.removeIf(h -> h.t() < now - KEEP_MS);
    }
}
