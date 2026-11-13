package de.xmoddevx.donuttweks;

import de.xmoddevx.donuttweks.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint of DonutTweks.
 *
 * <p>This class is loaded on both the client and the dedicated server.</p>
 */
public class DonutTweks implements ModInitializer {
    public static final String MOD_ID = "donuttweks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Replaced by Stonecutter at build time (see stonecutter.gradle.kts).
    // The active version compiles this file directly, so keep the literal in sync
    // with mod.version in stonecutter.properties.toml.
    public static final String VERSION = /*$ mod_version*/ "0.1.3";
    public static final String MINECRAFT = /*$ minecraft*/ "26.3";

    @Override
    public void onInitialize() {
        ModItems.initialize();

        LOGGER.info("DonutTweks {} loaded for Minecraft {}", VERSION, MINECRAFT);
    }

    /**
     * Creates a namespaced identifier for this mod.
     * The class was renamed from ResourceLocation to Identifier in 1.21.11,
     * so this is the only supported way in the 1.21.11+ range.
     */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
