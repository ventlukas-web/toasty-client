package dev.toasty.client;

import net.minecraft.client.gui.DrawContext;

/** Toasty-Logo: lila-blaue Plakette mit lachendem Toast. Wird komplett per Code gezeichnet (skaliert scharf). */
public final class ToastyLogo {
    private static final int TOAST = 0xFFFDE7B5, CRUST = 0xFFE9B96E;

    private ToastyLogo() {}

    public static void draw(DrawContext ctx, int x, int y, int s) {
        if (s < 8) return;
        int r = s * 22 / 100;
        ToastyTheme.roundRectExact(ctx, x, y, s, s, r, ToastyTheme.BLUE);
        ToastyTheme.roundRectExact(ctx, x, y, s * 58 / 100, s * 58 / 100, r, ToastyTheme.alpha(ToastyTheme.PURPLE, 0xB8));
        ToastyTheme.roundOutlineExact(ctx, x, y, s, s, r, ToastyTheme.alpha(0xFFFFFFFF, 0x70));

        int tx = x + s * 20 / 100, tw = s * 60 / 100;
        int ty = y + s * 32 / 100, th = s * 48 / 100;
        int br = s * 11 / 100;
        // Kruste (leicht groesser, dahinter)
        int c = Math.max(1, s / 36);
        ToastyTheme.roundRectExact(ctx, tx - c, ty - c, tw + 2 * c, th + 2 * c, br + c, CRUST);
        ToastyTheme.roundRectExact(ctx, tx - c - s * 2 / 100, y + s * 20 / 100 - c, s * 31 / 100 + 2 * c, s * 24 / 100 + 2 * c, s * 12 / 100, CRUST);
        ToastyTheme.roundRectExact(ctx, tx + tw - s * 29 / 100 - c, y + s * 20 / 100 - c, s * 31 / 100 + 2 * c, s * 24 / 100 + 2 * c, s * 12 / 100, CRUST);
        // Toast
        ToastyTheme.roundRectExact(ctx, tx, ty, tw, th, br, TOAST);
        ToastyTheme.roundRectExact(ctx, tx - s * 2 / 100, y + s * 20 / 100, s * 31 / 100, s * 24 / 100, s * 11 / 100, TOAST);
        ToastyTheme.roundRectExact(ctx, tx + tw - s * 29 / 100, y + s * 20 / 100, s * 31 / 100, s * 24 / 100, s * 11 / 100, TOAST);
        // Gesicht
        int ew = Math.max(2, s * 9 / 100), eh = Math.max(3, s * 13 / 100);
        int ey = ty + th * 22 / 100;
        int dark = 0xFF120826;
        int ex1 = tx + tw * 27 / 100 - ew / 2, ex2 = tx + tw * 73 / 100 - ew / 2;
        ToastyTheme.roundRectExact(ctx, ex1, ey, ew, eh, ew / 2, dark);
        ToastyTheme.roundRectExact(ctx, ex2, ey, ew, eh, ew / 2, dark);
        int sh = Math.max(1, ew / 3);   // Glanzpunkte
        ctx.fill(ex1 + ew / 4, ey + eh / 6, ex1 + ew / 4 + sh, ey + eh / 6 + sh, 0xFFFFFFFF);
        ctx.fill(ex2 + ew / 4, ey + eh / 6, ex2 + ew / 4 + sh, ey + eh / 6 + sh, 0xFFFFFFFF);
        // Wangen
        int cw = Math.max(2, s * 9 / 100), ch = Math.max(2, s * 5 / 100), cy = ey + eh + s * 2 / 100;
        ToastyTheme.roundRectExact(ctx, tx + tw * 12 / 100, cy, cw, ch, ch / 2, ToastyTheme.alpha(0xFFFB7185, 0xA0));
        ToastyTheme.roundRectExact(ctx, tx + tw * 88 / 100 - cw, cy, cw, ch, ch / 2, ToastyTheme.alpha(0xFFFB7185, 0xA0));
        // Mund: grosses Laecheln
        int mw = tw * 42 / 100, mh = Math.max(4, s * 12 / 100);
        int mxx = tx + (tw - mw) / 2, myy = ey + eh + s * 3 / 100;
        ToastyTheme.roundRectExact(ctx, mxx, myy, mw, mh, mh / 2 + 1, dark);
        ToastyTheme.roundRectExact(ctx, mxx + mw / 5, myy + mh * 55 / 100, mw * 3 / 5, Math.max(1, mh * 40 / 100), Math.max(1, mh / 5), 0xFFFB7185);
    }
}
