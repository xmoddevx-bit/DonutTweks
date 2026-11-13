package de.xmoddevx.donuttweks.client.gui;

import de.xmoddevx.donuttweks.DonutTweks;
import de.xmoddevx.donuttweks.client.AutoSellController;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration screen for the auto seller.
 *
 * <p>Pick one item type from the inventory, enter a price and items per auction,
 * then press "Start". The screen closes and
 * {@link AutoSellController} starts sending {@code /ah sell <price>}.</p>
 */
public class AutoSellScreen extends Screen {
    private static final int COLUMNS = 2;
    private static final int ROWS = 6;
    private static final int PAGE_SIZE = COLUMNS * ROWS;
    private static final int CELL_HEIGHT = 21;
    private static final int DEFAULT_PANEL_WIDTH = 176;
    private static final int MIN_PANEL_WIDTH = 140;
    private static final int CONTENT_HEIGHT = 196;

    private final List<Entry> entries = new ArrayList<>();

    private int page;
    private Item selectedItem;
    private String selectedName = "";
    private Component hint;

    private EditBox priceField;
    private EditBox stacksField;

    private String priceText = "20k";
    private String stacksText = "1";
    private boolean autoSplitEnabled = true;
    private boolean autoSellEnabled = true;
    private boolean autoRightClickEnabled = true;

    private int listX;
    private int listY;
    private int listWidth;
    private int cellWidth;
    private int panelX;
    private int panelWidth;
    private int contentWidth;

    public AutoSellScreen() {
        super(Component.translatable("screen.donuttweks.autosell.title"));
    }

    @Override
    protected void init() {
        super.init();

        int total = Math.min(456, Math.max(260, this.width - 16));
        this.panelWidth = Math.min(DEFAULT_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, total * 2 / 5));
        this.listWidth = total - this.panelWidth - 10;
        this.contentWidth = this.listWidth + this.panelWidth + 10;
        this.cellWidth = this.listWidth / COLUMNS;
        this.listX = Math.max(4, (this.width - this.contentWidth) / 2);
        this.listY = Math.max(26, (this.height - CONTENT_HEIGHT) / 2);
        this.panelX = this.listX + this.listWidth + 10;

        collectEntries();

        addItemButtons();
        addNavigation();
        addEditor();
    }

    private void collectEntries() {
        entries.clear();

        LocalPlayer player = this.minecraft == null ? null : this.minecraft.player;
        if (player == null) {
            return;
        }

        Inventory inventory = player.getInventory();
        Map<Item, Integer> counts = new LinkedHashMap<>();
        Map<Item, ItemStack> icons = new LinkedHashMap<>();
        Map<Item, String> names = new LinkedHashMap<>();

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            icons.putIfAbsent(stack.getItem(), stack.copyWithCount(1));
            names.putIfAbsent(stack.getItem(), stack.getHoverName().getString());
        }

        counts.forEach((item, count) -> entries.add(new Entry(item, icons.get(item), names.get(item), count)));
        entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));

        if (selectedItem != null && !counts.containsKey(selectedItem)) {
            selectedItem = null;
            selectedName = "";
        }

        int maxPage = Math.max(0, (entries.size() - 1) / PAGE_SIZE);
        this.page = Math.min(this.page, maxPage);
    }

    private void addItemButtons() {
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = this.page * PAGE_SIZE + i;
            if (index >= entries.size()) {
                break;
            }

            Entry entry = entries.get(index);
            int x = listX + (i % COLUMNS) * cellWidth;
            int y = listY + (i / COLUMNS) * CELL_HEIGHT;
            boolean selected = selectedItem == entry.item();

            String label = (selected ? "\u00bb " : "")
                    + shorten(entry.name(), Math.max(6, (cellWidth - 30) / 6))
                    + " x" + entry.count();

            addRenderableWidget(Button.builder(Component.literal(label), button -> select(entry))
                    .bounds(x + 20, y, cellWidth - 22, 20)
                    .build());
        }
    }

    private void addNavigation() {
        int y = listY + ROWS * CELL_HEIGHT + 6;

        addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            if (page > 0) {
                page--;
                rebuildWidgets();
            }
        }).bounds(listX, y, 20, 20).build());

        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            if ((page + 1) * PAGE_SIZE < entries.size()) {
                page++;
                rebuildWidgets();
            }
        }).bounds(listX + listWidth - 20, y, 20, 20).build());

        int refreshWidth = Math.max(60, Math.min(90, listWidth - 48));
        addRenderableWidget(Button.builder(Component.translatable("screen.donuttweks.autosell.refresh"), button -> {
            collectEntries();
            rebuildWidgets();
        }).bounds(listX + (listWidth - refreshWidth) / 2, y, refreshWidth, 20).build());
    }

    private void addEditor() {
        priceField = new EditBox(this.font, panelX, listY + 14, panelWidth, 20,
                Component.translatable("screen.donuttweks.autosell.price"));
        priceField.setMaxLength(9);
        priceField.setValue(priceText);
        priceField.setResponder(value -> priceText = value);
        addRenderableWidget(priceField);

        stacksField = new EditBox(this.font, panelX, listY + 56, panelWidth, 20,
                Component.translatable("screen.donuttweks.autosell.stacks"));
        stacksField.setMaxLength(2);
        stacksField.setValue(stacksText);
        stacksField.setResponder(value -> stacksText = value);
        addRenderableWidget(stacksField);

        int half = (panelWidth - 4) / 2;

        addRenderableWidget(Button.builder(toggleLabel("screen.donuttweks.autosell.auto_split", autoSplitEnabled), button -> {
                    autoSplitEnabled = !autoSplitEnabled;
                    button.setMessage(toggleLabel("screen.donuttweks.autosell.auto_split", autoSplitEnabled));
                }).bounds(panelX, listY + 88, half, 20).build());
        addRenderableWidget(Button.builder(toggleLabel("screen.donuttweks.autosell.auto_sell", autoSellEnabled), button -> {
                    autoSellEnabled = !autoSellEnabled;
                    button.setMessage(toggleLabel("screen.donuttweks.autosell.auto_sell", autoSellEnabled));
                }).bounds(panelX + half + 4, listY + 88, half, 20).build());

        addRenderableWidget(Button.builder(
                        toggleLabel("screen.donuttweks.autosell.auto_right_click", autoRightClickEnabled), button -> {
                            autoRightClickEnabled = !autoRightClickEnabled;
                            button.setMessage(toggleLabel("screen.donuttweks.autosell.auto_right_click",
                                    autoRightClickEnabled));
                        })
                .bounds(panelX, listY + 110, panelWidth, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.donuttweks.autosell.start"),
                        button -> startSelling())
                .bounds(panelX, listY + 134, half, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("screen.donuttweks.autosell.stop"),
                        button -> AutoSellController.get().stop())
                .bounds(panelX + half + 4, listY + 134, half, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("screen.donuttweks.autosell.close"),
                        button -> onClose())
                .bounds(panelX, listY + 158, panelWidth, 20)
                .build());
    }

    private void select(Entry entry) {
        this.selectedItem = entry.item();
        this.selectedName = entry.name();
        this.hint = null;
        rebuildWidgets();
    }

    private void startSelling() {
        hint = null;

        DonutTweks.LOGGER.info(
                "Auto-Sell start pressed: item={}, price='{}', itemsPerAuction='{}', autoSplit={}, autoSell={}, autoRightClick={}",
                selectedName, priceText, stacksText, autoSplitEnabled, autoSellEnabled, autoRightClickEnabled);

        if (selectedItem == null) {
            reject("screen.donuttweks.autosell.invalid_item", "no item selected");
            return;
        }
        if (this.minecraft != null && this.minecraft.player != null
                && AutoSellController.countItems(this.minecraft.player.getInventory(), selectedItem) <= 0) {
            reject("screen.donuttweks.autosell.invalid_item", "item no longer in inventory: " + selectedName);
            return;
        }

        String price = parsePrice(priceText);
        if (price == null) {
            reject("screen.donuttweks.autosell.invalid_price", "invalid price: '" + priceText + "'");
            return;
        }

        int stacks = parseInt(stacksText, -1);
        if (stacks < 1 || stacks > 64) {
            reject("screen.donuttweks.autosell.invalid_stack_size", "invalid items per auction: " + stacksText);
            return;
        }

        AutoSellController.get().start(selectedItem, selectedName, price, stacks,
                autoSplitEnabled, autoSellEnabled, autoRightClickEnabled);
        onClose();
    }

    /** Shows a validation error in the screen and in chat, and logs the reason. */
    private void reject(String translationKey, String logReason) {
        this.hint = Component.translatable(translationKey);
        DonutTweks.LOGGER.info("Auto-Sell start rejected: {}", logReason);
        AutoSellController.sendMessage(Component.translatable(translationKey));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static Component toggleLabel(String key, boolean enabled) {
        return Component.translatable(key, Component.translatable(enabled
                ? "screen.donuttweks.autosell.enabled" : "screen.donuttweks.autosell.disabled"));
    }

    /**
     * Parses a price the way players type it into the auction house: {@code 35000},
     * {@code 35k}, {@code 1.2m} or {@code 3b}. Suffixes are expanded to plain digits
     * before sending, e.g. {@code 20k} becomes {@code 20000}.
     *
     * @return the normalized price argument, or {@code null} if it is not a positive price
     */
    private static String parsePrice(String input) {
        if (input == null) {
            return null;
        }

        String value = input.trim().toLowerCase(java.util.Locale.ROOT).replace(" ", "");
        if (value.isEmpty()) {
            return null;
        }

        char suffix = value.charAt(value.length() - 1);
        int decimalShift = switch (suffix) {
            case 'k' -> 3;
            case 'm' -> 6;
            case 'b' -> 9;
            default -> 0;
        };

        String number = decimalShift == 0 ? value : value.substring(0, value.length() - 1);

        if (decimalShift == 0) {
            // Without a suffix only plain digits are accepted, so "35.000" is not
            // silently read as 35. Players who want 35,000 must type "35k".
            if (number.isEmpty()) {
                return null;
            }
            for (int i = 0; i < number.length(); i++) {
                char character = number.charAt(i);
                if (character < '0' || character > '9') {
                    return null;
                }
            }
        } else {
            number = number.replace(',', '.');

            int separators = 0;
            for (int i = 0; i < number.length(); i++) {
                char character = number.charAt(i);
                if (character == '.') {
                    if (++separators > 1) {
                        return null;
                    }
                } else if (character < '0' || character > '9') {
                    return null;
                }
            }

            if (number.endsWith(".")) {
                number = number.substring(0, number.length() - 1);
            }
            if (number.isEmpty()) {
                return null;
            }
        }

        try {
            java.math.BigInteger amount = new java.math.BigDecimal(number)
                    .movePointRight(decimalShift)
                    .toBigIntegerExact();
            if (amount.signum() <= 0) {
                return null;
            }
            return amount.toString();
        } catch (NumberFormatException | ArithmeticException exception) {
            return null;
        }
    }

    private static String shorten(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(1, maxChars - 1)) + "\u2026";
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    //? if >=26.1 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        renderContent(graphics, mouseX, mouseY);
    }
    //?} else {
    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        renderContent(graphics, mouseX, mouseY);
    }
    *///?}

    private void renderContent(Object graphics, int mouseX, int mouseY) {
        fill(graphics, listX - 8, listY - 26, listX + contentWidth + 8, listY + CONTENT_HEIGHT + 6, 0x99000000);
        fill(graphics, listX - 8, listY - 26, listX + contentWidth + 8, listY - 25, 0xFFFFFFFF);

        text(graphics, this.title, listX, listY - 20, 0xFFFFFF);
        text(graphics, Component.translatable("screen.donuttweks.autosell.price"),
                panelX, listY + 2, 0xA0A0A0);
        text(graphics, Component.translatable("screen.donuttweks.autosell.stacks"),
                panelX, listY + 39, 0xA0A0A0);

        int navY = listY + ROWS * CELL_HEIGHT + 6;
        String pageLabel = Component.translatable("screen.donuttweks.autosell.page", page + 1).getString();
        text(graphics, Component.literal(pageLabel),
                listX + contentWidth - this.font.width(pageLabel), listY - 20, 0xFFFFFF);

        // Item icons for the current page.
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = page * PAGE_SIZE + i;
            if (index >= entries.size()) {
                break;
            }
            int x = listX + (i % COLUMNS) * cellWidth + 2;
            int y = listY + (i / COLUMNS) * CELL_HEIGHT + 2;
            ItemStack icon = entries.get(index).icon();
            if (!icon.isEmpty()) {
                item(graphics, icon, x, y);
            }
        }

        if (entries.isEmpty()) {
            text(graphics, Component.translatable("screen.donuttweks.autosell.empty"),
                    listX + 4, listY + 6, 0xFFFF55);
        }

        Component line;
        int color;
        if (hint != null) {
            line = hint;
            color = 0xFF5555;
        } else if (AutoSellController.get().isRunning()) {
            line = AutoSellController.get().status();
            color = 0x55FF55;
        } else {
            line = AutoSellController.get().status();
            color = 0xFFFF55;
        }

        text(graphics, line, listX, navY + 28, color);

        if (selectedItem != null) {
            text(graphics, Component.translatable("screen.donuttweks.autosell.selected", selectedName),
                    listX, navY + 40, 0xFFFFFF);
        }

        text(graphics, Component.translatable("screen.donuttweks.autosell.split_hint"),
                listX, navY + 52, 0x808080);
    }

    private void text(Object graphics, Component text, int x, int y, int color) {
        //? if >=26.1 {
        ((GuiGraphicsExtractor) graphics).text(this.font, text, x, y, color, false);
        //?} else {
        /*((GuiGraphics) graphics).drawString(this.font, text, x, y, color, false);
        *///?}
    }

    private void fill(Object graphics, int minX, int minY, int maxX, int maxY, int color) {
        //? if >=26.1 {
        ((GuiGraphicsExtractor) graphics).fill(minX, minY, maxX, maxY, color);
        //?} else {
        /*((GuiGraphics) graphics).fill(minX, minY, maxX, maxY, color);
        *///?}
    }

    private void item(Object graphics, ItemStack stack, int x, int y) {
        //? if >=26.1 {
        ((GuiGraphicsExtractor) graphics).item(stack, x, y);
        //?} else {
        /*((GuiGraphics) graphics).renderItem(stack, x, y);
        *///?}
    }

    /** A distinct item type in the inventory. */
    private record Entry(Item item, ItemStack icon, String name, int count) {
    }
}
