package dev.toasty.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Tastenbelegung aller Toasty-Funktionen (Klick auf die Taste, dann neue Taste druecken). */
public class ToastyKeysScreen extends ToastyScreen {
    private static final int W = 300;
    /** Gueltige GLFW-Tastencodes (nur diese werden abgefragt, damit GLFW keine Fehler meldet). */
    private static final int[][] RANGES = {
        {32, 32}, {39, 39}, {44, 57}, {59, 59}, {61, 61}, {65, 93}, {96, 96},
        {256, 269}, {280, 284}, {290, 314}, {320, 336}, {340, 348}
    };

    private final Screen parent;
    private int left, top;
    private int listening = -1;
    private long listenSince;

    public ToastyKeysScreen(Screen parent) {
        super(Text.literal("Tasten"));
        this.parent = parent;
    }

    static KeyBinding[] binds() {
        return new KeyBinding[]{ToastyGamble.openMenu, ToastyGamble.calcKey, ToastyGamble.macroKey,
            ToastyGamble.hudEditKey, ToastyGamble.glassKey, ToastyMisc.fullbrightKey, ToastyMisc.autoClickKey, ToastyMisc.zoomKey};
    }

    private static final String[] NAMES = {
        "Mod-Menü öffnen", "Rechner öffnen", "Sell-Makro Start/Stopp", "HUD-Editor öffnen", "Unsichtbares Glas an/aus",
        "Fullbright an/aus", "Autoclicker an/aus", "Zoom (halten)"
    };

    @Override
    protected void build() {
        left = width / 2 - W / 2;
        top = Math.max(26, (height - 290) / 2 + 20);
        KeyBinding[] kb = binds();
        for (int i = 0; i < kb.length; i++) {
            final int idx = i;
            final KeyBinding k = kb[i];
            int y = top + 30 + i * 22;
            button(() -> listening == idx ? "Drücke eine Taste …" : k.getBoundKeyLocalizedText().getString(),
                () -> { listening = idx; listenSince = System.currentTimeMillis(); },
                left + 150, y, 100, 20, listening == idx);
            button(() -> "Reset", () -> {
                k.setBoundKey(k.getDefaultKey());
                apply();
            }, left + 254, y, 46, 20);
        }
        button(() -> "Zurück", this::close, left + W / 2 - 50, top + 30 + kb.length * 22 + 8, 100, 20);
    }

    private static void apply() {
        KeyBinding.updateKeysByCode();
        MinecraftClient.getInstance().options.write();
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height,
            ToastyTheme.alpha(ToastyTheme.lerp(0xFF05030C, ToastyTheme.PURPLE, 0.15f), 0xF0),
            ToastyTheme.alpha(ToastyTheme.lerp(0xFF05030C, ToastyTheme.BLUE, 0.18f), 0xF0));
        ToastyTheme.panel(ctx, left - 12, top - 24, W + 24, 30 + binds().length * 22 + 56);
        ToastyTheme.gradientText(ctx, textRenderer, "Tasten", left, top - 16);
    }

    @Override
    protected void extra(DrawContext ctx, int mouseX, int mouseY) {
        for (int i = 0; i < NAMES.length; i++) {
            ctx.drawTextWithShadow(textRenderer, NAMES[i], left, top + 30 + i * 22 + 6, ToastyTheme.TEXT);
        }
        String hint = listening >= 0
            ? "Taste drücken (Rücklösch = Belegung entfernen)."
            : "Klick auf eine Taste, dann neue Taste drücken.";
        ctx.drawTextWithShadow(textRenderer, hint, left, top + 30 + NAMES.length * 22 + 36, ToastyTheme.MUTED);

        if (listening >= 0 && System.currentTimeMillis() - listenSince > 200) {
            long handle = MinecraftClient.getInstance().getWindow().getHandle();
            for (int[] r : RANGES) {
                for (int code = r[0]; code <= r[1]; code++) {
                    if (GLFW.glfwGetKey(handle, code) != GLFW.GLFW_PRESS) continue;
                    if (code == GLFW.GLFW_KEY_ESCAPE) { listening = -1; return; }
                    KeyBinding k = binds()[listening];
                    if (code == GLFW.GLFW_KEY_BACKSPACE || code == GLFW.GLFW_KEY_DELETE) k.setBoundKey(InputUtil.UNKNOWN_KEY);
                    else k.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(code));
                    apply();
                    listening = -1;
                    return;
                }
            }
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
