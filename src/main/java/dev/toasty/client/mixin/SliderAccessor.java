package dev.toasty.client.mixin;

import net.minecraft.client.gui.widget.SliderWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Liest den Wert (0..1) eines Sliders. */
@Mixin(SliderWidget.class)
public interface SliderAccessor {
    @Accessor("value")
    double toasty$getValue();
}
