package dev.toasty.client.mixin;

import dev.toasty.client.ToastyConfig;
import dev.toasty.client.ToastyTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Inventare (Kisten, Ofen, Crafting, Spieler-Inventar ...) im Toasty-Design: runde Slots, durchsichtiges Panel. */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> extends Screen {
    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected int backgroundWidth;
    @Shadow protected int backgroundHeight;
    @Shadow protected T handler;

    @Shadow protected abstract void drawBackground(DrawContext context, float delta, int mouseX, int mouseY);

    protected HandledScreenMixin(Text title) { super(title); }

    @Redirect(method = {"render", "renderBackground"}, require = 0,
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawBackground(Lnet/minecraft/client/gui/DrawContext;FII)V"))
    private void toasty$bg(DrawContext ctx, float delta, int mouseX, int mouseY) {
        try {
            Object self = this;
            if (!ToastyConfig.get().niceInventory || self instanceof CreativeInventoryScreen) {
                this.drawBackground(ctx, delta, mouseX, mouseY);
                return;
            }
            long p0 = dev.toasty.client.ToastyProf.begin();
            ToastyTheme.panel(ctx, x, y, backgroundWidth, backgroundHeight, 0xD0);
            for (Slot s : handler.slots) {
                int sx = x + s.x - 1, sy = y + s.y - 1;
                ToastyTheme.softRect(ctx, sx, sy, 18, 18, 0x2EFFFFFF);
            }
            dev.toasty.client.ToastyProf.end("Inventar-Design", p0);
        } catch (Throwable t) {
            this.drawBackground(ctx, delta, mouseX, mouseY);
        }
    }
}
