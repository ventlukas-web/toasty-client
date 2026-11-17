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

    public boolean hud = true;
    public boolean sound = true;
    public boolean effects = true;
    public boolean watermark = true;
    public float hudScale = 1.0f;
    public long minBet = 0;
    public long maxBet = Long.MAX_VALUE;
    public double winMultiplier = 2.0;
    /** WindSMP: "You received $1.5k from Name". Needs named groups player, amount, suffix. */
    public String pattern = "^You received \\$(?<amount>[0-9][0-9.,]*)\\s*(?<suffix>[KkMmBbTt]?) from (?<player>\\.?[A-Za-z0-9_]{1,16})\\.?$";

    public static ToastyConfig get() {
        if (instance == null) {
            try {
                if (Files.exists(FILE)) instance = GSON.fromJson(Files.readString(FILE), ToastyConfig.class);
            } catch (Exception ignored) {}
            if (instance == null) { instance = new ToastyConfig(); instance.save(); }
        }
        return instance;
    }

    public void save() {
        try { Files.writeString(FILE, GSON.toJson(this)); } catch (Exception ignored) {}
    }
}
