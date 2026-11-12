package de.xmoddevx.donuttweks.client;

import de.xmoddevx.donuttweks.DonutTweks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if >=26.1 {
import net.minecraft.world.inventory.ContainerInput;
//?} else {
/*import net.minecraft.world.inventory.ClickType;
*///?}

/**
 * Repeatedly runs {@code /ah sell <price>} for one item type.
 *
 * <p>The auction house plugins we target sell the stack in the player's main hand,
 * so the controller swaps the next matching stack into the selected hotbar slot
 * (server-side inventory click) before sending the command. It keeps going until
 * no more matching items are left in the inventory.</p>
 *
 * <p>Everything runs on the client thread from the client tick event. The controller
 * stops as soon as the player opens any screen (chat, inventory or a plugin
 * confirmation GUI) so it never clicks through GUIs blindly.</p>
 */
public final class AutoSellController {
    private static final AutoSellController INSTANCE = new AutoSellController();

    /** Ticks to wait for the server to confirm an inventory swap before retrying. */
    private static final int SWAP_TIMEOUT_TICKS = 20;
    /** Short settle time after a confirmed swap, so the client has the new stack. */
    private static final int SWAP_SETTLE_TICKS = 2;
    /** Ticks to wait for the server to react to a sell command. */
    private static final int SELL_TIMEOUT_TICKS = 80;
    /** How often we may try to swap the same stack into the hand before aborting. */
    private static final int MAX_SWAP_ATTEMPTS = 8;
    /** Extra pause between two batches, so servers do not see a command flood. */
    private static final int BATCH_PAUSE_TICKS = 40;

    private boolean running;
    private Item targetItem;
    private String targetName = "";
    private String price = "0";
    private int stacksPerBatch = 1;
    private int intervalTicks = 20;

    private int cooldown;
    private int timeout;
    private boolean waitingForResult;
    private boolean waitingForSwap;
    private int handCountBefore;
    private int swapAttempts;
    private int soldStacks;
    private int batchSold;

    private Component status = Component.translatable("message.donuttweks.autosell.status.ready");

    private AutoSellController() {
    }

    public static AutoSellController get() {
        return INSTANCE;
    }

    public boolean isRunning() {
        return running;
    }

    public Component status() {
        return status;
    }

    public Item targetItem() {
        return targetItem;
    }

    public int soldStacks() {
        return soldStacks;
    }

    /**
     * Starts selling the given item.
     *
     * @param item            item type to sell
     * @param name            display name, used in the status messages
     * @param price           price passed to {@code /ah sell}, e.g. {@code 35000}, {@code 35k} or {@code 1.2m}
     * @param stacksPerBatch  how many stacks are listed before the short batch pause
     * @param intervalTicks   ticks between two commands
     */
    public void start(Item item, String name, String price, int stacksPerBatch, int intervalTicks) {
        this.targetItem = item;
        this.targetName = name;
        this.price = price;
        this.stacksPerBatch = Math.max(1, stacksPerBatch);
        this.intervalTicks = Math.max(1, intervalTicks);

        this.running = true;
        this.cooldown = 0;
        this.timeout = 0;
        this.waitingForResult = false;
        this.waitingForSwap = false;
        this.swapAttempts = 0;
        this.soldStacks = 0;
        this.batchSold = 0;
        this.status = Component.translatable("message.donuttweks.autosell.status.selling", name, price);

        DonutTweks.LOGGER.info(
                "Auto-Sell started: item={}, price={}, stacksPerBatch={}, interval={} ticks",
                name, price, this.stacksPerBatch, this.intervalTicks);
    }

    /** Stops a running session (key binding or button). */
    public void stop() {
        stop(Component.translatable("message.donuttweks.autosell.stopped"));
    }

    private void stop(Component reason) {
        if (!running) {
            return;
        }
        this.running = false;
        this.waitingForResult = false;
        this.waitingForSwap = false;
        this.status = reason;
        DonutTweks.LOGGER.info("Auto-Sell stopped: {}", reason.getString());
        sendMessage(Minecraft.getInstance().player, reason);
    }

    private void finish() {
        int sold = this.soldStacks;
        this.running = false;
        this.waitingForResult = false;
        this.waitingForSwap = false;
        this.status = Component.translatable("message.donuttweks.autosell.status.done", sold);
        sendMessage(Minecraft.getInstance().player, this.status);
    }

    /** Shows a message in the player's chat, if a player is available. */
    public static void sendMessage(Component message) {
        sendMessage(Minecraft.getInstance().player, message);
    }

    private static void sendMessage(LocalPlayer player, Component message) {
        if (player == null) {
            return;
        }
        //? if >=26.1 {
        player.sendSystemMessage(message);
        //?} else {
        /*player.displayClientMessage(message, false);
        *///?}
    }

    /** Called once per client tick. */
    public void tick(Minecraft client) {
        if (!running) {
            return;
        }

        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.gameMode == null) {
            stop(Component.translatable("message.donuttweks.autosell.status.left_world"));
            return;
        }

        if (hasOpenScreen(client)) {
            // Safety: never send commands or click slots while a GUI is open.
            stop(Component.translatable("message.donuttweks.autosell.status.gui"));
            return;
        }

        if (client.getConnection() == null) {
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        Inventory inventory = player.getInventory();
        ItemStack hand = inventory.getSelectedItem();

        if (waitingForResult) {
            handlePendingSell(inventory, hand);
            return;
        }

        if (waitingForSwap) {
            if (hand.getItem() == targetItem) {
                // The server applied the swap, the hand now holds the target stack.
                waitingForSwap = false;
                cooldown = SWAP_SETTLE_TICKS;
                return;
            }
            if (--timeout > 0) {
                return;
            }
            // The swap did not arrive in time; fall through and try again.
            waitingForSwap = false;
        }

        if (hand.getItem() != targetItem) {
            if (countItems(inventory, targetItem) <= 0) {
                finish();
                return;
            }
            if (moveToHand(client, player, inventory)) {
                timeout = SWAP_TIMEOUT_TICKS;
            }
            return;
        }

        // The hand holds the target item: sell it.
        handCountBefore = hand.getCount();
        swapAttempts = 0;
        status = Component.translatable("message.donuttweks.autosell.status.selling", targetName, price);
        client.getConnection().sendCommand("ah sell " + price);
        waitingForResult = true;
        timeout = SELL_TIMEOUT_TICKS;
    }

    private void handlePendingSell(Inventory inventory, ItemStack hand) {
        boolean changed = hand.getItem() != targetItem || hand.getCount() < handCountBefore;

        if (changed) {
            waitingForResult = false;
            soldStacks++;
            batchSold++;
            swapAttempts = 0;
            status = Component.translatable("message.donuttweks.autosell.status.sold", soldStacks);

            if (countItems(inventory, targetItem) <= 0) {
                finish();
                return;
            }

            if (batchSold >= stacksPerBatch) {
                batchSold = 0;
                cooldown = Math.max(intervalTicks, BATCH_PAUSE_TICKS);
            } else {
                cooldown = intervalTicks;
            }
            return;
        }

        if (--timeout <= 0) {
            stop(Component.translatable("message.donuttweks.autosell.status.no_response"));
        }
    }

    /**
     * Swaps the next matching stack into the currently selected hotbar slot.
     * The swap is a normal inventory click, so the server performs it and both
     * sides stay in sync. The selected slot itself is never changed.
     *
     * @return {@code true} if a click was sent
     */
    private boolean moveToHand(Minecraft client, LocalPlayer player, Inventory inventory) {
        int selected = inventory.getSelectedSlot();
        int source = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == targetItem && i != selected) {
                source = i;
                break;
            }
        }

        if (source < 0) {
            // Nothing left to swap even though the inventory still counts items.
            stop(Component.translatable("message.donuttweks.autosell.status.swap_failed"));
            return false;
        }

        if (swapAttempts++ >= MAX_SWAP_ATTEMPTS) {
            stop(Component.translatable("message.donuttweks.autosell.status.swap_failed"));
            return false;
        }

        AbstractContainerMenu menu = player.inventoryMenu;
        // Hotbar slots are 36-44 in the player menu, main inventory slots are 9-35.
        int menuSlot = source < 9 ? 36 + source : source;

        //? if >=26.1 {
        client.gameMode.handleContainerInput(menu.containerId, menuSlot, selected, ContainerInput.SWAP, player);
        //?} else {
        /*client.gameMode.handleInventoryMouseClick(menu.containerId, menuSlot, selected, ClickType.SWAP, player);
        *///?}

        status = Component.translatable("message.donuttweks.autosell.status.moving", targetName);
        waitingForSwap = true;
        return true;
    }

    private static boolean hasOpenScreen(Minecraft client) {
        //? if >=26.2 {
        return client.gui.screen() != null;
        //?} else {
        /*return client.screen != null;
        *///?}
    }

    /** Counts all items of the given type in the 36 main inventory slots. */
    public static int countItems(Inventory inventory, Item item) {
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
