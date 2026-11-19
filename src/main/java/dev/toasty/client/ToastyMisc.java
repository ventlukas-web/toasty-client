package dev.toasty.client;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWMouseButtonCallback;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fullbright, Autoclicker, Totem-Sounds, Anti-Leak. */
public final class ToastyMisc {
    private ToastyMisc() {}

    public static KeyBinding zoomKey, fullbrightKey, autoClickKey;

    public static final String[][] TOTEM = {
        {"Aus", ""}, {"Amboss", "block.anvil.land"}, {"Glocke", "block.bell.use"}, {"Ding", "entity.experience_orb.pickup"},
        {"Glas", "block.glass.break"}, {"Donner", "entity.lightning_bolt.thunder"}, {"Drache", "entity.ender_dragon.growl"},
        {"Amethyst", "block.amethyst_block.chime"}, {"Feuerwerk", "entity.firework_rocket.blast"},
        {"Wither", "entity.wither.shoot"}, {"Villager", "entity.villager.yes"}, {"Explosion", "entity.generic.explode"}
    };

    private static boolean fbApplied, zooming;
    private static int zoomOrig;
    private static double acc;

    public static void playId(String id) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.of("minecraft", id));
        if (ev != null) mc.player.playSound(ev, 1.0f, 1.0f);
    }

    public static void playTotem(int idx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || idx <= 0 || idx >= TOTEM.length) return;
        SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.of("minecraft", TOTEM[idx][1]));
        if (ev != null) mc.player.playSound(ev, 1.0f, 1.0f);
    }

    /** Totem-Pop erkennen: Sound-Listener (wie bei den Untertiteln). */
    public static void registerSound() {
        try {
            MinecraftClient.getInstance().getSoundManager().registerListener((sound, set, range) -> {
                int idx = ToastyConfig.get().totemIdx;
                if (idx <= 0) return;
                if (sound.getId().getPath().equals("item.totem.use")) playTotem(idx);
            });
        } catch (Throwable ignored) {}
    }

    /** Anti-Leak-Filter fuer ausgehende Nachrichten (frueh registrieren, unabhaengig vom Sound-System). */
    public static int combo, maxCombo, hits;
    public static long lastHit;

    private static Text alias(Text msg) {
        ToastyConfig cfg = ToastyConfig.get();
        if (!cfg.streamer || cfg.streamerName == null || cfg.streamerName.isBlank()) return msg;
        MinecraftClient mc = MinecraftClient.getInstance();
        String real = mc.player != null ? mc.player.getName().getString() : mc.getSession().getUsername();
        if (real == null || real.isBlank() || !msg.getString().toLowerCase().contains(real.toLowerCase())) return msg;
        Pattern pat = Pattern.compile(Pattern.quote(real), Pattern.CASE_INSENSITIVE);
        MutableText out = Text.empty();
        msg.visit((style, str) -> {
            out.append(Text.literal(pat.matcher(str).replaceAll(Matcher.quoteReplacement(cfg.streamerName))).setStyle(style));
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    public static void registerEvents() {
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.MODIFY_GAME.register((msg, overlay) -> alias(msg));
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (entity instanceof net.minecraft.entity.LivingEntity && world.isClient()) {
                long now = System.currentTimeMillis();
                if (now - lastHit > 2500) combo = 0;
                combo++; hits++; lastHit = now;
                maxCombo = Math.max(maxCombo, combo);
            }
            return net.minecraft.util.ActionResult.PASS;
        });
        ClientSendMessageEvents.ALLOW_CHAT.register(ToastyMisc::allowOut);
        ClientSendMessageEvents.ALLOW_COMMAND.register(cmd -> allowOut("/" + cmd));
    }

    public static void tick(MinecraftClient mc) {
        ToastyConfig cfg = ToastyConfig.get();
        if (mc.player == null || mc.world == null) { fbApplied = false; return; }

        while (fullbrightKey != null && fullbrightKey.wasPressed()) {
            cfg.fullbright = !cfg.fullbright; cfg.save();
            ToastyGamble.chat("Fullbright " + (cfg.fullbright ? "an" : "aus"));
        }
        while (autoClickKey != null && autoClickKey.wasPressed()) {
            cfg.autoClick = !cfg.autoClick; cfg.save();
            ToastyGamble.chat("Autoclicker " + (cfg.autoClick ? "an" : "aus"));
        }

        // Auto-Sprint
        if (cfg.autoSprint && mc.currentScreen == null && mc.options.forwardKey.isPressed() && !mc.player.isSneaking()
            && !mc.player.isUsingItem()) mc.player.setSprinting(true);

        // Zoom (Taste halten)
        boolean zk = zoomKey != null && zoomKey.isPressed() && mc.currentScreen == null;
        if (zk && !zooming) { zoomOrig = mc.options.getFov().getValue(); mc.options.getFov().setValue(30); zooming = true; }
        else if (!zk && zooming) { mc.options.getFov().setValue(zoomOrig); zooming = false; }

        // Fullbright = clientseitiger Nachtsicht-Effekt
        if (cfg.fullbright) {
            StatusEffectInstance cur = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (cur == null || cur.getDuration() < 400) {
                mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0, false, false, false));
                fbApplied = true;
            }
        } else if (fbApplied) {
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            fbApplied = false;
        }

        // Autoclicker: solange die Maustaste gehalten wird, zusaetzliche Klicks mit eingestellter CPS
        // in Inventaren/Containern: gehaltene Maustaste = wiederholte Klicks an der Mausposition
        if (cfg.autoClick && mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> && !ToastyMacro.isRunning()) {
            int btn = cfg.autoRight ? 1 : 0;
            long win = mc.getWindow().getHandle();
            if (GLFW.glfwGetMouseButton(win, btn) == GLFW.GLFW_PRESS) {
                acc += Math.max(1, Math.min(40, cfg.autoCps)) / 20.0;
                int n = Math.min(4, (int) acc);
                acc -= (int) acc;
                for (int i = 0; i < n; i++) guiClick(win, btn);
            } else acc = 0;
        }
        if (cfg.autoClick && mc.currentScreen == null) {
            KeyBinding kb = cfg.autoRight ? mc.options.useKey : mc.options.attackKey;
            if (kb.isPressed()) {
                acc += Math.max(1, Math.min(40, cfg.autoCps)) / 20.0;
                int n = Math.min(4, (int) acc);
                acc -= (int) acc;
                for (int i = 0; i < n; i++) clickOnce(mc, cfg.autoRight);
            } else acc = 0;
        }
    }

    private static GLFWMouseButtonCallback mouseCb;

    /** Synthetischer Klick (Druecken + Loslassen) ueber den echten Maus-Callback des Spiels - funktioniert in jedem GUI. */
    private static void guiClick(long win, int btn) {
        if (mouseCb == null) {
            mouseCb = GLFW.glfwSetMouseButtonCallback(win, null);
            GLFW.glfwSetMouseButtonCallback(win, mouseCb);
        }
        if (mouseCb == null) return;
        mouseCb.invoke(win, btn, GLFW.GLFW_PRESS, 0);
        mouseCb.invoke(win, btn, GLFW.GLFW_RELEASE, 0);
    }

    /** Ein Klick direkt ueber den Interaktions-Manager (Entities angreifen, Bloecke benutzen, ins Leere schwingen). */
    private static void clickOnce(MinecraftClient mc, boolean right) {
        var gm = mc.interactionManager;
        if (gm == null || mc.player == null) return;
        var h = mc.crosshairTarget;
        if (!right) {
            if (h instanceof net.minecraft.util.hit.EntityHitResult eh) {
                gm.attackEntity(mc.player, eh.getEntity());
                mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            } else if (h == null || h.getType() == net.minecraft.util.hit.HitResult.Type.MISS) {
                mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            } // Bloecke: gehaltene Maustaste baut sie normal ab
        } else {
            if (h instanceof net.minecraft.util.hit.BlockHitResult bh && bh.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                var r = gm.interactBlock(mc.player, net.minecraft.util.Hand.MAIN_HAND, bh);
                if (r.isAccepted()) mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            } else {
                gm.interactItem(mc.player, net.minecraft.util.Hand.MAIN_HAND);
            }
        }
    }

    // ---------- Anti-Leak ----------

    private static final Pattern IP = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern NUM = Pattern.compile("-?\\d{2,6}");
    private static final Pattern DM = Pattern.compile("^/(msg|w|tell|r|reply|m|whisper|t)\\b.*", Pattern.CASE_INSENSITIVE);

    /** @return Grund, warum die Nachricht gesperrt wuerde, sonst null */
    public static String leakReason(String text) {
        ToastyConfig cfg = ToastyConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        String low = text.toLowerCase();
        for (String w : cfg.leakWords.split(",")) {
            w = w.trim().toLowerCase();
            if (w.length() >= 2 && low.contains(w)) return "gesperrtes Wort \"" + w + "\"";
        }
        String user = System.getProperty("user.name", "");
        if (user.length() >= 4 && low.contains(user.toLowerCase())) return "dein PC-Benutzername";
        if (IP.matcher(text).find()) return "IP-Adresse";
        boolean chatLike = !text.startsWith("/") || DM.matcher(text).matches();
        if (chatLike && mc.player != null) {
            Matcher m = NUM.matcher(text);
            double px = mc.player.getX(), pz = mc.player.getZ();
            java.util.List<Integer> nums = new java.util.ArrayList<>();
            while (m.find()) { try { nums.add(Integer.parseInt(m.group())); } catch (Exception ignored) {} }
            for (int a : nums) {
                if (Math.abs(a) >= 50 && (Math.abs(a - px) <= 4 || Math.abs(a - pz) <= 4)) return "deine Koordinaten";
                for (int b : nums) if (Math.abs(a - px) < 150 && Math.abs(b - pz) < 150) return "deine Koordinaten (Gegend)";
            }
        }
        return null;
    }

    private static boolean allowOut(String text) {
        if (!ToastyConfig.get().antiLeak) return true;
        String why = leakReason(text);
        if (why == null) return true;
        ToastyNotify.push("Anti-Leak", "Blockiert: " + why, ToastyTheme.BAD);
        ToastyGamble.chatLong("Anti-Leak hat die Nachricht blockiert (" + why + ").");
        return false;
    }
}
