package dev.toasty.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/** Replay der letzten Sekunden vor dem Tod als Karte von oben (Norden oben). */
public class ToastyDeathScreen extends Screen {
    private final Screen parent;
    private List<ToastyDeath.Frame> fr;
    private List<ToastyDeath.Hit> hits;
    private long t0, t1;
    private double cursor;          // ms seit t0
    private boolean playing = true;
    private double speed = 1.0;
    private long lastNano;
    private boolean prevClick = true;

    public ToastyDeathScreen(Screen parent) {
        super(Text.literal("Death-Replay"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        fr = ToastyDeath.lastFrames;
        hits = ToastyDeath.lastHits;
        if (fr.isEmpty()) return;
        t0 = fr.get(0).t(); t1 = fr.get(fr.size() - 1).t();
        lastNano = System.nanoTime();
        int y = height - 26, x = width / 2 - 155;
        button("Play/Pause", x, y, 90, () -> playing = !playing);
        button("-1 s", x + 94, y, 40, () -> cursor = Math.max(0, cursor - 1000));
        button("+1 s", x + 138, y, 40, () -> cursor = Math.min(t1 - t0, cursor + 1000));
        button("Tempo", x + 182, y, 50, () -> speed = speed == 1.0 ? 0.5 : speed == 0.5 ? 2.0 : 1.0);
        button("Zurück", x + 236, y, 74, this::close);
    }

    private void button(String label, int x, int y, int w, Runnable r) {
        addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal(label), b -> r.run()).dimensions(x, y, w, 20).build());
    }

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(parent); }

    @Override
    public boolean shouldPause() { return false; }

    private ToastyDeath.Frame at(long t) {
        ToastyDeath.Frame best = fr.get(0);
        for (ToastyDeath.Frame f : fr) { if (f.t() <= t) best = f; else break; }
        return best;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int dark = 0xFF05030C;
        ctx.fillGradient(0, 0, width, height, ToastyTheme.lerp(dark, ToastyTheme.PURPLE, 0.3f), ToastyTheme.lerp(dark, ToastyTheme.BLUE, 0.3f));
        ToastyTheme.gradientText(ctx, tr, "DEATH-REPLAY", width / 2 - tr.getWidth("DEATH-REPLAY") / 2, 8);
        if (fr == null || fr.isEmpty()) {
            ctx.drawCenteredTextWithShadow(tr, "Keine Aufzeichnung vorhanden.", width / 2, height / 2, ToastyTheme.MUTED);
            super.render(ctx, mx, my, delta);
            return;
        }
        long now = System.nanoTime();
        if (playing) cursor += (now - lastNano) / 1e6 * speed;
        lastNano = now;
        double total = t1 - t0;
        if (cursor >= total) { cursor = total; }
        ToastyDeath.Frame cur = at(t0 + (long) cursor);

        // Karte
        int mw = Math.min(width - 190, 340), mh = height - 90, mxl = 10, myt = 24;
        ToastyTheme.panel(ctx, mxl, myt, mw, mh);
        double minX = 1e9, maxX = -1e9, minZ = 1e9, maxZ = -1e9;
        for (ToastyDeath.Frame f : fr) { minX = Math.min(minX, f.x()); maxX = Math.max(maxX, f.x()); minZ = Math.min(minZ, f.z()); maxZ = Math.max(maxZ, f.z()); }
        double cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2;
        double span = Math.max(14, Math.max(maxX - minX, maxZ - minZ) + 16);
        double sc = Math.min(mw - 16, mh - 16) / span;
        final int ox = mxl + mw / 2, oy = myt + mh / 2;
        java.util.function.DoubleUnaryOperator px = x -> ox + (x - cx) * sc;
        java.util.function.DoubleUnaryOperator pz = z -> oy + (z - cz) * sc;
        // Spur
        for (ToastyDeath.Frame f : fr) {
            if (f.t() > t0 + cursor) break;
            int a = 60 + (int) (150.0 * (f.t() - t0) / Math.max(1, total));
            int x = (int) px.applyAsDouble(f.x()), y = (int) pz.applyAsDouble(f.z());
            ctx.fill(x, y, x + 2, y + 2, ToastyTheme.alpha(ToastyTheme.BLUE, Math.min(255, a)));
        }
        // andere
        for (ToastyDeath.Other o : cur.others()) {
            int x = (int) px.applyAsDouble(o.x()), y = (int) pz.applyAsDouble(o.z());
            int col = o.kind() == 'P' ? 0xFFFBBF24 : o.kind() == 'H' ? 0xFFFB7185 : 0xFF9CA3AF;
            ToastyTheme.roundRectExact(ctx, x - 2, y - 2, 5, 5, 2, col);
            if (o.kind() == 'P') ctx.drawTextWithShadow(tr, o.name(), x + 5, y - 4, 0xFFFBBF24);
        }
        // Spieler
        int ppx = (int) px.applyAsDouble(cur.x()), ppy = (int) pz.applyAsDouble(cur.z());
        double yaw = Math.toRadians(cur.yaw());
        int lx = (int) (ppx - Math.sin(yaw) * 9), ly = (int) (ppy + Math.cos(yaw) * 9);
        for (int i = 1; i <= 4; i++) ctx.fill((int) (ppx + (lx - ppx) * i / 4.0), (int) (ppy + (ly - ppy) * i / 4.0), (int) (ppx + (lx - ppx) * i / 4.0) + 1, (int) (ppy + (ly - ppy) * i / 4.0) + 1, ToastyTheme.TEXT);
        ToastyTheme.roundRectExact(ctx, ppx - 3, ppy - 3, 7, 7, 3, ToastyTheme.PURPLE);
        // Todesort
        ToastyDeath.Frame last = fr.get(fr.size() - 1);
        int dx = (int) px.applyAsDouble(last.x()), dy = (int) pz.applyAsDouble(last.z());
        ctx.drawTextWithShadow(tr, "x", dx - 2, dy - 4, ToastyTheme.BAD);
        ctx.drawTextWithShadow(tr, "N", mxl + mw - 10, myt + 4, ToastyTheme.MUTED);

        // Infos rechts
        int ix = mxl + mw + 10, iy = 24;
        ToastyTheme.panel(ctx, ix, iy, width - ix - 10, mh);
        double sec = (cursor - total) / 1000.0;
        ctx.drawTextWithShadow(tr, String.format(Locale.ROOT, "%.1f s", sec), ix + 6, iy + 6, ToastyTheme.TEXT);
        ctx.drawTextWithShadow(tr, "Tempo " + speed + "x", ix + 6, iy + 18, ToastyTheme.MUTED);
        int bw = Math.max(20, width - ix - 10 - 12);
        ToastyTheme.roundRectExact(ctx, ix + 6, iy + 32, bw, 6, 3, 0x55FFFFFF);
        ToastyTheme.roundRectExact(ctx, ix + 6, iy + 32, Math.max(3, (int) (bw * Math.min(1f, cur.hp() / 20f))), 6, 3, cur.hp() < 7 ? ToastyTheme.BAD : ToastyTheme.GOOD);
        ctx.drawTextWithShadow(tr, String.format(Locale.ROOT, "%.1f ❤", cur.hp()), ix + 6, iy + 42, ToastyTheme.TEXT);
        ctx.drawTextWithShadow(tr, "Schaden:", ix + 6, iy + 58, ToastyTheme.MUTED);
        int ry = iy + 70, shown = 0;
        for (int i = hits.size() - 1; i >= 0 && shown < 8; i--) {
            ToastyDeath.Hit h = hits.get(i);
            if (h.t() > t0 + cursor) continue;
            String line = String.format(Locale.ROOT, "-%.1f %s", h.amount(), h.src());
            ctx.drawTextWithShadow(tr, tr.trimToWidth(line, width - ix - 22), ix + 6, ry, 0xFFFB7185);
            ry += 11; shown++;
        }
        if (shown == 0) ctx.drawTextWithShadow(tr, "–", ix + 6, ry, ToastyTheme.MUTED);

        // Zeitleiste (anklickbar/ziehbar)
        int tx = 10, ty = height - 42, tw = width - 20;
        ToastyTheme.roundRectExact(ctx, tx, ty, tw, 6, 3, 0x44FFFFFF);
        int kx = tx + (int) (tw * (total <= 0 ? 0 : cursor / total));
        ToastyTheme.roundRectExact(ctx, tx, ty, Math.max(3, kx - tx), 6, 3, ToastyTheme.PURPLE);
        ToastyTheme.roundRectExact(ctx, kx - 3, ty - 2, 7, 10, 3, ToastyTheme.TEXT);
        long win = mc.getWindow().getHandle();
        boolean click = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (click && my >= ty - 5 && my <= ty + 12 && mx >= tx - 4 && mx <= tx + tw + 4) {
            cursor = Math.max(0, Math.min(total, (mx - tx) * total / tw));
        }
        prevClick = click;
        super.render(ctx, mx, my, delta);
    }
}
