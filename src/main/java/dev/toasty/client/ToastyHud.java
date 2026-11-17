package dev.toasty.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;

public class ToastyHud {
    public static long flashUntil;
    public static int flashColor = 0xFFFFFFFF;

    public static void flash(int argb) {
        flashColor = argb;
        flashUntil = System.currentTimeMillis() + 1500;
    }

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        ToastyConfig cfg = ToastyConfig.get();
        if (!cfg.hud) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;

        List<String> lines = List.of(
            "Einnahmen: " + ToastyStats.income,
            "Auszahlung: " + ToastyStats.payouts,
            "Profit: " + ToastyStats.profit(),
            "Runden: " + ToastyStats.rounds + "  (W " + ToastyStats.wins + " / L " + ToastyStats.losses + ")",
            String.format("Winrate: %.1f%%", ToastyStats.winrate()),
            "Offen: " + ToastyStats.pending.size()
        );
        int w = 0;
        for (String s : lines) w = Math.max(w, tr.getWidth(s));
        int pad = 4, h = lines.size() * 10 + pad * 2 - 2;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(6, 6);
        ctx.getMatrices().scale(cfg.hudScale, cfg.hudScale);
        ctx.fill(0, 0, w + pad * 2, h, 0x90000000);
        if (System.currentTimeMillis() < flashUntil) {
            int c = flashColor;
            ctx.fill(0, 0, w + pad * 2, 1, c);
            ctx.fill(0, h - 1, w + pad * 2, h, c);
            ctx.fill(0, 0, 1, h, c);
            ctx.fill(w + pad * 2 - 1, 0, w + pad * 2, h, c);
        }
        int y = pad;
        for (String s : lines) {
            int col = s.startsWith("Profit") ? (ToastyStats.profit() >= 0 ? 0xFF55FF55 : 0xFFFF5555) : 0xFFFFFFFF;
            ctx.drawTextWithShadow(tr, s, pad, y, col);
            y += 10;
        }
        ctx.getMatrices().popMatrix();
    }
}
