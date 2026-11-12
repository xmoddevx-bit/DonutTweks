package de.xmoddevx.donuttweks.client;

import de.xmoddevx.donuttweks.DonutTweks;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client-only entrypoint. Never loaded on a dedicated server.
 *
 * <p>Put rendering, key bindings, screens and other client code here.</p>
 */
public class DonutTweksClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DonutTweks.LOGGER.info("DonutTweks client initialized");
    }
}
