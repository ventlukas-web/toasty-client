package dev.toasty.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Basis fuer alle Toasty-Screens: Buttons mit runden Ecken im Toasty-Design. */
public abstract class ToastyScreen extends Screen {
    protected record Btn(ButtonWidget w, Supplier<String> label, boolean accent) {}
    protected final List<Btn> btns = new ArrayList<>();
    private final java.util.Map<ButtonWidget, Float> hoverAnim = new java.util.HashMap<>();

    protected ToastyScreen(Text title) { super(title); }

    protected abstract void build();

    @Override
    protected final void init() {
        btns.clear();
        build();
    }

    protected ButtonWidget button(Supplier<String> label, Runnable action, int x, int y, int w, int h) {
        return button(label, action, x, y, w, h, false);
    }

    protected ButtonWidget button(Supplier<String> label, Runnable action, int x, int y, int w, int h, boolean accent) {
        ButtonWidget b = ButtonWidget.builder(Text.empty(), btn -> action.run()).dimensions(x, y, w, h).build();
        b.setAlpha(0f); // unsichtbar, wir zeichnen den Button selbst mit runden Ecken
        addDrawableChild(b);
        btns.add(new Btn(b, label, accent));
        return b;
    }

    /** Unsichtbare Klickflaeche (wird nicht als Button gezeichnet, z. B. fuer Item-Raster). */
    protected ButtonWidget hitbox(Runnable action, int x, int y, int w, int h) {
        ButtonWidget b = ButtonWidget.builder(Text.empty(), btn -> action.run()).dimensions(x, y, w, h).build();
        b.setAlpha(0f);
        addDrawableChild(b);
        return b;
    }

    protected void extra(DrawContext ctx, int mouseX, int mouseY) {}

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        for (Btn b : btns) {
            ButtonWidget w = b.w();
            boolean hov = w.isMouseOver(mouseX, mouseY);
            float cur = hoverAnim.getOrDefault(w, 0f);
            cur += ((hov ? 1f : 0f) - cur) * 0.25f;
            hoverAnim.put(w, cur);
            int x = w.getX(), y = w.getY(), bw = w.getWidth(), bh = w.getHeight();
            float base = b.accent() ? 0.38f : 0.16f;
            float mix = base + (0.6f - base) * cur;
            int fill = ToastyTheme.alpha(ToastyTheme.lerp(0xFF132039, ToastyTheme.lerp(ToastyTheme.PURPLE, ToastyTheme.BLUE, 0.65f), mix), Math.max(60, Math.min(255, ToastyConfig.get().buttonAlpha)));
            ToastyTheme.roundRectExact(ctx, x, y, bw, bh, Math.min(bh / 2, 13), fill);
            if (cur > 0.05f) ToastyTheme.roundOutlineExact(ctx, x, y, bw, bh, Math.min(bh / 2, 13), ToastyTheme.alpha(ToastyTheme.BLUE, (int) (0xB0 * cur)));
            String s = b.label().get();
            int maxW = bw - 10;
            if (textRenderer.getWidth(s) > maxW) {
                while (s.length() > 1 && textRenderer.getWidth(s + "…") > maxW) s = s.substring(0, s.length() - 1);
                s = s + "…";
            }
            ctx.drawTextWithShadow(textRenderer, s, x + (bw - textRenderer.getWidth(s)) / 2, y + (bh - 8) / 2, ToastyTheme.TEXT);
        }
        extra(ctx, mouseX, mouseY);
    }
}
