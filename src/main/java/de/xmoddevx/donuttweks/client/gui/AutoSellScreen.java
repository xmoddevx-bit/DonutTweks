package de.xmoddevx.donuttweks.client.gui;

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
 * <p>Pick one item type from the inventory, enter a price and how many stacks
 * are sold per batch, then press "Start". The screen closes and
 * {@link AutoSellController} starts sending {@code /ah sell <price>}.</p>
 */
public class AutoSellScreen extends Screen {
    private static final int COLUMNS = 2;
    private static final int ROWS = 6;
    private static final int PAGE_SIZE = COLUMNS * ROWS;
    private static final int CELL_HEIGHT = 21;
    private static final int DEFAULT_PANEL_WIDTH = 176;
    private static final int MIN_PANEL_WIDTH = 140;
    private static final int CONTENT_HEIGHT = 182;

    private final List<Entry> entries = new ArrayList<>();

    private int page;
    private Item selectedItem;
    private String selectedName = "";
    private Component hint;

    private EditBox priceField;
    private EditBox stacksField;
    private EditBox intervalField;

    private String priceText = "1000";
    private String stacksText = "1";
    private String intervalText = "1";

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
        stacksField.setMaxLength(3);
        stacksField.setValue(stacksText);
        stacksField.setResponder(value -> stacksText = value);
        addRenderableWidget(stacksField);

        intervalField = new EditBox(this.font, panelX, listY + 98, panelWidth, 20,
                Component.translatable("screen.donuttweks.autosell.interval"));
        intervalField.setMaxLength(3);
        intervalField.setValue(intervalText);
        intervalField.setResponder(value -> intervalText = value);
        addRenderableWidget(intervalField);

        int half = (panelWidth - 4) / 2;

        addRenderableWidget(Button.builder(Component.translatable("screen.donuttweks.autosell.start"),
                        button -> startSelling())
                .bounds(panelX, listY + 130, half, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("screen.donuttweks.autosell.stop"),
                        button -> AutoSellController.get().stop())
                .bounds(panelX + half + 4, listY + 130, half, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("screen.donuttweks.autosell.close"),
                        button -> onClose())
                .bounds(panelX, listY + 156, panelWidth, 20)
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

        if (selectedItem == null) {
            hint = Component.translatable("screen.donuttweks.autosell.invalid_item");
            return;
        }
        if (this.minecraft != null && this.minecraft.player != null
                && AutoSellController.countItems(this.minecraft.player.getInventory(), selectedItem) <= 0) {
            hint = Component.translatable("screen.donuttweks.autosell.invalid_item");
            return;
        }

        int price = parseInt(priceText, 0);
        if (price <= 0) {
            hint = Component.translatable("screen.donuttweks.autosell.invalid_price");
            return;
        }

        int stacks = Math.max(1, parseInt(stacksText, 1));
        int seconds = Math.max(1, parseInt(intervalText, 1));

        AutoSellController.get().start(selectedItem, selectedName, price, stacks, seconds * 20);
        onClose();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
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
                panelX, listY + 44, 0xA0A0A0);
        text(graphics, Component.translatable("screen.donuttweks.autosell.interval"),
                panelX, listY + 86, 0xA0A0A0);

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
