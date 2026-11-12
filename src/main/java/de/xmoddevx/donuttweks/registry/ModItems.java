package de.xmoddevx.donuttweks.registry;

import de.xmoddevx.donuttweks.DonutTweks;
//? if >=26.1 {
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
//?} else {
/*import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
*///?}
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

/**
 * Central item registration.
 *
 * <p>Fabric has no deferred registry, so items are registered directly while the
 * mod initializer runs. Keep all {@code Registry.register} calls in one place.</p>
 */
public final class ModItems {
    /** Example item: a plain donut. Replace or extend this with your actual content. */
    public static final Item DONUT = register("donut", new Item.Properties());

    private ModItems() {
    }

    private static Item register(String path, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DonutTweks.id(path));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
    }

    /** Called from {@link DonutTweks#onInitialize()}. */
    public static void initialize() {
        //? if >=26.1 {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS)
                .register(output -> output.accept(DONUT));
        //?} else {
        /*ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FOOD_AND_DRINKS)
                .register(entries -> entries.accept(DONUT));
        *///?}
    }
}
