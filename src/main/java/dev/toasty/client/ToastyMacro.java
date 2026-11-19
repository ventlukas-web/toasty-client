package dev.toasty.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.HashSet;
import java.util.Set;

/**
 * Sell-Makro: /sell oeffnen -> nur die gewaehlten Items per Shift-Klick ins Menue schieben ->
 * wenn nichts mehr hineinpasst (Menue voll) oder keine gewaehlten Items mehr da sind: Menue schliessen
 * (= verkaufen, wie ESC) -> kurz warten -> erneut /sell, solange gewaehlte Items im Inventar liegen.
 */
public final class ToastyMacro {
    private enum S { IDLE, OPENING, WAIT_OPEN, FILLING, FILL_WAIT, CLOSING, WAIT_REOPEN }

    private static S state = S.IDLE;
    private static int timer, waitTicks, cycles, strikes, lastCount, cycleStartCount, noProgress;
    private static long lastAuto;
    public static long soldItems, soldSince;

    public static void resetSold() { soldItems = 0; soldSince = 0; }

    public static String statusText() {
        ToastyConfig cfg = ToastyConfig.get();
        if (state != S.IDLE) return "läuft";
        if (!cfg.sellAuto) return "bereit";
        long left = Math.max(0, Math.max(100, cfg.sellIntervalMs) - (System.currentTimeMillis() - lastAuto));
        return String.format(java.util.Locale.ROOT, "Auto in %.1fs", left / 1000.0);
    }

    public static int cycleCount() { return cycles; }
    private static boolean burstMode;

    private static boolean wantAll() {
        ToastyConfig c = ToastyConfig.get();
        return c.sellAlwaysAll || (c.sellShiftAll && shiftHeld());
    }

    private static boolean shiftHeld() {
        long w = MinecraftClient.getInstance().getWindow().getHandle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(w, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == 1
            || org.lwjgl.glfw.GLFW.glfwGetKey(w, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == 1;
    }

    private static int countAll(PlayerEntity p) {
        int n = 0;
        for (int i = 0; i < 36; i++) n += p.getInventory().getStack(i).getCount();
        return n;
    }

    private ToastyMacro() {}

    public static boolean isRunning() { return state != S.IDLE; }

    /** Taste/Knopf: startet einen Durchlauf bzw. stoppt einen laufenden. */
    public static void toggle() {
        if (isRunning()) stop("Sell-Makro gestoppt.");
        else start(true);
    }

    public static boolean runOnce(boolean feedback) { return start(feedback); }

    private static Set<String> selected() { return new HashSet<>(ToastyConfig.get().sellItems); }

    private static String id(ItemStack s) { return Registries.ITEM.getId(s.getItem()).toString(); }

    private static boolean start(boolean feedback) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.networkHandler == null) return false;
        boolean all = wantAll();
        if (!all && ToastyConfig.get().sellItems.isEmpty()) {
            if (feedback) ToastyGamble.chat("Keine Sell-Items gewählt (Menü → Tools → Sell-Items).");
            return false;
        }
        if (all ? countAll(mc.player) == 0 : countInventory(mc.player) == 0) {
            if (feedback) ToastyGamble.chat("Keine gewählten Items im Inventar.");
            return false;
        }
        cycles = 0; noProgress = 0; burstMode = false;
        state = S.OPENING; timer = 0;
        if (feedback) ToastyGamble.chat("Sell-Makro gestartet.");
        return true;
    }

    private static void stop(String msg) {
        state = S.IDLE;
        if (msg != null) ToastyGamble.chat(msg);
    }

    /** Anzahl gewaehlter Items im eigenen Inventar (Hotbar + Hauptinventar). */
    private static int countInventory(PlayerEntity p) {
        Set<String> sel = selected();
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isEmpty() && sel.contains(id(s))) n += s.getCount();
        }
        return n;
    }

    private static boolean sellScreenOpen(MinecraftClient mc) {
        if (!(mc.currentScreen instanceof GenericContainerScreen gs)) return false;
        String f = ToastyConfig.get().sellTitleFilter;
        return f == null || f.isBlank() || gs.getTitle().getString().toLowerCase().contains(f.toLowerCase().trim());
    }

    private static int countInHandler(ScreenHandler h) {
        Set<String> sel = selected();
        int n = 0;
        for (Slot s : h.slots) {
            if (!(s.inventory instanceof PlayerInventory)) continue;
            ItemStack st = s.getStack();
            if (!st.isEmpty() && sel.contains(id(st))) n += st.getCount();
        }
        return n;
    }

    private static Slot nextSelectedSlot(ScreenHandler h) {
        Set<String> sel = selected();
        for (Slot s : h.slots) {
            if (!(s.inventory instanceof PlayerInventory)) continue;
            ItemStack st = s.getStack();
            if (!st.isEmpty() && sel.contains(id(st))) return s;
        }
        return null;
    }

    public static void tick(MinecraftClient mc) {
        ToastyConfig cfg = ToastyConfig.get();
        if (mc.player == null || mc.interactionManager == null) { state = S.IDLE; return; }

        if (state == S.IDLE) {
            long interval = Math.max(100, cfg.sellIntervalMs);
            long now = System.currentTimeMillis();
            if (cfg.sellAuto && mc.currentScreen == null && now - lastAuto >= interval) {
                lastAuto = now;
                start(false);
            }
            return;
        }
        if (timer > 0) { timer--; return; }

        switch (state) {
            case OPENING -> {
                String cmd = cfg.sellCommand == null || cfg.sellCommand.isBlank() ? "/sell" : cfg.sellCommand.trim();
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                cycleStartCount = burstMode ? countAll(mc.player) : countInventory(mc.player);
                mc.player.networkHandler.sendChatCommand(cmd);
                state = S.WAIT_OPEN; waitTicks = 0;
            }
            case WAIT_OPEN -> {
                if (sellScreenOpen(mc)) { state = S.FILLING; strikes = 0; timer = 2; }
                else if (++waitTicks > 40) stop("Sell-Menü ging nicht auf – Makro gestoppt.");
            }
            case FILLING -> {
                if (!sellScreenOpen(mc) || !(mc.currentScreen instanceof GenericContainerScreen gs)) {
                    stop("Sell-Menü wurde geschlossen – Makro gestoppt.");
                    return;
                }
                ScreenHandler h = gs.getScreenHandler();
                if (wantAll()) {
                    // Shift gehalten: ganzes Inventar auf einmal in das Menue schieben
                    burstMode = true;
                    for (Slot bs : h.slots) {
                        if (!(bs.inventory instanceof PlayerInventory) || !bs.hasStack()) continue;
                        mc.interactionManager.clickSlot(h.syncId, bs.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                    }
                    state = S.CLOSING; timer = 2;
                    return;
                }
                Slot s = nextSelectedSlot(h);
                if (s == null) { state = S.CLOSING; return; }
                lastCount = countInHandler(h);
                mc.interactionManager.clickSlot(h.syncId, s.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                state = S.FILL_WAIT;
                timer = Math.max(0, cfg.sellClickDelay);
            }
            case FILL_WAIT -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen gs)) { stop("Sell-Menü wurde geschlossen – Makro gestoppt."); return; }
                int now = countInHandler(gs.getScreenHandler());
                // nichts mehr verschoben worden = Menue voll
                if (now >= lastCount) strikes++; else strikes = 0;
                state = strikes >= 2 ? S.CLOSING : S.FILLING;
            }
            case CLOSING -> {
                mc.player.closeHandledScreen(); // wie ESC -> Server verkauft
                cycles++;
                state = S.WAIT_REOPEN; timer = 8;
            }
            case WAIT_REOPEN -> {
                int left = burstMode ? countAll(mc.player) : countInventory(mc.player);
                if (left < cycleStartCount) {
                    soldItems += cycleStartCount - left;
                    if (soldSince == 0) soldSince = System.currentTimeMillis();
                }
                if (left == 0) { state = S.IDLE; ToastyGamble.chat("Sell-Makro fertig."); return; }
                if (left >= cycleStartCount) noProgress++; else noProgress = 0;
                if (noProgress >= 3) { stop("Kein Fortschritt beim Verkaufen – Makro gestoppt."); return; }
                if (cycles >= 200) { stop("Maximale Durchläufe erreicht – Makro gestoppt."); return; }
                state = S.OPENING; timer = 2;
            }
            default -> {}
        }
    }
}
