package dev.toasty.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Block- und Item-Highlight: Boxen werden per Projektion auf den Bildschirm gezeichnet (kein Welt-Render-Hook noetig). */
public final class ToastyEsp {
    private ToastyEsp() {}

    private static double camX, camY, camZ, cy, cp, th, aspect;
    private static int sw, sh;

    private static float[] toScreen(double x, double y, double z) {
        double dx = x - camX, dy = y - camY, dz = z - camZ;
        double fx = -Math.sin(cy) * Math.cos(cp), fy = -Math.sin(cp), fz = Math.cos(cy) * Math.cos(cp);
        double rx = -Math.cos(cy), rz = -Math.sin(cy);
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        double zc = dx * fx + dy * fy + dz * fz;
        if (zc < 0.05) return null;
        double xc = dx * rx + dz * rz, yc = dx * ux + dy * uy + dz * uz;
        return new float[]{(float) (sw / 2.0 * (1 + xc / (zc * th * aspect))), (float) (sh / 2.0 * (1 - yc / (zc * th)))};
    }

    /** Eine Linie = ein einziges gedrehtes Rechteck (statt hunderter Pixel-Rechtecke). */
    private static void line(DrawContext ctx, float[] a, float[] b, int color, int t) {
        if (a == null || b == null) return;
        float dx = b[0] - a[0], dy = b[1] - a[1];
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f || len > 5000f) return;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(a[0], a[1]);
        ctx.getMatrices().rotate((float) Math.atan2(dy, dx));
        ctx.fill(0, 0, (int) Math.ceil(len), t, color);
        ctx.getMatrices().popMatrix();
    }

    private static void box(DrawContext ctx, Box b, int color, int t) {
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        float[][] p = new float[8][];
        for (int i = 0; i < 8; i++) p[i] = toScreen(xs[i & 1], ys[(i >> 1) & 1], zs[(i >> 2) & 1]);
        int[][] e = {{0,1},{2,3},{4,5},{6,7},{0,2},{1,3},{4,6},{5,7},{0,4},{1,5},{2,6},{3,7}};
        for (int[] k : e) line(ctx, p[k[0]], p[k[1]], color, t);
    }

    public static void render(DrawContext ctx, MinecraftClient mc, ToastyConfig cfg, float delta) {
        boolean wps = cfg.wpOn && !cfg.waypoints.isEmpty();
        if (!cfg.blockHighlight && !cfg.itemGlow && !wps) return;
        Entity ce = mc.getCameraEntity();
        if (ce == null || mc.world == null) return;
        var cam = mc.gameRenderer.getCamera();
        Vec3d eye = ce.getCameraPosVec(delta);
        camX = eye.x; camY = eye.y; camZ = eye.z;
        cy = Math.toRadians(cam.getYaw()); cp = Math.toRadians(cam.getPitch());
        sw = mc.getWindow().getScaledWidth(); sh = mc.getWindow().getScaledHeight();
        th = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        aspect = (double) sw / sh;

        if (wps && mc.player != null) {
            String dim = ToastyWaypoints.dim(mc);
            var tr = mc.textRenderer;
            for (ToastyConfig.Wp w : cfg.waypoints) {
                if (!dim.equals(w.dim)) continue;
                float[] p = toScreen(w.x + 0.5, w.y + 1.2, w.z + 0.5);
                if (p == null || p[0] < -20 || p[0] > sw + 20 || p[1] < -20 || p[1] > sh + 20) continue;
                String t = w.name + "  " + Math.round(ToastyWaypoints.dist(mc, w)) + "m";
                int tw = tr.getWidth(t);
                int x = (int) p[0], y = (int) p[1];
                ctx.fill(x - 2, y + 7, x + 3, y + 12, ToastyTheme.alpha(ToastyTheme.BLUE, 0xFF));
                ToastyTheme.roundRect(ctx, x - tw / 2 - 4, y - 6, tw + 8, 12, 4, ToastyTheme.bgA(0xC0));
                ToastyTheme.roundOutline(ctx, x - tw / 2 - 4, y - 6, tw + 8, 12, 4, ToastyTheme.alpha(ToastyTheme.PURPLE, 0xB0));
                ctx.drawTextWithShadow(tr, t, x - tw / 2, y - 4, ToastyTheme.TEXT);
            }
        }
        if (cfg.blockHighlight && mc.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            BlockPos bp = bhr.getBlockPos();
            var shape = mc.world.getBlockState(bp).getOutlineShape(mc.world, bp);
            if (!shape.isEmpty()) {
                Box bb = shape.getBoundingBox().offset(bp).expand(0.002);
                box(ctx, bb, ToastyTheme.alpha(ToastyTheme.PURPLE, 0xFF), 2);
            }
        }
        if (cfg.itemGlow) {
            int n = 0;
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof ItemEntity)) continue;
                if (e.squaredDistanceTo(eye) > 48 * 48) continue;
                box(ctx, e.getBoundingBox().expand(0.05), ToastyTheme.alpha(ToastyTheme.BLUE, 0xFF), 1);
                if (++n > 60) break;
            }
        }
    }
}
