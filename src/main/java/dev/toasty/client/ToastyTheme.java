package dev.toasty.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Lila / Hellblau Design. */
public final class ToastyTheme {
    /** Hauptfarbe (Standard Lila) und Zweitfarbe (Standard Hellblau) - einstellbar im Design-Tab. */
    public static int PURPLE = 0xFFA855F7;
    public static int BLUE = 0xFF7DD3FC;
    public static int BG = 0xD0140B2B;
    public static final int TEXT = 0xFFF3EEFF;
    public static final int MUTED = 0xFFB8B5E6;
    public static final int GOOD = 0xFF86EFAC;
    public static final int BAD = 0xFFFDA4AF;

    private ToastyTheme() {}

    /** "#RRGGBB" (mit oder ohne #) -> ARGB, sonst null. */
    public static Integer parseHex(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (!s.matches("[0-9a-fA-F]{6}")) return null;
        return 0xFF000000 | Integer.parseInt(s, 16);
    }

    public static String toHex(int argb) { return String.format("#%06X", argb & 0xFFFFFF); }

    public static void reload(ToastyConfig c) {
        Integer a = parseHex(c.color1), b = parseHex(c.color2);
        if (a != null) PURPLE = a;
        if (b != null) BLUE = b;
        int op = Math.max(32, Math.min(255, c.bgOpacity));
        BG = alpha(lerp(0xFF0A0614, PURPLE, 0.10f), op);
    }

    /** Hintergrundfarbe mit relativer Deckkraft (a = 0..255 bei voller Einstellung). */
    public static int bgA(int a) {
        return alpha(BG, ((BG >>> 24) * a) / 255);
    }

    public static int lerp(int a, int b, float t) {
        int r = 0;
        for (int s = 24; s >= 0; s -= 8) {
            int ca = (a >> s) & 0xFF, cb = (b >> s) & 0xFF;
            r |= ((int) (ca + (cb - ca) * t) & 0xFF) << s;
        }
        return r;
    }

    public static int alpha(int color, int a) {
        return (color & 0xFFFFFF) | ((Math.max(0, Math.min(255, a))) << 24);
    }

    public static void hGradient(DrawContext ctx, int x, int y, int w, int h, int c1, int c2) {
        for (int i = 0; i < w; i += 3) {
            ctx.fill(x + i, y, x + Math.min(w, i + 3), y + h, lerp(c1, c2, w <= 1 ? 0 : (float) i / (w - 1)));
        }
    }

    // ---------- Runde Ecken (geglaettet) ----------

    /** Wirklicher Radius: kleine Elemente werden zur Pille, grosse bekommen weiche, grosse Ecken. */
    private static int eff(int w, int h, int r) {
        int m = Math.min(w, h) / 2;
        if (r <= 0) return 0;
        if (h <= 26) return m;
        return Math.min(m, Math.max(r * 2, 10));
    }

    private static float cov(double d, double r) { return (float) Math.max(0, Math.min(1, r - d + 0.5)); }

    private static int fade(int c, float f) {
        int a = Math.round(((c >>> 24) & 0xFF) * f);
        return (a << 24) | (c & 0xFFFFFF);
    }

    public static void roundRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        roundRectExact(ctx, x, y, w, h, eff(w, h, r), color);
    }

    public static void roundOutline(DrawContext ctx, int x, int y, int w, int h, int r, int c) {
        roundOutlineExact(ctx, x, y, w, h, eff(w, h, r), c);
    }

    private static final java.util.Map<Integer, float[][]> CORNER = new java.util.HashMap<>();

    /** Pro Radius: [0][i] = erste voll gedeckte Spalte, [1][i] = Deckung des Randpixels davor. Einmal berechnet. */
    private static float[][] corner(int r) {
        float[][] c = CORNER.get(r);
        if (c != null) return c;
        c = new float[2][r];
        for (int i = 0; i < r; i++) {
            double dyy = r - (i + 0.5);
            int full = r;
            for (int j = 0; j < r; j++) {
                if (cov(Math.hypot(r - (j + 0.5), dyy), r) >= 1f) { full = j; break; }
            }
            c[0][i] = full;
            c[1][i] = full > 0 ? cov(Math.hypot(r - (full - 1 + 0.5), dyy), r) : 0f;
        }
        CORNER.put(r, c);
        return c;
    }

    private static final java.util.Map<Integer, int[]> HARD = new java.util.HashMap<>();

    /** Erste Spalte je Zeile, die zu >= 50 % gedeckt ist (harte Kante, ohne Glaettungspixel). */
    private static int[] hard(int r) {
        int[] c = HARD.get(r);
        if (c != null) return c;
        c = new int[Math.max(r, 0)];
        for (int i = 0; i < r; i++) {
            double dyy = r - (i + 0.5);
            int f = r;
            for (int j = 0; j < r; j++) if (cov(Math.hypot(r - (j + 0.5), dyy), r) >= 0.5f) { f = j; break; }
            c[i] = f;
        }
        HARD.put(r, c);
        return c;
    }

    /** Jedes ctx.fill ist in 1.21.x ein eigenes GUI-Element - viele Elemente kosten viel. Grosse Formen daher ohne Glaettungspixel. */
    public static void roundRectExact(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { ctx.fill(x, y, x + w, y + h, color); return; }
        ctx.fill(x, y + r, x + w, y + h - r, color);
        if (r > 6) {
            int[] f = hard(r);
            int step = r >= 100 ? 4 : r >= 32 ? 2 : 1;
            for (int i = 0; i < r; i += step) {
                int full = f[i];
                int n = Math.min(step, r - i);
                ctx.fill(x + full, y + i, x + w - full, y + i + n, color);
                ctx.fill(x + full, y + h - n - i, x + w - full, y + h - i, color);
            }
            return;
        }
        float[][] c = corner(r);
        for (int i = 0; i < r; i++) {
            int full = (int) c[0][i];
            ctx.fill(x + full, y + i, x + w - full, y + i + 1, color);
            ctx.fill(x + full, y + h - 1 - i, x + w - full, y + h - i, color);
            if (full > 0 && c[1][i] > 0f) {
                int cc = fade(color, c[1][i]);
                ctx.fill(x + full - 1, y + i, x + full, y + i + 1, cc);
                ctx.fill(x + w - full, y + i, x + w - full + 1, y + i + 1, cc);
                ctx.fill(x + full - 1, y + h - 1 - i, x + full, y + h - i, cc);
                ctx.fill(x + w - full, y + h - 1 - i, x + w - full + 1, y + h - i, cc);
            }
        }
    }

    /** Sehr billige Abrundung (3 Fuellungen) fuer viele kleine Flaechen, z. B. Inventar-Slots. */
    public static void softRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static final java.util.Map<Integer, float[]> OUT = new java.util.HashMap<>();

    private static float[] outlinePx(int r) {
        float[] o = OUT.get(r);
        if (o != null) return o;
        java.util.ArrayList<Float> l = new java.util.ArrayList<>();
        for (int i = 0; i < r; i++) {
            double dyy = r - (i + 0.5);
            int j0 = Math.max(0, (int) (r - Math.sqrt(Math.max(0, (double) r * r - dyy * dyy))) - 1);
            for (int j = j0; j < r; j++) {
                double d = Math.hypot(r - (j + 0.5), dyy);
                if (d < r - 1.6) break;
                float cv = cov(d, r) - cov(d, r - 1);
                if (cv <= 0f) continue;
                l.add((float) j); l.add((float) i); l.add(cv);
            }
        }
        o = new float[l.size()];
        for (int k = 0; k < o.length; k++) o[k] = l.get(k);
        OUT.put(r, o);
        return o;
    }

    public static void roundOutlineExact(DrawContext ctx, int x, int y, int w, int h, int r, int c) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            ctx.fill(x, y, x + w, y + 1, c); ctx.fill(x, y + h - 1, x + w, y + h, c);
            ctx.fill(x, y + 1, x + 1, y + h - 1, c); ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
            return;
        }
        ctx.fill(x + r, y, x + w - r, y + 1, c);
        ctx.fill(x + r, y + h - 1, x + w - r, y + h, c);
        ctx.fill(x, y + r, x + 1, y + h - r, c);
        ctx.fill(x + w - 1, y + r, x + w, y + h - r, c);
        if (r > 6) {
            int[] fo = hard(r), fi = hard(r - 1);
            ctx.fill(x + fo[0], y, x + w - fo[0], y + 1, c);
            ctx.fill(x + fo[0], y + h - 1, x + w - fo[0], y + h, c);
            for (int i = 1; i < r; i++) {
                int o = fo[i], in = Math.max(o + 1, 1 + fi[i - 1]);
                ctx.fill(x + o, y + i, x + in, y + i + 1, c);
                ctx.fill(x + w - in, y + i, x + w - o, y + i + 1, c);
                ctx.fill(x + o, y + h - 1 - i, x + in, y + h - i, c);
                ctx.fill(x + w - in, y + h - 1 - i, x + w - o, y + h - i, c);
            }
            return;
        }
        float[] px = outlinePx(r);
        for (int k = 0; k < px.length; k += 3) {
            int j = (int) px[k], i = (int) px[k + 1];
            int cc = fade(c, px[k + 2]);
            ctx.fill(x + j, y + i, x + j + 1, y + i + 1, cc);
            ctx.fill(x + w - 1 - j, y + i, x + w - j, y + i + 1, cc);
            ctx.fill(x + j, y + h - 1 - i, x + j + 1, y + h - i, cc);
            ctx.fill(x + w - 1 - j, y + h - 1 - i, x + w - j, y + h - i, cc);
        }
    }

    public static void panel(DrawContext ctx, int x, int y, int w, int h, int bgAlpha) {
        roundRect(ctx, x, y, w, h, 6, alpha(BG, bgAlpha));
        roundOutline(ctx, x, y, w, h, 6, alpha(lerp(PURPLE, BLUE, 0.5f), 0x60));
    }

    public static void panel(DrawContext ctx, int x, int y, int w, int h) {
        roundRect(ctx, x, y, w, h, 6, BG);
        roundOutline(ctx, x, y, w, h, 6, alpha(lerp(PURPLE, BLUE, 0.5f), 0x60));
    }

    /** Text mit Farbverlauf (Lila -> Hellblau), Rueckgabe = Breite. */
    public static int gradientText(DrawContext ctx, TextRenderer tr, String s, int x, int y) {
        int cx = x, n = Math.max(1, s.length() - 1);
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            ctx.drawTextWithShadow(tr, ch, cx, y, lerp(PURPLE, BLUE, (float) i / n));
            cx += tr.getWidth(ch);
        }
        return cx - x;
    }

    public static int gradientWidth(TextRenderer tr, String s) { return tr.getWidth(s); }
}
