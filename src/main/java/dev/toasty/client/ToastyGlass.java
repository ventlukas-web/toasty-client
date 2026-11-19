package dev.toasty.client;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Unsichtbares Glas ueber ein eingebautes Resource-Pack (alle Glas-Texturen komplett durchsichtig). */
public final class ToastyGlass {
    public static final Identifier PACK_ID = Identifier.of("toastyclient", "invisible_glass");

    private ToastyGlass() {}

    public static void register() {
        FabricLoader.getInstance().getModContainer("toastyclient").ifPresent(c ->
            ResourceManagerHelper.registerBuiltinResourcePack(PACK_ID, c,
                Text.literal("Toasty - Unsichtbares Glas"), ResourcePackActivationType.NORMAL));
    }

    private static String findId(ResourcePackManager pm) {
        for (ResourcePackProfile p : pm.getProfiles()) {
            if (p.getId().contains("invisible_glass")) return p.getId();
        }
        return null;
    }

    public static boolean isOn() {
        ResourcePackManager pm = MinecraftClient.getInstance().getResourcePackManager();
        String id = findId(pm);
        return id != null && pm.getEnabledIds().contains(id);
    }

    /** @return false, wenn das Pack nicht gefunden wurde. */
    public static boolean set(boolean on) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ResourcePackManager pm = mc.getResourcePackManager();
        String id = findId(pm);
        if (id == null) return false;
        List<String> enabled = new ArrayList<>(pm.getEnabledIds());
        if (on) { if (!enabled.contains(id)) enabled.add(id); } else enabled.remove(id);
        pm.setEnabledProfiles(enabled);
        mc.options.refreshResourcePacks(pm);
        return true;
    }

    public static boolean toggle() {
        boolean target = !isOn();
        return set(target);
    }
}
