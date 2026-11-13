package de.xmoddevx.donuttweks.client;

import de.xmoddevx.donuttweks.DonutTweks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
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
 * stops as soon as the player opens a real container GUI (chest, auction house, plugin
 * menu), because that replaces the inventory menu it clicks in. Chat, the inventory
 * screen and the pause screen are harmless.</p>
 */
public final class AutoSellController {
    private static final AutoSellController INSTANCE = new AutoSellController();

    /** Ticks to wait for the server to confirm an inventory click before retrying it. */
    private static final int CLICK_TIMEOUT_TICKS = 20;
    /** Wait briefly for a command result before trying the held-item right-click. */
    private static final int SELL_RETRY_TICKS = 20;
    /** Time to wait for the command or right-click to remove the listing stack. */
    private static final int SELL_RESULT_TIMEOUT_TICKS = 100;
    /** How often a single click may be retried before the controller gives up on it. */
    private static final int MAX_CLICK_RETRIES = 8;

    private boolean running;
    private Item targetItem;
    private String targetName = "";
    private String price = "0";
    private int itemsPerAuction = 64;
    private boolean autoSplit = true;
    private boolean autoSell = true;
    private boolean autoRightClick = true;

    private int timeout;
    private int clickRetries;
    private boolean waitingForSwap;
    private boolean swapWantsEmptyHand;
    private boolean waitingForResult;
    private int handCountBefore;
    private boolean rightClickSent;
    private int sellRetries;
    private int soldStacks;

    /** Inventory slot the current stack is being split from, or {@code -1}. */
    private int splitSource = -1;
    /** 0 = idle, 1 = picking the stack up, 2 = placing single items, 3 = returning the remainder. */
    private int splitPhase;
    /** Hand count when the split started, so the placed amount can be measured. */
    private int splitHandStart;
    /** True while a split click is waiting for the server to apply it. */
    private boolean splitClickPending;
    private int splitClickTimeout;
    private int splitHandSnapshot;
    private int splitCarriedSnapshot;
    private int splitRetries;
    /** Throttles returning a leftover cursor stack into the inventory. */
    private int cursorTimeout;
    /** Item count of the leftover cursor stack, to notice when nothing changes. */
    private int cursorSnapshot = -1;
    private int cursorAttempts;

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
     * @param price           price argument passed to {@code /ah sell}, already expanded to digits
     *                        ({@code 20k} arrives as {@code 20000})
     * @param itemsPerAuction how many items each auction should contain (1-64)
     * @param autoSplit       split bigger stacks down to {@code itemsPerAuction}
     * @param autoSell        actually send the sell command (off = only prepare the hand)
     * @param autoRightClick  right-click the held item once when a sell command is not
     *                        consumed by the server
     */
    public void start(Item item, String name, String price, int itemsPerAuction,
                      boolean autoSplit, boolean autoSell, boolean autoRightClick) {
        this.targetItem = item;
        this.targetName = name;
        this.price = price;
        this.itemsPerAuction = Math.max(1, Math.min(64, itemsPerAuction));
        this.autoSplit = autoSplit;
        this.autoSell = autoSell;
        this.autoRightClick = autoRightClick;

        this.running = true;
        this.timeout = 0;
        this.clickRetries = 0;
        this.waitingForSwap = false;
        this.swapWantsEmptyHand = false;
        this.waitingForResult = false;
        this.rightClickSent = false;
        this.sellRetries = 0;
        this.soldStacks = 0;
        resetSplit();
        this.cursorTimeout = 0;
        this.cursorSnapshot = -1;
        this.cursorAttempts = 0;
        this.status = Component.translatable("message.donuttweks.autosell.status.selling", name, price);

        DonutTweks.LOGGER.info(
                "Auto-Sell started: item={}, price={}, itemsPerAuction={}, autoSplit={}, autoSell={}, autoRightClick={}",
                name, price, this.itemsPerAuction, autoSplit, autoSell, autoRightClick);

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
        resetSplit();
        this.status = reason;
        DonutTweks.LOGGER.info("Auto-Sell stopped: {}", reason.getString());
        sendMessage(Minecraft.getInstance().player, reason);
    }

    private void finish() {
        int sold = this.soldStacks;
        this.running = false;
        this.waitingForResult = false;
        this.waitingForSwap = false;
        resetSplit();
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
            // A leftover cursor stack blocks every other inventory click, so put it back
            // before continuing. Throttled, because a click sent while the server is
            // still processing the previous one is rejected and has to be repeated.
            if (--cursorTimeout > 0) {
                return;
            }
            cursorTimeout = CLICK_TIMEOUT_TICKS;
            if (carried.getCount() == cursorSnapshot) {
                cursorAttempts++;
            } else {
                cursorSnapshot = carried.getCount();
                cursorAttempts = 0;
            }
            if (cursorAttempts > MAX_CLICK_RETRIES) {
                // Nothing fits anywhere: the run cannot continue without losing items.
                DonutTweks.LOGGER.info("Auto-Sell: cannot put the cursor stack ({}x {}) back into the inventory",
                        carried.getCount(), carried.getItem().getDescriptionId());
                stop(Component.translatable("message.donuttweks.autosell.status.cursor_stuck",
                        carried.getCount(), carried.getHoverName()));
                return;
            }
            int deposit = findDepositSlot(inventory, carried, splitSource, selected);
            if (deposit >= 0) {
                click(client, player, menuSlot(deposit), 0, false);
            }
            return;
        }

        // The cursor is empty again: give the next sell attempt a clean slate.
        cursorTimeout = 0;
        cursorSnapshot = -1;
        cursorAttempts = 0;

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

            if (autoRightClick && !rightClickSent) {
                // Some server auction plugins only list the item when it is used/right-
                // clicked. Send that through the game interaction API, so no physical
                // mouse click is needed. Skipped for items that would be consumed by a
                // use (food), because that would look like a successful sale.
                if (isSafeToUse(hand)) {
                    rightClickSent = true;
                    DonutTweks.LOGGER.info(
                            "Auto-Sell: command did not consume the stack; sending held-item right-click");
                    status = Component.translatable("message.donuttweks.autosell.status.right_click");
                    client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                    timeout = SELL_RESULT_TIMEOUT_TICKS;
                    return;
                }
                rightClickSent = true;
                DonutTweks.LOGGER.info("Auto-Sell: not right-clicking {} because using it would consume it",
                        hand.getItem().getDescriptionId());
            }

            // Still nothing: repeat the command instead of aborting. The server may
            // simply be slow, and commands are cheap.
            sellRetries++;
            DonutTweks.LOGGER.info("Auto-Sell: no inventory change yet, repeating the sell command (try #{})",
                    sellRetries);
            if (sellRetries % 15 == 0) {
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
                DonutTweks.LOGGER.info("Auto-Sell: inventory click was not confirmed, retrying (try #{})",
                        clickRetries + 1);
            }
        }

        // 3. The hand holds the target item: sell it right now.
        if (hand.getItem() == targetItem) {
            if (autoSplit && hand.getCount() > itemsPerAuction) {
                int empty = findEmptySlot(inventory, selected);
                if (empty < 0) {
                    // Splitting needs a second slot. Selling the oversized stack instead
                    // would list all of its items for the price of a single auction.
                    DonutTweks.LOGGER.info(
                            "Auto-Sell: no free slot to split the {}-item stack in the hand, stopping",
                            hand.getCount());
                    stop(Component.translatable("message.donuttweks.autosell.status.no_space"));
                    return;
                }
                // Move the oversized stack out of the way, then split it properly.
                click(client, player, menuSlot(empty), selected, true);
                waitingForSwap = true;
                swapWantsEmptyHand = true;
                timeout = CLICK_TIMEOUT_TICKS;
                return;
            }

            if (!autoSell) {
                finish();
                return;
            }

            handCountBefore = hand.getCount();
            rightClickSent = false;
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
            DonutTweks.LOGGER.info("Auto-Sell: could not move slot {} into the hotbar, stopping", source);
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

    /**
     * Splits an oversized stack down to {@link #itemsPerAuction} items: the stack is
     * picked up as a whole, single items are right-clicked into the selected hotbar
     * slot and the remainder is put back.
     *
     * <p>Every click waits for the server to confirm it before the next one is sent.
     * Clicking on while the server is still processing the previous click only gets
     * the click rejected, because it still carries the old menu state id.</p>
     */
    private void startSplit(Minecraft client, LocalPlayer player, int source) {
        Inventory inventory = player.getInventory();
        this.splitSource = source;
        this.splitPhase = 1;
        this.splitHandStart = inventory.getSelectedItem().getCount();
        this.splitClickPending = false;
        this.splitRetries = 0;
        this.splitClickTimeout = 0;
        this.status = Component.translatable("message.donuttweks.autosell.status.moving", targetName);
        DonutTweks.LOGGER.info("Auto-Sell: splitting slot {} ({} items) down to {}",
                source, inventory.getItem(source).getCount(), this.itemsPerAuction);
        trySplitClick(client, player, menuSlot(source), 0);
    }

    private void tickSplit(Minecraft client, LocalPlayer player, Inventory inventory, ItemStack carried) {
        if (splitSource < 0) {
            resetSplit();
            return;
        }

        ItemStack hand = inventory.getSelectedItem();

        // Phase 1: pick the whole stack up into the cursor.
        if (splitPhase == 1) {
            if (!carried.isEmpty()) {
                splitPhase = 2;
                splitClickPending = false;
                splitRetries = 0;
                return;
            }
            trySplitClick(client, player, menuSlot(splitSource), 0);
            return;
        }

        // Phase 2: right-click single items into the hotbar slot.
        if (splitPhase == 2) {
            int placed = hand.getCount() - splitHandStart;
            if (carried.isEmpty() || placed >= itemsPerAuction
                    || hand.getCount() >= hand.getMaxStackSize() || carried.getItem() != targetItem) {
                splitPhase = 3;
                splitClickPending = false;
                splitRetries = 0;
                return;
            }
            trySplitClick(client, player, menuSlot(inventory.getSelectedSlot()), 1);
            return;
        }

        // Phase 3: put the remainder back into the inventory.
        if (carried.isEmpty()) {
            DonutTweks.LOGGER.info("Auto-Sell: split finished (hand now: {} items)", hand.getCount());
            resetSplit();
            return;
        }
        int deposit = findDepositSlot(inventory, carried, splitSource, inventory.getSelectedSlot());
        if (deposit < 0) {
            DonutTweks.LOGGER.info("Auto-Sell: no room for the split remainder, leaving it on the cursor");
            resetSplit();
            return;
        }
        trySplitClick(client, player, menuSlot(deposit), 0);
    }

    /**
     * Sends one split click and waits until the server confirmed it. The controller
     * remembers the hand and cursor counts before the click; as soon as either count
     * changes, the click arrived. Only if nothing changes for {@link #CLICK_TIMEOUT_TICKS}
     * is the same click sent again.
     */
    private void trySplitClick(Minecraft client, LocalPlayer player, int slot, int button) {
        if (splitClickPending) {
            boolean confirmed = player.getInventory().getSelectedItem().getCount() != splitHandSnapshot
                    || player.inventoryMenu.getCarried().getCount() != splitCarriedSnapshot;
            if (confirmed) {
                splitClickPending = false;
                splitRetries = 0;
                return;
            }
            if (--splitClickTimeout > 0) {
                return;
            }
            if (splitRetries++ >= MAX_CLICK_RETRIES) {
                DonutTweks.LOGGER.info("Auto-Sell: split click on slot {} stayed unconfirmed, starting over", slot);
                resetSplit();
                return;
            }
        }

        this.splitHandSnapshot = player.getInventory().getSelectedItem().getCount();
        this.splitCarriedSnapshot = player.inventoryMenu.getCarried().getCount();
        this.splitClickPending = true;
        this.splitClickTimeout = CLICK_TIMEOUT_TICKS;
        click(client, player, slot, button, false);
    }

    private void resetSplit() {
        this.splitPhase = 0;
        this.splitSource = -1;
        this.splitClickPending = false;
        this.splitClickTimeout = 0;
        this.splitHandSnapshot = 0;
        this.splitCarriedSnapshot = 0;
        this.splitRetries = 0;
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
     * Finds a slot for a stack that sits on the cursor: the preferred slot (the one the
     * split came from) first, then any stack of the same item with enough room, then any
     * empty slot. The hotbar slot that is being filled for the next auction is never
     * used, otherwise the split would merge back into it.
     *
     * @return the inventory slot, or {@code -1} if there is no room at all
     */
    private static int findDepositSlot(Inventory inventory, ItemStack carried, int preferred, int excluded) {
        if (preferred >= 0 && preferred != excluded) {
            ItemStack stack = inventory.getItem(preferred);
            if (stack.isEmpty() || mergesInto(stack, carried)) {
                return preferred;
            }
        }

        int empty = -1;
        for (int i = 0; i < 36; i++) {
            if (i == excluded) {
                continue;
            }
            ItemStack stack = inventory.getItem(i);
            if (mergesInto(stack, carried)) {
                return i;
            }
            if (stack.isEmpty() && empty < 0) {
                empty = i;
            }
        }
        return empty;
    }

    /** {@code true} if the carried stack fits completely into the given slot stack. */
    private static boolean mergesInto(ItemStack stack, ItemStack carried) {
        return !stack.isEmpty()
                && stack.getItem() == carried.getItem()
                && stack.getCount() + carried.getCount() <= stack.getMaxStackSize();
    }

    /**
     * Right-clicking an item can consume it (food, potions, tools). That would look like
     * a successful auction, so only block items are used: a right-click into the air
     * does nothing for them, it sends no block placement and uses nothing up.
     */
    private static boolean isSafeToUse(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem;
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
