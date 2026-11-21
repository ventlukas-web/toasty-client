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
    /** Seiten (Blaetter): alte Tab-Nummern bleiben als Seiten-IDs erhalten. 21 = Komfort (Reconnect, Totem). */
    private static final String[] TABS = {"Allgemein", "Gamble", "HUD", "Design", "Effekte", "FPS", "Tools", "Sell", "Extras", "Twitch & Events", "Notizen", "Mehr HUDs", "Eigenes", "Scoreboard", "Streamer", "Commands", "Profile", "Wegpunkte", "Settings", "YouTube", "PvP", "Komfort", "Crosshair"};
    private static final String[] CATS = {"General", "Gamble", "Visuals", "PvP", "Streaming", "Tools", "Commands"};
    private static final int[][] CAT_PAGES = {{0, 3, 18, 16}, {1}, {4, 22, 2, 11, 12, 13, 8}, {20}, {9, 19, 14}, {6, 10, 17, 21, 5}, {15}};

    private static int catOf(int leaf) {
        for (int c = 0; c < CAT_PAGES.length; c++) for (int p : CAT_PAGES[c]) if (p == leaf) return c;
        return 0;
    }
    private static int pageOf(int leaf) {
        int[] ps = CAT_PAGES[catOf(leaf)];
        for (int i = 0; i < ps.length; i++) if (ps[i] == leaf) return i;
        return 0;
    }
    private static int tab = 0;

    /** Merkt sich den Tab fuer das naechste Oeffnen (z. B. vom Hauptmenue-Schnellzugriff). */
    public static void openTab(int t) { tab = t; }
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
        feats.clear();
        ToastyConfig cfg0 = ToastyConfig.get();
        int baseLeft = width / 2 - PW / 2, baseTop = Math.max(32, (height - 300) / 2 + 28);
        left = Math.max(14, baseLeft);
        top = baseTop;

        // Grosser Master-Schalter: alle Features auf einmal an/aus
        masterBtn = button(() -> "Toasty Client  ·  " + (ToastyConfig.get().masterOn ? "Alle Features AN" : "Alle Features AUS"),
            () -> { ToastyMaster.toggle(); clearAndInit(); }, left, top, PW, 22, true);

        // Sektion 1: Bereiche (anklicken = ausklappen) + deren Teile
        cardAy1 = top + 27;
        int tw = PW / 4, cat = catOf(tab);
        for (int i = 0; i < CATS.length; i++) {
            final int idx = i;
            button(() -> CATS[idx], () -> {
                if (catOf(tab) == idx) expanded = !expanded; else { tab = CAT_PAGES[idx][0]; expanded = true; }
                query = ""; scroll = 0; clearAndInit();
            }, left + (i % 4) * tw, top + 31 + (i / 4) * 16, tw - 2, 15, cat == i);
        }
        final int[] ps = CAT_PAGES[cat];
        int chipsEnd = top + 31 + 32;
        if (ps.length > 1 && expanded) {
            int cwid = PW / 3, cy0 = chipsEnd + 4;
            for (int i = 0; i < ps.length; i++) {
                final int leaf = ps[i];
                button(() -> TABS[leaf], () -> { tab = leaf; query = ""; scroll = 0; clearAndInit(); },
                    left + (i % 3) * cwid, cy0 + (i / 3) * 16, cwid - 2, 15, leaf == tab);
            }
            chipsEnd = cy0 + ((ps.length + 2) / 3) * 16;
        }
        cardAy2 = chipsEnd + 3;

        // Sektion 2: Suche + Inhalt der gewaehlten Seite
        cardBy1 = cardAy2 + 5;
        int y = cardBy1 + 33;
        panelBottom = 290;
        // Suchleiste: findet Einstellungen, Tools und Befehle in allen Seiten
        TextFieldWidget sf = field(left, cardBy1 + 7, PW, query, s -> { query = s; scroll = 0; searchFocus = true; clearAndInit(); });
        sf.setPlaceholder(Text.literal("Suche … (Einstellungen, Tools, Befehle)"));
        searchField = sf;
        if (searchFocus) { setFocused(sf); sf.setFocused(true); searchFocus = false; }
        int firstChild = children().size();
        if (query.trim().isEmpty()) runTab(tab, y);
        else searchResults(y);
        // Inhalt einsammeln: wird bei Platzmangel scrollbar, die untere Leiste bleibt fest und klickbar
        slots.clear();
        int bottomMost = y;
        for (int i = firstChild; i < children().size(); i++)
            if (children().get(i) instanceof net.minecraft.client.gui.widget.ClickableWidget cw) {
                slots.add(new Slot(cw, cw.getY()));
                bottomMost = Math.max(bottomMost, cw.getY() + cw.getHeight());
            }
        for (Label l : labels) bottomMost = Math.max(bottomMost, l.y() + 10);
        contentH = bottomMost - y + 2;
        int avail = height - 40 - y - 6;
        bandTop = y;
        bandH = Math.max(48, Math.min(contentH, avail));
        int by = y + bandH + 6;
        button(() -> "Tasten …", () -> MinecraftClient.getInstance().setScreen(new ToastyKeysScreen(this)), left, by, 96, 20, true);
        button(() -> "Schließen", this::close, left + 102, by, 96, 20);
        button(() -> "HUD verschieben", () -> MinecraftClient.getInstance().setScreen(new ToastyHudEditor(this)), left + 204, by, 96, 20, true);
        cardBy2 = by - 4;
        panelBottom = by + 20 + 24 - top;
        applyScroll();
    }

    private TextFieldWidget searchField;
    private net.minecraft.client.gui.widget.ButtonWidget masterBtn;
    private static boolean expanded = true;
    private int cardAy1, cardAy2, cardBy1, cardBy2;

    @Override
    protected boolean bigButton(net.minecraft.client.gui.widget.ButtonWidget w) { return w == masterBtn; }

    @Override
    protected boolean bigOn() { return ToastyConfig.get().masterOn; }

    /** Ein Klick in die Suchleiste reicht: sofort fokussieren (auch direkt nach einem Neuaufbau). */
    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        // Rechtsklick auf ein Feature: weitere Einstellungen dazu
        if (click.button() == 1) {
            for (var e : feats.entrySet()) {
                var w = e.getKey();
                if (w.visible && children().contains(w) && w.isMouseOver(click.x(), click.y())) {
                    Feat f = e.getValue();
                    MinecraftClient.getInstance().setScreen(new ToastyFeatureScreen(this, f.name(), f.get(), f.set()));
                    return true;
                }
            }
        }
        TextFieldWidget sf = searchField;
        if (sf != null && sf.visible && sf.isMouseOver(click.x(), click.y())) {
            setFocused(sf);
            sf.setFocused(true);
        }
        return super.mouseClicked(click, doubled);
    }

    private record Slot(net.minecraft.client.gui.widget.ClickableWidget w, int y) {}
    private final List<Slot> slots = new ArrayList<>();
    private static int scroll = 0;
    private int contentH, bandTop, bandH;

    private int maxScroll() { return Math.max(0, contentH - bandH); }

    private void applyScroll() {
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        for (Slot sl : slots) {
            int ny = sl.y() - scroll;
            sl.w().setY(ny);
            sl.w().visible = ny >= bandTop - 1 && ny + sl.w().getHeight() <= bandTop + bandH + 1;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll() > 0) {
            scroll -= (int) Math.signum(verticalAmount) * 24;
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private static String query = "";
    private boolean searchFocus;

    private void runTab(int t, int y) {
        switch (t) {
            case 0 -> generalTab(y);
            case 1 -> gambleTab(y);
            case 2 -> hudTab(y);
            case 3 -> designTab(y);
            case 4 -> visualsTab(y);
            case 5 -> perfTab(y);
            case 6 -> toolsTab(y);
            case 8 -> extrasTab(y);
            case 21 -> comfortTab(y);
            case 22 -> crosshairTab(y);
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
            case 19 -> youtubeTab(y);
            default -> pvpTab(y);
        }
    }

    /** Baut jeden Tab testweise auf, behaelt nur Buttons/Schalter, die zur Suche passen, und reiht sie untereinander auf. */
    private void searchResults(int y0) {
        String q = query.trim().toLowerCase(java.util.Locale.ROOT);
        java.util.List<Btn> hits = new ArrayList<>();
        java.util.List<String> from = new ArrayList<>();
        java.util.List<Integer> tabOf = new ArrayList<>();
        int saveTab = tab;
        for (int t = 0; t < TABS.length; t++) {
            if (t == 7) continue; // frueher Sell-Tab (entfernt)
            int b0 = btns.size(), c0 = children().size(), l0 = labels.size();
            tab = t;
            try { runTab(t, y0); } catch (Throwable ex) { /* Tab nicht durchsuchbar */ }
            java.util.List<Btn> added = new ArrayList<>(btns.subList(b0, btns.size()));
            java.util.List<net.minecraft.client.gui.Element> kids = new ArrayList<>(children().subList(c0, children().size()));
            for (var e : kids) remove(e);
            while (btns.size() > b0) btns.remove(btns.size() - 1);
            while (labels.size() > l0) labels.remove(labels.size() - 1);
            for (Btn b : added) {
                String lab;
                try { lab = b.label().get(); } catch (Throwable ex) { continue; }
                if (lab != null && !lab.isEmpty() && lab.toLowerCase(java.util.Locale.ROOT).contains(q)) {
                    hits.add(b); from.add(CATS[catOf(t)] + " › " + TABS[t]); tabOf.add(t);
                }
            }
        }
        tab = saveTab;
        // Tabs, deren Name passt
        int y = y0;
        for (int t = 0; t < TABS.length && y < y0 + 8 * 22; t++) {
            if (t == 7) continue;
            String full = CATS[catOf(t)] + " " + TABS[t];
            if (!full.toLowerCase(java.util.Locale.ROOT).contains(q)) continue;
            final int idx = t;
            button(() -> "Öffnen: " + CATS[catOf(idx)] + " › " + TABS[idx], () -> { tab = idx; query = ""; scroll = 0; clearAndInit(); }, left, y, PW, 20, true);
            y += 22;
        }
        int shown = 0;
        for (int k = 0; k < hits.size(); k++) {
            if (shown >= 8) break;
            Btn b = hits.get(k);
            String tn = from.get(k);
            var w = b.w();
            w.setX(left); w.setY(y); w.setWidth(PW);
            addDrawableChild(w);
            btns.add(new Btn(w, () -> tn + " › " + b.label().get(), b.accent()));
            y += 22; shown++;
        }
        if (shown == 0 && y == y0) labels.add(new Label("Nichts gefunden für „" + query.trim() + "“", left, y0 + 4, ToastyTheme.MUTED));
        else if (hits.size() > shown) labels.add(new Label("… " + (hits.size() - shown) + " weitere – Suche eingrenzen", left, y + 2, ToastyTheme.MUTED));
    }

    // ---------- General ----------

    private void generalTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        button(() -> "Rechner öffnen", () -> mc.setScreen(new ToastyCalculatorScreen(this)), left, y, PW, 20, true);
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
        button(() -> "Roulette öffnen", () -> MinecraftClient.getInstance().setScreen(new ToastyRouletteScreen(this)), left, y + 64, PW, 20, true);
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

    // Name, Farbe 1, Farbe 2, Hintergrund-Grundfarbe, Deckkraft Hintergrund, Deckkraft Buttons
    private static final Object[][] THEMES = {
        {"Standard", "#A855F7", "#7DD3FC", "#0A0614", 208, 200},
        {"Amoled", "#A855F7", "#7DD3FC", "#000000", 255, 235},
        {"Mitternacht", "#6366F1", "#38BDF8", "#050B1E", 224, 210},
        {"Pastell", "#F9A8D4", "#A5B4FC", "#2A2438", 224, 190},
        {"Glas", "#A855F7", "#7DD3FC", "#0A0614", 96, 110},
        {"Wald", "#34D399", "#BBF7D0", "#04120C", 216, 200},
        {"Blut", "#EF4444", "#F97316", "#140505", 216, 200},
        {"Sunset", "#FB7185", "#FBBF24", "#1A0A12", 208, 200},
        {"Eis", "#38BDF8", "#E0F2FE", "#06121C", 208, 200},
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
        int y3 = y + 150;
        labels.add(new Label("Themes (Farben + Hintergrund + Deckkraft)", left, y3, ToastyTheme.MUTED));
        for (int i = 0; i < THEMES.length; i++) {
            final Object[] th = THEMES[i];
            button(() -> (String) th[0], () -> {
                c.color1 = (String) th[1]; c.color2 = (String) th[2]; c.themeBase = (String) th[3];
                c.bgOpacity = (Integer) th[4]; c.buttonAlpha = (Integer) th[5];
                c.save(); ToastyTheme.reload(c); clearAndInit();
            }, left + (i % 3) * 102, y3 + 11 + (i / 3) * 22, 96, 20, true);
        }
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

        toggle("Intro-Trailer", () -> c.introTrailer, v -> { c.introTrailer = v; c.save(); }, left, y, 146);
        y += 24;
        y += 4;
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
        button(() -> "Screenshot (ohne HUD)", () -> ToastyMisc.screenshot(true), left, y, 146, 20, true);
        button(() -> "Screenshot (mit HUD)", () -> ToastyMisc.screenshot(false), left + 154, y, 146, 20);
        y += 24;
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


    // ---------- Extras ----------

    private void extrasTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        toggle("Fullbright", () -> c.fullbright, v -> { c.fullbright = v; c.save(); }, left, y, 146);
        toggle("Item-Highlight", () -> c.itemGlow, v -> { c.itemGlow = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Block-Highlight", () -> c.blockHighlight, v -> { c.blockHighlight = v; c.save(); }, left, y, 146);
        toggle("Anti-Leak", () -> c.antiLeak, v -> { c.antiLeak = v; c.save(); if (!ToastyGlass.setCloak(v)) ToastyGamble.chat("Anti-Leak-Pack nicht gefunden."); }, left + 154, y, 146);
        y += 24;
        toggle("Eigener Nametag (F5)", () -> c.ownNametag, v -> { c.ownNametag = v; c.save(); }, left, y, 146);
        toggle("NoFog", () -> c.noFog, v -> { c.noFog = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Saturation-Rand", () -> c.satBorder, v -> { c.satBorder = v; c.save(); }, left, y, 146);
        toggle("Himmel im Menü", () -> c.menuSky, v -> { c.menuSky = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Himmel-Farbton (Welt)", () -> c.skyTint, v -> { c.skyTint = v; c.save(); }, left, y, 146);
        y += 28;
        labels.add(new Label("Item-Highlight: nur diese Items (Name/ID, Komma; leer = alle)", left, y, ToastyTheme.MUTED));
        TextFieldWidget gf = field(left, y + 10, PW, c.glowItems == null ? "" : c.glowItems, s -> { c.glowItems = s; c.save(); });
        gf.setMaxLength(300);
        button(() -> "Item in Hand hinzufügen", () -> {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player == null || mc.player.getMainHandStack().isEmpty()) { ToastyGamble.chat("Kein Item in der Hand."); return; }
            String id = net.minecraft.registry.Registries.ITEM.getId(mc.player.getMainHandStack().getItem()).getPath();
            String cur = c.glowItems == null ? "" : c.glowItems.trim();
            if (ToastyEsp.glowMatches(cur, id, id)) { ToastyGamble.chat(id + " ist schon im Filter."); return; }
            c.glowItems = cur.isEmpty() ? id : cur + ", " + id;
            c.save(); gf.setText(c.glowItems);
        }, left, y + 34, 172, 20);
        button(() -> "Filter leeren", () -> { c.glowItems = ""; c.save(); gf.setText(""); }, left + 176, y + 34, 124, 20);
        y += 62;
        labels.add(new Label("Anti-Leak: gesperrte Wörter (Komma getrennt)", left, y, ToastyTheme.MUTED));
        TextFieldWidget f = field(left, y + 10, PW, c.leakWords, s -> { c.leakWords = s; c.save(); });
        f.setMaxLength(300);
        labels.add(new Label("Sperrt auch Koordinaten, IPs, PC-Namen.", left, y + 32, ToastyTheme.MUTED));
    }

    // ---------- Crosshair ----------

    private int previewY = -1;

    private void crosshairTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        previewY = y;
        y += 56; // Platz fuer die Vorschau (wird in extra() gezeichnet)
        labels.add(new Label(" ", left, y - 4, ToastyTheme.MUTED));
        toggle("Crosshair", () -> c.chOn, v -> { c.chOn = v; c.save(); }, left, y, 146);
        button(() -> "Stil: " + ToastyCrosshair.STYLES[Math.floorMod(c.chStyle, ToastyCrosshair.STYLES.length)],
            () -> { ToastyCrosshair.applyStyle(c, c.chStyle + 1); c.save(); }, left + 154, y, 146, 20, true);
        y += 24;
        button(() -> "-", () -> { c.chLen = Math.max(0, c.chLen - 1); c.chStyle = 5; c.save(); }, left, y, 30, 20);
        button(() -> "Länge: " + c.chLen, () -> {}, left + 34, y, PW - 68, 20);
        button(() -> "+", () -> { c.chLen = Math.min(24, c.chLen + 1); c.chStyle = 5; c.save(); }, left + PW - 30, y, 30, 20);
        y += 24;
        button(() -> "-", () -> { c.chGap = Math.max(0, c.chGap - 1); c.chStyle = 5; c.save(); }, left, y, 30, 20);
        button(() -> "Abstand: " + c.chGap, () -> {}, left + 34, y, PW - 68, 20);
        button(() -> "+", () -> { c.chGap = Math.min(16, c.chGap + 1); c.chStyle = 5; c.save(); }, left + PW - 30, y, 30, 20);
        y += 24;
        button(() -> "-", () -> { c.chThick = Math.max(1, c.chThick - 1); c.chStyle = 5; c.save(); }, left, y, 30, 20);
        button(() -> "Dicke: " + c.chThick, () -> {}, left + 34, y, PW - 68, 20);
        button(() -> "+", () -> { c.chThick = Math.min(5, c.chThick + 1); c.chStyle = 5; c.save(); }, left + PW - 30, y, 30, 20);
        y += 24;
        toggle("Mittelpunkt", () -> c.chDot, v -> { c.chDot = v; c.chStyle = 5; c.save(); }, left, y, 146);
        toggle("Kontur", () -> c.chOutline, v -> { c.chOutline = v; c.save(); }, left + 154, y, 146);
        y += 24;
        button(() -> "Farbe: " + ToastyCrosshair.COLORS[Math.floorMod(c.chColorIdx, ToastyCrosshair.COLORS.length)] + (c.chHex != null && !c.chHex.isBlank() ? " (Hex)" : ""),
            () -> { c.chColorIdx = (c.chColorIdx + 1) % ToastyCrosshair.COLORS.length; c.chHex = ""; c.save(); }, left, y, PW, 20);
        y += 28;
        labels.add(new Label("Eigene Farbe (#RRGGBB, leer = Auswahl oben)", left, y, ToastyTheme.MUTED));
        TextFieldWidget hf = field(left, y + 10, PW, c.chHex == null ? "" : c.chHex, s -> { c.chHex = s; c.save(); });
        hf.setMaxLength(7);
        y += 38;
        labels.add(new Label("Eigene Fadenkreuze (3 Speicherplätze)", left, y, ToastyTheme.MUTED));
        y += 11;
        for (int i = 0; i < 3; i++) {
            final int slot = i;
            button(() -> "Slot " + (slot + 1) + " speichern", () -> { c.chSlots[slot] = ToastyCrosshair.pack(c); c.save(); }, left, y, 146, 20);
            button(() -> c.chSlots[slot] == null || c.chSlots[slot].isBlank() ? "Slot " + (slot + 1) + " (leer)" : "Slot " + (slot + 1) + " laden",
                () -> { if (ToastyCrosshair.load(c, c.chSlots[slot])) c.save(); }, left + 154, y, 146, 20, true);
            y += 24;
        }
    }

    // ---------- Komfort: Auto-Reconnect, Totem-Sound ----------

    private void comfortTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        button(() -> "Beitreten-Button Größe: " + c.startBtnSize + "/5", () -> { c.startBtnSize = c.startBtnSize % 5 + 1; c.save(); }, left, y, 146, 20);
        button(() -> "Multiplayer-Button Größe: " + (c.mpBtnSize == 0 ? "wie Singleplayer" : c.mpBtnSize + "/5"), () -> { c.mpBtnSize = (c.mpBtnSize + 1) % 6; c.save(); }, left + 154, y, 146, 20);
        y += 22;
        labels.add(new Label("Wirkt, sobald das Hauptmenü neu geöffnet wird.", left, y, ToastyTheme.MUTED));
        y += 14;
        button(() -> "Inventar-GUI-Skalierung: " + (c.invGuiScale <= 0 ? "aus" : String.valueOf(c.invGuiScale)),
            () -> { c.invGuiScale = (c.invGuiScale + 1) % 7; c.save(); }, left, y, PW, 20);
        y += 22;
        labels.add(new Label("0/aus = deine normale Skalierung. Gilt ab dem nächsten Öffnen.", left, y, ToastyTheme.MUTED));
        y += 14;
        toggle("Auto-Reconnect", () -> c.autoReconnect, v -> { c.autoReconnect = v; c.save(); }, left, y, 146);
        button(() -> "Reconnect-Pause: " + c.reconnectSec + " s", () -> {
            int[] st = {3, 5, 10, 30};
            int n = st[0];
            for (int i = 0; i < st.length; i++) if (st[i] == c.reconnectSec) { n = st[(i + 1) % st.length]; break; }
            c.reconnectSec = n; c.save();
        }, left + 154, y, 146, 20);
        y += 24;
        button(() -> "Totem-Sound: " + ToastyMisc.TOTEM[c.totemIdx % ToastyMisc.TOTEM.length][0],
            () -> { c.totemIdx = (c.totemIdx + 1) % ToastyMisc.TOTEM.length; c.save(); ToastyMisc.playTotem(c.totemIdx); }, left, y, 200, 20, true);
        button(() -> "Test", () -> ToastyMisc.playTotem(c.totemIdx), left + 204, y, 96, 20);
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
        toggle("Combo-Zähler", () -> c.comboHud, v -> { c.comboHud = v; c.save(); }, left, y, 146);
        toggle("System (FPS/RAM)", () -> c.memHud, v -> { c.memHud = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Status (Leben/Hunger)", () -> c.vitalsHud, v -> { c.vitalsHud = v; c.save(); }, left, y, PW);        y += 24;
        toggle("Kompass", () -> c.compassHud, v -> { c.compassHud = v; c.save(); }, left, y, 146);
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
        labels.add(new Label("Verlust-Limit (leer = aus, z. B. 250k)", left + 154, y - 1, ToastyTheme.MUTED));
        field(left + 154, y + 10, 146, c.lossLimit <= 0 ? "" : ToastyStats.fmt(c.lossLimit), s -> {
            Long v = parseAmount(s); c.lossLimit = v == null || v < 0 ? 0 : v; c.save();
        });
    }

    // ---------- Scoreboard ----------

    private void scoreboardTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        toggle("Eigenes Scoreboard", () -> c.sbCustom, v -> { c.sbCustom = v; c.save(); }, left, y, 146);
        toggle("Zahlen", () -> c.sbNumbers, v -> { c.sbNumbers = v; c.save(); }, left + 154, y, 146);
        y += 24;
        button(() -> "Ecken kleiner", () -> { c.sbRadius = Math.max(5, c.sbRadius - 1); c.save(); }, left, y, 146, 20);
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
        "/toasty lag", "/toasty perf", "/toasty perf reset", "/toasty lite", "/toasty menu", "/toasty inventory", "/toasty buttons", "/toasty trailer", "/toasty hud", "/toasty keys", "/toasty leak Test",
        "/calc", "/roulette", "/roll 100", "/coinflip", "/stopwatch start", "/stopwatch stop", "/stopwatch lap", "/stopwatch reset", "/timer 60 Name", "/timer cancel", "/glass", "/fullbright", "/shot", "/payback",
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
            for (String k : new String[]{"menu", "inventory", "buttons", "trailer", "hud", "keys", "leak", "lag", "perf", "lite"})
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
        y += 24;
        toggle("Wasserzeichen", () -> c.watermark, v -> { c.watermark = v; c.save(); }, left, y, 146);
        y += 24;
        toggle("Sound", () -> c.sound, v -> { c.sound = v; c.save(); }, left, y, 146);
        toggle("Overlay-Effekte", () -> c.effects, v -> { c.effects = v; c.save(); }, left + 154, y, 146);
        y += 24;
        toggle("Konfetti", () -> c.confetti, v -> { c.confetti = v; c.save(); }, left, y, 146);
        toggle("Toasts", () -> c.notify, v -> { c.notify = v; c.save(); }, left + 154, y, 146);
        y += 24;
        button(() -> "HUD verschieben", () -> mc.setScreen(new ToastyHudEditor(this)), left, y, PW, 20, true);
        y += 24;
        button(() -> "HUD kleiner", () -> { c.hudScale = Math.max(0.5f, Math.round((c.hudScale - 0.1f) * 10) / 10f); c.save(); }, left, y, 146, 20);
        button(() -> "HUD größer (" + c.hudScale + ")", () -> { c.hudScale = Math.min(3f, Math.round((c.hudScale + 0.1f) * 10) / 10f); c.save(); }, left + 154, y, 146, 20);
        y += 28;
        labels.add(new Label("Beitreten-Button (Hauptmenü): Name", left, y, ToastyTheme.MUTED));
        labels.add(new Label("Server-IP", left + 154, y, ToastyTheme.MUTED));
        field(left, y + 10, 146, c.serverName, s -> { c.serverName = s.trim().isEmpty() ? "WindSMP" : s.trim(); c.save(); });
        field(left + 154, y + 10, 146, c.serverIp, s -> { c.serverIp = s.trim().isEmpty() ? "windsmp.net" : s.trim(); c.save(); });
        y += 34;
        toggle("Beitreten-Button", () -> c.joinButton, v -> { c.joinButton = v; c.save(); }, left, y, 146);
        y += 24;
        button(() -> "Mauszeiger: " + ToastyCursor.STYLES[Math.floorMod(c.cursorStyle, ToastyCursor.STYLES.length)],
            () -> { c.cursorStyle = (c.cursorStyle + 1) % ToastyCursor.STYLES.length; c.save(); }, left, y, 146, 20, true);
        button(() -> "Zeiger-Größe: " + (int) (ToastyCursor.SIZES[Math.floorMod(c.cursorSize, ToastyCursor.SIZES.length)] * 100) + " %",
            () -> { c.cursorSize = (c.cursorSize + 1) % ToastyCursor.SIZES.length; c.save(); }, left + 154, y, 146, 20);
    }

    // ---------- PvP ----------

    private void pvpTab(int y) {
        ToastyConfig c = ToastyConfig.get();
        labels.add(new Label("Nur Anzeigen & Komfort – keine Kampfhilfen.", left, y, ToastyTheme.MUTED));
        y += 14;
        String[] names = {"CPS-Anzeige", "Reach-Anzeige", "Item-Zähler", "Treffer-Marker", "Treffer-Sound", "Low-HP-Warnung", "Cooldown-Balken",
            "Combo-Zähler", "Armor-Haltbarkeit", "Effekte", "Keystrokes", "Status (Leben/Hunger)",
            "Saturation-Rand", "Auto-Sprint"};
        java.util.function.Supplier<?>[] get = {
            () -> c.cpsHud, () -> c.reachHud, () -> c.pvpItems, () -> c.hitMarker, () -> c.hitSound, () -> c.lowHpWarn, () -> c.cooldownBar,
            () -> c.comboHud, () -> c.armorHud, () -> c.potionHud, () -> c.keystrokes, () -> c.vitalsHud,
            () -> c.satBorder, () -> c.autoSprint};
        java.util.function.Consumer<Boolean>[] set = new java.util.function.Consumer[]{
            (java.util.function.Consumer<Boolean>) v -> c.cpsHud = v, (java.util.function.Consumer<Boolean>) v -> c.reachHud = v,
            (java.util.function.Consumer<Boolean>) v -> c.pvpItems = v, (java.util.function.Consumer<Boolean>) v -> c.hitMarker = v,
            (java.util.function.Consumer<Boolean>) v -> c.hitSound = v, (java.util.function.Consumer<Boolean>) v -> c.lowHpWarn = v,
            (java.util.function.Consumer<Boolean>) v -> c.cooldownBar = v, (java.util.function.Consumer<Boolean>) v -> c.comboHud = v,
            (java.util.function.Consumer<Boolean>) v -> c.armorHud = v, (java.util.function.Consumer<Boolean>) v -> c.potionHud = v,
            (java.util.function.Consumer<Boolean>) v -> c.keystrokes = v, (java.util.function.Consumer<Boolean>) v -> c.vitalsHud = v,
            (java.util.function.Consumer<Boolean>) v -> c.satBorder = v,
            (java.util.function.Consumer<Boolean>) v -> c.autoSprint = v};
        for (int k = 0; k < names.length; k++) {
            final int idx = k;
            toggle(names[k], () -> (Boolean) get[idx].get(), v -> { set[idx].accept(v); c.save(); },
                left + (k % 2) * 154, y + (k / 2) * 24, 146);
        }
        int by2 = y + ((names.length + 1) / 2) * 24;
        button(() -> "Treffer-Sound: " + ToastyPvp.SOUNDS[Math.floorMod(c.hitSoundIdx, ToastyPvp.SOUNDS.length)][0], () -> {
            c.hitSoundIdx = (c.hitSoundIdx + 1) % ToastyPvp.SOUNDS.length; c.save(); ToastyPvp.playHit();
        }, left, by2, 200, 20, true);
        button(() -> "Test", ToastyPvp::playHit, left + 204, by2, 96, 20);
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
        if (list.size() > 5 || list.isEmpty()) y += 16;
        labels.add(new Label("Speichert ALLES: Einstellungen, HUD-Positionen,", left, y + 4, ToastyTheme.MUTED));
        labels.add(new Label("Farben, Notizen, Wegpunkte und Tasten.", left, y + 16, ToastyTheme.MUTED));
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

    private record Feat(String name, Supplier<Boolean> get, Consumer<Boolean> set) {}
    private final java.util.Map<net.minecraft.client.gui.widget.ButtonWidget, Feat> feats = new java.util.HashMap<>();

    private void toggle(String name, Supplier<Boolean> get, Consumer<Boolean> set, int x, int y, int w) {
        feats.put(button(() -> name + ": " + (get.get() ? "AN" : "AUS"), () -> set.accept(!get.get()), x, y, w, 20), new Feat(name, get, set));
    }

    private TextFieldWidget field(int x, int y, int w, String value, Consumer<String> onChange) {
        // Runder Hintergrund wird vom Feld selbst gezeichnet (wandert beim Scrollen mit, bleibt bei der Suche nicht als Rest zurueck)
        TextFieldWidget f = new TextFieldWidget(textRenderer, x + 8, y + 5, w - 16, 12, Text.empty()) {
            @Override
            public void renderWidget(net.minecraft.client.gui.DrawContext dc, int mx, int my, float delta) {
                if (!this.visible) return;
                ToastyTheme.fieldBg(dc, getX() - 8, getY() - 5, getWidth() + 16, 18, isFocused());
                super.renderWidget(dc, mx, my, delta);
            }
        };
        f.setDrawsBackground(false);
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
        ToastyTheme.panel(ctx, left - 12, top - 28, PW + 24, need + 28, inWorld ? 0x72 : 0xA0);
        ToastyTheme.gradientText(ctx, textRenderer, "Toasty Client", left, top - 20);
        // getrennte Sektionen: Bereiche | Suche + Inhalt
        ToastyTheme.roundRectExact(ctx, left - 6, cardAy1, PW + 12, cardAy2 - cardAy1, 6, ToastyTheme.alpha(0xFFFFFFFF, 0x0C));
        ToastyTheme.roundOutlineExact(ctx, left - 6, cardAy1, PW + 12, cardAy2 - cardAy1, 6, ToastyTheme.alpha(0xFFFFFFFF, 0x20));
        ToastyTheme.roundRectExact(ctx, left - 6, cardBy1, PW + 12, cardBy2 - cardBy1, 6, ToastyTheme.alpha(0xFFFFFFFF, 0x0C));
        ToastyTheme.roundOutlineExact(ctx, left - 6, cardBy1, PW + 12, cardBy2 - cardBy1, 6, ToastyTheme.alpha(0xFFFFFFFF, 0x20));
    }

    @Override
    protected void extra(DrawContext ctx, int mouseX, int mouseY) {
        if (tab == 22 && query.trim().isEmpty() && previewY >= 0) {
            int ry = previewY - scroll;
            if (ry >= bandTop - 1 && ry + 48 <= bandTop + bandH + 1) {
                ToastyTheme.roundRectExact(ctx, left, ry, PW, 48, 8, 0xFF0B0716);
                ToastyTheme.roundOutlineExact(ctx, left, ry, PW, 48, 8, ToastyTheme.alpha(0xFFFFFFFF, 0x30));
                ToastyCrosshair.draw(ctx, left + PW / 2, ry + 24, ToastyConfig.get());
            }
        }
        for (Label l : labels) {
            int ly = l.y() - scroll;
            if (ly >= bandTop - 2 && ly + 9 <= bandTop + bandH + 2) {
                // Text kuerzen, damit er weder rechts raus noch in ein Nachbar-Label laeuft
                int maxW = left + PW - l.x();
                for (Label o : labels) if (o != l && Math.abs(o.y() - l.y()) < 9 && o.x() > l.x()) maxW = Math.min(maxW, o.x() - l.x() - 6);
                String t = l.text();
                if (textRenderer.getWidth(ToastyTheme.ui(t)) > maxW) {
                    while (t.length() > 1 && textRenderer.getWidth(ToastyTheme.ui(t + "…")) > maxW) t = t.substring(0, t.length() - 1);
                    t = t + "…";
                }
                ctx.drawTextWithShadow(textRenderer, ToastyTheme.ui(t), l.x(), ly, l.color());
            }
        }
        if (maxScroll() > 0) {
            int th = Math.max(16, bandH * bandH / contentH);
            int ty = bandTop + (int) ((bandH - th) * (scroll / (float) maxScroll()));
            ToastyTheme.roundRectExact(ctx, left + PW + 4, ty, 3, th, 1, ToastyTheme.alpha(0xFF7DD3FC, 0xB0));
        }
        // Aktueller Bereich: heller Rahmen um den aktiven Tab + Brotkrumen-Titel oben
        boolean searching = !query.trim().isEmpty();
        if (!searching) {
            int tw = PW / 4, cc = catOf(tab);
            ToastyTheme.roundOutlineExact(ctx, left + (cc % 4) * tw - 1, top + 31 + (cc / 4) * 16 - 1, tw, 17, 8, 0xFF7DD3FC);
        }
        String here = searching ? "Suche" : CATS[catOf(tab)] + " › " + TABS[tab];
        int hx = left + textRenderer.getWidth("Toasty Client") + 6;
        ctx.drawTextWithShadow(textRenderer, "›", hx, top - 20, ToastyTheme.MUTED);
        ctx.drawTextWithShadow(textRenderer, ToastyTheme.ui(here), hx + 10, top - 20, 0xFF7DD3FC);
        String hint = "Rechtsklick = Einstellungen";
        ctx.drawTextWithShadow(textRenderer, ToastyTheme.ui(hint), left + (PW - textRenderer.getWidth(ToastyTheme.ui(hint))) / 2, top + panelBottom - 14, ToastyTheme.MUTED);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
