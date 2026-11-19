package dev.toasty.client;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ToastyGamble implements ClientModInitializer {
    public static final String ID = "toastyclient";
    public static KeyBinding openMenu, calcKey, macroKey, hudEditKey, glassKey;
    private static int pendingScreen = 0;
    private static boolean debugChat = false;

    @Override
    public void onInitializeClient() {
        ToastyTheme.reload(ToastyConfig.get());
        ToastyGlass.register();
        ToastyTitle.register();

        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD,
            orig -> (ctx, tick) -> ToastyScoreboard.render(ctx, tick, () -> orig.render(ctx, tick)));
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
            Identifier.of(ID, "hud"), ToastyHud::render);

        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of(ID, "main"));
        openMenu = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.toastyclient.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, cat));
        calcKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.toastyclient.calc", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, cat));
        macroKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.toastyclient.macro", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        hudEditKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.toastyclient.hudedit", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        glassKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.toastyclient.glass", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        // '#' auf deutscher Tastatur = GLFW BACKSLASH
        ToastyMisc.zoomKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.toastyclient.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, cat));
        ToastyMisc.fullbrightKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.toastyclient.fullbright", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        ToastyMisc.autoClickKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.toastyclient.autoclick", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));

        ClientLifecycleEvents.CLIENT_STARTED.register(c -> { ToastyPerf.apply(); ToastyMisc.registerSound(); });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openMenu.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(new ToastyMenu(null));
            }
            while (calcKey.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(new ToastyCalculatorScreen(null));
            }
            while (macroKey.wasPressed()) {
                if (mc.currentScreen == null) ToastyMacro.toggle();
            }
            while (hudEditKey.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(new ToastyHudEditor(null));
            }
            while (glassKey.wasPressed()) {
                if (mc.currentScreen == null) { ToastyGlass.toggle(); chat("Unsichtbares Glas wird umgeschaltet ..."); }
            }
            long q0 = ToastyProf.begin();
            ToastyMacro.tick(mc);
            ToastyProf.end("Tick Sell-Makro", q0);
            q0 = ToastyProf.begin();
            ToastyMisc.tick(mc);
            ToastyProf.end("Tick Misc", q0);
            q0 = ToastyProf.begin();
            ToastyEvents.tick();
            ToastyProf.end("Tick Events+Death", q0);
            if (pendingScreen == 4) { pendingScreen = 0; mc.setScreen(new ToastyHudEditor(null)); }
            if (pendingScreen == 5) { pendingScreen = 0; mc.setScreen(new ToastyKeysScreen(null)); }
            if (pendingScreen == 1) { pendingScreen = 0; mc.setScreen(new ToastyMenu(null)); }
            if (pendingScreen == 2) { pendingScreen = 0; mc.setScreen(new ToastyCalculatorScreen(null)); }
            if (pendingScreen == 7) { pendingScreen = 0; mc.setScreen(new ToastyDeathScreen(null)); }
            if (pendingScreen == 6) { pendingScreen = 0; mc.setScreen(new ToastyTrailerScreen(null)); }
            if (pendingScreen == 3) { pendingScreen = 0; mc.setScreen(new ToastySellScreen(null)); }
        });
        ToastyMisc.registerEvents();
        ToastyTwitch.start();
        ToastyDeath.register();
        ToastyYoutube.start();

        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> { if (!overlay) onChat(msg.getString()); });
        ClientSendMessageEvents.COMMAND.register(ToastyGamble::onOwnCommand);

        // Wasserzeichen in allen Menues
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
                if (!ToastyConfig.get().watermark) return;
                var tr = client.textRenderer;
                String label = "Toasty Client";
                int lx = 17; // unten links in der Ecke: nie ueber Buttons (Zurueck/Fertig sitzen unten mittig)
                ToastyLogo.draw(ctx, lx - 13, s.height - 12, 11);
                ToastyTheme.gradientText(ctx, tr, label, lx, s.height - 11);
            }));

        ClientCommandRegistrationCallback.EVENT.register((d, reg) -> {
            d.register(ClientCommandManager.literal("win")
                .executes(c -> run(settle(null, true)))
                .then(ClientCommandManager.argument("spieler", StringArgumentType.word())
                    .executes(c -> run(settle(StringArgumentType.getString(c, "spieler"), true)))));
            d.register(ClientCommandManager.literal("lose")
                .executes(c -> run(settle(null, false)))
                .then(ClientCommandManager.argument("spieler", StringArgumentType.word())
                    .executes(c -> run(settle(StringArgumentType.getString(c, "spieler"), false)))));
            d.register(ClientCommandManager.literal("note")
                .then(ClientCommandManager.literal("add")
                    .then(ClientCommandManager.argument("text", StringArgumentType.greedyString()).executes(c -> {
                        ToastyConfig cf = ToastyConfig.get(); cf.notes.add(StringArgumentType.getString(c, "text").trim()); cf.save();
                        chat("Notiz " + cf.notes.size() + " gespeichert."); return 1;
                    })))
                .then(ClientCommandManager.literal("remove")
                    .then(ClientCommandManager.argument("nr", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1)).executes(c -> {
                        ToastyConfig cf = ToastyConfig.get(); int i = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "nr") - 1;
                        if (i >= cf.notes.size()) { chat("Keine Notiz " + (i + 1) + "."); return 0; }
                        cf.notes.remove(i); cf.save(); chat("Notiz gelöscht."); return 1;
                    })))
                .then(ClientCommandManager.literal("clear").executes(c -> {
                    ToastyConfig cf = ToastyConfig.get(); cf.notes.clear(); cf.save(); chat("Alle Notizen gelöscht."); return 1;
                }))
                .then(ClientCommandManager.literal("list").executes(c -> {
                    ToastyConfig cf = ToastyConfig.get();
                    if (cf.notes.isEmpty()) { chat("Keine Notizen."); return 1; }
                    for (int i = 0; i < cf.notes.size(); i++) chatLong((i + 1) + ". " + cf.notes.get(i));
                    return 1;
                })));
            d.register(ClientCommandManager.literal("profile")
                .then(ClientCommandManager.literal("list").executes(c -> {
                    var l = ToastyProfiles.list();
                    chatLong(l.isEmpty() ? "Keine Profile gespeichert." : "Profile: " + String.join(", ", l)); return 1;
                }))
                .then(ClientCommandManager.literal("save").then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(c -> {
                    String n = StringArgumentType.getString(c, "name");
                    chat(ToastyProfiles.save(n) ? "Profil \"" + ToastyProfiles.clean(n) + "\" gespeichert." : "Speichern fehlgeschlagen."); return 1;
                })))
                .then(ClientCommandManager.literal("load").then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(c -> {
                    String n = StringArgumentType.getString(c, "name");
                    chat(ToastyProfiles.load(n) ? "Profil \"" + ToastyProfiles.clean(n) + "\" geladen." : "Profil nicht gefunden."); return 1;
                })))
                .then(ClientCommandManager.literal("delete").then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(c -> {
                    chat(ToastyProfiles.delete(StringArgumentType.getString(c, "name")) ? "Profil gelöscht." : "Profil nicht gefunden."); return 1;
                }))));
            d.register(ClientCommandManager.literal("waypoint")
                .then(ClientCommandManager.literal("add")
                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(c -> {
                            String n = StringArgumentType.getString(c, "name");
                            chat(ToastyWaypoints.addHere(n) ? "Wegpunkt \"" + n + "\" gesetzt." : "Nicht möglich."); return 1;
                        })
                        .then(ClientCommandManager.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                            .then(ClientCommandManager.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                .then(ClientCommandManager.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer()).executes(c -> {
                                    String n = StringArgumentType.getString(c, "name");
                                    boolean ok = ToastyWaypoints.add(n, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "x"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "y"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "z"));
                                    chat(ok ? "Wegpunkt \"" + n + "\" gesetzt." : "Nicht möglich."); return 1;
                                })))))
                )
                .then(ClientCommandManager.literal("remove").then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(c -> {
                    chat(ToastyWaypoints.remove(StringArgumentType.getString(c, "name")) ? "Wegpunkt gelöscht." : "Wegpunkt nicht gefunden."); return 1;
                })))
                .then(ClientCommandManager.literal("clear").executes(c -> {
                    ToastyConfig cf = ToastyConfig.get(); cf.waypoints.clear(); cf.save(); chat("Alle Wegpunkte gelöscht."); return 1;
                }))
                .then(ClientCommandManager.literal("list").executes(c -> {
                    ToastyConfig cf = ToastyConfig.get();
                    if (cf.waypoints.isEmpty()) { chat("Keine Wegpunkte."); return 1; }
                    for (var w : cf.waypoints) chatLong(w.name + ": " + w.x + " " + w.y + " " + w.z + " (" + w.dim + ")");
                    return 1;
                })));
            d.register(ClientCommandManager.literal("fullbright").executes(c -> {
                ToastyConfig cf = ToastyConfig.get(); cf.fullbright = !cf.fullbright; cf.save();
                chat("Fullbright " + (cf.fullbright ? "an" : "aus")); return 1;
            }));
            d.register(ClientCommandManager.literal("twitch")
                .then(ClientCommandManager.literal("off").executes(c -> {
                    ToastyConfig cf = ToastyConfig.get(); cf.twitch = false; cf.save(); chat("Twitch-Chat aus."); return 1;
                }))
                .then(ClientCommandManager.argument("kanal", StringArgumentType.word()).executes(c -> {
                    ToastyConfig cf = ToastyConfig.get(); cf.twitchChannel = StringArgumentType.getString(c, "kanal"); cf.twitch = true; cf.save();
                    chat("Twitch-Chat: " + cf.twitchChannel); return 1;
                })));
            d.register(ClientCommandManager.literal("youtube")
                .then(ClientCommandManager.literal("off").executes(c -> {
                    ToastyConfig cf = ToastyConfig.get(); cf.youtube = false; cf.save(); chat("YouTube-Chat aus."); return 1;
                }))
                .then(ClientCommandManager.argument("video", StringArgumentType.greedyString()).executes(c -> {
                    ToastyConfig cf = ToastyConfig.get(); cf.youtubeVideo = StringArgumentType.getString(c, "video").trim(); cf.youtube = true; cf.save();
                    chat(cf.youtubeKey.isBlank() ? "Video gesetzt – API-Key fehlt (Menü → YouTube)." : "YouTube-Chat: " + ToastyYoutube.videoId(cf.youtubeVideo)); return 1;
                })));
            d.register(ClientCommandManager.literal("auction")
                .then(ClientCommandManager.literal("start")
                    .then(ClientCommandManager.argument("startpreis", StringArgumentType.word())
                        .then(ClientCommandManager.argument("sekunden", com.mojang.brigadier.arguments.IntegerArgumentType.integer(5, 3600))
                            .then(ClientCommandManager.argument("item", StringArgumentType.greedyString()).executes(c -> {
                                Long v = ToastyMenu.parseAmount(StringArgumentType.getString(c, "startpreis"));
                                if (v == null) { chat("Startpreis ungültig."); return 0; }
                                ToastyEvents.startAuction(StringArgumentType.getString(c, "item"), v,
                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "sekunden"));
                                return 1;
                            })))))
                .then(ClientCommandManager.literal("end").executes(c -> { ToastyEvents.endAuction(false); return 1; }))
                .then(ClientCommandManager.literal("cancel").executes(c -> { ToastyEvents.endAuction(true); return 1; })));
            d.register(ClientCommandManager.literal("giveaway")
                .then(ClientCommandManager.literal("start")
                    .then(ClientCommandManager.argument("sekunden", com.mojang.brigadier.arguments.IntegerArgumentType.integer(5, 3600))
                        .then(ClientCommandManager.argument("preis", StringArgumentType.greedyString()).executes(c -> {
                            ToastyEvents.startGiveaway(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "sekunden"),
                                StringArgumentType.getString(c, "preis"));
                            return 1;
                        }))))
                .then(ClientCommandManager.literal("end").executes(c -> { ToastyEvents.drawWinner(true); return 1; }))
                .then(ClientCommandManager.literal("cancel").executes(c -> { ToastyEvents.cancelGiveaway(); return 1; })));
            d.register(ClientCommandManager.literal("stopwatch")
                .executes(c -> { chat(ToastyEvents.stopwatch(ToastyEvents.swRunning() ? "stop" : "start")); return 1; })
                .then(ClientCommandManager.literal("start").executes(c -> { chat(ToastyEvents.stopwatch("start")); return 1; }))
                .then(ClientCommandManager.literal("stop").executes(c -> { chat(ToastyEvents.stopwatch("stop")); return 1; }))
                .then(ClientCommandManager.literal("lap").executes(c -> { chat(ToastyEvents.stopwatch("lap")); return 1; }))
                .then(ClientCommandManager.literal("reset").executes(c -> { chat(ToastyEvents.stopwatch("reset")); return 1; })));
            d.register(ClientCommandManager.literal("timer")
                .then(ClientCommandManager.literal("cancel").executes(c -> { chat(ToastyEvents.cancelTimers() + " Timer gelöscht."); return 1; }))
                .then(ClientCommandManager.argument("sekunden", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 86400))
                    .executes(c -> { int s = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "sekunden"); ToastyEvents.addTimer(s, s + " s"); chat("Timer gestartet: " + s + " s"); return 1; })
                    .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                        .executes(c -> { int s = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "sekunden"); String n = StringArgumentType.getString(c, "name"); ToastyEvents.addTimer(s, n); chat("Timer \"" + n + "\": " + s + " s"); return 1; }))));
            d.register(ClientCommandManager.literal("roll")
                .executes(c -> { roll(100); return 1; })
                .then(ClientCommandManager.argument("max", com.mojang.brigadier.arguments.IntegerArgumentType.integer(2, 1000000))
                    .executes(c -> { roll(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "max")); return 1; })));
            d.register(ClientCommandManager.literal("coinflip").executes(c -> {
                boolean heads = java.util.concurrent.ThreadLocalRandom.current().nextBoolean();
                ToastyNotify.push("Münzwurf", heads ? "KOPF" : "ZAHL", heads ? 0xFF7DD3FC : 0xFFFBBF24);
                chat("Münzwurf: " + (heads ? "Kopf" : "Zahl"));
                return 1;
            }));
            d.register(ClientCommandManager.literal("calc").executes(c -> { pendingScreen = 2; return 1; }));
            d.register(ClientCommandManager.literal("glass").executes(c -> {
                boolean ok = ToastyGlass.toggle();
                chat(ok ? "Unsichtbares Glas wird umgeschaltet ..." : "Glas-Pack nicht gefunden.");
                return ok ? 1 : 0;
            }));
            d.register(ClientCommandManager.literal("toasty")
                .then(ClientCommandManager.literal("menu").executes(c -> { pendingScreen = 1; return 1; }))
                .then(ClientCommandManager.literal("lag").executes(c -> { for (String ln : ToastyProf.analyze()) chat(ln); return 1; }))
                .then(ClientCommandManager.literal("perf")
                    .executes(c -> { for (String ln : ToastyProf.report()) chat(ln); return 1; })
                    .then(ClientCommandManager.literal("reset").executes(c -> { ToastyProf.reset(); chat("Messung neu gestartet."); return 1; })))
                .then(ClientCommandManager.literal("lite").executes(c -> {
                    ToastyConfig cf = ToastyConfig.get();
                    cf.niceButtons = false; cf.niceInventory = false; cf.hud = false; cf.keystrokes = false; cf.coords = false;
                    cf.moduleList = false; cf.payHud = false; cf.watermark = false; cf.effects = false; cf.confetti = false;
                    cf.save(); chat("Lite-Modus: Designs, HUDs und Effekte aus."); return 1;
                }))
                .then(ClientCommandManager.literal("sell").executes(c -> { pendingScreen = 3; return 1; }))
                .then(ClientCommandManager.literal("replay").executes(c -> { if (ToastyDeath.hasReplay()) pendingScreen = 7; else chat("Noch keine Death-Aufzeichnung."); return 1; }))
                .then(ClientCommandManager.literal("inventory").executes(c -> { ToastyConfig cf = ToastyConfig.get(); cf.niceInventory = !cf.niceInventory; cf.save(); chat("Inventar-Design: " + (cf.niceInventory ? "AN" : "AUS")); return 1; }))
                .then(ClientCommandManager.literal("buttons").executes(c -> { ToastyConfig cf = ToastyConfig.get(); cf.niceButtons = !cf.niceButtons; cf.save(); chat("Button-Design: " + (cf.niceButtons ? "AN" : "AUS")); return 1; }))
                .then(ClientCommandManager.literal("trailer").executes(c -> { pendingScreen = 6; return 1; }))
                .then(ClientCommandManager.literal("hud").executes(c -> { pendingScreen = 4; return 1; }))
                .then(ClientCommandManager.literal("keys").executes(c -> { pendingScreen = 5; return 1; }))
                .then(ClientCommandManager.literal("win")
                    .executes(c -> run(settle(null, true)))
                    .then(ClientCommandManager.argument("spieler", StringArgumentType.word())
                        .executes(c -> run(settle(StringArgumentType.getString(c, "spieler"), true)))))
                .then(ClientCommandManager.literal("lose")
                    .executes(c -> run(settle(null, false)))
                    .then(ClientCommandManager.argument("spieler", StringArgumentType.word())
                        .executes(c -> run(settle(StringArgumentType.getString(c, "spieler"), false)))))
                .then(ClientCommandManager.literal("leak")
                    .then(ClientCommandManager.argument("text", StringArgumentType.greedyString()).executes(c -> {
                        String w = ToastyMisc.leakReason(StringArgumentType.getString(c, "text"));
                        chatLong("Anti-Leak (" + (ToastyConfig.get().antiLeak ? "an" : "AUS") + "): "
                            + (w == null ? "würde durchgehen" : "würde blockieren – " + w));
                        return 1;
                    })))
                .then(ClientCommandManager.literal("debug").executes(c -> {
                    debugChat = !debugChat; chat("Debug " + (debugChat ? "an: nicht erkannte $-Nachrichten werden angezeigt" : "aus")); return 1;
                }))
                .then(ClientCommandManager.literal("stats").executes(c -> {
                    chat("Profit " + ToastyStats.fmt(ToastyStats.profit()) + " | Runden " + ToastyStats.rounds
                        + String.format(java.util.Locale.ROOT, " | Winrate %.1f%%", ToastyStats.winrate()));
                    return 1;
                }))
                .then(ClientCommandManager.literal("reset").executes(c -> {
                    ToastyStats.reset(); chat("Statistik zurückgesetzt."); return 1;
                }))
                .then(ClientCommandManager.literal("multiplier")
                    .then(ClientCommandManager.argument("wert", DoubleArgumentType.doubleArg(0)).executes(c -> {
                        ToastyConfig cfg = ToastyConfig.get(); cfg.winMultiplier = DoubleArgumentType.getDouble(c, "wert"); cfg.save();
                        chat("Gewinn-Multiplikator " + cfg.winMultiplier); return 1;
                    })))
                .then(ClientCommandManager.literal("min")
                    .then(ClientCommandManager.argument("wert", LongArgumentType.longArg(0)).executes(c -> {
                        ToastyConfig cfg = ToastyConfig.get(); cfg.minBet = LongArgumentType.getLong(c, "wert"); cfg.save();
                        chat("Mindesteinsatz " + ToastyStats.fmt(cfg.minBet)); return 1;
                    })))
                .then(ClientCommandManager.literal("max")
                    .then(ClientCommandManager.argument("wert", LongArgumentType.longArg(0)).executes(c -> {
                        ToastyConfig cfg = ToastyConfig.get(); cfg.maxBet = LongArgumentType.getLong(c, "wert"); cfg.save();
                        chat("Höchsteinsatz " + ToastyStats.fmt(cfg.maxBet)); return 1;
                    })))
                .then(ClientCommandManager.literal("scale")
                    .then(ClientCommandManager.argument("wert", FloatArgumentType.floatArg(0.5f, 3f)).executes(c -> {
                        ToastyConfig cfg = ToastyConfig.get(); cfg.hudScale = FloatArgumentType.getFloat(c, "wert"); cfg.save();
                        chat("HUD-Größe " + cfg.hudScale); return 1;
                    })))
                .then(ClientCommandManager.literal("pattern")
                    .then(ClientCommandManager.argument("regex", StringArgumentType.greedyString()).executes(c -> {
                        String r = StringArgumentType.getString(c, "regex");
                        try { Pattern.compile(r); } catch (Exception e) { chat("Ungültige Regex."); return 0; }
                        ToastyConfig cfg = ToastyConfig.get(); cfg.pattern = r; cfg.save();
                        chat("Muster gesetzt."); return 1;
                    })))
            );
        });
    }

    private static int run(String err) {
        if (err != null) { chat(err); return 0; }
        return 1;
    }

    /** Immer als Chat-Nachricht (nicht als Toast), z. B. fuer lange Texte. */
    public static void chatLong(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || text == null) return;
        mc.player.sendMessage(Text.literal("[Toasty] ").formatted(Formatting.LIGHT_PURPLE)
            .append(Text.literal(text).formatted(Formatting.AQUA)), false);
    }

    public static void chat(String text) {
        if (text == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (ToastyConfig.get().notify && text.length() <= 70) {
            ToastyNotify.push("Toasty Client", text, ToastyTheme.PURPLE);
            return;
        }
        mc.player.sendMessage(Text.literal("[Toasty] ").formatted(Formatting.LIGHT_PURPLE)
            .append(Text.literal(text).formatted(Formatting.AQUA)), false);
    }

    // ---------- Zahlungen ----------

    private static void onChat(String text) {
        ToastyConfig cfg = ToastyConfig.get();
        ToastyEvents.onChatLine(text);
        Matcher m;
        try { m = Pattern.compile(cfg.pattern).matcher(text); } catch (Exception e) { return; }
        if (!m.find()) {
            if (debugChat && text.contains("$")) chat("Nicht erkannt: " + text);
            return;
        }
        long amount;
        try {
            String suf;
            try { suf = m.group("suffix"); } catch (Exception e) { suf = ""; }
            amount = parseAmount(m.group("amount"), suf);
        } catch (Exception e) { return; }
        String player = m.group("player");
        if (ToastyEvents.onPayment(player, amount)) return;
        if (amount < cfg.minBet || amount > cfg.maxBet) {
            chat(player + " hat " + ToastyStats.fmt(amount) + " gezahlt – außerhalb Min/Max, ignoriert.");
            return;
        }
        ToastyStats.pending.merge(player, amount, Long::sum);
        ToastyStats.pendingAt.putIfAbsent(player, System.currentTimeMillis());
        ToastyStats.income += amount;
        ToastyStats.lastPayer = player;
        ToastyHud.flash(ToastyTheme.PURPLE);
    }

    /** "1,500" / "2.5" + K/M/B/T -> long. Komma = Tausender, Punkt = Dezimal. */
    static long parseAmount(String num, String suffix) {
        java.math.BigDecimal v = new java.math.BigDecimal(num.replace(",", "").replaceAll("\\.$", ""));
        long f = 1;
        switch (suffix == null ? "" : suffix.toLowerCase()) {
            case "k" -> f = 1_000L;
            case "m" -> f = 1_000_000L;
            case "b" -> f = 1_000_000_000L;
            case "t" -> f = 1_000_000_000_000L;
            default -> {}
        }
        return v.multiply(java.math.BigDecimal.valueOf(f)).longValue();
    }

    private static final Pattern PAY_CMD = Pattern.compile(
        "^pay\\s+(\\.?[A-Za-z0-9_]{1,16})\\s+\\$?([0-9][0-9.,]*\\s*[KkMmBbTt]?)\\s*$", Pattern.CASE_INSENSITIVE);

    /** Eigener "/pay <name> <betrag>": zaehlt als Auszahlung und beendet den offenen Einsatz als Gewinn. */
    private static void onOwnCommand(String cmd) {
        Matcher m = PAY_CMD.matcher(cmd.trim());
        if (!m.matches()) return;
        long amount;
        try {
            String raw = m.group(2).replaceAll("\\s", "");
            amount = parseAmount(raw.replaceAll("[KkMmBbTt]", ""), raw.replaceAll("[^KkMmBbTt]", ""));
        } catch (Exception e) { return; }
        String p = m.group(1);
        Long bet = ToastyStats.pending.remove(p);
        if (bet == null) return;
        ToastyStats.payouts += amount;
        ToastyStats.rounds++; ToastyStats.wins++;
        ToastyStats.finish(p, amount, true);
        fixLast(p);
        effect(true, p, amount);
    }

    private static void fixLast(String p) {
        if (p.equals(ToastyStats.lastPayer))
            ToastyStats.lastPayer = ToastyStats.pending.keySet().stream().reduce((a, b) -> b).orElse(null);
    }

    /** Zahlt den Gewinn per /pay aus. */
    private static final java.util.ArrayDeque<Object[]> PAYQ = new java.util.ArrayDeque<>();
    private static long nextPay;

    private static void sendPay(String player, long amount) {
        PAYQ.add(new Object[]{player, amount});
        payTick();
    }

    /** Sendet wartende Auszahlungen mit der eingestellten Pause (Anti-Spam). */
    /** Zufallszahl 1..max (lokal, nur fuer dich sichtbar). */
    private static void roll(int max) {
        int v = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, max + 1);
        int col = v == max ? 0xFF4ADE80 : v == 1 ? 0xFFFB7185 : 0xFF7DD3FC;
        ToastyNotify.push("Roll 1–" + max, String.valueOf(v), col);
        chat("Roll (1–" + max + "): " + v);
    }

    public static void payTick() {
        if (PAYQ.isEmpty()) return;
        long now = System.currentTimeMillis();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.networkHandler == null) { PAYQ.clear(); return; }
        int delay = Math.max(0, ToastyConfig.get().payDelayMs);
        while (!PAYQ.isEmpty() && now >= nextPay) {
            Object[] e = PAYQ.poll();
            String pl = (String) e[0]; long amount = (Long) e[1];
            mc.player.networkHandler.sendChatCommand("pay " + pl + " " + amount);
            chat("Auszahlung: /pay " + pl + " " + ToastyStats.fmt(amount));
            nextPay = now + delay;
            if (delay > 0) break;
        }
    }

    /** @return Fehlermeldung oder null bei Erfolg */
    public static String settle(String player, boolean win) {
        if (player != null && (player.equalsIgnoreCase("all") || player.equalsIgnoreCase("alle"))) {
            int n = ToastyStats.pending.size();
            if (n == 0) return "Keine offene Zahlung.";
            for (String k : new java.util.ArrayList<>(ToastyStats.pending.keySet())) settle(k, win);
            return n + " Zahlungen abgeschlossen.";
        }
        String p = player != null ? player : ToastyStats.pending.keySet().stream().findFirst().orElse(null); // ohne Namen: der Älteste in der Warteschlange
        if (p != null) for (String k : ToastyStats.pending.keySet())
            if (k.equalsIgnoreCase(p) || k.equalsIgnoreCase("." + p) || ("." + k).equalsIgnoreCase(p)) { p = k; break; }
        Long bet = p == null ? null : ToastyStats.pending.remove(p);
        if (bet == null) return "Keine offene Zahlung" + (p != null ? " von " + p : "") + ".";
        ToastyConfig cfg = ToastyConfig.get();
        ToastyStats.rounds++;
        if (win) {
            long payout = Math.round(bet * cfg.winMultiplier);
            ToastyStats.payouts += payout;
            ToastyStats.wins++;
            ToastyStats.recordRound(p, bet, true, payout);
            ToastyStats.finish(p, payout, true);
            effect(true, p, payout);
            if (cfg.autoPay && payout > 0) sendPay(p, payout);
        } else {
            ToastyStats.losses++;
            ToastyStats.recordRound(p, bet, false, 0);
            ToastyStats.finish(p, bet, false);
            effect(false, p, bet);
        }
        fixLast(p);
        return null;
    }

    private static void effect(boolean win, String player, long amount) {
        ToastyConfig cfg = ToastyConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        ToastyHud.flash(win ? ToastyTheme.GOOD : ToastyTheme.BAD);
        if (cfg.effects) ToastyHud.overlay(win, win ? "GEWONNEN" : "VERLOREN", player + "  ·  " + ToastyStats.fmt(amount));
        if (cfg.sound && mc.player != null) {
            SoundEvent ev = Registries.SOUND_EVENT.get(
                Identifier.of("minecraft", win ? "entity.player.levelup" : "entity.villager.no"));
            if (ev != null) mc.player.playSound(ev, 1.0f, 1.0f);
        }
    }
}
