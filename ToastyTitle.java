package dev.toasty.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

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
            if (screen instanceof net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen
                || screen instanceof net.minecraft.client.gui.screen.world.SelectWorldScreen) {
                // Server-/Weltauswahl im Toasty-Design: Hintergrund ersetzen, Widgets danach neu darueber zeichnen
                ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
                    plainBackground(ctx, s.width, s.height);
                    for (var e : s.children()) if (e instanceof net.minecraft.client.gui.Drawable d) d.render(ctx, mx, my, delta);
                });
                return;
            }
            if (!(screen instanceof TitleScreen)) return;
            if (!introDone && ToastyConfig.get().introTrailer) {
                introDone = true;
                client.execute(() -> client.setScreen(new ToastyTrailerScreen(screen)));
                return;
            }
            joinBtn = null;
            ToastyConfig jc = ToastyConfig.get();
            int sz = Math.max(1, Math.min(5, jc.startBtnSize));
            int bw = 160 + 30 * sz, bh = 20 + 4 * sz;
            if (!jc.mpSizeMigrated) { jc.mpSizeMigrated = true; jc.mpBtnSize = 0; jc.save(); }
            int msz = Math.max(0, Math.min(5, jc.mpBtnSize));
            int mw = msz == 0 ? 200 : 160 + 30 * msz, mh = msz == 0 ? 20 : 20 + 4 * msz;
            // Multiplayer-Button: groesser, die Buttons darunter ruecken nach
            for (var vb : new java.util.ArrayList<>(Screens.getButtons(screen))) {
                if (vb.getMessage().getContent() instanceof net.minecraft.text.TranslatableTextContent tc && tc.getKey().equals("menu.multiplayer")) {
                    if (msz == 0) break;
                    int dh = mh - vb.getHeight(), my0 = vb.getY();
                    for (var ob : Screens.getButtons(screen)) if (ob != vb && ob.getY() > my0) ob.setY(ob.getY() + dh);
                    vb.setHeight(mh); vb.setWidth(mw); vb.setX(screen.width / 2 - mw / 2);
                    break;
                }
            }
            if (jc.joinButton) {
                // Beitreten-Button ueber den Vanilla-Buttons, groesser, ueberdeckt nichts
                int minY = screen.height;
                for (var vb : Screens.getButtons(screen)) minY = Math.min(minY, vb.getY());
                int by = minY - bh - 6;
                int need = 34 - by;
                if (need > 0) { for (var vb : Screens.getButtons(screen)) vb.setY(vb.getY() + need); by += need; }
                String nm = jc.serverName == null || jc.serverName.isBlank() ? "Server" : jc.serverName.trim();
                joinBtn = ButtonWidget.builder(Text.literal(nm + " beitreten"), b -> joinServer(client, screen))
                    .dimensions(screen.width / 2 - bw / 2, by, bw, bh).build();
                Screens.getButtons(screen).add(joinBtn);
            }
            // Mod-Menue-Knopf unten rechts
            Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Mod-Menü"), b -> client.setScreen(new ToastyMenu(screen)))
                .dimensions(screen.width - 96, screen.height - 36, 88, 20).build());
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
            ToastyTheme.roundRectExact(ctx, (int) px, (int) py, 2, 2, 1, ToastyTheme.alpha(0xFF7DD3FC, a));
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

    private static ButtonWidget joinBtn;

    /** Tritt dem in den Einstellungen eingetragenen Server bei (Standard: WindSMP / windsmp.net). */
    public static void joinServer(MinecraftClient mc, net.minecraft.client.gui.screen.Screen from) {
        ToastyConfig cfg = ToastyConfig.get();
        String ip = cfg.serverIp == null || cfg.serverIp.isBlank() ? "windsmp.net" : cfg.serverIp.trim();
        String name = cfg.serverName == null || cfg.serverName.isBlank() ? "Server" : cfg.serverName.trim();
        net.minecraft.client.network.ServerAddress addr = net.minecraft.client.network.ServerAddress.parse(ip);
        net.minecraft.client.network.ServerInfo info = new net.minecraft.client.network.ServerInfo(name, ip, net.minecraft.client.network.ServerInfo.ServerType.OTHER);
        net.minecraft.client.gui.screen.multiplayer.ConnectScreen.connect(from, mc, addr, info, false, null);
    }


    private static final float[][] SKY = new float[70][4]; // x, y, Phase, Groesse
    static { java.util.Random r = new java.util.Random(7); for (float[] st : SKY) { st[0] = r.nextFloat(); st[1] = r.nextFloat(); st[2] = r.nextFloat() * 6.28f; st[3] = r.nextFloat(); } }

    /** Himmel im Menue: funkelnde Sterne und langsam ziehende weiche Wolken (leichtgewichtig, nur Rechtecke). */
    public static void sky(DrawContext ctx, int w, int h) {
        long now = System.currentTimeMillis();
        double t = now / 1000.0;
        for (int i = 0; i < 3; i++) {
            int cw = (int) (w * (0.30 + 0.06 * i)), ch = Math.max(8, h / 14);
            int cx = (int) (((t * (5 + 3 * i) + i * w * 0.4) % (w + cw)) - cw);
            int cy = (int) (h * (0.12 + 0.22 * i));
            ToastyTheme.roundRectExact(ctx, cx, cy, cw, ch, ch / 2, ToastyTheme.alpha(ToastyTheme.lerp(ToastyTheme.PURPLE, ToastyTheme.BLUE, 0.5f), 0x14));
            ToastyTheme.roundRectExact(ctx, cx + cw / 5, cy - ch / 2, cw / 2, ch, ch / 2, ToastyTheme.alpha(ToastyTheme.BLUE, 0x10));
        }
        for (float[] st : SKY) {
            double tw = 0.5 + 0.5 * Math.sin(t * (1.0 + st[3]) + st[2]);
            int a = (int) (40 + 190 * tw * (0.4 + 0.6 * st[3]));
            int x = (int) (st[0] * w), y = (int) (st[1] * h * 0.85), sz = st[3] > 0.85f ? 2 : 1;
            int col = ToastyTheme.alpha(ToastyTheme.lerp(0xFFFFFFFF, ToastyTheme.BLUE, st[3] * 0.5f), a);
            ToastyTheme.roundRectExact(ctx, x, y, sz, sz, sz / 2, col);
            if (sz == 2 && tw > 0.7) { ctx.fill(x - 1, y, x, y + 1, ToastyTheme.alpha(col, 0x70)); ctx.fill(x + 2, y, x + 3, y + 1, ToastyTheme.alpha(col, 0x70)); }
        }
    }

    /** Theme-Hintergrund ohne Toast-Logo (Server-/Weltauswahl). */
    private static void plainBackground(DrawContext ctx, int w, int h) {
        int dark = 0xFF05030C;
        ctx.fillGradient(0, 0, w, h, ToastyTheme.lerp(dark, ToastyTheme.PURPLE, 0.20f), ToastyTheme.lerp(dark, ToastyTheme.BLUE, 0.55f));
        ctx.fillGradient(0, 0, w, h, ToastyTheme.alpha(ToastyTheme.lerp(dark, ToastyTheme.PURPLE, 0.12f), 0x88),
            ToastyTheme.alpha(ToastyTheme.lerp(dark, ToastyTheme.BLUE, 0.30f), 0xA8));
        if (ToastyConfig.get().menuSky) sky(ctx, w, h);
    }

    /** Vanilla-Button im Toasty-Design: runde Pille, leicht durchsichtig, weiches Hover. */
    public static void styledButton(DrawContext ctx, MinecraftClient mc, net.minecraft.client.gui.widget.ClickableWidget b, int mx, int my) {
        if (!b.visible) return;
        boolean hov = b.active && b.isMouseOver(mx, my);
        float cur = HOVER.getOrDefault(b, 0f);
        cur += ((hov ? 1f : 0f) - cur) * 0.25f;
        HOVER.put(b, cur);
        int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
        int a = Math.max(60, Math.min(255, ToastyConfig.get().buttonAlpha));
        boolean big = b == joinBtn;
        int edge = ToastyTheme.lerp(ToastyTheme.PURPLE, ToastyTheme.BLUE, 0.5f);
        if (big) {
            // Haupt-Button: ruhiger Zwei-Farben-Verlauf, duenne helle Kante, kein Glanz-Effekt
            int cA = ToastyTheme.lerp(ToastyTheme.PURPLE, 0xFFFFFFFF, 0.06f + 0.10f * cur), cB = ToastyTheme.lerp(ToastyTheme.BLUE, 0xFFFFFFFF, 0.06f + 0.10f * cur);
            int bx = x + h / 2, bw2 = w - h;
            ToastyTheme.roundRectExact(ctx, x, y + 1, w, h, h / 2, 0x40000000);
            ToastyTheme.roundRectExact(ctx, x, y, h, h, h / 2, cA);
            ToastyTheme.roundRectExact(ctx, x + w - h, y, h, h, h / 2, cB);
            ToastyTheme.hGradient(ctx, bx, y, bw2, h, cA, cB);
            ToastyTheme.roundOutlineExact(ctx, x, y, w, h, h / 2, ToastyTheme.alpha(0xFFFFFFFF, 0x50));
        } else {
            // Mattglas: dunkle, leicht durchscheinende Flaeche mit feiner Kante; Hover = heller + Akzentkante
            int base = ToastyTheme.lerp(0xFF0E1426, edge, 0.10f + 0.22f * cur);
            int fa = (int) ((a * 0.70f) * (b.active ? 1f : 0.5f)) + (int) (40 * cur);
            ToastyTheme.roundRect(ctx, x, y, w, h, 5, ToastyTheme.alpha(base, Math.min(255, fa)));
            ToastyTheme.roundOutline(ctx, x, y, w, h, 5, cur > 0.05f
                ? ToastyTheme.alpha(edge, (int) (0x30 + 0x90 * cur)) : 0x26FFFFFF);
        }
        String s = b.getMessage().getString();
        ctx.drawTextWithShadow(mc.textRenderer, s, x + (w - mc.textRenderer.getWidth(s)) / 2, y + (h - 8) / 2, b.active ? ToastyTheme.TEXT : 0xFF8A86A8);
    }

    /** Karte links neben den Buttons: Logo, Begruessung, echte Session-Werte, Motto. Statisch gezeichnet (kein Lag). */
    private static void leftCard(DrawContext ctx, TextRenderer tr, int w, int h) {
        int cw = Math.min(176, w / 2 - 125);
        if (cw < 120) return;
        int ch = 128, cx = Math.max(10, (w / 2 - 110 - cw) / 2), cy = h / 4 + 36;
        ToastyTheme.panel(ctx, cx, cy, cw, ch, 0xB8);
        ToastyLogo.draw(ctx, cx + 10, cy + 8, 26);
        ToastyTheme.gradientText(ctx, tr, "Toasty Client", cx + 42, cy + 10);
        ctx.drawTextWithShadow(tr, "v1.3.0 · Fabric 1.21.11", cx + 42, cy + 22, ToastyTheme.MUTED);

        String user = MinecraftClient.getInstance().getSession().getUsername();
        ctx.drawTextWithShadow(tr, "Hi, " + user, cx + 10, cy + 48, ToastyTheme.TEXT);
        String[][] rows = {
            {"Rounds", String.valueOf(ToastyStats.rounds)},
            {"Winrate", String.format(java.util.Locale.ROOT, "%.0f %%", ToastyStats.winrate())},
            {"Profit", ToastyStats.fmt(ToastyStats.profit())},
        };
        int y = cy + 63;
        for (String[] r : rows) {
            ctx.drawTextWithShadow(tr, r[0], cx + 10, y, ToastyTheme.MUTED);
            int vc = r[0].equals("Profit") ? (ToastyStats.profit() >= 0 ? ToastyTheme.GOOD : ToastyTheme.BAD) : ToastyTheme.TEXT;
            ctx.drawTextWithShadow(tr, r[1], cx + cw - 10 - tr.getWidth(r[1]), y, vc);
            y += 12;
        }
        String motto = "Frisch getoastet.";
        ctx.drawTextWithShadow(tr, motto, cx + (cw - tr.getWidth(motto)) / 2, cy + ch - 16, ToastyTheme.lerp(ToastyTheme.PURPLE, ToastyTheme.BLUE, 0.6f));
    }

    /** Farbton einer Farbe verschieben (fuer mehrfarbige Verlaeufe). */
    private static int hueShift(int argb, float d) {
        float[] hsb = java.awt.Color.RGBtoHSB((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, null);
        int rgb = java.awt.Color.HSBtoRGB((hsb[0] + d + 1f) % 1f, Math.min(1f, hsb[1] * 1.1f), Math.max(0.75f, hsb[2]));
        return 0xFF000000 | (rgb & 0xFFFFFF);
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

        if (ToastyConfig.get().menuSky) sky(ctx, w, h);

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
        // Branding oben mittig: Poppins Bold, Farbverlauf Lila -> Blau (statisch, ohne Unterstrich)
        String tag = "TOASTY CLIENT";
        net.minecraft.text.Style fs = net.minecraft.text.Style.EMPTY.withFont(
            new net.minecraft.text.StyleSpriteSource.Font(net.minecraft.util.Identifier.of("toastyclient", "title")));
        float bs = 2.1f;
        int gap = 1, n = tag.length();
        int[] gwid = new int[n];
        int gsum = 0;
        for (int i = 0; i < n; i++) { gwid[i] = tr.getWidth(net.minecraft.text.Text.literal(String.valueOf(tag.charAt(i))).setStyle(fs)); gsum += gwid[i] + gap; }
        float bx = (w - gsum * bs) / 2f;
        ToastyLogo.draw(ctx, (int) bx - 32, 7, 26);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(bx, 6);
        ctx.getMatrices().scale(bs, bs);
        int cxp = 0;
        for (int i = 0; i < n; i++) {
            float k2 = n <= 1 ? 0f : i / (float) (n - 1);
            int col = ToastyTheme.lerp(ToastyTheme.lerp(ToastyTheme.PURPLE, 0xFFC084FC, 0.35f), ToastyTheme.lerp(ToastyTheme.BLUE, 0xFF7DD3FC, 0.35f), k2);
            ctx.drawTextWithShadow(tr, net.minecraft.text.Text.literal(String.valueOf(tag.charAt(i))).setStyle(fs), cxp, 0, col);
            cxp += gwid[i] + gap;
        }
        ctx.getMatrices().popMatrix();

        leftCard(ctx, tr, w, h);

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
            ToastyTheme.roundRectExact(ctx, (int) px, (int) py, sz, sz, sz / 2,
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

        int ly = (int) (cy + 8 * scale + 6);

        // Untertitel
        float sa = clamp01((t - 0.55f) / 0.15f) * fade;
        if (sa > 0.03f) {
            String sub = "Gamble · HUD · Tools";
            ctx.drawTextWithShadow(tr, sub, (w - tr.getWidth(sub)) / 2, ly + 10,
                ToastyTheme.alpha(ToastyTheme.TEXT, (int) (255 * sa)));
        }
    }
}
