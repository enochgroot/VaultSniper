package com.vaultsniper;

import com.vaultsniper.screen.VaultSniperScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class VaultSniperMod implements ClientModInitializer {
    public static final String MOD_ID = "vaultsniper";
    private static KeyMapping openKey;
    private static KeyMapping toggleKey;

    @Override public void onInitializeClient() {
        try {
            // V = open config screen
            openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.vaultsniper.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, KeyMapping.Category.MISC));

            // G = quick toggle on/off
            toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.vaultsniper.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, KeyMapping.Category.MISC));

            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                // Open config screen
                while (openKey.consumeClick()) {
                    if (client.screen == null)
                        client.setScreen(new VaultSniperScreen());
                }
                // Quick toggle
                while (toggleKey.consumeClick()) {
                    VaultSniper.getInstance().toggle();
                }
                // Run sniper logic
                VaultSniper.getInstance().tick(client);
            });

            System.out.println("[VaultSniper] v1.0.0 loaded! V = config, G = toggle");
        } catch (Throwable t) {
            System.err.println("[VaultSniper] INIT FAILED: " + t);
            t.printStackTrace();
        }
    }

    public static KeyMapping getOpenKey() { return openKey; }
    public static KeyMapping getToggleKey() { return toggleKey; }
}
