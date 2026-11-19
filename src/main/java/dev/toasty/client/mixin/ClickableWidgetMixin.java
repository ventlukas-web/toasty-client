package dev.toasty.client.mixin;

import dev.toasty.client.ToastyConfig;
import dev.toasty.client.ToastyScreen;
import dev.toasty.client.ToastyTheme;
import dev.toasty.client.ToastyTitle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Alle Vanilla-Buttons und Slider im Toasty-Design. Haengt an ClickableWidget.render (stabiler Einstieg fuer jedes Widget). */
@Mixin(ClickableWidget.class)
public abstract class ClickableWidgetMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void toasty$render(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            if (!ToastyConfig.get().niceButtons) return;
            Object self = this;
            boolean button = self.getClass() == ButtonWidget.class || self instanceof CyclingButtonWidget;
            boolean slider = self instanceof SliderWidget;
            if (!button && !slider) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen instanceof ToastyScreen) return;
            long p0 = dev.toasty.client.ToastyProf.begin();
            ClickableWidget w = (ClickableWidget) self;
            if (button) {
                ToastyTitle.styledButton(ctx, mc, w, mouseX, mouseY);
            } else {
                double value = self instanceof SliderAccessor acc ? acc.toasty$getValue() : 0.5;
                slider(ctx, mc, w, value, mouseX, mouseY);
            }
            dev.toasty.client.ToastyProf.end(button ? "Vanilla-Buttons" : "Vanilla-Slider", p0);
            ci.cancel();
        } catch (Throwable t) { /* fail-soft: Vanilla zeichnen lassen */ }
    }

    private static void slider(DrawContext ctx, MinecraftClient mc, ClickableWidget w, double value, int mouseX, int mouseY) {
        if (!w.visible) return;
        int x = w.getX(), y = w.getY(), bw = w.getWidth(), bh = w.getHeight();
        boolean hov = w.active && mouseX >= x && mouseX < x + bw && mouseY >= y && mouseY < y + bh;
        int a = Math.max(60, Math.min(255, ToastyConfig.get().buttonAlpha));
        int r = Math.min(bh / 2, 10);
        ToastyTheme.roundRectExact(ctx, x, y, bw, bh, r, ToastyTheme.alpha(0xFF132039, w.active ? a : a / 2));
        double v = Math.max(0, Math.min(1, value));
        int hw = 8;
        int hx = x + (int) (v * (bw - hw));
        int fillW = Math.max(hw, hx - x + hw);
        ToastyTheme.roundRectExact(ctx, x, y, Math.min(bw, fillW), bh, r,
            ToastyTheme.alpha(ToastyTheme.lerp(ToastyTheme.PURPLE, ToastyTheme.BLUE, 0.65f), hov ? 0xA0 : 0x78));
        ToastyTheme.roundRectExact(ctx, hx, y + 1, hw, bh - 2, Math.min(4, (bh - 2) / 2),
            ToastyTheme.alpha(hov ? 0xFFFFFFFF : 0xFFE6F2FF, 0xF0));
        if (hov) ToastyTheme.roundOutlineExact(ctx, x, y, bw, bh, r, ToastyTheme.alpha(ToastyTheme.BLUE, 0xB0));
        String s = w.getMessage().getString();
        ctx.drawTextWithShadow(mc.textRenderer, s, x + (bw - mc.textRenderer.getWidth(s)) / 2, y + (bh - 8) / 2,
            w.active ? ToastyTheme.TEXT : 0xFF8A86A8);
    }
}
