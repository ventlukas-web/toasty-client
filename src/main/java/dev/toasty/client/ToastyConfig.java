package dev.toasty.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class ToastyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("toastyclient.json");
    private static ToastyConfig instance;

    // Anzeige
    public boolean hud = true;
    public boolean sound = true;
    public boolean effects = true;
    public boolean confetti = true;
    public boolean watermark = true;
    public boolean titleAnimation = false;
    /** Trailer mit Musik beim Spielstart (einmal pro Start), ueberspringbar. */
    public boolean introTrailer = false;
    /** Deckkraft der Buttons (0-255), leicht durchsichtig. */
    public int buttonAlpha = 200;
    public boolean niceInventory = true;
    public boolean niceButtons = true;
    /** Startbildschirm: Theme-Verlauf + grosser Toast rechts (ohne Animationen). */
    public boolean titleToast = true;
    public int cfgVersion = 0;

    // Sell-Makro
    public String sellCommand = "/sell";
    /** Item-IDs (z. B. "minecraft:wheat"), die ins Sell-Menue geschoben werden. */
    public java.util.List<String> sellItems = new java.util.ArrayList<>();
    /** Pause zwischen den Shift-Klicks in Ticks (20 Ticks = 1 Sekunde). */
    public int sellClickDelay = 2;
    /** Optional: Sell-Menue nur erkennen, wenn der Titel diesen Text enthaelt. */
    public String sellTitleFilter = "";
    public boolean sellAuto = false;
    public int sellIntervalSec = 10;
    /** Auto-Intervall in Millisekunden (ab 100 ms). */
    public int sellIntervalMs = 10000;
    /** Shift gehalten = ganzes Inventar auf einmal ins Sell-Menue. */
    public boolean sellShiftAll = true;
    /** Immer das ganze Inventar per Shift-Klick ins Sell-Menü schieben (ohne Shift halten). */
    public boolean sellAlwaysAll = true;
    public boolean showFps = false;
    public boolean keystrokes = true;
    public boolean coords = true;
    public boolean ping = false;
    public boolean speed = false;
    public boolean clock = false;
    public boolean moduleList = true;
    public float hudScale = 1.0f;
    /** Verschobene HUD-Positionen: id -> {x, y} als Bruchteil der Bildschirmgroesse (0..1). */
    public java.util.Map<String, float[]> hudPos = new java.util.HashMap<>();

    // Neue Module
    public boolean payHud = true;
    /** Ansage, wenn eine Zahlung so viele Sekunden wartet (0 = aus), wiederholt sich im selben Abstand. */
    public int waitWarnSec = 30;
    public boolean notesHud = true;
    // Wegpunkte + Tasten-Snapshot fuer Profile
    public static class Wp { public String name = "", dim = ""; public int x, y, z; }
    public boolean wpOn = true;
    public java.util.List<Wp> waypoints = new java.util.ArrayList<>();
    public java.util.Map<String, String> keys = new java.util.HashMap<>();
    // Scoreboard (rechte Seite)
    public boolean sbCustom = false, sbNumbers = false, sbBorderOn = true;
    /** Scoreboard nutzt die Farben aus dem Design-Tab (Farbe 1/2). */
    public boolean sbTheme = true;
    public int sbRadius = 6, sbAlpha = 170;
    public float sbScale = 1.0f;
    public String sbBg = "#0B0618", sbBorder = "#A855F7", sbText = "#FFFFFF";
    // Streamer-Modus
    public boolean streamer = false, streamerHideXyz = true;
    public String streamerName = "Toasty";
    public boolean customHud = false;
    public String customTitle = "Mein HUD";
    public java.util.List<String> customLines = new java.util.ArrayList<>(java.util.List.of("XYZ: {x} {y} {z}", "FPS: {fps}", "Profit: {profit}"));
    public boolean nearbyHud = false, targetHud = false, comboHud = false, memHud = false, vitalsHud = false;
    public boolean topHud = false, graphHud = false;
    /** Pause zwischen automatischen /pay-Auszahlungen in ms (0 = sofort). */
    public int payDelayMs = 0;
    /** Warnung, wenn der Session-Verlust des Hauses diesen Betrag erreicht (0 = aus). */
    public long lossLimit = 0;
    public boolean compassHud = false, sellHud = false, armorWarn = true;
    public int armorWarnPct = 10;
    public boolean armorHud = false, potionHud = false, sessionHud = false, autoSprint = false;
    public java.util.List<String> notes = new java.util.ArrayList<>();
    public boolean notify = true;
    public boolean fullbright = false;
    public boolean itemGlow = false;
    public boolean blockHighlight = false;
    public boolean antiLeak = false;
    public String leakWords = "";
    public boolean autoClick = false;
    public int autoCps = 10;
    public boolean autoRight = false;
    public int totemIdx = 0;
    public boolean youtube = false;
    public String youtubeKey = "", youtubeVideo = "";
    public boolean twitch = false;
    public String twitchChannel = "";
    public int twitchLines = 8;
    public boolean auctionAnnounce = true;
    public String giveawayKeyword = "join";
    public boolean perfClouds = false, perfBlend = false, perfAo = false, perfFps = false;
    public java.util.Map<String, String> orig = new java.util.HashMap<>();

    // Design
    public String color1 = "#A855F7";
    public String color2 = "#7DD3FC";
    public int bgOpacity = 208;

    // Gamble
    public long minBet = 0;
    public long maxBet = Long.MAX_VALUE;
    public double winMultiplier = 2.0;
    /** /win zahlt den Gewinn automatisch per /pay aus. */
    public boolean autoPay = true;
    /** WindSMP: "You received $1.5k from Name". Benannte Gruppen: player, amount, suffix. */
    public String pattern = "^You received \\$(?<amount>[0-9][0-9.,]*)\\s*(?<suffix>[KkMmBbTt]?) from (?<player>\\.?[A-Za-z0-9_]{1,16})\\.?$";

    // Performance (Einstellungen werden gemerkt, damit man sie zurueckstellen kann)
    public boolean perfParticles = false;
    public boolean perfShadows = false;
    public boolean perfDistance = false;
    public String origParticles = null;
    public Boolean origShadows = null;
    public Integer origView = null;
    public Integer origSim = null;
    public Double origScaling = null;

    public static ToastyConfig get() {
        if (instance == null) {
            try {
                if (Files.exists(FILE)) instance = GSON.fromJson(Files.readString(FILE), ToastyConfig.class);
            } catch (Exception ignored) {}
            if (instance == null) { instance = new ToastyConfig(); instance.cfgVersion = 2; instance.save(); }
            if (instance.cfgVersion < 2) {
                instance.cfgVersion = 2;
                instance.sellIntervalMs = Math.max(100, instance.sellIntervalSec * 1000);
                if (instance.orig == null) instance.orig = new java.util.HashMap<>();
                instance.save();
            }
        }
        return instance;
    }

    public String toJson() { return GSON.toJson(this); }

    public static ToastyConfig parse(String json) {
        try { return GSON.fromJson(json, ToastyConfig.class); } catch (Exception e) { return null; }
    }

    public static void replace(ToastyConfig c) {
        if (c.orig == null) c.orig = new java.util.HashMap<>();
        if (c.waypoints == null) c.waypoints = new java.util.ArrayList<>();
        if (c.notes == null) c.notes = new java.util.ArrayList<>();
        if (c.customLines == null) c.customLines = new java.util.ArrayList<>();
        if (c.hudPos == null) c.hudPos = new java.util.HashMap<>();
        if (c.keys == null) c.keys = new java.util.HashMap<>();
        if (c.sellItems == null) c.sellItems = new java.util.ArrayList<>();
        c.cfgVersion = 2;
        instance = c;
        c.save();
    }

    public void save() {
        try { Files.writeString(FILE, GSON.toJson(this)); } catch (Exception ignored) {}
    }
}
