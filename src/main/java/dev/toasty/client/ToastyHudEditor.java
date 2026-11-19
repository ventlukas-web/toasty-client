package dev.toasty.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

/** HUD-Editor: Elemente mit der Maus (linke Taste halten) verschieben. */
public class ToastyHudEditor extends ToastyScreen {
    private final Screen parent;
    private String dragId = null;
    private float grabDx, grabDy;
    private boolean wasDown = false;

    public ToastyHudEditor(Screen parent) {
        super(Text.literal("HUD-Editor"));
        this.parent = parent;
    }

    private static String nameOf(String id) {
        return switch (id) {
            case "stats" -> "Stats";
            case "info" -> "Info";
            case "keys" -> "Keystrokes";
            case "mods" -> "Module";
            case "pay" -> "Zahlungen";
            case "waypoints" -> "Wegpunkte";
            case "scoreboard" -> "Scoreboard";
            case "custom" -> "Eigenes HUD";
            case "nearby" -> "Spieler";
            case "target" -> "Ziel";
            case "combo" -> "Combo";
            case "mem" -> "System";
            case "vitals" -> "Status";
            case "armor" -> "Armor Durability";
            case "compass" -> "Kompass";
            case "top" -> "Top-Spieler";
            case "graph" -> "Gewinn-Graph";
            case "sellstatus" -> "Sell Status";
            case "potions" -> "Effekte";
            case "session" -> "Session";
            case "notes" -> "Notizen";
            case "events" -> "Events";
            case "twitch" -> "Twitch";
            default -> id;
        };
    }

    @Override
    protected void build() {
        int cx = width / 2;
        button(() -> "Fertig", this::close, cx - 104, height - 28, 100, 20, true);
        button(() -> "Zurücksetzen", () -> {
            ToastyConfig c = ToastyConfig.get();
            c.hudPos.clear(); c.save();
        }, cx + 4, height - 28, 100, 20);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // nur leicht abdunkeln, damit das HUD dahinter sichtbar bleibt
        ctx.fill(0, 0, width, height, 0x50000000);
    }

    private boolean overButton(int mx, int my) {
        for (Btn b : btns) if (b.w().isMouseOver(mx, my)) return true;
        return false;
    }

    @Override
    protected void extra(DrawContext ctx, int mx, int my) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long handle = mc.getWindow().getHandle();
        boolean down = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        ToastyConfig cfg = ToastyConfig.get();

        if (down && !wasDown && !overButton(mx, my)) {
            // oberstes Element unter der Maus greifen (zuletzt gezeichnet = oben)
            String hit = null;
            for (Map.Entry<String, float[]> e : ToastyHud.BOUNDS.entrySet()) {
                float[] b = e.getValue();
                if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
                    hit = e.getKey();
                    grabDx = mx - b[0]; grabDy = my - b[1];
                }
            }
            dragId = hit;
        } else if (down && dragId != null) {
            float[] b = ToastyHud.BOUNDS.get(dragId);
            if (b != null) {
                float nx = Math.max(0, Math.min(width - b[2], mx - grabDx));
                float ny = Math.max(0, Math.min(height - b[3], my - grabDy));
                cfg.hudPos.put(dragId, new float[]{nx / width, ny / height});
            }
        } else if (!down && wasDown && dragId != null) {
            cfg.save();
            dragId = null;
        }
        wasDown = down;

        // Rahmen + Namen der Elemente
        for (Map.Entry<String, float[]> e : ToastyHud.BOUNDS.entrySet()) {
            float[] b = e.getValue();
            boolean active = e.getKey().equals(dragId);
            boolean hov = mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
            int col = active ? ToastyTheme.TEXT : (hov ? ToastyTheme.BLUE : ToastyTheme.alpha(ToastyTheme.PURPLE, 0xE0));
            ToastyTheme.roundOutline(ctx, (int) b[0] - 2, (int) b[1] - 2, (int) b[2] + 4, (int) b[3] + 4, 6, col);
            ctx.drawTextWithShadow(textRenderer, nameOf(e.getKey()), (int) b[0], (int) b[1] - 12, col);
        }

        String t1 = ToastyHud.BOUNDS.isEmpty()
            ? "Keine HUD-Elemente aktiv – schalte sie im Menü (HUD-Tab) ein."
            : "Linke Maustaste halten und ziehen, um ein Element zu verschieben.";
        ctx.drawTextWithShadow(textRenderer, t1, (width - textRenderer.getWidth(t1)) / 2, height / 2 - 4, ToastyTheme.TEXT);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
