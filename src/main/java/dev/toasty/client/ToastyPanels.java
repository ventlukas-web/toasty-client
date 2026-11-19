package dev.toasty.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Zusaetzliche HUD-Panels: Zahlungen, Auktion/Giveaway, Twitch-Chat, Toasts. */
public final class ToastyPanels {
    private ToastyPanels() {}

    private record Row(String left, String mid, String right, int color) {}

    public static void render(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg, float delta) {
        if (cfg.payHud) pay(ctx, tr, mc, cfg);
        events(ctx, tr, mc, cfg);
        if (cfg.wpOn && !cfg.waypoints.isEmpty()) waypoints(ctx, tr, mc, cfg);
        if (cfg.customHud) custom(ctx, tr, mc, cfg);
        if (cfg.nearbyHud) nearby(ctx, tr, mc, cfg);
        if (cfg.targetHud) target(ctx, tr, mc, cfg);
        if (cfg.comboHud) combo(ctx, tr, mc, cfg);
        if (cfg.memHud) mem(ctx, tr, mc, cfg);
        if (cfg.vitalsHud) vitals(ctx, tr, mc, cfg);
        if (cfg.armorHud) armor(ctx, tr, mc, cfg);
        if (cfg.compassHud) compass(ctx, tr, mc, cfg);
        if (cfg.topHud) topPlayers(ctx, tr, mc, cfg);
        if (cfg.graphHud) graph(ctx, tr, mc, cfg);
        if (cfg.sellHud) sellStatus(ctx, tr, mc, cfg);
        if (cfg.potionHud) potions(ctx, tr, mc, cfg);
        if (cfg.sessionHud) session(ctx, tr, mc, cfg);
        if (cfg.notesHud) notes(ctx, tr, mc, cfg);
        if (cfg.twitch || cfg.youtube) twitch(ctx, tr, mc, cfg);
        ToastyEsp.render(ctx, mc, cfg, delta);
        if (cfg.notify) ToastyNotify.render(ctx, tr, mc);
    }

    private static void panel(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg, String id, String title,
                              List<Row> rows, float defX, float defY, int minW) {
        int w = Math.max(minW, tr.getWidth(title) + 4);
        for (Row r : rows) w = Math.max(w, tr.getWidth(r.left()) + 12 + tr.getWidth(r.mid()) + (r.mid().isEmpty() ? 0 : 10) + tr.getWidth(r.right()));
        int pad = 6, h = 17 + rows.size() * 11 + pad;
        float[] pos = ToastyHud.place(id, defX < 0 ? (mc.getWindow().getScaledWidth() - (w + pad * 2) * cfg.hudScale) / 2f : defX, defY,
            (w + pad * 2) * cfg.hudScale, h * cfg.hudScale, mc);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(pos[0], pos[1]);
        ctx.getMatrices().scale(cfg.hudScale, cfg.hudScale);
        ToastyTheme.panel(ctx, 0, 0, w + pad * 2, h);
        ToastyTheme.gradientText(ctx, tr, title, pad, 5);
        int y = 17;
        for (Row r : rows) {
            ctx.drawTextWithShadow(tr, r.left(), pad, y, r.color());
            if (!r.mid().isEmpty()) ctx.drawTextWithShadow(tr, r.mid(), pad + tr.getWidth(r.left()) + 10, y, ToastyTheme.TEXT);
            ctx.drawTextWithShadow(tr, r.right(), w + pad - tr.getWidth(r.right()), y, r.color());
            y += 11;
        }
        ctx.getMatrices().popMatrix();
    }

    // Fertige Zahlungen oben (kurz sichtbar), darunter alle wartenden. Wird eine fertig, rutschen die naechsten nach oben.
    private static void pay(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        long now = System.currentTimeMillis();
        List<Row> rows = new ArrayList<>();
        List<ToastyStats.Done> d = ToastyStats.done;
        for (int i = d.size() - 1; i >= 0 && rows.size() < 4; i--) {
            ToastyStats.Done e = d.get(i);
            if (now - e.at() > 6000) break;
            boolean win = e.result() == 1;
            rows.add(new Row(e.name(), ToastyStats.fmt(e.amount()), win ? "bezahlt" : "verloren", win ? ToastyTheme.GOOD : ToastyTheme.BAD));
        }
        int shown = 0, total = ToastyStats.pending.size();
        for (var e : ToastyStats.pending.entrySet()) {
            if (shown++ >= 24) break;
            rows.add(new Row(e.getKey(), ToastyStats.fmt(e.getValue()), "wartend " + ToastyStats.clock(ToastyStats.waitedSec(e.getKey())), 0xFFFBBF24));
        }
        if (total > 24) rows.add(new Row("+" + (total - 24) + " weitere wartend", "", "", ToastyTheme.MUTED));
        if (rows.isEmpty()) rows.add(new Row("keine offenen Zahlungen", "", "", ToastyTheme.MUTED));
        panel(ctx, tr, mc, cfg, "pay", "Zahlungen", rows, -1, 6, 110);
    }

    private static void waypoints(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        if (mc.player == null || mc.world == null) return;
        String dim = ToastyWaypoints.dim(mc);
        List<ToastyConfig.Wp> ws = new ArrayList<>();
        for (var w : cfg.waypoints) if (dim.equals(w.dim)) ws.add(w);
        ws.sort((a, b) -> Double.compare(ToastyWaypoints.dist(mc, a), ToastyWaypoints.dist(mc, b)));
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(6, ws.size()); i++) {
            var w = ws.get(i);
            rows.add(new Row(w.name, "", ToastyWaypoints.arrow(mc, w) + " " + Math.round(ToastyWaypoints.dist(mc, w)) + "m", ToastyTheme.TEXT));
        }
        if (rows.isEmpty()) rows.add(new Row("keine Wegpunkte hier", "", "", ToastyTheme.MUTED));
        panel(ctx, tr, mc, cfg, "waypoints", "Wegpunkte", rows, 6, mc.getWindow().getScaledHeight() / 2f + 60, 120);
    }

    public static final String[] TOKENS = {"{x}", "{y}", "{z}", "{fps}", "{ping}", "{profit}", "{income}", "{payout}", "{runden}",
        "{wins}", "{losses}", "{winrate}", "{offen}", "{zeit}", "{leben}", "{hunger}", "{combo}", "{name}", "{slots}"};

    private static String fill(String s, MinecraftClient mc) {
        var p = mc.player;
        if (p == null || !s.contains("{")) return s;
        int free = 0;
        for (int i = 0; i < 36; i++) if (p.getInventory().getStack(i).isEmpty()) free++;
        int ping = 0;
        if (mc.getNetworkHandler() != null) { var e = mc.getNetworkHandler().getPlayerListEntry(p.getUuid()); if (e != null) ping = e.getLatency(); }
        boolean hide = ToastyConfig.get().streamer && ToastyConfig.get().streamerHideXyz;
        String[] v = {hide ? "?" : String.valueOf((int) Math.floor(p.getX())), hide ? "?" : String.valueOf((int) Math.floor(p.getY())), hide ? "?" : String.valueOf((int) Math.floor(p.getZ())),
            String.valueOf(mc.getCurrentFps()), String.valueOf(ping), ToastyStats.fmt(ToastyStats.profit()), ToastyStats.fmt(ToastyStats.income),
            ToastyStats.fmt(ToastyStats.payouts), String.valueOf(ToastyStats.rounds), String.valueOf(ToastyStats.wins), String.valueOf(ToastyStats.losses),
            String.format(java.util.Locale.ROOT, "%.1f%%", ToastyStats.winrate()), String.valueOf(ToastyStats.pending.size()),
            java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
            String.format(java.util.Locale.ROOT, "%.1f", p.getHealth()), String.valueOf(p.getHungerManager().getFoodLevel()),
            String.valueOf(ToastyMisc.combo), ToastyConfig.get().streamer ? ToastyConfig.get().streamerName : p.getName().getString(), String.valueOf(free)};
        for (int i = 0; i < TOKENS.length; i++) s = s.replace(TOKENS[i], v[i]);
        return s;
    }

    private static void custom(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        List<Row> rows = new ArrayList<>();
        for (String l : cfg.customLines) if (l != null && !l.isBlank()) rows.add(new Row(fill(l, mc), "", "", ToastyTheme.TEXT));
        if (rows.isEmpty()) rows.add(new Row("Zeilen im Menü eintragen", "", "", ToastyTheme.MUTED));
        panel(ctx, tr, mc, cfg, "custom", cfg.customTitle == null || cfg.customTitle.isBlank() ? "Mein HUD" : cfg.customTitle, rows,
            mc.getWindow().getScaledWidth() - 190, 120, 130);
    }

    private static void nearby(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        if (mc.world == null || mc.player == null) return;
        List<net.minecraft.entity.player.PlayerEntity> ps = new ArrayList<>();
        for (var p : mc.world.getPlayers()) if (p != mc.player) ps.add(p);
        ps.sort((a, b) -> Float.compare(a.distanceTo(mc.player), b.distanceTo(mc.player)));
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(8, ps.size()); i++) {
            var p = ps.get(i);
            float d = p.distanceTo(mc.player);
            rows.add(new Row(p.getName().getString(), "", Math.round(d) + "m", d < 16 ? ToastyTheme.BAD : ToastyTheme.TEXT));
        }
        if (ps.size() > 8) rows.add(new Row("+" + (ps.size() - 8) + " weitere", "", "", ToastyTheme.MUTED));
        if (rows.isEmpty()) rows.add(new Row("niemand in der Nähe", "", "", ToastyTheme.MUTED));
        panel(ctx, tr, mc, cfg, "nearby", "Spieler", rows, mc.getWindow().getScaledWidth() - 130, mc.getWindow().getScaledHeight() / 2f + 40, 110);
    }

    private static net.minecraft.entity.LivingEntity lastTarget;
    private static long lastTargetAt;

    private static void target(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        long now = System.currentTimeMillis();
        if (mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult eh && eh.getEntity() instanceof net.minecraft.entity.LivingEntity le) {
            lastTarget = le; lastTargetAt = now;
        }
        List<Row> rows = new ArrayList<>();
        if (lastTarget != null && lastTarget.isAlive() && now - lastTargetAt < 2000) {
            float hp = lastTarget.getHealth(), max = Math.max(1f, lastTarget.getMaxHealth());
            rows.add(new Row(lastTarget.getName().getString(), "", "", ToastyTheme.TEXT));
            rows.add(new Row("Leben", "", String.format(java.util.Locale.ROOT, "%.1f / %.0f", hp, max),
                ToastyTheme.lerp(ToastyTheme.BAD, ToastyTheme.GOOD, Math.min(1f, hp / max))));
            if (mc.player != null) rows.add(new Row("Distanz", "", String.format(java.util.Locale.ROOT, "%.1f m", lastTarget.distanceTo(mc.player)), ToastyTheme.MUTED));
        } else rows.add(new Row("kein Ziel", "", "", ToastyTheme.MUTED));
        panel(ctx, tr, mc, cfg, "target", "Ziel", rows, mc.getWindow().getScaledWidth() / 2f - 60, mc.getWindow().getScaledHeight() / 2f + 30, 120);
    }

    private static void combo(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        long now = System.currentTimeMillis();
        if (now - ToastyMisc.lastHit > 2500 || (mc.player != null && mc.player.hurtTime > 0)) ToastyMisc.combo = 0;
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Combo", "", String.valueOf(ToastyMisc.combo), ToastyMisc.combo > 0 ? ToastyTheme.GOOD : ToastyTheme.MUTED));
        rows.add(new Row("Bester Combo", "", String.valueOf(ToastyMisc.maxCombo), ToastyTheme.TEXT));
        rows.add(new Row("Treffer", "", String.valueOf(ToastyMisc.hits), ToastyTheme.TEXT));
        panel(ctx, tr, mc, cfg, "combo", "Combo", rows, mc.getWindow().getScaledWidth() / 2f + 70, mc.getWindow().getScaledHeight() / 2f - 30, 110);
    }

    private static void mem(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) >> 20, max = rt.maxMemory() >> 20;
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("FPS", "", String.valueOf(mc.getCurrentFps()), ToastyTheme.TEXT));
        rows.add(new Row("RAM", "", used + " / " + max + " MB", used * 10 > max * 9 ? ToastyTheme.BAD : ToastyTheme.TEXT));
        panel(ctx, tr, mc, cfg, "mem", "System", rows, 6, mc.getWindow().getScaledHeight() - 80, 110);
    }

    private static void vitals(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        var p = mc.player;
        if (p == null) return;
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Leben", "", String.format(java.util.Locale.ROOT, "%.1f", p.getHealth() + p.getAbsorptionAmount()),
            ToastyTheme.lerp(ToastyTheme.BAD, ToastyTheme.GOOD, Math.min(1f, p.getHealth() / Math.max(1f, p.getMaxHealth())))));
        rows.add(new Row("Hunger", "", String.valueOf(p.getHungerManager().getFoodLevel()), ToastyTheme.TEXT));
        rows.add(new Row("XP-Level", "", String.valueOf(p.experienceLevel), ToastyTheme.TEXT));
        panel(ctx, tr, mc, cfg, "vitals", "Status", rows, mc.getWindow().getScaledWidth() / 2f - 160, mc.getWindow().getScaledHeight() - 70, 100);
    }

    private static final long START = System.currentTimeMillis();

    private static void armor(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        if (mc.player == null) return;
        net.minecraft.entity.EquipmentSlot[] sl = {net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
            net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET, net.minecraft.entity.EquipmentSlot.MAINHAND};
        List<net.minecraft.item.ItemStack> st = new ArrayList<>();
        for (var s : sl) { var is = mc.player.getEquippedStack(s); if (!is.isEmpty()) st.add(is); }
        int w = 112, rows = Math.max(1, st.size()), h = rows * 18 + 8;
        float[] pos = ToastyHud.place("armor", mc.getWindow().getScaledWidth() - w - 6, mc.getWindow().getScaledHeight() - h - 30,
            w * cfg.hudScale, h * cfg.hudScale, mc);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(pos[0], pos[1]);
        ctx.getMatrices().scale(cfg.hudScale, cfg.hudScale);
        ToastyTheme.panel(ctx, 0, 0, w, h);
        int y = 4;
        if (st.isEmpty()) ctx.drawTextWithShadow(tr, "Armor Durability", 8, 8, ToastyTheme.MUTED);
        for (var is : st) {
            ctx.drawItem(is, 5, y);
            String t = is.isDamageable() ? Math.round(100f * (is.getMaxDamage() - is.getDamage()) / Math.max(1, is.getMaxDamage())) + " %  ·  " + (is.getMaxDamage() - is.getDamage()) : (is.getCount() > 1 ? "x" + is.getCount() : "");
            float frac = is.isDamageable() ? 1f - (float) is.getDamage() / Math.max(1, is.getMaxDamage()) : 1f;
            ctx.drawTextWithShadow(tr, t, 26, y + 5, ToastyTheme.lerp(ToastyTheme.BAD, ToastyTheme.GOOD, frac));
            y += 18;
        }
        ctx.getMatrices().popMatrix();
    }

    private static final boolean[] armorWarned = new boolean[4];
    private static final net.minecraft.entity.EquipmentSlot[] ARMOR_SLOTS = {net.minecraft.entity.EquipmentSlot.HEAD,
        net.minecraft.entity.EquipmentSlot.CHEST, net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET};
    private static final String[] ARMOR_NAMES = {"Helm", "Brustplatte", "Hose", "Schuhe"};

    /** Warnt (Toast + Ton) einmal, wenn ein Rüstungsteil unter die eingestellte Haltbarkeit fällt. */
    public static void armorCheck() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ToastyConfig cfg = ToastyConfig.get();
        if (mc.player == null) return;
        for (int i = 0; i < 4; i++) {
            var is = mc.player.getEquippedStack(ARMOR_SLOTS[i]);
            if (is.isEmpty() || !is.isDamageable()) { armorWarned[i] = false; continue; }
            int pct = Math.round(100f * (is.getMaxDamage() - is.getDamage()) / Math.max(1, is.getMaxDamage()));
            if (pct > cfg.armorWarnPct) { armorWarned[i] = false; continue; }
            if (!cfg.armorWarn || armorWarned[i]) continue;
            armorWarned[i] = true;
            ToastyNotify.push("Armor Durability niedrig", ARMOR_NAMES[i] + " nur noch " + pct + " %", 0xFFFB7185);
            if (cfg.sound) ToastyMisc.playId("block.anvil.land");
        }
    }

    private static void sellStatus(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        long sold = ToastyMacro.soldItems;
        double perMin = 0;
        if (ToastyMacro.soldSince > 0) {
            double min = Math.max(1.0 / 60, (System.currentTimeMillis() - ToastyMacro.soldSince) / 60000.0);
            perMin = sold / min;
        }
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Status", "", ToastyMacro.statusText(), ToastyMacro.isRunning() ? ToastyTheme.GOOD : ToastyTheme.TEXT));
        rows.add(new Row("Durchläufe", "", String.valueOf(ToastyMacro.cycleCount()), ToastyTheme.TEXT));
        rows.add(new Row("Verkauft", "", sold + " Items", ToastyTheme.TEXT));
        rows.add(new Row("Tempo", "", Math.round(perMin) + " Items/min", ToastyTheme.MUTED));
        panel(ctx, tr, mc, cfg, "sellstatus", "Sell Status", rows, 6, mc.getWindow().getScaledHeight() / 2f - 130, 120);
    }

    private static void topPlayers(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        List<Map.Entry<String, long[]>> top = new ArrayList<>(ToastyStats.players.entrySet());
        top.sort((a, b) -> Long.compare(b.getValue()[2], a.getValue()[2]));
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(5, top.size()); i++) {
            long[] p = top.get(i).getValue();
            rows.add(new Row(top.get(i).getKey(), "", ToastyStats.fmt(p[2]) + " · " + p[0] + "R · " + Math.round(100.0 * p[1] / Math.max(1, p[0])) + "%", ToastyTheme.TEXT));
        }
        if (rows.isEmpty()) rows.add(new Row("noch keine Runde", "", "", ToastyTheme.MUTED));
        panel(ctx, tr, mc, cfg, "top", "Top-Spieler", rows, 6, mc.getWindow().getScaledHeight() / 2f + 20, 130);
    }

    private static void graph(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        List<Long> v = new ArrayList<>(ToastyStats.roundNet);
        int n = Math.min(24, v.size());
        List<Long> last = v.subList(v.size() - n, v.size());
        int w = 130, h = 66, pad = 6;
        float[] pos = ToastyHud.place("graph", 6, mc.getWindow().getScaledHeight() / 2f + 90, w * cfg.hudScale, h * cfg.hudScale, mc);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(pos[0], pos[1]);
        ctx.getMatrices().scale(cfg.hudScale, cfg.hudScale);
        ToastyTheme.panel(ctx, 0, 0, w, h);
        ToastyTheme.gradientText(ctx, tr, "Gewinn/Runde", pad, 5);
        String tot = ToastyStats.fmt(ToastyStats.profit());
        ctx.drawTextWithShadow(tr, tot, w - pad - tr.getWidth(tot), 5, ToastyStats.profit() >= 0 ? ToastyTheme.GOOD : ToastyTheme.BAD);
        int base = 43;
        ctx.fill(pad, base, w - pad, base + 1, ToastyTheme.alpha(ToastyTheme.MUTED, 110));
        if (n == 0) {
            ctx.drawTextWithShadow(tr, "noch keine Runde", pad, 34, ToastyTheme.MUTED);
        } else {
            long max = 1;
            for (long x : last) max = Math.max(max, Math.abs(x));
            int bw = Math.max(2, (w - pad * 2) / 24 - 1);
            for (int i = 0; i < n; i++) {
                long x = last.get(i);
                int bh = (int) Math.max(1, Math.round(Math.abs(x) * 20.0 / max));
                int bx = pad + i * (bw + 1);
                if (x >= 0) ctx.fill(bx, base - bh, bx + bw, base, ToastyTheme.GOOD);
                else ctx.fill(bx, base + 1, bx + bw, base + 1 + bh, ToastyTheme.BAD);
            }
        }
        ctx.getMatrices().popMatrix();
    }

    private static final String[] DIRS = {"N", "NO", "O", "SO", "S", "SW", "W", "NW"};

    private static void compass(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        if (mc.player == null) return;
        int w = 200, h = 27;
        float[] pos = ToastyHud.place("compass", (mc.getWindow().getScaledWidth() - w * cfg.hudScale) / 2f,
            mc.getWindow().getScaledHeight() - 78, w * cfg.hudScale, h * cfg.hudScale, mc);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(pos[0], pos[1]);
        ctx.getMatrices().scale(cfg.hudScale, cfg.hudScale);
        ToastyTheme.roundRect(ctx, 0, 0, w, h, 6, ToastyTheme.bgA(0xB0));
        double bear = ((mc.player.getYaw() + 180) % 360 + 360) % 360;
        String head = Math.round(bear) % 360 + "°";
        ctx.drawTextWithShadow(tr, head, (w - tr.getWidth(head)) / 2, 3, ToastyTheme.MUTED);
        double range = 90, half = w / 2.0 - 10;
        for (int b = -105; b <= 105; b += 15) {
            double abs = Math.floor(bear / 15) * 15 + b;
            double off = abs - bear;
            if (Math.abs(off) > range) continue;
            int px = (int) Math.round(w / 2.0 + off / range * half);
            int ab = (int) (((Math.round(abs) % 360) + 360) % 360);
            if (ab % 45 == 0) {
                String d = DIRS[ab / 45];
                int col = ab == 0 ? ToastyTheme.BAD : ToastyTheme.TEXT;
                ctx.drawTextWithShadow(tr, d, px - tr.getWidth(d) / 2, 13, col);
            } else {
                ctx.fill(px, 15, px + 1, 19, ToastyTheme.alpha(ToastyTheme.MUTED, 0xA0));
            }
        }
        if (cfg.wpOn) {
            String dim = ToastyWaypoints.dim(mc);
            for (var wp : cfg.waypoints) {
                if (!dim.equals(wp.dim)) continue;
                double dx = wp.x + 0.5 - mc.player.getX(), dz = wp.z + 0.5 - mc.player.getZ();
                double wb = ((Math.toDegrees(Math.atan2(-dx, dz)) + 180) % 360 + 360) % 360;
                double off = ((wb - bear + 540) % 360) - 180;
                boolean edge = Math.abs(off) > range;
                double o = Math.max(-range, Math.min(range, off));
                int px = (int) Math.round(w / 2.0 + o / range * half);
                ctx.fill(px - 1, 21, px + 2, 24, edge ? ToastyTheme.alpha(0xFFFBBF24, 0x70) : 0xFFFBBF24);
            }
        }
        ctx.fill(w / 2, 22, w / 2 + 1, 26, ToastyTheme.BLUE);
        ctx.getMatrices().popMatrix();
    }

    private static void potions(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        if (mc.player == null) return;
        List<Row> rows = new ArrayList<>();
        for (var e : mc.player.getStatusEffects()) {
            var type = e.getEffectType().value();
            String name = type.getName().getString() + (e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "");
            int d = e.getDuration();
            String time = d < 0 || d > 100000 ? "∞" : String.format(java.util.Locale.ROOT, "%d:%02d", d / 1200, (d / 20) % 60);
            rows.add(new Row(name, "", time, type.isBeneficial() ? ToastyTheme.GOOD : ToastyTheme.BAD));
        }
        if (rows.isEmpty()) rows.add(new Row("keine Effekte", "", "", ToastyTheme.MUTED));
        panel(ctx, tr, mc, cfg, "potions", "Effekte", rows, mc.getWindow().getScaledWidth() - 140, 60, 110);
    }

    private static void session(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        if (mc.player == null) return;
        long sec = (System.currentTimeMillis() - START) / 1000;
        int free = 0;
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getStack(i).isEmpty()) free++;
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Spielzeit", "", String.format(java.util.Locale.ROOT, "%d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60), ToastyTheme.TEXT));
        rows.add(new Row("Freie Slots", "", free + "/36", free == 0 ? ToastyTheme.BAD : ToastyTheme.TEXT));
        if (mc.world != null) {
            long t = (mc.world.getTimeOfDay() / 1000 + 6) % 24;
            long m = (mc.world.getTimeOfDay() % 1000) * 60 / 1000;
            rows.add(new Row("Welt-Zeit", "", String.format(java.util.Locale.ROOT, "%02d:%02d", t, m), ToastyTheme.TEXT));
        }
        panel(ctx, tr, mc, cfg, "session", "Session", rows, 6, mc.getWindow().getScaledHeight() / 2f - 60, 120);
    }

    private static void notes(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        List<Row> rows = new ArrayList<>();
        int n = cfg.notes.size();
        for (int i = 0; i < Math.min(n, 10); i++)
            rows.add(new Row((i + 1) + ". " + tr.trimToWidth(cfg.notes.get(i), 170), "", "", ToastyTheme.TEXT));
        if (n > 10) rows.add(new Row("+" + (n - 10) + " weitere", "", "", ToastyTheme.MUTED));
        if (rows.isEmpty()) rows.add(new Row("/note add <Text>", "", "", ToastyTheme.MUTED));
        panel(ctx, tr, mc, cfg, "notes", "Notizen", rows, mc.getWindow().getScaledWidth() - 190, mc.getWindow().getScaledHeight() / 2f, 150);
    }

    private static void events(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        List<String[]> r = ToastyEvents.rows();
        if (r.isEmpty()) return;
        List<Row> rows = new ArrayList<>();
        for (String[] s : r) rows.add(new Row(s[0], "", s[1], ToastyTheme.TEXT));
        panel(ctx, tr, mc, cfg, "events", "Events", rows, 6, 120, 120);
    }

    private static void twitch(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ToastyConfig cfg) {
        List<ToastyTwitch.Msg> m = ToastyTwitch.last(Math.max(3, Math.min(15, cfg.twitchLines)));
        int w = 220, pad = 6, h = 17 + Math.max(1, m.size()) * 11 + pad;
        float[] pos = ToastyHud.place("twitch", 6, mc.getWindow().getScaledHeight() / 2f, (w + pad * 2) * cfg.hudScale, h * cfg.hudScale, mc);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(pos[0], pos[1]);
        ctx.getMatrices().scale(cfg.hudScale, cfg.hudScale);
        ToastyTheme.panel(ctx, 0, 0, w + pad * 2, h);
        ToastyTheme.gradientText(ctx, tr, cfg.twitch && cfg.youtube ? "Chat" : cfg.youtube ? "YouTube" : "Twitch", pad, 5);
        String st = cfg.twitch && cfg.youtube ? "Twitch + YouTube" : cfg.youtube ? ToastyYoutube.state : ToastyTwitch.state;
        ctx.drawTextWithShadow(tr, st, w + pad - tr.getWidth(st), 5, ToastyTheme.MUTED);
        int y = 17;
        if (m.isEmpty()) ctx.drawTextWithShadow(tr, (cfg.youtube ? cfg.youtubeKey.isBlank() || cfg.youtubeVideo.isBlank() : cfg.twitchChannel.isBlank()) ? "Kanal/Video im Menü setzen" : "warte auf Nachrichten …", pad, y, ToastyTheme.MUTED);
        for (var e : m) {
            String name = e.user() + ": ";
            ctx.drawTextWithShadow(tr, name, pad, y, e.color());
            ctx.drawTextWithShadow(tr, tr.trimToWidth(e.text(), Math.max(10, w - tr.getWidth(name))), pad + tr.getWidth(name), y, ToastyTheme.TEXT);
            y += 11;
        }
        ctx.getMatrices().popMatrix();
    }
}
