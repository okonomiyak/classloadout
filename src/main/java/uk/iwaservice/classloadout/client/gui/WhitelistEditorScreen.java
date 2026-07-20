package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import uk.iwaservice.classloadout.client.LoadoutClientData;
import uk.iwaservice.classloadout.loadout.LoadoutSlot;

import java.util.List;

/**
 * OP-only screen for curating, per slot, the set of items players are
 * allowed to self-assign via {@link LoadoutScreen}. Opened exclusively via
 * {@link uk.iwaservice.classloadout.network.OpenWhitelistEditorPacket} that
 * follows a successful {@code /class whitelist} command. Unlike
 * {@link ItemPickerScreen}, clicking a cell here toggles membership rather
 * than picking-and-closing, so the screen stays open for repeated edits.
 */
public class WhitelistEditorScreen extends Screen {

    private static final int PAD = 10;
    private static final int HEADER_H = 24;
    private static final int TAB_H = 20;
    private static final int SEARCH_H = 20;
    private static final int CELL = 20;
    private static final int COLS = 9;
    private static final int ICON = 16;

    private static final int COLOR_PANEL_BG = 0xF4222222;
    private static final int COLOR_HEADER_BG = 0xFF1F2333;
    private static final int COLOR_OUTLINE = 0xFF454A66;
    private static final int COLOR_HOVER = 0x60FFFFFF;

    private LoadoutSlot selectedSlot = LoadoutSlot.MAIN;
    private List<ResourceLocation> allItems = List.of();
    private List<ResourceLocation> shown = List.of();
    private EditBox search;

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int gridLeft;
    private int gridTop;
    private int gridHeight;
    private int scrollOffset;
    private int maxScroll;
    private int dataRevision = -1;

    public WhitelistEditorScreen() {
        super(Component.translatable("classloadout.gui.whitelist_editor_title"));
    }

    @Override
    protected void init() {
        panelWidth = Math.max(PAD * 2 + COLS * CELL, 260);
        panelHeight = Math.min(300, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        dataRevision = LoadoutClientData.getRevision();

        if (allItems.isEmpty()) {
            allItems = ItemCatalog.all();
        }

        int tabWidth = (panelWidth - 2 * PAD) / LoadoutSlot.values().length;
        int tabY = panelTop + HEADER_H + 4;
        int x = panelLeft + PAD;
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            LoadoutSlot captured = slot;
            Button b = Button.builder(Component.translatable("classloadout.gui.slot_" + slot.key()),
                            btn -> selectSlot(captured))
                    .bounds(x, tabY, tabWidth, TAB_H).build();
            b.active = slot != selectedSlot;
            addRenderableWidget(b);
            x += tabWidth;
        }

        String previousQuery = search != null ? search.getValue() : "";
        search = new EditBox(this.font, panelLeft + PAD, tabY + TAB_H + 6,
                panelWidth - 2 * PAD, SEARCH_H, Component.translatable("classloadout.gui.item_search"));
        search.setHint(Component.translatable("classloadout.gui.item_search"));
        search.setValue(previousQuery);
        search.setResponder(s -> updateShown());
        addRenderableWidget(search);

        gridLeft = panelLeft + PAD;
        gridTop = tabY + TAB_H + 6 + SEARCH_H + 6;
        gridHeight = panelTop + panelHeight - PAD - 24 - gridTop;

        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.close"), b -> onClose())
                .bounds(panelLeft + PAD, panelTop + panelHeight - PAD - 20, panelWidth - 2 * PAD, 20).build());

        updateShown();
    }

    @Override
    public void tick() {
        if (dataRevision != LoadoutClientData.getRevision()) {
            this.init(this.minecraft, this.width, this.height);
        }
    }

    private void selectSlot(LoadoutSlot slot) {
        if (slot != selectedSlot) {
            selectedSlot = slot;
            this.init(this.minecraft, this.width, this.height);
        }
    }

    private void updateShown() {
        shown = ItemCatalog.search(allItems, search.getValue());
        int rows = (shown.size() + COLS - 1) / COLS;
        int contentHeight = rows * CELL;
        maxScroll = Math.max(0, contentHeight - gridHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private int cellIndexAt(double mouseX, double mouseY) {
        if (mouseX < gridLeft || mouseX >= gridLeft + COLS * CELL || mouseY < gridTop || mouseY >= gridTop + gridHeight) {
            return -1;
        }
        int col = (int) ((mouseX - gridLeft) / CELL);
        int row = (int) ((mouseY - gridTop + scrollOffset) / CELL);
        int index = row * COLS + col;
        return index < shown.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = cellIndexAt(mouseX, mouseY);
        if (index >= 0) {
            ResourceLocation item = shown.get(index);
            boolean whitelisted = LoadoutClientData.getWhitelist(selectedSlot).contains(item);
            String cmd = "class whitelist " + (whitelisted ? "remove " : "add ") + selectedSlot.key() + " " + item;
            command(cmd);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (delta * CELL)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void command(String cmd) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(cmd);
        }
    }

    // --- rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int l = panelLeft;
        int t = panelTop;
        int r = l + panelWidth;
        int b = t + panelHeight;
        graphics.fill(l - 1, t - 1, r + 1, b + 1, 0x90000000);
        graphics.fill(l, t, r, b, COLOR_PANEL_BG);
        graphics.fill(l, t, r, t + HEADER_H, COLOR_HEADER_BG);
        graphics.renderOutline(l - 1, t - 1, panelWidth + 2, panelHeight + 2, COLOR_OUTLINE);
        graphics.drawString(this.font, this.title, l + PAD, t + 8, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTick);

        List<ResourceLocation> whitelist = LoadoutClientData.getWhitelist(selectedSlot);

        graphics.enableScissor(gridLeft, gridTop, gridLeft + COLS * CELL, gridTop + gridHeight);
        ItemStack hoveredStack = null;
        int hoveredX = 0;
        int hoveredY = 0;
        boolean hoveredWhitelisted = false;
        for (int index = 0; index < shown.size(); index++) {
            int col = index % COLS;
            int row = index / COLS;
            int x = gridLeft + col * CELL;
            int y = gridTop + row * CELL - scrollOffset;
            if (y + CELL <= gridTop || y >= gridTop + gridHeight) {
                continue;
            }
            ResourceLocation loc = shown.get(index);
            boolean whitelisted = whitelist.contains(loc);
            boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL
                    && mouseY >= gridTop && mouseY < gridTop + gridHeight;
            if (whitelisted) {
                graphics.fill(x, y, x + CELL, y + CELL, 0x4055FF55);
                graphics.renderOutline(x, y, CELL, CELL, 0xFF55FF55);
            }
            if (hovered) {
                graphics.fill(x, y, x + CELL, y + CELL, COLOR_HOVER);
            }
            ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(loc));
            graphics.renderItem(stack, x + (CELL - ICON) / 2, y + (CELL - ICON) / 2);
            if (hovered) {
                hoveredStack = stack;
                hoveredX = mouseX;
                hoveredY = mouseY;
                hoveredWhitelisted = whitelisted;
            }
        }
        graphics.disableScissor();

        if (hoveredStack != null) {
            Component name = hoveredStack.getHoverName().copy().append(hoveredWhitelisted
                    ? Component.translatable("classloadout.gui.whitelist_on")
                    : Component.translatable("classloadout.gui.whitelist_off"));
            graphics.renderTooltip(this.font, name, hoveredX, hoveredY);
        }

        if (maxScroll > 0) {
            int trackX = gridLeft + COLS * CELL + 4;
            graphics.fill(trackX, gridTop, trackX + 2, gridTop + gridHeight, 0x40FFFFFF);
            int thumbHeight = Math.max(10, gridHeight * gridHeight / Math.max(1, gridHeight + maxScroll));
            int thumbY = gridTop + (gridHeight - thumbHeight) * scrollOffset / Math.max(1, maxScroll);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xB0FFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
