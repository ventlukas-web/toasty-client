package dev.toasty.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** YouTube-Live-Chat im Spiel (lesen) ueber die offizielle YouTube Data API v3 - braucht einen eigenen, kostenlosen API-Key. */
public final class ToastyYoutube {
    public static volatile String state = "aus";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static String chatId, pageToken, usedVideo = "", usedKey = "";
    private static long nextPoll;
    private static boolean first;

    private ToastyYoutube() {}

    public static void start() {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "toasty-youtube"); t.setDaemon(true); return t;
        });
        ex.scheduleWithFixedDelay(() -> { try { tick(); } catch (Throwable e) { state = "Fehler"; nextPoll = System.currentTimeMillis() + 20000; } }, 4, 1, TimeUnit.SECONDS);
    }

    /** Video-ID aus Link (watch?v=, youtu.be/, /live/, /embed/) oder direkt als ID. */
    public static String videoId(String s) {
        if (s == null) return "";
        s = s.trim();
        int i = s.indexOf("v=");
        if (i >= 0) { String r = s.substring(i + 2); int e = r.indexOf('&'); return e >= 0 ? r.substring(0, e) : r; }
        for (String p : new String[]{"youtu.be/", "/live/", "/embed/", "/shorts/"}) {
            i = s.indexOf(p);
            if (i >= 0) { String r = s.substring(i + p.length()); int e = r.indexOf('?'); if (e >= 0) r = r.substring(0, e); e = r.indexOf('/'); return e >= 0 ? r.substring(0, e) : r; }
        }
        return s;
    }

    private static String get(String url) throws Exception {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (r.statusCode() == 403 && r.body().contains("uota")) throw new IllegalStateException("Quota leer");
        if (r.statusCode() == 400 || r.statusCode() == 403) throw new IllegalStateException("Key/Video falsch");
        if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
        return r.body();
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static void tick() {
        ToastyConfig c = ToastyConfig.get();
        String key = c.youtubeKey == null ? "" : c.youtubeKey.trim();
        String vid = videoId(c.youtubeVideo);
        if (!c.youtube || key.isEmpty() || vid.isEmpty()) { state = "aus"; chatId = null; usedVideo = ""; return; }
        long now = System.currentTimeMillis();
        if (!vid.equals(usedVideo) || !key.equals(usedKey)) { chatId = null; pageToken = null; usedVideo = vid; usedKey = key; nextPoll = 0; }
        if (now < nextPoll) return;
        try {
            if (chatId == null) {
                state = "verbinde …";
                JsonObject o = JsonParser.parseString(get("https://www.googleapis.com/youtube/v3/videos?part=liveStreamingDetails&id="
                    + enc(vid) + "&key=" + enc(key))).getAsJsonObject();
                JsonArray items = o.getAsJsonArray("items");
                if (items == null || items.size() == 0) { state = "Video nicht gefunden"; nextPoll = now + 20000; return; }
                JsonObject d = items.get(0).getAsJsonObject().getAsJsonObject("liveStreamingDetails");
                JsonElement id = d == null ? null : d.get("activeLiveChatId");
                if (id == null) { state = "kein Live-Chat"; nextPoll = now + 20000; return; }
                chatId = id.getAsString(); pageToken = null; first = true;
            }
            String url = "https://www.googleapis.com/youtube/v3/liveChat/messages?part=snippet,authorDetails&maxResults=200&liveChatId="
                + enc(chatId) + "&key=" + enc(key) + (pageToken != null ? "&pageToken=" + enc(pageToken) : "");
            JsonObject o = JsonParser.parseString(get(url)).getAsJsonObject();
            JsonArray items = o.getAsJsonArray("items");
            int from = first && items != null ? Math.max(0, items.size() - 8) : 0;
            if (items != null) for (int i = from; i < items.size(); i++) {
                JsonObject it = items.get(i).getAsJsonObject();
                JsonObject sn = it.getAsJsonObject("snippet");
                JsonObject au = it.getAsJsonObject("authorDetails");
                if (sn == null || au == null || !sn.has("displayMessage")) continue;
                ToastyTwitch.addMsg(au.get("displayName").getAsString(), sn.get("displayMessage").getAsString(), 0xFFFF7A7A);
            }
            first = false;
            if (o.has("nextPageToken")) pageToken = o.get("nextPageToken").getAsString();
            long poll = o.has("pollingIntervalMillis") ? o.get("pollingIntervalMillis").getAsLong() : 8000;
            nextPoll = System.currentTimeMillis() + Math.max(8000, poll); // Quota schonen
            state = "verbunden";
        } catch (Exception e) {
            String m = e instanceof IllegalStateException && e.getMessage() != null ? e.getMessage() : "Netzwerkfehler";
            state = m; nextPoll = System.currentTimeMillis() + 30000;
            if (!"Quota leer".equals(m)) chatId = null;
        }
    }
}
