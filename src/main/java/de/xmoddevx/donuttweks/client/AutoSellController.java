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
//? } else {
/*import net.minecraft.world.inventory.ClickType;
*///?}

/**
 * Repeatedly runs {@code /ah sell <price>} for one item type.
 *
 * <p>The auction house plugins we target sell the stack in the player's main hand,
 * so the controller swaps the next matching stack into the selected hotbar slot
 * (server-side inventory click) before sending the command. Larger stacks are split
 * down to the configured auction size. It keeps going until no more matching items
 * remain.</p>
 *
 * <p>There is <b>no delay</b> between two sell commands: as soon as the server
 * removed the stack from the hand, the next command is sent on the following tick.
 * If the server does not react at all, the command is simply repeated - the
 * controller never aborts because of a slow server.</p>
 *
 * <p>Everything runs on the client thread from the client tick event. The controller
 * stops as soon as the player opens any screen (chat, inventory or a plugin
 * confirmation GUI) so it never clicks through GUIs blindly.</p>
 */
public final class AutoSellController {
    private static final AutoSellController INSTANCE = new AutoSellController();

    /** Ticks to wait for the server to confirm an inventory click before retrying it. */
    private static final int CLICK_TIMEOUT_TICKS = 20;
    /** Ticks before an unanswered sell command is repeated. */
    private static final int SELL_RETRY_TICKS = 20;
    /** Ticks to wait for the cursor to empty again after returning a split remainder. */
    private static final int SPLIT_RETURN_TIMEOUT_TICKS = 40;
    /** How often a single click may be retried before the controller gives up on it. */
    private static final int MAX_CLICK_RETRIES = 8;

    private boolean running;
    private Item targetItem;
    private String targetName = "";
    private String price = "0";
    private int itemsPerAuction = 64;
    private boolean autoSplit = true;
    private boolean autoSell = true;

    private int timeout;
    private int clickRetries;
    private boolean waitingForSwap;
    private boolean swapWantsEmptyHand;
    private boolean waitingForResult;
    private int handCountBefore;
    private int sellRetries;
    private int soldStacks;

    /** Inventory slot the current stack is being split from, or {@code -1}. */
    private int splitSource = -1;
    /** 0 = idle, 1 = picking the stack up, 2 = placing single items, 3 = returning the remainder. */
    private int splitPhase;
    private int splitClicksRemaining;

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
     * @param price           price argument passed to {@code /ah sell}, kept exactly as typed (e.g. {@code 20k})
     * @param itemsPerAuction how many items each auction should contain (1-64)
     * @param autoSplit       split bigger stacks down to {@code itemsPerAuction}
     * @param autoSell        actually send the sell command (off = only prepare the hand)
     */
    public void start(Item item, String name, String price, int itemsPerAuction,
                      boolean autoSplit, boolean autoSell) {
        this.targetItem = item;
        this.targetName = name;
        this.price = price;
        this.itemsPerAuction = Math.max(1, Math.min(64, itemsPerAuction));
        this.autoSplit = autoSplit;
        this.autoSell = autoSell;

        this.running = true;
        this.timeout = 0;
        this.clickRetries = 0;
        this.waitingForSwap = false;
        this.swapWantsEmptyHand = false;
        this.waitingForResult = false;
        this.sellRetries = 0;
        this.soldStacks = 0;
        this.splitSource = -1;
        this.splitPhase = 0;
        this.splitClicksRemaining = 0;
        this.status = Component.translatable("message.donuttweks.autosell.status.selling", name, price);

        DonutTweks.LOGGER.info(
                "Auto-Sell started: item={}, price={}, itemsPerAuction={}, autoSplit={}, autoSell={}",
                name, price, this.itemsPerAuction, autoSplit, autoSell);

        Minecraft client = Minecraft.getInstance();
        DonutTweks.LOGGER.info(
                "Auto-Sell start context: connected={}, world={}, position={}",
                client.getConnection() != null,
                client.level != null,
                client.player != null ? client.player.blockPosition().toString() : "no player");
    }

    /** Stops a running session (key binding or button). */
    public void stop() {
        stop(Component.translatable("message.donuttweks.autosell.stopped"));
    }

    private void stop(Component reason) {
        if (!running) {
            return;
        }
        // Never leave a split remainder on the cursor when stopping.
        Minecraft client = Minecraft.getInstance();
        if (splitSource >= 0 && client.player != null && client.gameMode != null
                && !client.player.inventoryMenu.getCarried().isEmpty()) {
            click(client, client.player, menuSlot(splitSource), 0, false);
        }
        this.running = false;
        this.waitingForResult = false;
        this.waitingForSwap = false;
        this.splitPhase = 0;
        this.splitSource = -1;
        this.status = reason;
        DonutTweks.LOGGER.info("Auto-Sell stopped: {}", reason.getString());
        sendMessage(Minecraft.getInstance().player, reason);
    }

    private void finish() {
        int sold = this.soldStacks;
        this.running = false;
        this.waitingForResult = false;
        this.waitingForSwap = false;
        this.splitPhase = 0;
        this.splitSource = -1;
        this.status = Component.translatable("message.donuttweks.autosell.status.done", sold);
        DonutTweks.LOGGER.info("Auto-Sell finished: {} auctions", sold);
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

        // Only a real container GUI (chest, auction house, plugin menu) is dangerous,
        // because it replaces the menu we click in. Chat, the inventory screen and the
        // pause screen are harmless - the player can keep watching the run there.
        if (player.containerMenu != player.inventoryMenu) {
            DonutTweks.LOGGER.info("Auto-Sell stopped: a container GUI was opened ({})",
                    player.containerMenu.getClass().getName());
            stop(Component.translatable("message.donuttweks.autosell.status.gui"));
            return;
        }

        if (client.getConnection() == null) {
            return;
        }

        Inventory inventory = player.getInventory();
        AbstractContainerMenu menu = player.inventoryMenu;
        int selected = inventory.getSelectedSlot();
        ItemStack hand = inventory.getSelectedItem();
        ItemStack carried = menu.getCarried();

        // Splitting needs the cursor. Only our own split may leave something there.
        if (splitPhase != 0) {
            tickSplit(client, player, inventory, carried);
            return;
        }
        if (!carried.isEmpty()) {
            if (splitSource >= 0) {
                // Leftover from a split: put it back into its source slot.
                click(client, player, menuSlot(splitSource), 0, false);
            }
            return;
        }

        // 1. Did the last sell command work? If so, continue on the very next tick.
        if (waitingForResult) {
            boolean sold = hand.isEmpty()
                    || hand.getItem() != targetItem
                    || hand.getCount() < handCountBefore;

            if (sold) {
                waitingForResult = false;
                sellRetries = 0;
                soldStacks++;
                clickRetries = 0;
                status = Component.translatable("message.donuttweks.autosell.status.sold", soldStacks);
                return;
            }

            if (--timeout > 0) {
                return;
            }

            // The server did not react (yet): repeat the command instead of aborting.
            sellRetries++;
            DonutTweks.LOGGER.info("Auto-Sell: no inventory change after sell command, repeating (try #{})",
                    sellRetries);
            if (sellRetries == 10) {
                // One-shot probe: if the server answers /ah at all, commands work and
                // only the sell command itself is ignored.
                DonutTweks.LOGGER.info("Auto-Sell: probing /ah to check whether commands are accepted");
                sendMessage(Component.translatable("message.donuttweks.autosell.status.probe"));
                client.getConnection().sendCommand("ah");
            }
            if (sellRetries == 15) {
                sendMessage(Component.translatable("message.donuttweks.autosell.status.waiting_server"));
            }
            handCountBefore = hand.getCount();
            sendSell(client);
            timeout = SELL_RETRY_TICKS;
            return;
        }

        // 2. Waiting for an inventory click to arrive from the server.
        if (waitingForSwap) {
            boolean arrived = swapWantsEmptyHand ? hand.isEmpty() : hand.getItem() == targetItem;
            if (arrived) {
                waitingForSwap = false;
                clickRetries = 0;
            } else if (--timeout > 0) {
                return;
            } else {
                waitingForSwap = false;
            }
        }

        // 3. The hand holds the target item: sell it right now.
        if (hand.getItem() == targetItem) {
            if (autoSplit && hand.getCount() > itemsPerAuction) {
                int empty = findEmptySlot(inventory, selected);
                if (empty >= 0) {
                    // Move the oversized stack out of the way, then split it properly.
                    click(client, player, menuSlot(empty), selected, true);
                    waitingForSwap = true;
                    swapWantsEmptyHand = true;
                    timeout = CLICK_TIMEOUT_TICKS;
                    return;
                }
                // No free slot to split into: sell the stack as it is.
            }

            if (!autoSell) {
                finish();
                return;
            }

            handCountBefore = hand.getCount();
            sendSell(client);
            waitingForResult = true;
            timeout = SELL_RETRY_TICKS;
            status = Component.translatable("message.donuttweks.autosell.status.selling", targetName, price);
            return;
        }

        // 4. Nothing left to sell?
        if (countItems(inventory, targetItem) <= 0) {
            finish();
            return;
        }

        int source = findBestTargetSlot(inventory, selected);
        if (source < 0) {
            finish();
            return;
        }

        // 5. Split an oversized stack down to the configured auction size.
        if (autoSplit && hand.isEmpty() && inventory.getItem(source).getCount() > itemsPerAuction) {
            startSplit(client, player, source);
            return;
        }

        // 6. Move the whole stack into the hand.
        if (clickRetries++ >= MAX_CLICK_RETRIES) {
            stop(Component.translatable("message.donuttweks.autosell.status.swap_failed"));
            return;
        }
        status = Component.translatable("message.donuttweks.autosell.status.moving", targetName);
        click(client, player, menuSlot(source), selected, true);
        waitingForSwap = true;
        swapWantsEmptyHand = false;
        timeout = CLICK_TIMEOUT_TICKS;
    }

    // ------------------------------------------------------------------
    // Splitting
    // ------------------------------------------------------------------

    private void startSplit(Minecraft client, LocalPlayer player, int source) {
        this.splitSource = source;
        this.splitPhase = 1;
        this.splitClicksRemaining = this.itemsPerAuction;
        this.clickRetries = 0;
        this.timeout = CLICK_TIMEOUT_TICKS;
        this.status = Component.translatable("message.donuttweks.autosell.status.moving", targetName);
        DonutTweks.LOGGER.info("Auto-Sell: splitting slot {} ({} items) down to {}",
                source, player.getInventory().getItem(source).getCount(), this.itemsPerAuction);
        // Pick up the whole stack, the single items are placed on the next ticks.
        click(client, player, menuSlot(source), 0, false);
    }

    private void tickSplit(Minecraft client, LocalPlayer player, Inventory inventory, ItemStack carried) {
        int selected = inventory.getSelectedSlot();

        if (splitPhase == 1) {
            if (!carried.isEmpty()) {
                splitPhase = 2;
                return;
            }
            if (--timeout > 0) {
                return;
            }
            if (clickRetries++ >= MAX_CLICK_RETRIES) {
                resetSplit();
                return;
            }
            timeout = CLICK_TIMEOUT_TICKS;
            click(client, player, menuSlot(splitSource), 0, false);
            return;
        }

        if (splitPhase == 2) {
            if (carried.isEmpty()) {
                // Nothing left to place, the hand already has its items.
                splitPhase = 3;
                clickRetries = 0;
                timeout = SPLIT_RETURN_TIMEOUT_TICKS;
                return;
            }
            if (splitClicksRemaining > 0) {
                splitClicksRemaining--;
                click(client, player, menuSlot(selected), 1, false);
                return;
            }
            // Hand is filled: return the remainder to the source slot.
            splitPhase = 3;
            clickRetries = 0;
            timeout = SPLIT_RETURN_TIMEOUT_TICKS;
            click(client, player, menuSlot(splitSource), 0, false);
            return;
        }

        // Phase 3: wait until the cursor is empty again.
        if (carried.isEmpty()) {
            DonutTweks.LOGGER.info("Auto-Sell: split finished (hand now: {} items)", inventory.getSelectedItem().getCount());
            resetSplit();
            return;
        }
        if (--timeout > 0) {
            return;
        }
        if (clickRetries++ >= MAX_CLICK_RETRIES) {
            resetSplit();
            return;
        }
        timeout = SPLIT_RETURN_TIMEOUT_TICKS;
        click(client, player, menuSlot(splitSource), 0, false);
    }

    private void resetSplit() {
        this.splitPhase = 0;
        this.splitSource = -1;
        this.splitClicksRemaining = 0;
        this.clickRetries = 0;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void sendSell(Minecraft client) {
        LocalPlayer player = client.player;
        if (player != null) {
            ItemStack hand = player.getInventory().getSelectedItem();
            DonutTweks.LOGGER.info(
                    "Auto-Sell: sending /ah sell {} (hand: {}x {} in slot {}, {} matching items left)",
                    price, hand.getCount(), hand.getItem().getDescriptionId(),
                    player.getInventory().getSelectedSlot(), countItems(player.getInventory(), targetItem));
        }
        client.getConnection().sendCommand("ah sell " + price);
    }

    private static void click(Minecraft client, LocalPlayer player, int slot, int button, boolean swap) {
        AbstractContainerMenu menu = player.inventoryMenu;
        //? if >=26.1 {
        client.gameMode.handleContainerInput(menu.containerId, slot, button,
                swap ? ContainerInput.SWAP : ContainerInput.PICKUP, player);
        //?} else {
        /*client.gameMode.handleInventoryMouseClick(menu.containerId, slot, button,
                swap ? ClickType.SWAP : ClickType.PICKUP, player);
        *///?}
    }

    private static int menuSlot(int inventorySlot) {
        return inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
    }

    private static int findEmptySlot(Inventory inventory, int excluded) {
        for (int i = 0; i < 36; i++) {
            if (i != excluded && inventory.getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Picks the next stack to sell. Small stacks come first, and with auto-split
     * enabled stacks that fit into one auction are preferred so fewer clicks are needed.
     */
    private int findBestTargetSlot(Inventory inventory, int excluded) {
        int best = -1;
        int bestScore = Integer.MAX_VALUE;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            if (i == excluded || stack.isEmpty() || stack.getItem() != targetItem) {
                continue;
            }
            int count = stack.getCount();
            int score = (autoSplit && count > itemsPerAuction) ? count + 1_000 : count;
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
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
