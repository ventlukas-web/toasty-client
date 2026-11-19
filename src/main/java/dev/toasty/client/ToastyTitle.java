package dev.toasty.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;

/** Start-Animation + Branding auf dem Hauptmenue. */
public final class ToastyTitle {
    private static long introStart = -1;
    private static boolean introDone = false;
    private static final long INTRO_MS = 2800;
    private static final String NAME = "TOASTY CLIENT";

    private ToastyTitle() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (isLoading(screen)) {
                return; // Lade-Animation entfernt (Performance)
            }
            if (!(screen instanceof TitleScreen)) return;
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) ->
                render(ctx, client, s, mx, my, delta));
        });
    }

    /** Ladebildschirme (Server beitreten / Welt laden) bekommen den Toasty-Look. */
    private static boolean isLoading(net.minecraft.client.gui.screen.Screen s) {
        if (!(s.getTitle().getContent() instanceof net.minecraft.text.TranslatableTextContent t)) return false;
        String k = t.getKey();
        return k.startsWith("connect.") || k.equals("multiplayer.downloadTerrain") || k.startsWith("menu.generating")
            || k.startsWith("menu.loading") || k.startsWith("selectWorld.data_read");
    }

    private static void loading(DrawContext ctx, MinecraftClient mc, net.minecraft.client.gui.screen.Screen s, int mx, int my, float delta) {
        if (!ToastyConfig.get().titleAnimation) return;
        TextRenderer tr = mc.textRenderer;
        int w = s.width, h = s.height;
        long now = System.currentTimeMillis();
        ctx.fillGradient(0, 0, w, h, 0xFF050B1E, 0xFF0B2A5B);
        for (int i = 0; i < 36; i++) {
            float seed = i * 37.17f;
            float px = (float) ((Math.sin(seed) * 0.5 + 0.5) * w);
            float py = h - ((now / 1000f * (16 + (i % 7) * 5) + (seed % 90) * 4) % (h + 20));
            int a = (int) (170 * (0.4 + 0.6 * Math.abs(Math.sin(now / 500.0 + i))));
            ctx.fill((int) px, (int) py, (int) px + 2, (int) py + 2, ToastyTheme.alpha(0xFF7DD3FC, a));
        }
        String name = "TOASTY CLIENT";
        int ls = Math.max(24, Math.min(96, h / 5));
        ToastyLogo.draw(ctx, (w - ls) / 2, Math.max(4, h / 2 - ls - 40), ls);
        int total = tr.getWidth(name);
        float scale = Math.max(3f, Math.min(10f, w * 0.85f / total));
        float pulse = 1f + 0.015f * (float) Math.sin(now / 400.0);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(w / 2f, h / 2f - 8 * scale);
        ctx.getMatrices().scale(scale * pulse, scale * pulse);
        int x = -total / 2;
        for (int i = 0; i < name.length(); i++) {
            String ch = String.valueOf(name.charAt(i));
            float k = 0.5f + 0.5f * (float) Math.sin(now / 350.0 + i * 0.5);
            ctx.drawTextWithShadow(tr, ch, x, 0, ToastyTheme.lerp(0xFF3B82F6, 0xFFBAE6FD, k));
            x += tr.getWidth(ch);
        }
        ctx.getMatrices().popMatrix();
        int bw = (int) (total * scale * 0.6f), bx = (w - bw) / 2, by = (int) (h / 2f + 6);
        ToastyTheme.roundRect(ctx, bx, by, bw, 6, 3, 0x66FFFFFF);
        float t = (now % 1800) / 1800f;
        int seg = bw / 3, sx = bx + (int) ((bw - seg) * (0.5f - 0.5f * (float) Math.cos(t * Math.PI * 2)));
        ToastyTheme.roundRect(ctx, sx, by, seg, 6, 3, 0xFF60A5FA);
        String st = s.getTitle().getString();
        ctx.drawTextWithShadow(tr, st, (w - tr.getWidth(st)) / 2, by + 16, ToastyTheme.TEXT);
        for (var e : s.children()) {
            if (e.getClass() == net.minecraft.client.gui.widget.ButtonWidget.class) styledButton(ctx, mc, (net.minecraft.client.gui.widget.ButtonWidget) e, mx, my);
            else if (e instanceof net.minecraft.client.gui.Drawable d) d.render(ctx, mx, my, delta);
        }
    }

    private static final java.util.Map<Object, Float> HOVER = new java.util.WeakHashMap<>();

    /** Vanilla-Button im Toasty-Design: runde Pille, leicht durchsichtig, weiches Hover. */
    public static void styledButton(DrawContext ctx, MinecraftClient mc, net.minecraft.client.gui.widget.ClickableWidget b, int mx, int my) {
        if (!b.visible) return;
        boolean hov = b.active && b.isMouseOver(mx, my);
        float cur = HOVER.getOrDefault(b, 0f);
        cur += ((hov ? 1f : 0f) - cur) * 0.25f;
        HOVER.put(b, cur);
        int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
        int a = Math.max(60, Math.min(255, ToastyConfig.get().buttonAlpha));
        int fill = ToastyTheme.lerp(0xFF132039, ToastyTheme.lerp(ToastyTheme.PURPLE, ToastyTheme.BLUE, 0.65f), 0.16f + 0.44f * cur);
        ToastyTheme.roundRect(ctx, x, y, w, h, 5, ToastyTheme.alpha(fill, b.active ? a : a / 2));
        if (cur > 0.05f) ToastyTheme.roundOutline(ctx, x, y, w, h, 5, ToastyTheme.alpha(ToastyTheme.BLUE, (int) (0xB0 * cur)));
        String s = b.getMessage().getString();
        ctx.drawTextWithShadow(mc.textRenderer, s, x + (w - mc.textRenderer.getWidth(s)) / 2, y + (h - 8) / 2, b.active ? ToastyTheme.TEXT : 0xFF8A86A8);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    /** Ersetzt das Panorama: Theme-Verlauf, riesiger Toast, schwebende Farbflaechen, Funken. Buttons werden danach neu darueber gezeichnet. */
    private static void background(DrawContext ctx, MinecraftClient mc, net.minecraft.client.gui.screen.Screen s, int mx, int my, float delta) {
        int w = s.width, h = s.height;
        long now = System.currentTimeMillis();
        double ph = now / 1000.0;
        int dark = 0xFF05030C;
        ctx.fillGradient(0, 0, w, h, ToastyTheme.lerp(dark, ToastyTheme.PURPLE, 0.20f), ToastyTheme.lerp(dark, ToastyTheme.BLUE, 0.55f));

        // Schleier, damit Buttons lesbar bleiben
        ctx.fillGradient(0, 0, w, h, ToastyTheme.alpha(ToastyTheme.lerp(dark, ToastyTheme.PURPLE, 0.12f), 0x88),
            ToastyTheme.alpha(ToastyTheme.lerp(dark, ToastyTheme.BLUE, 0.30f), 0xA8));

        // riesiger Toast rechts neben den Buttons (Gesicht frei sichtbar), schwebt und wippt leicht
        int ls = (int) Math.min(h * 0.70, w * 0.34);
        int lx = (int) (w * 0.825 - ls / 2.0 + Math.sin(ph * 0.6) * ls * 0.015);
        int ly = (h - ls) / 2 + (int) (Math.sin(ph * 0.9) * ls * 0.025);
        ToastyLogo.draw(ctx, lx, ly, ls);

        // Buttons & Co. nochmal obendrauf
        for (var e : s.children()) if (e instanceof net.minecraft.client.gui.Drawable d) d.render(ctx, mx, my, delta);
    }

    private static void render(DrawContext ctx, MinecraftClient mc, net.minecraft.client.gui.screen.Screen scr, int mx, int my, float delta) {
        int w = scr.width, h = scr.height;
        TextRenderer tr = mc.textRenderer;
        long now = System.currentTimeMillis();
        if (ToastyConfig.get().titleToast) background(ctx, mc, scr, mx, my, delta);

        // dauerhaftes Branding oben mittig, gross, mit Glanz-Effekt
        String tag = "Toasty Client";
        float bs = 2.5f;
        int tw = tr.getWidth(tag);
        float bx = (w - tw * bs) / 2f;
        ToastyLogo.draw(ctx, (int) bx - 30, 6, 24);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(bx, 4);
        ctx.getMatrices().scale(bs, bs);
        for (int i = 0, x = 0; i < tag.length(); i++) {
            String ch = String.valueOf(tag.charAt(i));
            float k = 0.5f + 0.5f * (float) Math.sin(now / 450.0 + i * 0.55);
            ctx.drawTextWithShadow(tr, ch, x, 0, ToastyTheme.lerp(ToastyTheme.PURPLE, ToastyTheme.BLUE, k));
            x += tr.getWidth(ch);
        }
        ctx.getMatrices().popMatrix();
        ToastyTheme.hGradient(ctx, (int) bx, (int) (4 + 8 * bs + 2), (int) (tw * bs), 2, ToastyTheme.PURPLE, ToastyTheme.BLUE);

        // Intro (einmal pro Spielstart)
        if (introStart < 0 || introDone) return;
        float t = (now - introStart) / (float) INTRO_MS;
        if (t >= 1f) { introDone = true; return; }
        float fade = t < 0.78f ? 1f : 1f - (t - 0.78f) / 0.22f;
        int bgA = (int) (235 * fade);

        ctx.fillGradient(0, 0, w, h,
            ToastyTheme.alpha(ToastyTheme.lerp(0xFF05030C, ToastyTheme.PURPLE, 0.18f), bgA),
            ToastyTheme.alpha(ToastyTheme.lerp(0xFF05030C, ToastyTheme.BLUE, 0.22f), bgA));

        // aufsteigende Funken
        for (int i = 0; i < 40; i++) {
            float seed = i * 37.17f;
            float px = (float) ((Math.sin(seed) * 0.5 + 0.5) * w);
            float speed = 18 + (i % 7) * 6;
            float py = h - ((now / 1000f * speed + (seed % 90) * 4) % (h + 20));
            int a = (int) (150 * fade * (0.4 + 0.6 * Math.abs(Math.sin(now / 500.0 + i))));
            int sz = 1 + (i % 3 == 0 ? 1 : 0);
            ctx.fill((int) px, (int) py, (int) px + sz, (int) py + sz,
                ToastyTheme.alpha(ToastyTheme.lerp(ToastyTheme.PURPLE, ToastyTheme.BLUE, (i % 10) / 9f), a));
        }

        // Buchstaben erscheinen nacheinander (gross, fuellen die Breite)
        int total = 0;
        for (int i = 0; i < NAME.length(); i++) total += tr.getWidth(String.valueOf(NAME.charAt(i)));
        float scale = Math.max(3f, Math.min(10f, w * 0.88f / total));
        int cy = (int) (h / 2f - 4 * scale);
        float startX = (w - total * scale) / 2f;
        float x = startX;
        for (int i = 0; i < NAME.length(); i++) {
            String ch = String.valueOf(NAME.charAt(i));
            float tl = 0.10f + i * 0.03f;
            float a = clamp01((t - tl) / 0.09f);
            int cw = tr.getWidth(ch);
            if (a > 0.03f && !ch.equals(" ")) {
                ctx.getMatrices().pushMatrix();
                ctx.getMatrices().translate(x, cy + (1f - a) * 14f);
                ctx.getMatrices().scale(scale, scale);
                int col = ToastyTheme.lerp(ToastyTheme.PURPLE, ToastyTheme.BLUE, (float) i / (NAME.length() - 1));
                ctx.drawTextWithShadow(tr, ch, 0, 0, ToastyTheme.alpha(col, (int) (255 * a * fade)));
                ctx.getMatrices().popMatrix();
            }
            x += cw * scale;
        }

        // Linie waechst aus der Mitte (unter dem Namen)
        float lineP = clamp01(t / 0.35f);
        int lw = (int) (total * scale * lineP);
        int ly = (int) (cy + 8 * scale + 6);
        if (lw > 0) {
            ToastyTheme.hGradient(ctx, (w - lw) / 2, ly, lw, 3,
                ToastyTheme.alpha(ToastyTheme.PURPLE, (int) (255 * fade)),
                ToastyTheme.alpha(ToastyTheme.BLUE, (int) (255 * fade)));
        }

        // Untertitel
        float sa = clamp01((t - 0.55f) / 0.15f) * fade;
        if (sa > 0.03f) {
            String sub = "Gamble · HUD · Tools  —  für WindSMP";
            ctx.drawTextWithShadow(tr, sub, (w - tr.getWidth(sub)) / 2, ly + 10,
                ToastyTheme.alpha(ToastyTheme.TEXT, (int) (255 * sa)));
        }
    }
}
