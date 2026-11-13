package de.xmoddevx.donuttweks.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.xmoddevx.donuttweks.DonutTweks;
import de.xmoddevx.donuttweks.client.gui.AutoSellScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
*///?}
import net.minecraft.client.KeyMapping;

/**
 * Client-only entrypoint. Never loaded on a dedicated server.
 *
 * <p>Registers the auto-sell key and drives {@link AutoSellController} once per tick.</p>
 */
public class DonutTweksClient implements ClientModInitializer {
    private static KeyMapping autoSellKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(DonutTweks.id("main"));

        //? if >=26.1 {
        autoSellKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.donuttweks.auto_sell",
                //? if >=26.3 {
                InputConstants.Type.KEYBOARD,
                //?} else {
                /*InputConstants.Type.KEYSYM,
                *///?}
                InputConstants.KEY_R,
                category));
        //?} else {
        /*autoSellKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.donuttweks.auto_sell",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_R,
                category));
        *///?}

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (autoSellKey.consumeClick()) {
                AutoSellController controller = AutoSellController.get();

                if (controller.isRunning()) {
                    client.setScreenAndShow(new AutoSellScreen());
                } else if (client.player != null) {
                    client.setScreenAndShow(new AutoSellScreen());
                }
            }

            AutoSellController.get().tick(client);
        });

        DonutTweks.LOGGER.info("DonutTweks client initialized");
    }
}
