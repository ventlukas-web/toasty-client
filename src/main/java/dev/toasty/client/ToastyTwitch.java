package dev.toasty.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Twitch-Chat im Spiel (nur lesen, anonym per Twitch-IRC ueber WebSocket - kein Login noetig). */
public final class ToastyTwitch {
    public record Msg(String user, String text, int color) {}
    private static final List<Msg> MSGS = new ArrayList<>();
    private static volatile WebSocket ws;
    private static volatile boolean connecting;
    private static volatile String connectedTo = "";
    public static volatile String state = "aus";

    private ToastyTwitch() {}

    public static void start() {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "toasty-twitch"); t.setDaemon(true); return t;
        });
        ex.scheduleWithFixedDelay(() -> { try { ensure(); } catch (Throwable ignored) {} }, 3, 4, TimeUnit.SECONDS);
    }

    public static synchronized List<Msg> last(int n) {
        int from = Math.max(0, MSGS.size() - n);
        return new ArrayList<>(MSGS.subList(from, MSGS.size()));
    }

    /** Fuer andere Chat-Quellen (YouTube), landet im selben Chat-HUD. */
    public static void addMsg(String user, String text, int color) { add(new Msg(user, text, color)); }

    private static synchronized void add(Msg m) {
        MSGS.add(m);
        while (MSGS.size() > 60) MSGS.remove(0);
    }

    private static void ensure() {
        ToastyConfig c = ToastyConfig.get();
        String ch = c.twitchChannel == null ? "" : c.twitchChannel.trim().toLowerCase().replace("#", "");
        if (!c.twitch || ch.isEmpty()) {
            if (ws != null) { try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye"); } catch (Exception ignored) {} ws = null; }
            state = "aus"; connectedTo = "";
            return;
        }
        if (ws != null && !ch.equals(connectedTo)) { try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "switch"); } catch (Exception ignored) {} ws = null; }
        if (ws != null || connecting) return;
        connecting = true; state = "verbinde …";
        final String channel = ch;
        HttpClient.newHttpClient().newWebSocketBuilder()
            .buildAsync(URI.create("wss://irc-ws.chat.twitch.tv:443"), new WebSocket.Listener() {
                private final StringBuilder buf = new StringBuilder();
                @Override public void onOpen(WebSocket w) {
                    w.sendText("CAP REQ :twitch.tv/tags", true);
                    w.sendText("PASS SCHMOOPIIE", true);
                    w.sendText("NICK justinfan" + (10000 + (int) (Math.random() * 80000)), true);
                    w.sendText("JOIN #" + channel, true);
                    ws = w; connectedTo = channel; connecting = false; state = "verbunden: " + channel;
                    w.request(1);
                }
                @Override public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
                    buf.append(data);
                    if (last) { String all = buf.toString(); buf.setLength(0); for (String l : all.split("\r?\n")) handle(w, l); }
                    w.request(1);
                    return null;
                }
                @Override public CompletionStage<?> onClose(WebSocket w, int code, String reason) { ws = null; connecting = false; state = "getrennt"; return null; }
                @Override public void onError(WebSocket w, Throwable e) { ws = null; connecting = false; state = "Fehler"; }
            }).exceptionally(e -> { connecting = false; ws = null; state = "Fehler"; return null; });
    }

    private static void handle(WebSocket w, String line) {
        if (line.startsWith("PING")) { w.sendText("PONG :tmi.twitch.tv", true); return; }
        int pm = line.indexOf(" PRIVMSG #");
        if (pm < 0) return;
        String tags = line.startsWith("@") ? line.substring(1, line.indexOf(' ')) : "";
        String user = "?"; int color = 0xFFFFFFFF;
        for (String t : tags.split(";")) {
            if (t.startsWith("display-name=") && t.length() > 13) user = t.substring(13);
            if (t.startsWith("color=#") && t.length() == 13) { try { color = 0xFF000000 | Integer.parseInt(t.substring(7), 16); } catch (Exception ignored) {} }
        }
        if (user.equals("?")) { int b = line.indexOf('!'); int a = line.indexOf(':'); if (a >= 0 && b > a) user = line.substring(a + 1, b); }
        int tx = line.indexOf(" :", pm);
        if (tx < 0) return;
        add(new Msg(user, line.substring(tx + 2), color));
    }
}
