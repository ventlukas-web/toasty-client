package dev.toasty.client;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Auktion (Gebote = erkannte Zahlungen) und Giveaway (Teilnahme per Chat-Stichwort). */
public final class ToastyEvents {
    private ToastyEvents() {}

    // Auktion
    public static boolean aucOn;
    private static String aucItem = "";
    private static long aucEnd, aucBest;
    private static String aucBestName;
    private static final Map<String, Long> bids = new LinkedHashMap<>();

    // Giveaway
    public static boolean gwOn;
    private static String gwPrize = "";
    private static long gwEnd;
    private static final LinkedHashSet<String> entrants = new LinkedHashSet<>();
    private static final Random RND = new Random();

    private static void say(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (ToastyConfig.get().auctionAnnounce && mc.player.networkHandler != null) mc.player.networkHandler.sendChatMessage(s);
        else ToastyGamble.chat(s);
    }

    // ---------- Auktion ----------

    public static void startAuction(String item, long startBid, int sec) {
        aucOn = true; aucItem = item; aucBest = startBid; aucBestName = null; bids.clear();
        aucEnd = System.currentTimeMillis() + sec * 1000L;
        say("AUKTION: " + item + " | Startgebot " + ToastyStats.fmt(startBid) + " | " + sec + "s | Gebot = /pay an mich");
        ToastyNotify.push("Auktion", item + " gestartet", ToastyTheme.PURPLE);
    }

    /** @return true, wenn die Zahlung als Gebot gewertet wurde (dann kein Gamble-Einsatz). */
    public static boolean onPayment(String player, long amount) {
        if (!aucOn) return false;
        long total = bids.merge(player, amount, Long::sum);
        if (total > aucBest) {
            aucBest = total; aucBestName = player;
            say("Neues Höchstgebot: " + ToastyStats.fmt(total) + " von " + player);
        } else {
            ToastyGamble.chat(player + " hat " + ToastyStats.fmt(total) + " geboten – zu niedrig (Höchstgebot " + ToastyStats.fmt(aucBest) + ").");
        }
        return true;
    }

    public static void endAuction(boolean cancel) {
        if (!aucOn) { ToastyGamble.chat("Keine Auktion aktiv."); return; }
        aucOn = false;
        if (cancel) { ToastyGamble.chat("Auktion abgebrochen. Alle Gebote zurückzahlen!"); return; }
        if (aucBestName == null) { say("Auktion beendet: " + aucItem + " – keine Gebote."); return; }
        say("Auktion beendet: " + aucItem + " geht an " + aucBestName + " für " + ToastyStats.fmt(aucBest) + "!");
        StringBuilder sb = new StringBuilder();
        for (var e : bids.entrySet()) if (!e.getKey().equals(aucBestName)) sb.append(e.getKey()).append(' ').append(ToastyStats.fmt(e.getValue())).append("  ");
        if (sb.length() > 0) ToastyGamble.chat("Zurückzahlen: " + sb.toString().trim());
        ToastyNotify.push("Auktion", aucBestName + " gewinnt (" + ToastyStats.fmt(aucBest) + ")", ToastyTheme.GOOD);
    }

    // ---------- Giveaway ----------

    public static void startGiveaway(int sec, String prize) {
        gwOn = true; gwPrize = prize; entrants.clear();
        gwEnd = System.currentTimeMillis() + sec * 1000L;
        say("GIVEAWAY: " + prize + " | Schreib \"" + ToastyConfig.get().giveawayKeyword + "\" in den Chat | " + sec + "s");
        ToastyNotify.push("Giveaway", prize, ToastyTheme.BLUE);
    }

    public static void drawWinner(boolean announce) {
        if (entrants.isEmpty()) { gwOn = false; say("Giveaway beendet – keine Teilnehmer."); return; }
        List<String> l = new ArrayList<>(entrants);
        String w = l.get(RND.nextInt(l.size()));
        gwOn = false;
        say("Giveaway-Gewinner: " + w + " (" + gwPrize + ")! " + l.size() + " Teilnehmer.");
        ToastyNotify.push("Giveaway", w + " gewinnt!", ToastyTheme.GOOD);
    }

    public static void cancelGiveaway() { gwOn = false; ToastyGamble.chat("Giveaway abgebrochen."); }

    /** Chatzeile pruefen: enthaelt sie ein Spieler-Name gefolgt vom Stichwort? */
    public static void onChatLine(String text) {
        if (!gwOn) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return;
        String kw = ToastyConfig.get().giveawayKeyword.toLowerCase().trim();
        if (kw.isEmpty()) return;
        String low = text.toLowerCase();
        for (var e : mc.getNetworkHandler().getPlayerList()) {
            String n = e.getProfile().name();
            int i = low.indexOf(n.toLowerCase());
            if (i < 0) continue;
            int k = low.indexOf(kw, i + n.length());
            if (k >= 0) { if (entrants.add(n)) ToastyNotify.push("Giveaway", n + " nimmt teil (" + entrants.size() + ")", ToastyTheme.BLUE); return; }
        }
    }

    // ---------- Stoppuhr (/stopwatch) ----------
    private static long swStart = 0, swAcc = 0;
    private static boolean swRun = false;
    private static final java.util.List<Long> LAPS = new java.util.ArrayList<>();

    public static long swElapsed() { return swAcc + (swRun ? System.currentTimeMillis() - swStart : 0); }
    public static boolean swRunning() { return swRun; }
    public static boolean swActive() { return swRun || swAcc > 0; }

    public static String swFmt(long ms) {
        long s = ms / 1000;
        return String.format(java.util.Locale.ROOT, "%d:%02d.%d", s / 60, s % 60, (ms % 1000) / 100);
    }

    /** start / stop / lap / reset */
    public static String stopwatch(String cmd) {
        switch (cmd) {
            case "start" -> { if (!swRun) { swStart = System.currentTimeMillis(); swRun = true; } return "Stoppuhr läuft."; }
            case "stop" -> { if (swRun) { swAcc = swElapsed(); swRun = false; } return "Stoppuhr gestoppt: " + swFmt(swAcc); }
            case "lap" -> { long t = swElapsed(); LAPS.add(t); return "Runde " + LAPS.size() + ": " + swFmt(t); }
            default -> { swRun = false; swAcc = 0; LAPS.clear(); return "Stoppuhr zurückgesetzt."; }
        }
    }

    // ---------- Timer (/timer) ----------
    private static final java.util.List<Object[]> TIMERS = new java.util.ArrayList<>();

    public static void addTimer(int sec, String name) {
        TIMERS.add(new Object[]{System.currentTimeMillis() + sec * 1000L, name});
    }

    /** Verbleibende Zeit je Timer als Text, z. B. "Auszahlung 0:42". */
    public static java.util.List<String> timerLines() {
        java.util.List<String> l = new java.util.ArrayList<>();
        long now = System.currentTimeMillis();
        for (Object[] t : TIMERS) {
            long s = Math.max(0, ((Long) t[0] - now + 999) / 1000);
            l.add("Timer " + t[1] + "  " + (s / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60));
        }
        return l;
    }

    public static int cancelTimers() { int n = TIMERS.size(); TIMERS.clear(); return n; }

    private static void timerTick() {
        if (TIMERS.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (java.util.Iterator<Object[]> it = TIMERS.iterator(); it.hasNext(); ) {
            Object[] t = it.next();
            if (now >= (Long) t[0]) {
                it.remove();
                ToastyNotify.push("Timer fertig", (String) t[1], 0xFF4ADE80);
                ToastyGamble.chat("Timer fertig: " + t[1]);
            }
        }
    }

    public static void tick() {
        timerTick();
        ToastyStats.checkWait();
        ToastyPanels.armorCheck();
        ToastyStats.checkLimit();
        ToastyGamble.payTick();
        long now = System.currentTimeMillis();
        if (aucOn && now >= aucEnd) endAuction(false);
        if (gwOn && now >= gwEnd) drawWinner(true);
    }

    private static String mmss(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        return (s / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60);
    }

    /** Auktion-HUD: {links, rechts, farbe(0=normal,1=gut,2=warnung,3=gedimmt)}. Gebote derselben Person sind bereits aufaddiert. */
    public static List<String[]> auctionRows() {
        List<String[]> r = new ArrayList<>();
        if (!aucOn) return r;
        long left = aucEnd - System.currentTimeMillis();
        r.add(new String[]{aucItem, "", "0"});
        r.add(new String[]{"Dauer", mmss(left), left < 10_000 ? "2" : "0"});
        r.add(new String[]{"Höchstgebot", aucBestName == null ? ToastyStats.fmt(aucBest) + " (Start)" : ToastyStats.fmt(aucBest), "1"});
        r.add(new String[]{"Meistbietender", aucBestName == null ? "–" : aucBestName, "1"});
        List<Map.Entry<String, Long>> top = new ArrayList<>(bids.entrySet());
        top.sort((x, y) -> Long.compare(y.getValue(), x.getValue()));
        for (int i = 0; i < Math.min(3, top.size()); i++)
            r.add(new String[]{(i + 1) + ". " + top.get(i).getKey(), ToastyStats.fmt(top.get(i).getValue()), i == 0 ? "1" : "3"});
        long sum = 0;
        for (long v : bids.values()) sum += v;
        r.add(new String[]{"Gesamt geboten", ToastyStats.fmt(sum), "1"});
        r.add(new String[]{"Bieter", String.valueOf(bids.size()), "3"});
        return r;
    }

    /** Giveaway-HUD. */
    public static List<String[]> giveawayRows() {
        List<String[]> r = new ArrayList<>();
        if (!gwOn) return r;
        long left = gwEnd - System.currentTimeMillis();
        r.add(new String[]{gwPrize, "", "0"});
        r.add(new String[]{"Dauer", mmss(left), left < 10_000 ? "2" : "0"});
        r.add(new String[]{"Teilnehmer", String.valueOf(entrants.size()), "1"});
        r.add(new String[]{"Stichwort", ToastyConfig.get().giveawayKeyword, "3"});
        List<String> l = new ArrayList<>(entrants);
        int show = Math.min(6, l.size());
        for (int i = 0; i < show; i++) r.add(new String[]{(i + 1) + ". " + l.get(i), "", "0"});
        if (l.size() > show) r.add(new String[]{"+ " + (l.size() - show) + " weitere", "", "3"});
        return r;
    }

    /** Alte kombinierte Zeilen (Kompatibilitaet). */
    public static List<String[]> rows() {
        List<String[]> r = new ArrayList<>();
        for (String[] s : auctionRows()) r.add(new String[]{s[0], s[1]});
        for (String[] s : giveawayRows()) r.add(new String[]{s[0], s[1]});
        return r;
    }
}
