package dev.toasty.client;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ToastyGamble implements ClientModInitializer {
    public static final String ID = "toastyclient";

    @Override
    public void onInitializeClient() {
        ToastyConfig.get();
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
            Identifier.of(ID, "stats"), ToastyHud::render);
        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (!overlay) onChat(msg.getString());
        });
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
                if (!ToastyConfig.get().watermark) return;
                var tr = client.textRenderer;
                String label = "Toasty Client";
                ctx.drawTextWithShadow(tr, label, (s.width - tr.getWidth(label)) / 2, s.height - 11, 0xFFFFC857);
            }));
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.COMMAND.register(ToastyGamble::onOwnCommand);
        ClientCommandRegistrationCallback.EVENT.register((d, reg) -> d.register(
            ClientCommandManager.literal("toasty")
                .then(ClientCommandManager.literal("win")
                    .executes(c -> result(c.getSource(), null, true))
                    .then(ClientCommandManager.argument("spieler", StringArgumentType.word())
                        .executes(c -> result(c.getSource(), StringArgumentType.getString(c, "spieler"), true))))
                .then(ClientCommandManager.literal("lose")
                    .executes(c -> result(c.getSource(), null, false))
                    .then(ClientCommandManager.argument("spieler", StringArgumentType.word())
                        .executes(c -> result(c.getSource(), StringArgumentType.getString(c, "spieler"), false))))
                .then(ClientCommandManager.literal("stats").executes(c -> {
                    msg(c.getSource(), "Profit " + ToastyStats.profit() + " | Runden " + ToastyStats.rounds
                        + String.format(" | Winrate %.1f%%", ToastyStats.winrate()));
                    return 1;
                }))
                .then(ClientCommandManager.literal("reset").executes(c -> {
                    ToastyStats.reset(); msg(c.getSource(), "Statistik zurückgesetzt."); return 1;
                }))
                .then(ClientCommandManager.literal("hud").executes(c -> {
                    ToastyConfig cfg = ToastyConfig.get(); cfg.hud = !cfg.hud; cfg.save();
                    msg(c.getSource(), "HUD " + (cfg.hud ? "an" : "aus")); return 1;
                }))
                .then(ClientCommandManager.literal("sound").executes(c -> {
                    ToastyConfig cfg = ToastyConfig.get(); cfg.sound = !cfg.sound; cfg.save();
                    msg(c.getSource(), "Sound " + (cfg.sound ? "an" : "aus")); return 1;
                }))
                .then(ClientCommandManager.literal("watermark").executes(c -> {
                    ToastyConfig cfg = ToastyConfig.get(); cfg.watermark = !cfg.watermark; cfg.save();
                    msg(c.getSource(), "Wasserzeichen " + (cfg.watermark ? "an" : "aus")); return 1;
                }))
                .then(ClientCommandManager.literal("effects").executes(c -> {
                    ToastyConfig cfg = ToastyConfig.get(); cfg.effects = !cfg.effects; cfg.save();
                    msg(c.getSource(), "Effekte " + (cfg.effects ? "an" : "aus")); return 1;
                }))
                .then(ClientCommandManager.literal("scale")
                    .then(ClientCommandManager.argument("wert", FloatArgumentType.floatArg(0.5f, 3f)).executes(c -> {
                        ToastyConfig cfg = ToastyConfig.get(); cfg.hudScale = FloatArgumentType.getFloat(c, "wert"); cfg.save();
                        msg(c.getSource(), "Skalierung " + cfg.hudScale); return 1;
                    })))
                .then(ClientCommandManager.literal("multiplier")
                    .then(ClientCommandManager.argument("wert", DoubleArgumentType.doubleArg(0)).executes(c -> {
                        ToastyConfig cfg = ToastyConfig.get(); cfg.winMultiplier = DoubleArgumentType.getDouble(c, "wert"); cfg.save();
                        msg(c.getSource(), "Gewinn-Multiplikator " + cfg.winMultiplier); return 1;
                    })))
                .then(ClientCommandManager.literal("min")
                    .then(ClientCommandManager.argument("wert", LongArgumentType.longArg(0)).executes(c -> {
                        ToastyConfig cfg = ToastyConfig.get(); cfg.minBet = LongArgumentType.getLong(c, "wert"); cfg.save();
                        msg(c.getSource(), "Mindesteinsatz " + cfg.minBet); return 1;
                    })))
                .then(ClientCommandManager.literal("max")
                    .then(ClientCommandManager.argument("wert", LongArgumentType.longArg(0)).executes(c -> {
                        ToastyConfig cfg = ToastyConfig.get(); cfg.maxBet = LongArgumentType.getLong(c, "wert"); cfg.save();
                        msg(c.getSource(), "Höchsteinsatz " + cfg.maxBet); return 1;
                    })))
                .then(ClientCommandManager.literal("pattern")
                    .then(ClientCommandManager.argument("regex", StringArgumentType.greedyString()).executes(c -> {
                        String r = StringArgumentType.getString(c, "regex");
                        try { Pattern.compile(r); } catch (Exception e) { msg(c.getSource(), "Ungültige Regex."); return 0; }
                        ToastyConfig cfg = ToastyConfig.get(); cfg.pattern = r; cfg.save();
                        msg(c.getSource(), "Muster gesetzt."); return 1;
                    })))
        ));
    }

    private static void msg(FabricClientCommandSource s, String text) {
        s.sendFeedback(Text.literal("[Toasty] ").formatted(Formatting.AQUA).append(Text.literal(text).formatted(Formatting.WHITE)));
    }

    private static void onChat(String text) {
        ToastyConfig cfg = ToastyConfig.get();
        Matcher m;
        try { m = Pattern.compile(cfg.pattern).matcher(text); } catch (Exception e) { return; }
        if (!m.find()) return;
        long amount;
        try {
            String suf;
            try { suf = m.group("suffix"); } catch (Exception e) { suf = ""; }
            amount = parseAmount(m.group("amount"), suf);
        } catch (Exception e) { return; }
        if (amount < cfg.minBet || amount > cfg.maxBet) return;
        String player = m.group("player");
        ToastyStats.pending.merge(player, amount, Long::sum);
        ToastyStats.income += amount;
        ToastyStats.lastPayer = player;
        ToastyHud.flash(0xFFFFD700);
    }

    /** "1,500" / "2.5" + K/M/B/T -> long. Comma = thousands separator, dot = decimal. */
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

    /** Own "/pay <name> <amount>" commands: count as payout and settle that player's open bet as a win. */
    private static void onOwnCommand(String cmd) {
        Matcher m = PAY_CMD.matcher(cmd.trim());
        if (!m.matches()) return;
        long amount;
        try {
            String raw = m.group(2).replaceAll("\\s", "");
            String suf = raw.replaceAll("[^KkMmBbTt]", "");
            amount = parseAmount(raw.replaceAll("[KkMmBbTt]", ""), suf);
        } catch (Exception e) { return; }
        String p = m.group(1);
        Long bet = ToastyStats.pending.remove(p);
        if (bet == null) return;
        ToastyStats.payouts += amount;
        ToastyStats.rounds++; ToastyStats.wins++;
        effect(true, p, amount);
    }

    private static final Pattern PAY_CMD = Pattern.compile(
        "^pay\\s+(\\.?[A-Za-z0-9_]{1,16})\\s+\\$?([0-9][0-9.,]*\\s*[KkMmBbTt]?)\\s*$", Pattern.CASE_INSENSITIVE);

    private static int result(FabricClientCommandSource src, String player, boolean win) {
        String p = player != null ? player : ToastyStats.lastPayer;
        Long bet = p == null ? null : ToastyStats.pending.remove(p);
        if (bet == null) { msg(src, "Keine offene Zahlung" + (p != null ? " von " + p : "") + "."); return 0; }
        ToastyConfig cfg = ToastyConfig.get();
        ToastyStats.rounds++;
        if (win) {
            long payout = Math.round(bet * cfg.winMultiplier);
            ToastyStats.payouts += payout;
            ToastyStats.wins++;
            effect(true, p, payout);
        } else {
            ToastyStats.losses++;
            effect(false, p, bet);
        }
        if (p.equals(ToastyStats.lastPayer)) ToastyStats.lastPayer = ToastyStats.pending.keySet().stream().reduce((a, b) -> b).orElse(null);
        return 1;
    }

    private static void effect(boolean win, String player, long amount) {
        ToastyConfig cfg = ToastyConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        ToastyHud.flash(win ? 0xFF55FF55 : 0xFFFF5555);
        if (cfg.effects) {
            mc.inGameHud.setTitleTicks(5, 40, 10);
            mc.inGameHud.setTitle(Text.literal(win ? "GEWONNEN" : "VERLOREN")
                .formatted(win ? Formatting.GREEN : Formatting.RED, Formatting.BOLD));
            mc.inGameHud.setSubtitle(Text.literal(player + " · " + amount).formatted(Formatting.GRAY));
        }
        if (cfg.sound) {
            mc.getSoundManager().play(PositionedSoundInstance.master(
                win ? SoundEvents.ENTITY_PLAYER_LEVELUP : SoundEvents.ENTITY_VILLAGER_NO, 1.0f));
        }
    }
}
