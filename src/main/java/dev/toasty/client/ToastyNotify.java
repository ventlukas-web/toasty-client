package dev.toasty.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** Kleine Benachrichtigungen (Toasts) unten rechts im Spiel. */
public final class ToastyNotify {
    private record T(String title, String msg, long at, int color) {}
    private static final List<T> LIST = new ArrayList<>();
    private static final long LIFE = 3800;

    private ToastyNotify() {}

    public static synchronized void push(String title, String msg, int color) {
        LIST.add(new T(title, msg, System.currentTimeMillis(), color));
        while (LIST.size() > 5) LIST.remove(0);
    }

    public static synchronized void render(DrawContext ctx, TextRenderer tr, MinecraftClient mc) {
        long now = System.currentTimeMillis();
        LIST.removeIf(t -> now - t.at() > LIFE);
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int y = sh - 40;
        for (int i = LIST.size() - 1; i >= 0; i--) {
            T t = LIST.get(i);
            long el = now - t.at();
            String m = tr.trimToWidth(t.msg(), 200);
            int w = Math.max(tr.getWidth(m), tr.getWidth(t.title())) + 16, h = 26;
            float in = Math.min(1f, el / 250f), out = el > LIFE - 300 ? (LIFE - el) / 300f : 1f;
            int x = (int) (sw - 6 - w * in * Math.max(0f, out));
            ToastyTheme.panel(ctx, x, y - h, w, h);
            ToastyTheme.roundOutline(ctx, x, y - h, w, h, 6, ToastyTheme.alpha(t.color(), 0xC0));
            ctx.drawTextWithShadow(tr, t.title(), x + 8, y - h + 4, t.color());
            ctx.drawTextWithShadow(tr, m, x + 8, y - h + 14, ToastyTheme.TEXT);
            y -= h + 4;
        }
    }
}
