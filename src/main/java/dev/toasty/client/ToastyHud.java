package dev.toasty.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ToastyHud {
    public static long flashUntil;
    public static int flashColor = ToastyTheme.BLUE;

    /** Zuletzt gezeichnete Position/Groesse je HUD-Element: {x, y, w, h} in GUI-Pixeln (fuer den HUD-Editor). */
    public static final java.util.Map<String, float[]> BOUNDS = new java.util.LinkedHashMap<>();

    /** Position eines Elements: gespeicherte (verschobene) Position oder Standard. Positionen sind relative Bruchteile der Bildschirmgroesse. */
    static float[] place(String id, float defX, float defY, float bw, float bh, MinecraftClient mc) {
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        float[] f = ToastyConfig.get().hudPos.get(id);
        float x = (f != null && f.length >= 2) ? f[0] * sw : defX;
        float y = (f != null && f.length >= 2) ? f[1] * sh : defY;
        x = Math.max(0, Math.min(sw - bw, x));
        y = Math.max(0, Math.min(sh - bh, y));
        BOUNDS.put(id, new float[]{x, y, bw, bh});
        return new float[]{x, y};
    }

    // Overlay-Banner
    private static String ovTitle = "", ovSub = "";
    private static int ovColor;
    private static long ovStart = -100000;
    private static final long OV_MS = 2800;

    // Konfetti
    private static final class P { float x, y, vx, vy, size; int color; }
    private static final List<P> parts = new ArrayList<>();
    private static final Random RND = new Random();
    private static long lastNano = System.nanoTime();

    public static void flash(int argb) {
        flashColor = argb;
        flashUntil = System.currentTimeMillis() + 1500;
    }

    public static void overlay(boolean win, String title, String sub) {
        ovTitle = title; ovSub = sub;
        ovColor = win ? ToastyTheme.GOOD : ToastyTheme.BAD;
        ovStart = System.currentTimeMillis();
        if (win && ToastyConfig.get().confetti) {
            MinecraftClient mc = MinecraftClient.getInstance();
            int w = mc.getWindow().getScaledWidth();
            for (int i = 0; i < 110; i++) {
                P p = new P();
                p.x = RND.nextFloat() * w; p.y = -10 - RND.nextFloat() * 60;
                p.vx = (RND.nextFloat() - 0.5f) * 40; p.vy = 60 + RND.nextFloat() * 90;
                p.size = 2 + RND.nextInt(3);
                p.color = ToastyTheme.lerp(ToastyTheme.PURPLE, ToastyTheme.BLUE, RND.nextFloat());
                parts.add(p);
            }
        }
    }

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float[] sbb = BOUNDS.get("scoreboard");
        BOUNDS.clear();
        if (sbb != null) BOUNDS.put("scoreboard", sbb);
        if (mc.options.hudHidden) return;
        ToastyConfig cfg = ToastyConfig.get();
        TextRenderer tr = mc.textRenderer;

        long nowN = System.nanoTime();
        float dt = Math.min(0.1f, (nowN - lastNano) / 1e9f);
        lastNano = nowN;

        ToastyProf.frame();
        long p0 = ToastyProf.begin();
        float y = 6;
        if (cfg.hud) y += renderStats(ctx, tr, mc, cfg) + 6;
        renderInfo(ctx, tr, mc, cfg, y, dt);
        renderKeys(ctx, tr, mc, cfg);
        renderModList(ctx, tr, mc, cfg);
        ToastyProf.end("HUD Grundpanels", p0);
        p0 = ToastyProf.begin();
        ToastyPanels.render(ctx, tr, mc, cfg, tick.getTickProgress(true));
        ToastyProf.end("HUD Zusatz-Panels", p0);
        if (ToastyEvents.swActive()) {
            String sw = "Stoppuhr " + ToastyEvents.swFmt(ToastyEvents.swElapsed());
            ctx.drawTextWithShadow(tr, sw, (mc.getWindow().getScaledWidth() - tr.getWidth(sw)) / 2, 22, ToastyEvents.swElapsed() > 0 ? ToastyTheme.BLUE : ToastyTheme.TEXT);
        }
        int ty = ToastyEvents.swActive() ? 34 : 22;
        for (String tl : ToastyEvents.timerLines()) {
            ctx.drawTextWithShadow(tr, tl, (mc.getWindow().getScaledWidth() - tr.getWidth(tl)) / 2, ty, ToastyTheme.PURPLE);
            ty += 11;
        }
        p0 = ToastyProf.begin();
        renderConfetti(ctx, mc, dt);
        renderOverlay(ctx, tr, mc);
        ToastyProf.end("Konfetti/Overlay", p0);
    }

    private static float renderStats(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Einnahmen", ToastyStats.fmt(ToastyStats.income)});
        rows.add(new String[]{"Auszahlung", ToastyStats.fmt(ToastyStats.payouts)});
        rows.add(new String[]{"Profit", ToastyStats.fmt(ToastyStats.profit())});
        rows.add(new String[]{"Runden", ToastyStats.rounds + "  (W" + ToastyStats.wins + "/L" + ToastyStats.losses + ")"});
        rows.add(new String[]{"Winrate", String.format(java.util.Locale.ROOT, "%.1f%%", ToastyStats.winrate())});
        rows.add(new String[]{"Offen", String.valueOf(ToastyStats.pending.size())});

        String title = "Toasty Client";
        int w = tr.getWidth(title);
        for (String[] r : rows) w = Math.max(w, tr.getWidth(r[0]) + 14 + tr.getWidth(r[1]));
        int pad = 6, h = 16 + rows.size() * 11 + pad;

        float[] pos = place("stats", 6, 6, (w + pad * 2) * cfg.hudScale, h * cfg.hudScale, mc);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(pos[0], pos[1]);
        ctx.getMatrices().scale(cfg.hudScale, cfg.hudScale);
        ToastyTheme.panel(ctx, 0, 0, w + pad * 2, h);
        ToastyTheme.gradientText(ctx, tr, title, pad, 6);
        int y = 18;
        for (String[] r : rows) {
            int vc = ToastyTheme.TEXT;
            if (r[0].equals("Profit")) vc = ToastyStats.profit() >= 0 ? ToastyTheme.GOOD : ToastyTheme.BAD;
            ctx.drawTextWithShadow(tr, r[0], pad, y, ToastyTheme.MUTED);
            ctx.drawTextWithShadow(tr, r[1], w + pad - tr.getWidth(r[1]), y, vc);
            y += 11;
        }
        if (System.currentTimeMillis() < flashUntil) {
            ToastyTheme.roundOutline(ctx, 0, 0, w + pad * 2, h, 6, ToastyTheme.alpha(flashColor, 0xFF));
        }
        ctx.getMatrices().popMatrix();
        return h * cfg.hudScale;
    }


    // ---------- Info-Panel (Koordinaten, FPS, Ping, Speed, Uhr) ----------

    private static double lastX, lastZ, speedSmooth;
    private static boolean haveLast;

    private static String dirName(net.minecraft.util.math.Direction d) {
        return switch (d) {
            case NORTH -> "Norden (-Z)";
            case SOUTH -> "Süden (+Z)";
            case EAST -> "Osten (+X)";
            case WEST -> "Westen (-X)";
            default -> d.asString();
        };
    }

    private static void renderInfo(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg, float y0, float dt) {
        var p = mc.player;
        if (p == null) { haveLast = false; return; }
        // Speed immer mitzaehlen, damit der Wert beim Einschalten sofort stimmt
        if (haveLast && dt > 0) {
            double d = Math.hypot(p.getX() - lastX, p.getZ() - lastZ) / dt;
            speedSmooth += (d - speedSmooth) * Math.min(1.0, dt * 6);
        }
        lastX = p.getX(); lastZ = p.getZ(); haveLast = true;

        List<String[]> rows = new ArrayList<>();
        if (cfg.coords) {
            rows.add(new String[]{"XYZ", cfg.streamer && cfg.streamerHideXyz ? "versteckt" : (int) Math.floor(p.getX()) + " " + (int) Math.floor(p.getY()) + " " + (int) Math.floor(p.getZ())});
            rows.add(new String[]{"Blick", dirName(p.getHorizontalFacing())});
        }
        if (cfg.showFps) rows.add(new String[]{"FPS", String.valueOf(mc.getCurrentFps())});
        if (cfg.ping && mc.getNetworkHandler() != null) {
            var e = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            rows.add(new String[]{"Ping", e == null ? "?" : e.getLatency() + " ms"});
        }
        if (cfg.speed) rows.add(new String[]{"Speed", String.format(java.util.Locale.ROOT, "%.1f b/s", speedSmooth)});
        if (cfg.clock) rows.add(new String[]{"Uhr", java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))});
        if (rows.isEmpty()) return;

        int w = 0;
        for (String[] r : rows) w = Math.max(w, tr.getWidth(r[0]) + 14 + tr.getWidth(r[1]));
        int pad = 6, h = rows.size() * 11 + pad * 2 - 1;
        float[] pos = place("info", 6, y0, (w + pad * 2) * cfg.hudScale, h * cfg.hudScale, mc);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(pos[0], pos[1]);
        ctx.getMatrices().scale(cfg.hudScale, cfg.hudScale);
        ToastyTheme.panel(ctx, 0, 0, w + pad * 2, h);
        int y = pad + 1;
        for (String[] r : rows) {
            ctx.drawTextWithShadow(tr, r[0], pad, y, ToastyTheme.MUTED);
            ctx.drawTextWithShadow(tr, r[1], w + pad - tr.getWidth(r[1]), y, ToastyTheme.TEXT);
            y += 11;
        }
        ctx.getMatrices().popMatrix();
    }

    // ---------- Keystrokes + CPS ----------

    private static final java.util.ArrayDeque<Long> leftClicks = new java.util.ArrayDeque<>();
    private static final java.util.ArrayDeque<Long> rightClicks = new java.util.ArrayDeque<>();
    private static boolean wasLeft, wasRight;

    private static int cps(java.util.ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
        return q.size();
    }

    private static void key(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String label, boolean down) {
        ToastyTheme.roundRect(ctx, x, y, w, h, 4, down ? ToastyTheme.alpha(ToastyTheme.PURPLE, 0xD0) : ToastyTheme.bgA(0xC0));
        ToastyTheme.roundOutline(ctx, x, y, w, h, 4, down ? ToastyTheme.BLUE : ToastyTheme.alpha(ToastyTheme.PURPLE, 0xA0));
        ctx.drawTextWithShadow(tr, label, x + (w - tr.getWidth(label)) / 2, y + (h - 8) / 2, down ? 0xFFFFFFFF : ToastyTheme.TEXT);
    }

    private static void renderKeys(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        long now = System.currentTimeMillis();
        boolean l = mc.options.attackKey.isPressed(), r = mc.options.useKey.isPressed();
        if (l && !wasLeft) leftClicks.addLast(now);
        if (r && !wasRight) rightClicks.addLast(now);
        wasLeft = l; wasRight = r;
        if (!cfg.keystrokes) return;

        int s = 22, g = 2;
        int totalW = s * 3 + g * 2, totalH = s * 2 + g * 2 + 16 + g + 14;
        int sh = mc.getWindow().getScaledHeight();
        float[] pos = place("keys", 8, sh - 8 - totalH * cfg.hudScale, totalW * cfg.hudScale, totalH * cfg.hudScale, mc);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(pos[0], pos[1]);
        ctx.getMatrices().scale(cfg.hudScale, cfg.hudScale);
        var o = mc.options;
        key(ctx, tr, s + g, 0, s, s, "W", o.forwardKey.isPressed());
        key(ctx, tr, 0, s + g, s, s, "A", o.leftKey.isPressed());
        key(ctx, tr, s + g, s + g, s, s, "S", o.backKey.isPressed());
        key(ctx, tr, (s + g) * 2, s + g, s, s, "D", o.rightKey.isPressed());
        int y = (s + g) * 2;
        int half = (totalW - g) / 2;
        key(ctx, tr, 0, y, half, 16, "LMB " + cps(leftClicks, now), l);
        key(ctx, tr, half + g, y, totalW - half - g, 16, "RMB " + cps(rightClicks, now), r);
        key(ctx, tr, 0, y + 16 + g, totalW, 14, "SPACE", o.jumpKey.isPressed());
        ctx.getMatrices().popMatrix();
    }

    // ---------- Modul-Liste (alles, was gerade an ist) ----------

    private static long glassCheckAt; private static boolean glassCached;

    private static void renderModList(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        if (!cfg.moduleList) return;
        long now = System.currentTimeMillis();
        if (now - glassCheckAt > 1000) {
            glassCheckAt = now;
            try { glassCached = ToastyGlass.isOn(); } catch (Exception e) { glassCached = false; }
        }
        List<String> m = new ArrayList<>();
        if (cfg.hud) m.add("Stats-HUD");
        if (cfg.keystrokes) m.add("Keystrokes");
        if (cfg.coords) m.add("Koordinaten");
        if (cfg.showFps) m.add("FPS");
        if (cfg.ping) m.add("Ping");
        if (cfg.speed) m.add("Speed");
        if (cfg.clock) m.add("Uhr");
        if (cfg.sound) m.add("Sound");
        if (cfg.effects) m.add("Overlay");
        if (cfg.confetti) m.add("Konfetti");
        if (cfg.watermark) m.add("Wasserzeichen");
        if (cfg.fullbright) m.add("Fullbright");
        if (cfg.autoClick) m.add("Autoclicker " + cfg.autoCps);
        if (cfg.antiLeak) m.add("Anti-Leak");
        if (cfg.blockHighlight) m.add("Block-Highlight");
        if (cfg.itemGlow) m.add("Item-Highlight");
        if (cfg.twitch) m.add("Twitch-Chat");
        if (cfg.sellAuto) m.add("Sell-Makro Auto");
        if (ToastyMacro.isRunning()) m.add("Sell-Makro läuft");
        if (glassCached) m.add("Unsichtbares Glas");
        if (cfg.perfParticles) m.add("Partikel minimal");
        if (cfg.perfShadows) m.add("Schatten aus");
        if (cfg.perfDistance) m.add("Weniger Distanz");
        if (m.isEmpty()) return;
        m.sort((a, b) -> tr.getWidth(b) - tr.getWidth(a));

        int sw = mc.getWindow().getScaledWidth();
        int maxW = 0;
        for (String s : m) maxW = Math.max(maxW, tr.getWidth(s) + 8);
        float[] pos = place("mods", sw - 4 - maxW * cfg.hudScale, 4, maxW * cfg.hudScale, m.size() * 13 * cfg.hudScale, mc);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(pos[0] + maxW * cfg.hudScale, pos[1]);
        ctx.getMatrices().scale(cfg.hudScale, cfg.hudScale);
        int y = 0, n = Math.max(1, m.size() - 1);
        for (int i = 0; i < m.size(); i++) {
            String s = m.get(i);
            int w = tr.getWidth(s);
            int col = ToastyTheme.lerp(ToastyTheme.PURPLE, ToastyTheme.BLUE, (float) i / n);
            ToastyTheme.roundRect(ctx, -w - 8, y, w + 8, 11, 4, ToastyTheme.bgA(0xC0));
            ToastyTheme.roundOutline(ctx, -w - 8, y, w + 8, 11, 4, ToastyTheme.alpha(col, 0x90));
            ctx.drawTextWithShadow(tr, s, -w - 4, y + 2, col);
            y += 13;
        }
        ctx.getMatrices().popMatrix();
    }

    private static void renderConfetti(DrawContext ctx, MinecraftClient mc, float dt) {
        if (parts.isEmpty()) return;
        int h = mc.getWindow().getScaledHeight();
        parts.removeIf(p -> p.y > h + 10);
        for (P p : parts) {
            p.x += p.vx * dt; p.y += p.vy * dt;
            int s = (int) p.size;
            ctx.fill((int) p.x, (int) p.y, (int) p.x + s, (int) p.y + s, p.color);
        }
    }

    private static void renderOverlay(DrawContext ctx, TextRenderer tr, MinecraftClient mc) {
        long el = System.currentTimeMillis() - ovStart;
        if (el < 0 || el > OV_MS) return;
        float t = el / (float) OV_MS;
        float a = t < 0.1f ? t / 0.1f : (t > 0.7f ? (1f - t) / 0.3f : 1f);
        float pop = t < 0.15f ? 0.6f + 0.4f * (t / 0.15f) : 1f;
        int alpha = (int) (255 * Math.max(0, Math.min(1, a)));
        if (alpha < 8) return;

        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int cy = sh / 4;
        int bandH = 58;
        int pw = Math.min(sw - 20, Math.max(tr.getWidth(ovTitle) * 3 + 70, tr.getWidth(ovSub) + 60));
        int px = (sw - pw) / 2, py = cy - bandH / 2;
        ToastyTheme.roundRect(ctx, px, py, pw, bandH, 12,
            ToastyTheme.alpha(ToastyTheme.lerp(0xFF0A0614, ToastyTheme.lerp(ToastyTheme.PURPLE, ToastyTheme.BLUE, 0.5f), 0.35f), alpha * 9 / 10));
        ToastyTheme.roundOutline(ctx, px, py, pw, bandH, 12, ToastyTheme.alpha(ovColor, alpha));

        // Titel gross
        float scale = 3f * pop;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(sw / 2f, cy - 14);
        ctx.getMatrices().scale(scale, scale);
        ctx.drawTextWithShadow(tr, ovTitle, -tr.getWidth(ovTitle) / 2, 0, ToastyTheme.alpha(ovColor, alpha));
        ctx.getMatrices().popMatrix();

        // Untertitel
        ctx.drawTextWithShadow(tr, ovSub, (sw - tr.getWidth(ovSub)) / 2, cy + 12, ToastyTheme.alpha(ToastyTheme.TEXT, alpha));
    }
}
