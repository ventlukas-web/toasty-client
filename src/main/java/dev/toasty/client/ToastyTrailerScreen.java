package dev.toasty.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Trailer mit Musik (25 s) - laeuft beim Spielstart und per Menue/Befehl. Ueberspringen: Skip-Button unten rechts, Leertaste, Enter oder ESC. */
public class ToastyTrailerScreen extends Screen {
    private static final double T_INTRO = 3.5, T_MENU_END = 12.5, T_OUT = 21.0, DUR = 25.0;
    private static final String[] TABS = {"Allgemein", "HUD", "HUDs+"};
    private static final String[][] PAGES = {
        {"Koordinaten", "Wasserzeichen", "Toasts", "Keystrokes"},
        {"Ping", "Speed", "FPS", "Uhrzeit"},
        {"Kompass", "Sell Status", "Armor Durability", "Gewinn-Graph"}};
    /** {Zeit, Typ (0 Toggle, 1 Tab, 2 Schliessen), Seite, Index} */
    private static final double[][] EV = {
        {1.5, 0, 0, 0}, {2.2, 0, 0, 1}, {3.1, 1, 1, 0}, {4.0, 0, 1, 0}, {4.5, 0, 1, 1},
        {5.0, 0, 1, 2}, {5.9, 1, 2, 0}, {6.8, 0, 2, 0}, {7.3, 0, 2, 1}, {8.2, 2, 0, 0}};
    private static final int PX = 170, PY = 36, PWD = 300, PHT = 270;
    private static final String NAME = "TOASTY CLIENT";
    private static SoundInstance music;

    /** Eigene Sound-Instanz (unabhaengig von Registry-Signaturen): Musik ohne Position, Lautstaerke ueber "Master". */
    private static final class TrailerSound extends AbstractSoundInstance {
        TrailerSound() {
            super(Identifier.of("toastyclient", "trailer"), SoundCategory.MASTER, net.minecraft.util.math.random.Random.create());
            this.volume = 1.0f;
            this.pitch = 1.0f;
            this.relative = true;
            this.attenuationType = SoundInstance.AttenuationType.NONE;
        }
    }

    private final Screen parent;
    private long start;
    private boolean prevClick = true, closed;

    public ToastyTrailerScreen(Screen parent) {
        super(Text.literal("Toasty Trailer"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (start != 0) return;
        start = System.currentTimeMillis();
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            stopMusic();
            music = new TrailerSound();
            mc.getSoundManager().play(music);
        } catch (Throwable ignored) {}
    }

    private static void stopMusic() {
        try { if (music != null) MinecraftClient.getInstance().getSoundManager().stop(music); } catch (Throwable ignored) {}
        music = null;
    }

    @Override
    public void removed() { stopMusic(); }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        stopMusic();
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
    private static double ease(double t) { t = clamp(t); return 1 - Math.pow(1 - t, 3); }
    private static double back(double t) { t = clamp(t); double c = 1.70158; return 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2); }
    private static int a(double alpha) { return (int) Math.max(0, Math.min(255, alpha * 255)); }
    private static int col(int rgb, double alpha) { return ToastyTheme.alpha(0xFF000000 | rgb, a(alpha)); }

    /** Text mit Skalierung, linksbuendig (x,y = obere linke Ecke). */
    private static void text(DrawContext ctx, TextRenderer tr, String s, float x, float y, float scale, int color) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.getMatrices().scale(scale, scale);
        ctx.drawTextWithShadow(tr, s, 0, 0, color);
        ctx.getMatrices().popMatrix();
    }

    private static void gradText(DrawContext ctx, TextRenderer tr, String s, float x, float y, float scale, double alpha, boolean center) {
        float tot = tr.getWidth(s) * scale;
        float cx = center ? x - tot / 2f : x;
        int n = Math.max(1, s.length() - 1);
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            text(ctx, tr, ch, cx, y, scale, ToastyTheme.alpha(ToastyTheme.lerp(ToastyTheme.PURPLE, ToastyTheme.BLUE, (float) i / n), a(alpha)));
            cx += tr.getWidth(ch) * scale;
        }
    }

    private static int ca(int c, double al) {
        return ToastyTheme.alpha(c, a(((c >>> 24) & 0xFF) / 255.0 * al));
    }

    private static void ctext(DrawContext ctx, TextRenderer tr, String s, float cx, float y, float sc, int color) {
        text(ctx, tr, s, cx - tr.getWidth(s) * sc / 2f, y, sc, color);
    }

    private static boolean toggleOn(int page, int idx, double u) {
        for (double[] e : EV) if (e[1] == 0 && (int) e[2] == page && (int) e[3] == idx && u >= e[0]) return true;
        return false;
    }

    private static int tabAt(double u) {
        int tab = 0;
        for (double[] e : EV) if (e[1] == 1 && u >= e[0]) tab = (int) e[2];
        return tab;
    }

    private static float[] evPos(double[] e) {
        if (e[1] == 1) return new float[]{PX + 14 + (int) e[2] * 96 + 45, PY + 34 + 9};
        if (e[1] == 2) return new float[]{PX + PWD - 16, PY + 14};
        int i = (int) e[3];
        return new float[]{PX + 14 + (i % 2) * 142 + 67, PY + 64 + (i / 2) * 34 + 12};
    }

    private void menuScene(DrawContext ctx, TextRenderer tr, double u) {
        double ap = ease(u / 0.5) * (1 - ease((u - 8.3) / 0.5));
        if (ap <= 0.01) return;
        float pop = (float) (0.94 + 0.06 * back(u / 0.5));
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(320, 180);
        ctx.getMatrices().scale(pop, pop);
        ctx.getMatrices().translate(-320, -180);
        ToastyTheme.roundRectExact(ctx, PX, PY, PWD, PHT, 14, ca(0xFF140B2B, ap * 0.9));
        ToastyTheme.roundOutlineExact(ctx, PX, PY, PWD, PHT, 14, ca(ToastyTheme.lerp(ToastyTheme.PURPLE, ToastyTheme.BLUE, 0.5f), ap * 0.4));
        gradText(ctx, tr, "Toasty Client", PX + 14, PY + 12, 1.3f, ap, false);
        int tab = tabAt(u);
        for (int i = 0; i < TABS.length; i++) {
            int x = PX + 14 + i * 96;
            ToastyTheme.roundRectExact(ctx, x, PY + 34, 90, 18, 9, ca(tab == i ? 0xFF8B6BEF : 0xFF1B2748, ap * 0.9));
            ctext(ctx, tr, TABS[i], x + 45, PY + 39, 1.0f, ca(0xFFF3EEFF, ap));
        }
        ToastyTheme.roundRectExact(ctx, PX + PWD - 26, PY + 6, 20, 16, 8, ca(0xFF1B2748, ap * 0.9));
        ctext(ctx, tr, "x", PX + PWD - 16, PY + 10, 1.0f, ca(0xFFF3EEFF, ap));
        for (int i = 0; i < 4; i++) {
            int x = PX + 14 + (i % 2) * 142, y = PY + 64 + (i / 2) * 34;
            boolean on = toggleOn(tab, i, u);
            ToastyTheme.roundRectExact(ctx, x, y, 134, 24, 12, ca(on ? 0xFF4C6BE0 : 0xFF132039, ap * 0.85));
            ToastyTheme.roundOutlineExact(ctx, x, y, 134, 24, 12, ca(on ? 0xFF7DD3FC : 0xFFFFFFFF, ap * (on ? 0.7 : 0.15)));
            text(ctx, tr, PAGES[tab][i], x + 10, y + 8, 1.0f, ca(0xFFF3EEFF, ap));
            text(ctx, tr, on ? "AN" : "AUS", x + 134 - 10 - tr.getWidth(on ? "AN" : "AUS"), y + 8, 1.0f, ca(on ? 0xFF4ADE80 : 0xFF9A96C0, ap));
        }
        // Cursor gleitet von Aktion zu Aktion, Klick-Welle
        float cx = PX + PWD + 40, cy = PY + 200;
        float[] prev = {cx, cy};
        double prevT = 0.4;
        for (double[] e : EV) {
            float[] p = evPos(e);
            double mv0 = prevT, mv1 = e[0] - 0.12;
            if (u >= mv1) { prev = p; prevT = e[0]; cx = p[0]; cy = p[1]; double dt = u - e[0]; if (dt >= 0 && dt < 0.35) {
                    int rr = (int) (4 + dt / 0.35 * 14);
                    ToastyTheme.roundOutlineExact(ctx, (int) p[0] - rr, (int) p[1] - rr, rr * 2, rr * 2, rr, ca(0xFFFFFFFF, ap * (1 - dt / 0.35)));
                } continue; }
            double k = ease((u - mv0) / Math.max(0.05, mv1 - mv0));
            cx = (float) (prev[0] + (p[0] - prev[0]) * k); cy = (float) (prev[1] + (p[1] - prev[1]) * k);
            break;
        }
        for (int i = 0; i < 7; i++) ctx.fill((int) cx, (int) cy + i, (int) cx + 1 + i / 2, (int) cy + i + 1, ca(0xFFFFFFFF, ap));
        ctx.getMatrices().popMatrix();
    }

    private static void hudPanel(DrawContext ctx, TextRenderer tr, float x, float y, float w, String title, String[][] rows, int[] vc, double u, double t0) {
        double pop = ease((u - t0) / 0.4);
        if (pop <= 0.01) return;
        float h = 24 + rows.length * 13;
        float oy = (float) ((1 - back((u - t0) / 0.4)) * 12);
        ToastyTheme.roundRectExact(ctx, (int) x, (int) (y + oy), (int) w, (int) h, 7, ca(0xFF140B2B, pop * 0.88));
        ToastyTheme.roundOutlineExact(ctx, (int) x, (int) (y + oy), (int) w, (int) h, 7, ca(ToastyTheme.lerp(ToastyTheme.PURPLE, ToastyTheme.BLUE, 0.5f), pop * 0.45));
        gradText(ctx, tr, title, x + 8, y + oy + 6, 1.1f, pop, false);
        for (int i = 0; i < rows.length; i++) {
            float ry = y + oy + 22 + i * 13;
            text(ctx, tr, rows[i][0], x + 8, ry, 1.0f, ca(0xFFF3EEFF, pop));
            text(ctx, tr, rows[i][1], x + w - 8 - tr.getWidth(rows[i][1]), ry, 1.0f, ca(vc[i], pop));
        }
    }

    private void hudScene(DrawContext ctx, TextRenderer tr, double u) {
        int GR = 0xFF4ADE80, AM = 0xFFFBBF24, WH = 0xFFB8B5E6;
        hudPanel(ctx, 14, 14, 120, "Toasty", new String[][]{{"Gewinn", "+1.25M"}, {"Runden", "13"}, {"Winrate", "57%"}}, new int[]{GR, WH, WH}, u, 0.0);
        hudPanel(ctx, 640 - 14 - 150, 14, 150, "Zahlungen", new String[][]{{"Spieler1", "500k  wartend 0:10"}, {"Spieler2", "250k  wartend 0:05"}, {"Spieler3", "1M  wartend 0:00"}}, new int[]{AM, AM, AM}, u, 0.5);
        hudPanel(ctx, 14, 140, 130, "Sell Status", new String[][]{{"Status", "läuft"}, {"Durchläufe", "8"}, {"Verkauft", "369 Items"}, {"Tempo", "1064/min"}}, new int[]{GR, WH, WH, WH}, u, 1.0);
        // Gewinn-Graph
        double gp = ease((u - 1.5) / 0.4);
        if (gp > 0.01) {
            int gx = 14, gy = 232, gw = 130, gh = 56;
            ToastyTheme.roundRectExact(ctx, gx, gy, gw, gh, 7, ca(0xFF140B2B, gp * 0.88));
            ToastyTheme.roundOutlineExact(ctx, gx, gy, gw, gh, 7, ca(ToastyTheme.lerp(ToastyTheme.PURPLE, ToastyTheme.BLUE, 0.5f), gp * 0.45));
            gradText(ctx, tr, "Gewinn/Runde", gx + 8, gy + 5, 1.0f, gp, false);
            int[] v = {14, -8, 10, 18, -12, 9, 16, 12};
            int shown = (int) Math.min(v.length, Math.max(0, (u - 1.8) / 0.25));
            for (int i = 0; i < shown; i++) {
                int bx = gx + 10 + i * 14, base = gy + 38;
                if (v[i] >= 0) ctx.fill(bx, base - v[i], bx + 9, base, ca(GR, gp)); else ctx.fill(bx, base, bx + 9, base - v[i], ca(0xFFFB7185, gp));
            }
        }
        // Kompass
        double cp = ease((u - 2.0) / 0.4);
        if (cp > 0.01) {
            ToastyTheme.roundRectExact(ctx, 245, 330, 150, 18, 9, ca(0xFF140B2B, cp * 0.88));
            ToastyTheme.roundOutlineExact(ctx, 245, 330, 150, 18, 9, ca(ToastyTheme.lerp(ToastyTheme.PURPLE, ToastyTheme.BLUE, 0.5f), cp * 0.45));
            String[] dirs = {"N", "NO", "O", "SO"};
            for (int i = 0; i < 4; i++) text(ctx, tr, dirs[i], 258 + i * 35, 335, 1.0f, ca(i == 0 ? 0xFFFB7185 : 0xFFF3EEFF, cp));
        }
        // GEWONNEN
        double g = u - 3.4;
        if (g > 0 && g < 1.8) {
            double ga = Math.min(ease(g / 0.2), 1 - ease((g - 1.4) / 0.4));
            float sc = (float) (3.2 + 1.0 * (1 - back(g / 0.35)));
            gradText(ctx, tr, "GEWONNEN", 320, 120, sc, ga, true);
            ctext(ctx, tr, "+1.25M", 320, 120 + 8 * sc + 6, 2.4f, ca(GR, ga));
        }
        // Toasts
        double[][] toasts = {{4.9, 0}, {6.4, 1}};
        String[][] tt = {{"Spieler1 hat gezahlt", "500k angekommen"}, {"Auszahlung fertig", "3 Spieler bezahlt"}};
        for (int i = 0; i < 2; i++) {
            double d0 = u - toasts[i][0];
            if (d0 < 0 || d0 > 1.4) continue;
            double ta = Math.min(ease(d0 / 0.25), 1 - ease((d0 - 1.1) / 0.3));
            float slide = (float) ((1 - ease(d0 / 0.3)) * 30);
            int x = 640 - 14 - 170 + (int) slide, y = 66;
            ToastyTheme.roundRectExact(ctx, x, y, 170, 28, 10, ca(0xFF140B2B, ta * 0.92));
            ToastyTheme.roundOutlineExact(ctx, x, y, 170, 28, 10, ca(ToastyTheme.BLUE, ta * 0.5));
            text(ctx, tr, tt[i][0], x + 10, y + 5, 1.0f, ca(0xFFF3EEFF, ta));
            text(ctx, tr, tt[i][1], x + 10, y + 16, 1.0f, ca(0xFFB8B5E6, ta));
        }
        text(ctx, tr, "Beispieldaten", 10, 346, 0.9f, ca(0xFF9A96C0, 0.7));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        double t = (System.currentTimeMillis() - start) / 1000.0;
        if (start == 0) t = 0;
        long win = mc.getWindow().getHandle();
        boolean click = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean key = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS;
        int bw = 60, bh = 20, bx = width - bw - 8, by = height - bh - 8;
        boolean over = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        if (t > 0.3 && ((click && !prevClick && over) || key)) { close(); return; }
        prevClick = click;
        if (t >= DUR) { close(); return; }

        int w = width, h = height;
        int dark = 0xFF05030C;
        ctx.fillGradient(0, 0, w, h, ToastyTheme.lerp(dark, ToastyTheme.PURPLE, 0.20f), ToastyTheme.lerp(dark, ToastyTheme.BLUE, 0.55f));
        // virtuelle 640x360-Flaeche, passend in den Bildschirm eingepasst
        float sc = (float) Math.min(w / 640.0, h / 360.0);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((w - 640 * sc) / 2f, (h - 360 * sc) / 2f);
        ctx.getMatrices().scale(sc, sc);

        if (t < T_INTRO) {
            int s = (int) (back(t / 1.0) * 150);
            int bob = (int) (Math.sin(t * 2.2) * 3);
            ToastyLogo.draw(ctx, 320 - s / 2, 125 + bob - s / 2, s);
            float ns = 5.4f;
            float tot = tr.getWidth(NAME) * ns, x = 320 - tot / 2f;
            for (int i = 0; i < NAME.length(); i++) {
                String ch = String.valueOf(NAME.charAt(i));
                double al = ease((t - 1.0 - i * 0.06) / 0.35);
                if (al > 0.02 && !ch.equals(" "))
                    text(ctx, tr, ch, x, (float) (205 + (1 - al) * 14), ns,
                        ToastyTheme.alpha(ToastyTheme.lerp(ToastyTheme.PURPLE, ToastyTheme.BLUE, i / 12f), a(al)));
                x += tr.getWidth(ch) * ns;
            }
            int ln = (int) (ease((t - 1.6) / 0.8) * 350);
            if (ln > 0) ToastyTheme.hGradient(ctx, 320 - ln / 2, 272, ln, 2, ToastyTheme.PURPLE, ToastyTheme.BLUE);
            String sub = "Fabric-Client für WindSMP";
            float ss = 1.9f;
            text(ctx, tr, sub, 320 - tr.getWidth(sub) * ss / 2f, 290, ss, col(0xF3EEFF, ease((t - 2.3) / 0.6)));
        } else if (t < T_MENU_END) {
            menuScene(ctx, tr, t - T_INTRO);
        } else if (t < T_OUT) {
            hudScene(ctx, tr, t - T_MENU_END);
        } else {
            double k = t - T_OUT;
            int s = (int) (back(k / 0.8) * 150);
            int bob = (int) (Math.sin(t * 2.2) * 3);
            ToastyLogo.draw(ctx, 320 - s / 2, 85 + bob - s / 2, s);
            float ns = (float) Math.min(5.0, 560.0 / tr.getWidth(NAME));
            gradText(ctx, tr, NAME, 320, 170, ns, ease((k - 0.5) / 0.6), true);
            double ka = ease((k - 1.0) / 0.5);
            int kw = 150, kh = 34, kx = 320 - kw / 2, ky = 225;
            ToastyTheme.roundRectExact(ctx, kx, ky + 4, kw, kh, 10, ca(0xFF140B2B, ka));
            ToastyTheme.roundRectExact(ctx, kx, ky, kw, kh, 10, ca(0xFF8B93F0, ka * 0.95));
            ToastyTheme.roundOutlineExact(ctx, kx, ky, kw, kh, 10, ca(0xFFFFFFFF, ka * 0.6));
            String ks = "Right Shift";
            text(ctx, tr, ks, 320 - tr.getWidth(ks) * 2.0f / 2f, ky + 9, 2.0f, ca(0xFF12082A, ka));
            String l1 = "Just one click away.", l2 = "Fabric 1.21.11";
            text(ctx, tr, l1, 320 - tr.getWidth(l1) * 1.9f / 2f, 280, 1.9f, col(0xF3EEFF, ease((k - 1.5) / 0.6)));
            text(ctx, tr, l2, 320 - tr.getWidth(l2) * 1.4f / 2f, 305, 1.4f, col(0xB8B5E6, ease((k - 1.8) / 0.6)));
        }
        ctx.getMatrices().popMatrix();

        // Ein- und Ausblenden + Skip-Hinweis
        double fade = 0;
        if (t < 0.4) fade = 1 - t / 0.4;
        double fo = clamp((t - (DUR - 0.5)) / 0.5);
        fade = Math.max(fade, fo);
        if (fade > 0.01) ctx.fill(0, 0, w, h, ToastyTheme.alpha(dark, a(fade)));
        for (double st : new double[]{T_INTRO, T_MENU_END, T_OUT}) {
            double d0 = t - st;
            if (d0 >= 0 && d0 < 0.15) ctx.fill(0, 0, w, h, ToastyTheme.alpha(0xFFFFFFFF, a((1 - d0 / 0.15) * 0.35)));
        }
        if (t > 0.3) {
            ToastyTheme.roundRectExact(ctx, bx, by, bw, bh, bh / 2, ToastyTheme.alpha(over ? ToastyTheme.PURPLE : 0xFF140B2B, over ? 0xF0 : 0xB8));
            ToastyTheme.roundOutlineExact(ctx, bx, by, bw, bh, bh / 2, ToastyTheme.alpha(ToastyTheme.BLUE, over ? 0xFF : 0x90));
            String sk = "Skip »";
            ctx.drawTextWithShadow(tr, sk, bx + (bw - tr.getWidth(sk)) / 2, by + (bh - 8) / 2, ToastyTheme.TEXT);
        }
    }
}
