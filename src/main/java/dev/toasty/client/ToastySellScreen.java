package dev.toasty.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Auswahl, welche Items das Sell-Makro verkauft. */
public class ToastySellScreen extends ToastyScreen {
    private static final int CW = 200, CELL = 18, MAX_SHOWN = 18;
    private final Screen parent;
    private int left, top, gx;
    private TextFieldWidget idField;

    private record Cell(int x, int y, ItemStack stack, boolean selected) {}
    private record Label(String text, int x, int y, int color) {}
    private final List<Cell> cells = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();

    public ToastySellScreen(Screen parent) {
        super(Text.literal("Sell-Makro"));
        this.parent = parent;
    }

    private static String id(ItemStack s) { return Registries.ITEM.getId(s.getItem()).toString(); }

    private static ItemStack stackOf(String id) {
        Identifier i = Identifier.tryParse(id);
        if (i == null || !Registries.ITEM.containsId(i)) return ItemStack.EMPTY;
        return new ItemStack(Registries.ITEM.get(i));
    }

    private void toggleId(String id) {
        ToastyConfig c = ToastyConfig.get();
        if (!c.sellItems.remove(id)) c.sellItems.add(id);
        c.save();
        clearAndInit();
    }

    @Override
    protected void build() {
        cells.clear();
        labels.clear();
        ToastyConfig c = ToastyConfig.get();
        left = width / 2 - CW / 2;
        gx = left + (CW - 9 * CELL) / 2;
        top = Math.max(26, (height - 250) / 2 + 22);

        int y = top;
        button(() -> ToastyMacro.isRunning() ? "Stoppen" : "Starten", ToastyMacro::toggle, left, y, 96, 20, true);
        button(() -> "Auto: " + (c.sellAuto ? "AN" : "AUS"), () -> { c.sellAuto = !c.sellAuto; c.save(); }, left + 104, y, 96, 20);

        y += 28;
        int more = Math.max(0, c.sellItems.size() - MAX_SHOWN);
        labels.add(new Label("Gewählte Items (Klick = entfernen)" + (more > 0 ? "  +" + more : ""), left, y, ToastyTheme.MUTED));
        y += 11;
        List<String> ids = c.sellItems;
        if (ids.isEmpty()) {
            labels.add(new Label("Noch keine Items gewählt.", gx, y + 5, ToastyTheme.TEXT));
            y += CELL + 4;
        } else {
            int shown = Math.min(ids.size(), MAX_SHOWN);
            for (int i = 0; i < shown; i++) {
                final String iid = ids.get(i);
                ItemStack st = stackOf(iid);
                int cx = gx + (i % 9) * CELL, cy = y + (i / 9) * CELL;
                cells.add(new Cell(cx, cy, st, true));
                hitbox(() -> toggleId(iid), cx, cy, CELL, CELL);
            }
            y += ((shown + 8) / 9) * CELL + 4;
        }

        labels.add(new Label("Dein Inventar (Klick = wählen / abwählen)", left, y, ToastyTheme.MUTED));
        y += 11;
        MinecraftClient mc = MinecraftClient.getInstance();
        Set<String> sel = new HashSet<>(c.sellItems);
        if (mc.player != null) {
            for (int r = 0; r < 4; r++) {
                int cy = y + r * CELL + (r == 3 ? 4 : 0);
                for (int col = 0; col < 9; col++) {
                    int slot = r == 3 ? col : 9 + r * 9 + col;   // letzte Reihe = Hotbar
                    ItemStack st = mc.player.getInventory().getStack(slot);
                    int cx = gx + col * CELL;
                    if (st.isEmpty()) continue;
                    final String iid = id(st);
                    cells.add(new Cell(cx, cy, st, sel.contains(iid)));
                    hitbox(() -> toggleId(iid), cx, cy, CELL, CELL);
                }
            }
        }
        y += 4 * CELL + 4 + 6;

        idField = new TextFieldWidget(textRenderer, left, y, 90, 18, Text.empty());
        idField.setMaxLength(80);
        idField.setPlaceholder(Text.literal("z. B. wheat"));
        addDrawableChild(idField);
        button(() -> "Hinzufügen", () -> {
            String t = idField.getText().trim().toLowerCase();
            if (t.isEmpty()) return;
            String full = t.contains(":") ? t : "minecraft:" + t;
            if (stackOf(full).isEmpty()) { ToastyGamble.chat("Unbekanntes Item: " + t); return; }
            if (!c.sellItems.contains(full)) { c.sellItems.add(full); c.save(); }
            clearAndInit();
        }, left + 94, y - 1, 62, 20);
        button(() -> "Hand", () -> {
            if (mc.player == null) return;
            ItemStack st = mc.player.getMainHandStack();
            if (st.isEmpty()) { ToastyGamble.chat("Du hältst kein Item in der Hand."); return; }
            String iid = id(st);
            if (!c.sellItems.contains(iid)) { c.sellItems.add(iid); c.save(); }
            clearAndInit();
        }, left + 160, y - 1, 40, 20);

        y += 26;
        button(() -> "-", () -> { c.sellClickDelay = Math.max(0, c.sellClickDelay - 1); c.save(); }, left, y, 24, 20);
        button(() -> "Klick-Pause: " + c.sellClickDelay + " Tick" + (c.sellClickDelay == 1 ? "" : "s"), () -> {}, left + 28, y, 144, 20);
        button(() -> "+", () -> { c.sellClickDelay = Math.min(20, c.sellClickDelay + 1); c.save(); }, left + 176, y, 24, 20);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height,
            ToastyTheme.alpha(ToastyTheme.lerp(0xFF05030C, ToastyTheme.PURPLE, 0.15f), 0xF0),
            ToastyTheme.alpha(ToastyTheme.lerp(0xFF05030C, ToastyTheme.BLUE, 0.18f), 0xF0));
        ToastyTheme.panel(ctx, left - 14, top - 24, CW + 28, 262);
        ToastyTheme.gradientText(ctx, textRenderer, "Sell-Makro", left, top - 16);
    }

    @Override
    protected void extra(DrawContext ctx, int mouseX, int mouseY) {
        for (Label l : labels) ctx.drawTextWithShadow(textRenderer, l.text(), l.x(), l.y(), l.color());
        String hover = null;
        for (Cell c : cells) {
            boolean hov = mouseX >= c.x() && mouseX < c.x() + CELL && mouseY >= c.y() && mouseY < c.y() + CELL;
            ToastyTheme.roundRect(ctx, c.x(), c.y(), CELL - 1, CELL - 1, 3, ToastyTheme.alpha(0xFF1B1235, 0xE0));
            ToastyTheme.roundOutline(ctx, c.x(), c.y(), CELL - 1, CELL - 1, 3,
                hov ? ToastyTheme.TEXT : (c.selected() ? ToastyTheme.BLUE : ToastyTheme.alpha(ToastyTheme.PURPLE, 0x70)));
            if (!c.stack().isEmpty()) ctx.drawItem(c.stack(), c.x() + 1, c.y() + 1);
            if (hov && !c.stack().isEmpty()) hover = c.stack().getName().getString();
        }
        if (hover != null) {
            ctx.drawTextWithShadow(textRenderer, hover, left, top + 226, ToastyTheme.TEXT);
        } else {
            String s = "Läuft nur mit gewählten Items. Menü voll → schließen → /sell";
            ctx.drawTextWithShadow(textRenderer, s, left, top + 226, ToastyTheme.MUTED);
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
