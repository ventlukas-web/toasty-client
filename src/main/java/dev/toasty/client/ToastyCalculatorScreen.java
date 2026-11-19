package dev.toasty.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Rechner (Standardtaste J). Versteht k/m/b/t, z. B. "2.5m * 1.5 + 300k". */
public class ToastyCalculatorScreen extends ToastyScreen {
    private static final int W = 192, CW = 36, CH = 22, G = 3;
    private static final List<String> history = new ArrayList<>();
    private static String keep = "";
    private final Screen parent;
    private TextFieldWidget input;
    private int left, top;

    public ToastyCalculatorScreen(Screen parent) {
        super(Text.literal("Rechner"));
        this.parent = parent;
    }

    @Override
    protected void build() {
        left = width / 2 - W / 2;
        top = height / 2 - 112;

        input = new TextFieldWidget(textRenderer, left, top, W, 20, Text.empty());
        input.setMaxLength(80);
        input.setText(keep);
        input.setChangedListener(s -> keep = s);
        addDrawableChild(input);
        setInitialFocus(input);

        String[][] rows = {
            {"C", "<", "(", ")", "/"},
            {"7", "8", "9", "k", "*"},
            {"4", "5", "6", "m", "-"},
            {"1", "2", "3", "b", "+"},
            {"0", ".", "^", "=", "Kopie"},
        };
        int y0 = top + 52;
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length; c++) {
                final String key = rows[r][c];
                boolean accent = !key.matches("[0-9.]");
                button(() -> key.equals("<") ? "⌫" : key, () -> press(key),
                    left + c * (CW + G), y0 + r * (CH + G), CW, CH, accent);
            }
        }
    }

    private void press(String k) {
        String t = input.getText();
        switch (k) {
            case "C" -> input.setText("");
            case "<" -> { if (!t.isEmpty()) input.setText(t.substring(0, t.length() - 1)); }
            case "=" -> {
                Double v = ToastyCalc.eval(t);
                if (v != null) {
                    history.add(0, t + " = " + ToastyCalc.pretty(v));
                    while (history.size() > 4) history.remove(history.size() - 1);
                    input.setText(ToastyCalc.plain(v));
                }
            }
            case "Kopie" -> {
                Double v = ToastyCalc.eval(t);
                if (v != null) {
                    MinecraftClient.getInstance().keyboard.setClipboard(ToastyCalc.plain(v));
                    ToastyGamble.chat("Ergebnis kopiert: " + ToastyCalc.plain(v));
                }
            }
            default -> input.setText(t + k);
        }
        setFocused(input);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height,
            ToastyTheme.alpha(ToastyTheme.lerp(0xFF05030C, ToastyTheme.PURPLE, 0.15f), 0xE0),
            ToastyTheme.alpha(ToastyTheme.lerp(0xFF05030C, ToastyTheme.BLUE, 0.18f), 0xE0));
        ToastyTheme.panel(ctx, left - 12, top - 24, W + 24, 236);
        ToastyTheme.gradientText(ctx, textRenderer, "Rechner", left, top - 16);
    }

    @Override
    protected void extra(DrawContext ctx, int mouseX, int mouseY) {
        Double v = ToastyCalc.eval(input.getText());
        String main = v == null ? (input.getText().isEmpty() ? "= 0" : "= …") : "= " + ToastyCalc.pretty(v);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(left + 2, top + 26);
        ctx.getMatrices().scale(1.5f, 1.5f);
        ctx.drawTextWithShadow(textRenderer, main, 0, 0, v == null ? ToastyTheme.MUTED : ToastyTheme.GOOD);
        ctx.getMatrices().popMatrix();
        if (v != null && Math.abs(v) >= 1000) {
            String sh = ToastyStats.fmt(Math.round(v));
            ctx.drawTextWithShadow(textRenderer, sh, left + W - textRenderer.getWidth(sh) - 2, top + 26, ToastyTheme.BLUE);
        }
        int y = top + 52 + 5 * (CH + G) + 2;
        for (String h : history) {
            ctx.drawTextWithShadow(textRenderer, h, left + 2, y, ToastyTheme.MUTED);
            y += 10;
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
