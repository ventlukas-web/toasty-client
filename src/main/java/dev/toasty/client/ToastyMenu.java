package dev.toasty.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Mod-Menue mit allen Funktionen des Toasty Clients. */
public class ToastyMenu extends ToastyScreen {
    private static final int PW = 300;
    private int panelBottom = 274;
    private static final String[] TABS = {"General", "Gamble", "HUD", "Design", "Visuals", "FPS", "Tools", "Sell", "Extras", "Twitch", "Notizen", "HUDs+", "Eigenes", "Score", "Streamer", "Befehle", "Profile", "Wegpunkte", "Settings", "YouTube"};
    private static int tab = 0;
    private final Screen parent;

    private record Label(String text, int x, int y, int color) {}
    private final List<Label> labels = new ArrayList<>();
    private int left, top;
    private boolean glassOn;

    public ToastyMenu(Screen parent) {
        super(Text.literal("Toasty Client"));
        this.parent = parent;
    }

    @Override
    protected void build() {
        labels.clear();
        left = width / 2 - PW / 2;
        top = Math.max(32, (height - 302) / 2 + 28);

        int tw = PW / 5;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            button(() -> TABS[idx], () -> { tab = idx; clearAndInit(); },
                left + (i % 5) * tw, top + (i / 5) * 16, tw - 2, 15, tab == i);
        }
        int y = top + 66;
        panelBottom = 274;
        switch (tab) {
            case 0 -> generalTab(y);
            case 1 -> gambleTab(y);
            case 2 -> hudTab(y);
            case 3 -> designTab(y);
            case 4 -> visualsTab(y);
            case 5 -> perfTab(y);
            case 6 -> toolsTab(y);
            case 7 -> sellTab(y);
            case 8 -> extrasTab(y);
            case 9 -> eventsTab(y);
            case 10 -> notesTab(y);
            case 11 -> moreHudTab(y);
            case 12 -> customTab(y);
            case 13 -> scoreboardTab(y);
            case 14 -> streamerTab(y);
            case 15 -> commandsTab(y);
            case 16 -> profileTab(y);
            case 17 -> waypointTab(y);
            case 18 -> settingsTab(y);
            default -> youtubeTab(y);
        }
        // Untere Buttons immer UNTER dem Inhalt des Tabs (nie darueber/ueberlappend)
        int by = top + 242;
        for (var el : children()) if (el instanceof net.minecraft.client.gui.widget.ClickableWidget cw && cw.getAlpha() > 0f)
            if (cw.getY() > top + 60) by = Math.max(by, cw.getY() + cw.getHeight() + 8);
        button(() -> "Tasten …", () -> MinecraftClient.getInstance().setScreen(new ToastyKeysScreen(this)), left, by, 96, 20, true);
        button(() -> "Schließen", this::close, left + 102, by, 96, 20);
        button(() -> "HUD verschieben", () -> MinecraftClient.getInstance().setScreen(new ToastyHudEditor(this)), left + 204, by, 96, 20, true);
        panelBottom = by + 20 + 12 - top;
        // Passt das Ganze nicht auf den Bildschirm: alles nach oben schieben
        int excess = top + panelBottom + 4 - height;
        int room = (top - 28) - 4;
        int shift = Math.max(0, Math.min(excess, room));
        if (shift > 0) {
            for (var el : children()) if (el instanceof net.minecraft.client.gui.widget.ClickableWidget cw) cw.setY(cw.getY() - shift);
            java.util.List<Label> moved = new java.util.ArrayList<>();
            for (Label l : labels) moved.add(new Label(l.text(), l.x(), l.y() - shift, l.color()));
            labels.clear(); labels.addAll(moved);
            top -= shift;
        }
    }

    // ---------- General ----------

    private void generalTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        button(() -> "Rechner öffnen", () -> mc.setScreen(new ToastyCalculatorScreen(this)), left, y, 146, 20, true);
        button(() -> "Tasten ändern …", () -> mc.setScreen(new ToastyKeysScreen(this)), left + 154, y, 146, 20, true);
        y += 24;
        button(() -> "HUD verschieben", () -> mc.setScreen(new ToastyHudEditor(this)), left, y, 146, 20, true);
        toggle("Modul-Liste", () -> c.moduleList, v -> { c.moduleList = v; c.save(); }, left + 154, y, 146);
        y += 24;
        button(() -> "HUD kleiner", () -> { c.hudScale = Math.max(0.5f, Math.round((c.hudScale - 0.1f) * 10) / 10f); c.save(); }, left, y, 146, 20);
        button(() -> "HUD größer (" + c.hudScale + ")", () -> { c.hudScale = Math.min(3f, Math.round((c.hudScale + 0.1f) * 10) / 10f); c.save(); }, left + 154, y, 146, 20);
        y += 24;
        toggle("Stats-HUD", () -> c.hud, v -> { c.hud = v; c.save(); }, left, y, 146);
        toggle("Zahlungs-HUD", () -> c.payHud, v -> { c.payHud = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Keystrokes", () -> c.keystrokes, v -> { c.keystrokes = v; c.save(); }, left, y, 146);
        toggle("Koordinaten", () -> c.coords, v -> { c.coords = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Toasts", () -> c.notify, v -> { c.notify = v; c.save(); }, left, y, 146);
        toggle("Wasserzeichen", () -> c.watermark, v -> { c.watermark = v; c.save(); }, left + 154, y, 146);
    }

    // ---------- Gamble ----------

    private void gambleTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        labels.add(new Label("Mindesteinsatz", left, y, ToastyTheme.MUTED));
        labels.add(new Label("Höchsteinsatz (leer = ∞)", left + 152, y, ToastyTheme.MUTED));
        field(left, y + 10, 146, c.minBet == 0 ? "" : ToastyStats.fmt(c.minBet), s -> {
            Long v = parseAmount(s); c.minBet = v == null ? 0 : v; c.save();
        });
        field(left + 154, y + 10, 146, c.maxBet == Long.MAX_VALUE ? "" : ToastyStats.fmt(c.maxBet), s -> {
            Long v = parseAmount(s); c.maxBet = v == null ? Long.MAX_VALUE : v; c.save();
        });

        y += 36;
        labels.add(new Label("Gewinn-Multiplikator", left, y, ToastyTheme.MUTED));
        labels.add(new Label("2.0 = doppelte Auszahlung", left + 152, y + 14, ToastyTheme.MUTED));
        field(left, y + 10, 146, String.valueOf(c.winMultiplier), s -> {
            try { c.winMultiplier = Double.parseDouble(s.replace(',', '.')); c.save(); } catch (Exception ignored) {}
        });

        y += 36;
        labels.add(new Label("Chat-Muster (Regex mit player, amount, suffix)", left, y, ToastyTheme.MUTED));
        TextFieldWidget f = field(left, y + 10, PW, c.pattern, s -> {
            try { java.util.regex.Pattern.compile(s); c.pattern = s; c.save(); } catch (Exception ignored) {}
        });
        f.setMaxLength(400);

        y += 38;
        button(() -> "Gewinn (letzter)", () -> ToastyGamble.chat(ToastyGamble.settle(null, true)), left, y, 96, 20, true);
        button(() -> "Verlust (letzter)", () -> ToastyGamble.chat(ToastyGamble.settle(null, false)), left + 102, y, 96, 20, true);
        button(() -> "Statistik reset", () -> { ToastyStats.reset(); ToastyGamble.chat("Statistik zurückgesetzt."); }, left + 204, y, 96, 20);
        toggle("Auto-Auszahlung bei /win", () -> c.autoPay, v -> { c.autoPay = v; c.save(); }, left, y + 24, PW);
        labels.add(new Label("Befehle: /win /lose [Spieler|all] · /toasty stats|debug", left, y + 48, ToastyTheme.MUTED));
    }

    // ---------- HUD ----------

    private void hudTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        toggle("Stats-HUD", () -> c.hud, v -> { c.hud = v; c.save(); }, left, y, 146);
        toggle("Keystrokes", () -> c.keystrokes, v -> { c.keystrokes = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Koordinaten", () -> c.coords, v -> { c.coords = v; c.save(); }, left, y, 146);
        toggle("FPS", () -> c.showFps, v -> { c.showFps = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Ping", () -> c.ping, v -> { c.ping = v; c.save(); }, left, y, 146);
        toggle("Speed", () -> c.speed, v -> { c.speed = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Uhrzeit", () -> c.clock, v -> { c.clock = v; c.save(); }, left, y, 146);
        toggle("Modul-Liste", () -> c.moduleList, v -> { c.moduleList = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Zahlungs-HUD", () -> c.payHud, v -> { c.payHud = v; c.save(); }, left, y, 146);
        toggle("Twitch-Chat", () -> c.twitch, v -> { c.twitch = v; c.save(); }, left + 154, y, 146);
        y += 28;
        button(() -> "HUD kleiner", () -> { c.hudScale = Math.max(0.5f, Math.round((c.hudScale - 0.1f) * 10) / 10f); c.save(); }, left, y, 146, 20);
        button(() -> "HUD größer", () -> { c.hudScale = Math.min(3f, Math.round((c.hudScale + 0.1f) * 10) / 10f); c.save(); }, left + 154, y, 146, 20);
        y += 24;
        int[] steps = {0, 10, 15, 30, 60, 120};
        button(() -> c.waitWarnSec <= 0 ? "Warte-Ansage: AUS" : "Warte-Ansage: nach " + c.waitWarnSec + " s", () -> {
            int next = steps[0];
            for (int i = 0; i < steps.length; i++) if (steps[i] == c.waitWarnSec) { next = steps[(i + 1) % steps.length]; break; }
            if (c.waitWarnSec != 0 && java.util.Arrays.stream(steps).noneMatch(v -> v == c.waitWarnSec)) next = 0;
            c.waitWarnSec = next; c.save();
        }, left, y, PW, 20, true);
    }

    // ---------- Design ----------

    private static final String[][] PRESETS = {
        {"Lila-Blau", "#A855F7", "#7DD3FC"},
        {"Sunset", "#FB7185", "#FBBF24"},
        {"Mint", "#34D399", "#7DD3FC"},
        {"Neon", "#F472B6", "#22D3EE"},
        {"Feuer", "#EF4444", "#F97316"},
        {"Gold", "#F59E0B", "#FDE68A"},
        {"Rot", "#DC2626", "#FCA5A5"},
        {"Blutrot", "#B91C1C", "#F87171"},
        {"Rosa", "#EC4899", "#F9A8D4"},
        {"Ozean", "#2563EB", "#22D3EE"},
        {"Wald", "#16A34A", "#BBF7D0"},
        {"Eis", "#38BDF8", "#E0F2FE"},
    };

    private void designTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        labels.add(new Label("Vorlagen", left, y, ToastyTheme.MUTED));
        for (int i = 0; i < PRESETS.length; i++) {
            final String[] p = PRESETS[i];
            int bx = left + (i % 3) * 102, by = y + 11 + (i / 3) * 22;
            button(() -> p[0], () -> {
                c.color1 = p[1]; c.color2 = p[2]; c.save(); ToastyTheme.reload(c); clearAndInit();
            }, bx, by, 96, 20);
        }
        int y1 = y + 104;
        labels.add(new Label("Farbe 1 (#RRGGBB)", left, y1, ToastyTheme.MUTED));
        labels.add(new Label("Farbe 2 (#RRGGBB)", left + 154, y1, ToastyTheme.MUTED));
        field(left, y1 + 10, 146, c.color1, s -> {
            Integer v = ToastyTheme.parseHex(s);
            if (v != null) { c.color1 = ToastyTheme.toHex(v); c.save(); ToastyTheme.reload(c); }
        });
        field(left + 154, y1 + 10, 146, c.color2, s -> {
            Integer v = ToastyTheme.parseHex(s);
            if (v != null) { c.color2 = ToastyTheme.toHex(v); c.save(); ToastyTheme.reload(c); }
        });
        int y2 = y + 146;
        button(() -> "Hintergrund heller", () -> { c.bgOpacity = Math.max(32, c.bgOpacity - 24); c.save(); ToastyTheme.reload(c); }, left, y2, 146, 20);
        button(() -> "Hintergrund dunkler", () -> { c.bgOpacity = Math.min(255, c.bgOpacity + 24); c.save(); ToastyTheme.reload(c); }, left + 154, y2, 146, 20);
    }

    // ---------- Visuals ----------

    private void visualsTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        toggle("Sound", () -> c.sound, v -> { c.sound = v; c.save(); }, left, y, 146);
        toggle("Overlay-Effekte", () -> c.effects, v -> { c.effects = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Konfetti", () -> c.confetti, v -> { c.confetti = v; c.save(); }, left, y, 146);
        toggle("Wasserzeichen", () -> c.watermark, v -> { c.watermark = v; c.save(); }, left + 154, y, 146);
        y += 24;

        toggle("Inventar-Design", () -> c.niceInventory, v -> { c.niceInventory = v; c.save(); }, left, y, 146);
        toggle("Button-Design", () -> c.niceButtons, v -> { c.niceButtons = v; c.save(); }, left + 154, y, 146);
        y += 28;
        glassOn = ToastyGlass.isOn();
        button(() -> "Glas (/glass): " + (glassOn ? "AN" : "AUS"), () -> {
            if (ToastyGlass.toggle()) glassOn = !glassOn; else ToastyGamble.chat("Glas-Pack nicht gefunden.");
        }, left, y, 146, 20);
        button(() -> "Trailer ansehen", () -> MinecraftClient.getInstance().setScreen(new ToastyTrailerScreen(this)), left + 154, y, 146, 20, true);
        labels.add(new Label("Glas lädt Texturen neu (kurzer Ruckler).", left, y + 24, ToastyTheme.MUTED));
        y += 40;
        toggle("Armor Durability", () -> c.armorHud, v -> { c.armorHud = v; c.save(); }, left, y, 146);
        toggle("Effekte-HUD", () -> c.potionHud, v -> { c.potionHud = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Session-HUD", () -> c.sessionHud, v -> { c.sessionHud = v; c.save(); }, left, y, 146);
        toggle("Auto-Sprint", () -> c.autoSprint, v -> { c.autoSprint = v; c.save(); }, left + 154, y, 146);

    }

    // ---------- FPS ----------

    private void perfTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        toggle("Partikel minimal", () -> c.perfParticles, v -> { c.perfParticles = v; ToastyPerf.apply(); }, left, y, PW);
        y += 24;
        toggle("Entity-Schatten aus", () -> c.perfShadows, v -> { c.perfShadows = v; ToastyPerf.apply(); }, left, y, PW);
        y += 24;
        toggle("Weniger Distanz (Render 8, Simulation 6)", () -> c.perfDistance, v -> { c.perfDistance = v; ToastyPerf.apply(); }, left, y, PW);
        y += 24;
        toggle("Wolken aus", () -> c.perfClouds, v -> { c.perfClouds = v; ToastyPerf.apply(); }, left, y, 146);
        toggle("Biom-Blend 0", () -> c.perfBlend, v -> { c.perfBlend = v; ToastyPerf.apply(); }, left + 154, y, 146);
        y += 24;
        toggle("Weiches Licht aus", () -> c.perfAo, v -> { c.perfAo = v; ToastyPerf.apply(); }, left, y, 146);
        toggle("Kein FPS-Limit", () -> c.perfFps, v -> { c.perfFps = v; ToastyPerf.apply(); }, left + 154, y, 146);
        y += 30;
        labels.add(new Label("Ändert nur normale Spieloptionen. Beim Ausschalten", left, y, ToastyTheme.MUTED));
        labels.add(new Label("werden deine alten Werte wiederhergestellt.", left, y + 11, ToastyTheme.MUTED));
        labels.add(new Label("Tipp: Für noch mehr FPS zusätzlich Sodium nutzen.", left, y + 28, ToastyTheme.MUTED));
    }

    // ---------- Tools ----------

    private void toolsTab(int y) {
        button(() -> "Rechner öffnen (J)", () -> MinecraftClient.getInstance().setScreen(new ToastyCalculatorScreen(this)),
            left, y, 146, 20, true);
        button(() -> "Timer: 1 Minute", () -> { ToastyEvents.addTimer(60, "1 Minute"); ToastyGamble.chat("Timer gestartet: 60 s"); }, left + 154, y, 146, 20);
        y += 24;
        button(() -> "Timer: 5 Minuten", () -> { ToastyEvents.addTimer(300, "5 Minuten"); ToastyGamble.chat("Timer gestartet: 300 s"); }, left, y, 146, 20);
        button(() -> "Timer löschen", () -> ToastyGamble.chat(ToastyEvents.cancelTimers() + " Timer gelöscht."), left + 154, y, 146, 20);
        y += 24;
        button(() -> ToastyEvents.swActive() && ToastyEvents.swElapsed() > 0 ? "Stoppuhr: " + ToastyEvents.swFmt(ToastyEvents.swElapsed()) : "Stoppuhr starten",
            () -> ToastyGamble.chat(ToastyEvents.stopwatch(swRunning() ? "stop" : "start")), left, y, 146, 20, true);
        button(() -> "Runde", () -> ToastyGamble.chat(ToastyEvents.stopwatch("lap")), left + 154, y, 70, 20);
        button(() -> "Reset", () -> ToastyGamble.chat(ToastyEvents.stopwatch("reset")), left + 228, y, 72, 20);
        y += 24;
        button(() -> "Lag-Analyse", () -> { for (String ln : ToastyProf.analyze()) ToastyGamble.chat(ln); }, left, y, 146, 20, true);
        toggle("Toast-Startbild", () -> c().titleToast, v -> { c().titleToast = v; c().save(); }, left + 154, y, 146);
    }

    private static ToastyConfig c() { return ToastyConfig.get(); }

    private static boolean swRunning() { return ToastyEvents.swRunning(); }

    // ---------- Sell ----------

    private static String ms(int v) {
        return v % 1000 == 0 ? (v / 1000) + " s" : String.format(java.util.Locale.ROOT, "%.1f s", v / 1000.0);
    }

    private static int stepMs(int v, int dir) {
        int st = (dir > 0 ? v >= 3000 : v > 3000) ? 1000 : 100;
        return Math.max(100, Math.min(3_600_000, v + dir * st));
    }

    private void sellTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        labels.add(new Label("Sell-Befehl (Standard /sell)", left, y, ToastyTheme.MUTED));
        field(left, y + 10, PW, c.sellCommand == null ? "" : c.sellCommand, s -> { c.sellCommand = s; c.save(); });

        y += 34;
        toggle("Auto", () -> c.sellAuto, v -> { c.sellAuto = v; c.save(); }, left, y, 146);
        button(() -> "-", () -> { c.sellIntervalMs = stepMs(c.sellIntervalMs, -1); c.save(); }, left + 154, y, 30, 20);
        button(() -> "alle " + ms(c.sellIntervalMs), () -> {}, left + 188, y, 54, 20);
        button(() -> "+", () -> { c.sellIntervalMs = stepMs(c.sellIntervalMs, 1); c.save(); }, left + 246, y, 30, 20);
        y += 24;
        button(() -> ToastyMacro.isRunning() ? "Sell-Makro stoppen" : "Sell-Makro starten", ToastyMacro::toggle, left, y, 146, 20, true);
        button(() -> "Sell-Items wählen …", () -> MinecraftClient.getInstance().setScreen(new ToastySellScreen(this)),
            left + 154, y, 146, 20, true);
        y += 24;
        toggle("Immer ganzes Inv.", () -> c.sellAlwaysAll, v -> { c.sellAlwaysAll = v; c.save(); }, left, y, 146);
        toggle("Shift = ganzes Inv.", () -> c.sellShiftAll, v -> { c.sellShiftAll = v; c.save(); }, left + 154, y, 146);
        labels.add(new Label("Auto: startet, sobald gewählte Items da sind.", left, y + 26, ToastyTheme.MUTED));
        labels.add(new Label("Ganzes Inventar per Shift-Klick auf einmal", left, y + 38, ToastyTheme.MUTED));
        labels.add(new Label("ins Sell-Menü (auch nicht gewählte Items!)", left, y + 49, ToastyTheme.MUTED));
    }

    // ---------- Extras ----------

    private void extrasTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        toggle("Fullbright", () -> c.fullbright, v -> { c.fullbright = v; c.save(); }, left, y, 146);
        toggle("Item-Highlight", () -> c.itemGlow, v -> { c.itemGlow = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Block-Highlight", () -> c.blockHighlight, v -> { c.blockHighlight = v; c.save(); }, left, y, 146);
        toggle("Anti-Leak", () -> c.antiLeak, v -> { c.antiLeak = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Autoclicker", () -> c.autoClick, v -> { c.autoClick = v; c.save(); }, left, y, 146);
        button(() -> "Taste: " + (c.autoRight ? "Rechts" : "Links"), () -> { c.autoRight = !c.autoRight; c.save(); }, left + 154, y, 146, 20);
        y += 24;
        button(() -> "-", () -> { c.autoCps = Math.max(1, c.autoCps - 1); c.save(); }, left, y, 30, 20);
        button(() -> "CPS: " + c.autoCps, () -> {}, left + 34, y, 232, 20);
        button(() -> "+", () -> { c.autoCps = Math.min(40, c.autoCps + 1); c.save(); }, left + 270, y, 30, 20);
        y += 24;
        button(() -> "Totem-Sound: " + ToastyMisc.TOTEM[c.totemIdx % ToastyMisc.TOTEM.length][0],
            () -> { c.totemIdx = (c.totemIdx + 1) % ToastyMisc.TOTEM.length; c.save(); ToastyMisc.playTotem(c.totemIdx); }, left, y, 200, 20, true);
        button(() -> "Test", () -> ToastyMisc.playTotem(c.totemIdx), left + 204, y, 96, 20);
        y += 28;
        labels.add(new Label("Anti-Leak: gesperrte Wörter (Komma getrennt)", left, y, ToastyTheme.MUTED));
        TextFieldWidget f = field(left, y + 10, PW, c.leakWords, s -> { c.leakWords = s; c.save(); });
        f.setMaxLength(300);
        labels.add(new Label("Sperrt auch Koordinaten, IPs, PC-Namen.", left, y + 32, ToastyTheme.MUTED));
    }

    // ---------- Events: Twitch, Auktion, Giveaway ----------

    private void eventsTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        labels.add(new Label("Twitch-Kanal (Name)", left, y, ToastyTheme.MUTED));
        field(left, y + 10, PW, c.twitchChannel, s -> { c.twitchChannel = s.trim(); c.save(); });
        y += 34;
        toggle("Twitch-Chat", () -> c.twitch, v -> { c.twitch = v; c.save(); }, left, y, 146);
        button(() -> "Zeilen: " + c.twitchLines, () -> { c.twitchLines = c.twitchLines >= 15 ? 3 : c.twitchLines + 1; c.save(); }, left + 154, y, 146, 20);
        y += 24;
        toggle("Ansagen im Chat", () -> c.auctionAnnounce, v -> { c.auctionAnnounce = v; c.save(); }, left, y, 146);
        button(() -> "Auktion beenden", () -> ToastyEvents.endAuction(false), left + 154, y, 146, 20, true);
        y += 28;
        labels.add(new Label("Giveaway-Stichwort", left, y, ToastyTheme.MUTED));
        field(left, y + 10, 146, c.giveawayKeyword, s -> { c.giveawayKeyword = s.trim(); c.save(); });
        button(() -> "Gewinner ziehen", () -> ToastyEvents.drawWinner(true), left + 154, y + 9, 146, 20, true);
        y += 34;
        labels.add(new Label("/auction start <Preis> <Sek> <Item>", left, y, ToastyTheme.MUTED));
        labels.add(new Label("/giveaway start <Sek> <Preis>", left, y + 11, ToastyTheme.MUTED));
        labels.add(new Label("Gebote = erkannte Zahlungen an dich", left, y + 22, ToastyTheme.MUTED));
    }

    // ---------- YouTube ----------

    private void youtubeTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        labels.add(new Label("YouTube API-Key", left, y, ToastyTheme.MUTED));
        field(left, y + 10, PW, c.youtubeKey, s -> { c.youtubeKey = s.trim(); c.save(); });
        y += 34;
        labels.add(new Label("Live-Video (Link oder ID)", left, y, ToastyTheme.MUTED));
        field(left, y + 10, PW, c.youtubeVideo, s -> { c.youtubeVideo = s.trim(); c.save(); });
        y += 34;
        toggle("YouTube-Chat", () -> c.youtube, v -> { c.youtube = v; c.save(); }, left, y, 146);
        button(() -> "Status: " + ToastyYoutube.state, () -> {}, left + 154, y, 146, 20);
        y += 28;
        labels.add(new Label("Gratis-Key: Google Cloud Console → \"YouTube", left, y, ToastyTheme.MUTED));
        labels.add(new Label("Data API v3\" aktivieren → API-Key erstellen.", left, y + 11, ToastyTheme.MUTED));
        labels.add(new Label("Limit: ca. 4 h Live-Chat pro Tag (Quota).", left, y + 22, ToastyTheme.MUTED));
        labels.add(new Label("Chat erscheint im Twitch-Chat-HUD (rot).", left, y + 33, ToastyTheme.MUTED));
    }

    // ---------- Mehr HUDs ----------

    private void moreHudTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        toggle("Spieler in der Nähe", () -> c.nearbyHud, v -> { c.nearbyHud = v; c.save(); }, left, y, 146);
        toggle("Ziel-HUD", () -> c.targetHud, v -> { c.targetHud = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Combo-Zähler", () -> c.comboHud, v -> { c.comboHud = v; c.save(); }, left, y, 146);
        toggle("System (FPS/RAM)", () -> c.memHud, v -> { c.memHud = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Status (Leben/Hunger)", () -> c.vitalsHud, v -> { c.vitalsHud = v; c.save(); }, left, y, PW);        y += 24;
        toggle("Kompass", () -> c.compassHud, v -> { c.compassHud = v; c.save(); }, left, y, 146);
        toggle("Sell Status", () -> c.sellHud, v -> { c.sellHud = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Armor-Warnung", () -> c.armorWarn, v -> { c.armorWarn = v; c.save(); }, left, y, 146);
        int[] ws = {5, 10, 20, 30};
        button(() -> "Warnung bei " + c.armorWarnPct + " %", () -> {
            int n = ws[0];
            for (int i = 0; i < ws.length; i++) if (ws[i] == c.armorWarnPct) { n = ws[(i + 1) % ws.length]; break; }
            c.armorWarnPct = n; c.save();
        }, left + 154, y, 146, 20);
        y += 24;
        toggle("Top-Spieler", () -> c.topHud, v -> { c.topHud = v; c.save(); }, left, y, 146);
        toggle("Gewinn-Graph", () -> c.graphHud, v -> { c.graphHud = v; c.save(); }, left + 154, y, 146);
        y += 24;
        int[] pd = {0, 300, 500, 1000, 2000};
        button(() -> c.payDelayMs <= 0 ? "Auszahlung: sofort" : "Auszahlungs-Pause " + (c.payDelayMs / 1000.0) + " s", () -> {
            int n = pd[0];
            for (int i = 0; i < pd.length; i++) if (pd[i] == c.payDelayMs) { n = pd[(i + 1) % pd.length]; break; }
            c.payDelayMs = n; c.save();
        }, left, y, 146, 20);
        long[] ll = {0, 100_000L, 500_000L, 1_000_000L, 5_000_000L, 10_000_000L};
        button(() -> c.lossLimit <= 0 ? "Verlust-Limit: AUS" : "Verlust-Limit " + ToastyStats.fmt(c.lossLimit), () -> {
            long n = ll[0];
            for (int i = 0; i < ll.length; i++) if (ll[i] == c.lossLimit) { n = ll[(i + 1) % ll.length]; break; }
            c.lossLimit = n; c.save();
        }, left + 154, y, 146, 20);
    }

    // ---------- Scoreboard ----------

    private void scoreboardTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        toggle("Eigenes Scoreboard", () -> c.sbCustom, v -> { c.sbCustom = v; c.save(); }, left, y, 146);
        toggle("Zahlen", () -> c.sbNumbers, v -> { c.sbNumbers = v; c.save(); }, left + 154, y, 146);
        y += 24;
        button(() -> "Ecken kleiner", () -> { c.sbRadius = Math.max(0, c.sbRadius - 1); c.save(); }, left, y, 146, 20);
        button(() -> "Ecken größer (" + c.sbRadius + ")", () -> { c.sbRadius = Math.min(12, c.sbRadius + 1); c.save(); }, left + 154, y, 146, 20);
        y += 24;
        button(() -> "Transparenter", () -> { c.sbAlpha = Math.max(0, c.sbAlpha - 15); c.save(); }, left, y, 146, 20);
        button(() -> "Deckender (" + Math.round(c.sbAlpha * 100f / 255f) + "%)", () -> { c.sbAlpha = Math.min(255, c.sbAlpha + 15); c.save(); }, left + 154, y, 146, 20);
        y += 26;
        labels.add(new Label("Hintergrund (#RRGGBB)", left, y, ToastyTheme.MUTED));
        labels.add(new Label("Rand (#RRGGBB)", left + 154, y, ToastyTheme.MUTED));
        field(left, y + 10, 146, c.sbBg, s -> { if (ToastyTheme.parseHex(s) != null) { c.sbBg = s; c.save(); } });
        field(left + 154, y + 10, 146, c.sbBorder, s -> { if (ToastyTheme.parseHex(s) != null) { c.sbBorder = s; c.save(); } });
        y += 34;
        labels.add(new Label("Textfarbe (#RRGGBB)", left, y, ToastyTheme.MUTED));
        field(left, y + 10, 146, c.sbText, s -> { if (ToastyTheme.parseHex(s) != null) { c.sbText = s; c.save(); } });
        toggle("Rand", () -> c.sbBorderOn, v -> { c.sbBorderOn = v; c.save(); }, left + 154, y + 9, 146);
        y += 34;
        button(() -> "Kleiner", () -> { c.sbScale = Math.max(0.5f, Math.round((c.sbScale - 0.1f) * 10) / 10f); c.save(); }, left, y, 146, 20);
        button(() -> "Größer (" + c.sbScale + ")", () -> { c.sbScale = Math.min(2.5f, Math.round((c.sbScale + 0.1f) * 10) / 10f); c.save(); }, left + 154, y, 146, 20);
        y += 24;
        toggle("Design-Farben", () -> c.sbTheme, v -> { c.sbTheme = v; c.save(); }, left, y, 146);
    }

    // ---------- Befehle ----------

    private static final String[] CMD_DEFAULT = {
        // Gamble
        "/win", "/win Spieler", "/win all", "/lose", "/lose Spieler", "/lose all", "/toasty win", "/toasty lose",
        "/toasty stats", "/toasty reset", "/toasty multiplier 2", "/toasty min 0", "/toasty max 1b", "/toasty pattern <regex>",
        "/toasty debug", "/toasty scale 1",
        // Menues und Tools
        "/toasty lag", "/toasty perf", "/toasty perf reset", "/toasty lite", "/toasty menu", "/toasty inventory", "/toasty buttons", "/toasty trailer", "/toasty replay", "/toasty hud", "/toasty keys", "/toasty sell", "/toasty leak Test",
        "/calc", "/roll 100", "/coinflip", "/stopwatch start", "/stopwatch stop", "/stopwatch lap", "/stopwatch reset", "/timer 60 Name", "/timer cancel", "/glass", "/fullbright",
        // Notizen
        "/note add Text", "/note list", "/note remove 1", "/note clear",
        // Wegpunkte
        "/waypoint add Name", "/waypoint add Name 0 64 0", "/waypoint list", "/waypoint remove Name", "/waypoint clear",
        // Profile
        "/profile save Name", "/profile load Name", "/profile list", "/profile delete Name",
        // Twitch, Auktion, Giveaway
        "/twitch Kanal", "/twitch off", "/youtube Link", "/youtube off",
        "/auction start 1k 60 Item", "/auction end", "/auction cancel",
        "/giveaway start 60 Preis", "/giveaway end", "/giveaway cancel"
    };
    private static final String[] cmdText = CMD_DEFAULT.clone();
    private static int cmdPage = 0;
    private static final int PER_PAGE = 6;

    private static void runCommand(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.networkHandler == null) return;
        String c = text.trim();
        if (c.startsWith("/")) c = c.substring(1);
        if (c.isEmpty()) return;
        mc.player.networkHandler.sendChatCommand(c);
    }

    private static String cmdCategory(String c) {
        String s = c.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.startsWith("/win") || s.startsWith("/lose")) return "Gamble";
        if (s.startsWith("/toasty")) {
            for (String k : new String[]{"menu", "inventory", "buttons", "trailer", "replay", "hud", "keys", "sell", "leak", "lag", "perf", "lite"})
                if (s.startsWith("/toasty " + k)) return "Menüs";
            return "Gamble";
        }
        if (s.startsWith("/note")) return "Notizen";
        if (s.startsWith("/waypoint")) return "Wegpunkte";
        if (s.startsWith("/profile")) return "Profile";
        if (s.startsWith("/twitch") || s.startsWith("/youtube")) return "Chat";
        if (s.startsWith("/auction") || s.startsWith("/giveaway")) return "Events";
        return "Tools";
    }

    private void commandsTab(int y) {
        int pages = (cmdText.length + PER_PAGE - 1) / PER_PAGE;
        cmdPage = Math.max(0, Math.min(pages - 1, cmdPage));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = cmdPage * PER_PAGE + i;
            if (idx >= cmdText.length) break;
            TextFieldWidget f = field(left, y + i * 24 + 1, 214, cmdText[idx], s -> cmdText[idx] = s);
            f.setMaxLength(120);
            button(() -> "Ausführen", () -> runCommand(cmdText[idx]), left + 220, y + i * 24, 80, 20, true);
        }
        int ny = y + PER_PAGE * 24 + 2;
        button(() -> "<", () -> { cmdPage = (cmdPage + pages - 1) % pages; clearAndInit(); }, left, ny, 40, 20);
        button(() -> cmdCategory(cmdText[Math.min(cmdText.length - 1, cmdPage * PER_PAGE)]) + " · " + (cmdPage + 1) + "/" + pages, () -> {}, left + 44, ny, 130, 20);
        button(() -> ">", () -> { cmdPage = (cmdPage + 1) % pages; clearAndInit(); }, left + 178, ny, 40, 20);
        button(() -> "Reset", () -> { System.arraycopy(CMD_DEFAULT, 0, cmdText, 0, cmdText.length); clearAndInit(); }, left + 222, ny, 78, 20);
    }

    // ---------- Settings: Farben, Tasten, Optik ----------

    private static int presetIdx = 0;

    private void settingsTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        labels.add(new Label("Farbe 1 (#RRGGBB)", left, y, ToastyTheme.MUTED));
        labels.add(new Label("Farbe 2 (#RRGGBB)", left + 154, y, ToastyTheme.MUTED));
        field(left, y + 10, 146, c.color1, s -> { Integer v = ToastyTheme.parseHex(s); if (v != null) { c.color1 = ToastyTheme.toHex(v); c.save(); ToastyTheme.reload(c); } });
        field(left + 154, y + 10, 146, c.color2, s -> { Integer v = ToastyTheme.parseHex(s); if (v != null) { c.color2 = ToastyTheme.toHex(v); c.save(); ToastyTheme.reload(c); } });
        y += 34;
        button(() -> "Vorlage: " + PRESETS[presetIdx % PRESETS.length][0], () -> {
            presetIdx = (presetIdx + 1) % PRESETS.length;
            String[] p = PRESETS[presetIdx];
            c.color1 = p[1]; c.color2 = p[2]; c.save(); ToastyTheme.reload(c); clearAndInit();
        }, left, y, 146, 20, true);
        button(() -> "Heller", () -> { c.bgOpacity = Math.max(32, c.bgOpacity - 24); c.save(); ToastyTheme.reload(c); }, left + 154, y, 72, 20);
        button(() -> "Dunkler", () -> { c.bgOpacity = Math.min(255, c.bgOpacity + 24); c.save(); ToastyTheme.reload(c); }, left + 230, y, 70, 20);
        y += 24;
        toggle("Wasserzeichen", () -> c.watermark, v -> { c.watermark = v; c.save(); }, left, y, 146);
        y += 24;
        toggle("Sound", () -> c.sound, v -> { c.sound = v; c.save(); }, left, y, 146);
        toggle("Overlay-Effekte", () -> c.effects, v -> { c.effects = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Konfetti", () -> c.confetti, v -> { c.confetti = v; c.save(); }, left, y, 146);
        toggle("Toasts", () -> c.notify, v -> { c.notify = v; c.save(); }, left + 154, y, 146);
        y += 24;
        button(() -> "Tasten ändern …", () -> mc.setScreen(new ToastyKeysScreen(this)), left, y, 146, 20, true);
        button(() -> "HUD verschieben", () -> mc.setScreen(new ToastyHudEditor(this)), left + 154, y, 146, 20, true);
        y += 24;
        button(() -> "HUD kleiner", () -> { c.hudScale = Math.max(0.5f, Math.round((c.hudScale - 0.1f) * 10) / 10f); c.save(); }, left, y, 146, 20);
        button(() -> "HUD größer (" + c.hudScale + ")", () -> { c.hudScale = Math.min(3f, Math.round((c.hudScale + 0.1f) * 10) / 10f); c.save(); }, left + 154, y, 146, 20);
    }

    // ---------- Profile ----------

    private static String profName = "";

    private void profileTab(int y) {
        labels.add(new Label("Profilname (Buchstaben, Zahlen, _ -)", left, y, ToastyTheme.MUTED));
        TextFieldWidget f = field(left, y + 10, 214, profName, s -> profName = s);
        f.setMaxLength(30);
        button(() -> "Speichern", () -> {
            if (ToastyProfiles.save(profName)) { ToastyGamble.chat("Profil gespeichert."); clearAndInit(); }
            else ToastyGamble.chat("Bitte einen Namen eingeben.");
        }, left + 218, y + 9, 82, 20, true);
        y += 38;
        var list = ToastyProfiles.list();
        for (int i = 0; i < Math.min(5, list.size()); i++) {
            final String n = list.get(i);
            labels.add(new Label(textRenderer.trimToWidth(n, 190), left, y + 6, ToastyTheme.TEXT));
            button(() -> "Laden", () -> { if (ToastyProfiles.load(n)) { ToastyGamble.chat("Profil geladen."); clearAndInit(); } }, left + 200, y, 64, 20, true);
            button(() -> "X", () -> { ToastyProfiles.delete(n); clearAndInit(); }, left + 270, y, 30, 20);
            y += 22;
        }
        if (list.size() > 5) labels.add(new Label("+" + (list.size() - 5) + " weitere (/profile list)", left, y + 4, ToastyTheme.MUTED));
        if (list.isEmpty()) labels.add(new Label("Noch keine Profile.", left, y + 4, ToastyTheme.MUTED));
        labels.add(new Label("Speichert ALLES: Einstellungen, HUD-Positionen, Farben,", left, top + 216, ToastyTheme.MUTED));
        labels.add(new Label("Notizen, Wegpunkte und Tasten.", left, top + 227, ToastyTheme.MUTED));
    }

    // ---------- Wegpunkte ----------

    private static String wpName = "";

    private void waypointTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        toggle("Wegpunkte", () -> c.wpOn, v -> { c.wpOn = v; c.save(); }, left, y, 146);
        button(() -> "Hier setzen", () -> {
            if (wpName.isBlank()) { ToastyGamble.chat("Bitte einen Namen eingeben."); return; }
            ToastyWaypoints.addHere(wpName.trim().replace(' ', '_')); clearAndInit();
        }, left + 154, y, 146, 20, true);
        y += 24;
        labels.add(new Label("Name", left, y, ToastyTheme.MUTED));
        TextFieldWidget f = field(left, y + 10, PW, wpName, s -> wpName = s);
        f.setMaxLength(24);
        y += 34;
        int shown = Math.min(5, c.waypoints.size());
        for (int i = 0; i < shown; i++) {
            final ToastyConfig.Wp w = c.waypoints.get(i);
            String pos = c.streamer && c.streamerHideXyz ? "???" : w.x + " " + w.y + " " + w.z;
            labels.add(new Label(textRenderer.trimToWidth(w.name + "  " + pos, 224), left, y + 6, ToastyTheme.TEXT));
            button(() -> "Löschen", () -> { c.waypoints.remove(w); c.save(); clearAndInit(); }, left + 244, y, 56, 20);
            y += 22;
        }
        if (c.waypoints.size() > shown) labels.add(new Label("+" + (c.waypoints.size() - shown) + " weitere (/waypoint list)", left, y + 4, ToastyTheme.MUTED));
        if (c.waypoints.isEmpty()) labels.add(new Label("/waypoint add <Name> [x y z] · Marker + Pfeil im HUD", left, y + 4, ToastyTheme.MUTED));
    }

    // ---------- Streamer ----------

    private void streamerTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        toggle("Streamer-Modus", () -> c.streamer, v -> { c.streamer = v; c.save(); }, left, y, PW);
        y += 28;
        labels.add(new Label("Dein Name im Chat/HUD wird ersetzt durch:", left, y, ToastyTheme.MUTED));
        field(left, y + 10, PW, c.streamerName, s -> { c.streamerName = s; c.save(); });
        y += 36;
        toggle("Koordinaten verstecken", () -> c.streamerHideXyz, v -> { c.streamerHideXyz = v; c.save(); }, left, y, PW);
        y += 30;
        labels.add(new Label("Ersetzt deinen Namen in Chat-Nachrichten und im HUD.", left, y, ToastyTheme.MUTED));
        labels.add(new Label("Skin und Namensschild über dem Kopf ändern geht", left, y + 12, ToastyTheme.MUTED));
        labels.add(new Label("nur mit einem Mixin (noch nicht eingebaut).", left, y + 23, ToastyTheme.MUTED));
    }

    // ---------- Eigenes HUD ----------

    private void customTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        while (c.customLines.size() < 5) c.customLines.add("");
        toggle("Eigenes HUD", () -> c.customHud, v -> { c.customHud = v; c.save(); }, left, y, 146);
        button(() -> "Platzhalter (Chat)", () -> ToastyGamble.chatLong("Platzhalter: " + String.join(" ", ToastyPanels.TOKENS)),
            left + 154, y, 146, 20, true);
        y += 24;
        labels.add(new Label("Titel", left, y, ToastyTheme.MUTED));
        field(left, y + 10, PW, c.customTitle, s -> { c.customTitle = s; c.save(); });
        y += 34;
        labels.add(new Label("Zeilen (leer = ausgeblendet)", left, y, ToastyTheme.MUTED));
        y += 10;
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            TextFieldWidget f = field(left, y + i * 21, PW, c.customLines.get(i), s -> { c.customLines.set(idx, s); c.save(); });
            f.setMaxLength(80);
        }
    }

    // ---------- Notizen ----------

    private static String draft = "";

    private void notesTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        labels.add(new Label("Neue Notiz", left, y, ToastyTheme.MUTED));
        TextFieldWidget f = field(left, y + 10, 214, draft, s -> draft = s);
        f.setMaxLength(120);
        button(() -> "Hinzufügen", () -> {
            if (!draft.isBlank()) { c.notes.add(draft.trim()); draft = ""; c.save(); clearAndInit(); }
        }, left + 218, y + 9, 82, 20, true);
        y += 34;
        toggle("Notizen-HUD", () -> c.notesHud, v -> { c.notesHud = v; c.save(); }, left, y, PW);
        y += 26;
        int shown = Math.min(4, c.notes.size());
        for (int i = 0; i < shown; i++) {
            final int idx = i;
            labels.add(new Label((i + 1) + ". " + textRenderer.trimToWidth(c.notes.get(i), 224), left, y + 6, ToastyTheme.TEXT));
            button(() -> "Löschen", () -> { if (idx < c.notes.size()) { c.notes.remove(idx); c.save(); clearAndInit(); } }, left + 244, y, 56, 20);
            y += 22;
        }
        if (c.notes.size() > shown) labels.add(new Label("+" + (c.notes.size() - shown) + " weitere (/note list)", left, y + 4, ToastyTheme.MUTED));
        if (c.notes.isEmpty()) labels.add(new Label("/note add <Text> · /note remove <Nr>", left, y + 4, ToastyTheme.MUTED));
    }

    // ---------- Helper ----------

    private void toggle(String name, Supplier<Boolean> get, Consumer<Boolean> set, int x, int y, int w) {
        button(() -> name + ": " + (get.get() ? "AN" : "AUS"), () -> set.accept(!get.get()), x, y, w, 20);
    }

    private TextFieldWidget field(int x, int y, int w, String value, Consumer<String> onChange) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 18, Text.empty());
        f.setMaxLength(60);
        f.setText(value);
        f.setChangedListener(onChange);
        addDrawableChild(f);
        return f;
    }

    /** "5k", "1.5m", "100" -> Zahl; leer -> null */
    static Long parseAmount(String s) {
        s = s.trim().replace(" ", "");
        if (s.isEmpty()) return null;
        try {
            String suf = s.replaceAll("[^KkMmBbTt]", "");
            String num = s.replaceAll("[KkMmBbTt]", "");
            return ToastyGamble.parseAmount(num, suf.length() > 0 ? suf.substring(0, 1) : "");
        } catch (Exception e) { return null; }
    }

    // ---------- Rendering ----------

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean inWorld = MinecraftClient.getInstance().world != null;
        int ga = inWorld ? 0x30 : 0xF0;
        ctx.fillGradient(0, 0, width, height,
            ToastyTheme.alpha(ToastyTheme.lerp(0xFF05030C, ToastyTheme.PURPLE, 0.15f), ga),
            ToastyTheme.alpha(ToastyTheme.lerp(0xFF05030C, ToastyTheme.BLUE, 0.18f), ga));
        int need = panelBottom;
        for (var el : children()) if (el instanceof net.minecraft.client.gui.widget.ClickableWidget cw && cw.getAlpha() > 0f)
            need = Math.max(need, cw.getY() + cw.getHeight() - top + 14);
        ToastyTheme.panel(ctx, left - 12, top - 28, PW + 24, need + 28, inWorld ? 0xB4 : 0xFF);
        ToastyTheme.gradientText(ctx, textRenderer, "Toasty Client", left, top - 20);
    }

    @Override
    protected void extra(DrawContext ctx, int mouseX, int mouseY) {
        for (Label l : labels) ctx.drawTextWithShadow(textRenderer, l.text(), l.x(), l.y(), l.color());
        String hint = "v1.3.0 · Rechte Shift = Menü";
        ctx.drawTextWithShadow(textRenderer, hint, left + PW - textRenderer.getWidth(hint), top - 20, ToastyTheme.MUTED);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
