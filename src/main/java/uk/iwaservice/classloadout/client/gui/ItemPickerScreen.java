package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import uk.iwaservice.classloadout.ItemResolver;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Generic item-grid picker used for icon/slot assignment. With no
 * restriction it lists the full {@link ItemCatalog} (used by the OP-only
 * preset editor, which is trusted with any item); when constructed with a
 * {@code restrictTo} set it shows only those items (used by the player-facing
 * loadout screen, restricted to that slot's OP-curated whitelist - an empty
 * set means nothing is assignable yet). No server round trip either way:
 * the item registry is already fully populated on the client after login.
 * Cell 0 is a fixed "none" entry that reports {@code minecraft:air}, the
 * sentinel the save/assign commands treat as "unset".
 */
public class ItemPickerScreen extends Screen {

    private static final int PAD = 10;
    private static final int HEADER_H = 24;
    private static final int SEARCH_H = 20;
    private static final int CELL = 20;
    private static final int COLS = 9;
    private static final int ICON = 16;

    private static final int COLOR_PANEL_BG = 0xF4222222;
    private static final int COLOR_HEADER_BG = 0xFF1F2333;
    private static final int COLOR_OUTLINE = 0xFF454A66;
    private static final int COLOR_HOVER = 0x60FFFFFF;

    private final Screen parent;
    private final Consumer<ResourceLocation> onPick;
    @Nullable
    private final List<ResourceLocation> restrictTo;

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

    /** Unrestricted: lists the full item catalog (OP preset editor). */
    public ItemPickerScreen(Screen parent, Consumer<ResourceLocation> onPick) {
        this(parent, onPick, null);
    }

    /** Restricted to {@code restrictTo} (player loadout screen, that slot's whitelist). */
    public ItemPickerScreen(Screen parent, Consumer<ResourceLocation> onPick, @Nullable List<ResourceLocation> restrictTo) {
        super(Component.translatable("classloadout.gui.item_picker_title"));
        this.parent = parent;
        this.onPick = onPick;
        this.restrictTo = restrictTo;
    }

    @Override
    protected void init() {
        panelWidth = PAD * 2 + COLS * CELL;
        panelHeight = Math.min(280, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        allItems = restrictTo != null ? restrictTo : ItemCatalog.all();

        search = new EditBox(this.font, panelLeft + PAD, panelTop + HEADER_H + 4,
                panelWidth - 2 * PAD, SEARCH_H, Component.translatable("classloadout.gui.item_search"));
        search.setHint(Component.translatable("classloadout.gui.item_search"));
        search.setResponder(s -> updateShown());
        addRenderableWidget(search);
        setInitialFocus(search);

        gridLeft = panelLeft + PAD;
        gridTop = panelTop + HEADER_H + 4 + SEARCH_H + 6;
        gridHeight = panelTop + panelHeight - PAD - 24 - gridTop;

        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.cancel"),
                        b -> minecraft.setScreen(parent))
                .bounds(panelLeft + PAD, panelTop + panelHeight - PAD - 20, panelWidth - 2 * PAD, 20).build());

        updateShown();
    }

    private void updateShown() {
        shown = ItemCatalog.search(allItems, search.getValue());
        int rows = (shown.size() + 1 + COLS - 1) / COLS; // +1 for the "none" cell
        int contentHeight = rows * CELL;
        maxScroll = Math.max(0, contentHeight - gridHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    /** Cell index 0 is "none"; index n>0 maps to shown.get(n - 1). Returns -1 if out of range. */
    private int cellIndexAt(double mouseX, double mouseY) {
        if (mouseX < gridLeft || mouseX >= gridLeft + COLS * CELL || mouseY < gridTop || mouseY >= gridTop + gridHeight) {
            return -1;
        }
        int col = (int) ((mouseX - gridLeft) / CELL);
        int row = (int) ((mouseY - gridTop + scrollOffset) / CELL);
        int index = row * COLS + col;
        return index <= shown.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = cellIndexAt(mouseX, mouseY);
        if (index == 0) {
            onPick.accept(new ResourceLocation("minecraft", "air"));
            minecraft.setScreen(parent);
            return true;
        } else if (index > 0) {
            onPick.accept(shown.get(index - 1));
            minecraft.setScreen(parent);
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

        if (restrictTo != null && restrictTo.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("classloadout.gui.whitelist_empty"),
                    l + PAD, gridTop + 4, 0xA0A8C0);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.enableScissor(gridLeft, gridTop, gridLeft + COLS * CELL, gridTop + gridHeight);
        ItemStack hoveredStack = null;
        int hoveredX = 0;
        int hoveredY = 0;
        int total = shown.size() + 1;
        for (int index = 0; index < total; index++) {
            int col = index % COLS;
            int row = index / COLS;
            int x = gridLeft + col * CELL;
            int y = gridTop + row * CELL - scrollOffset;
            if (y + CELL <= gridTop || y >= gridTop + gridHeight) {
                continue;
            }
            boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL
                    && mouseY >= gridTop && mouseY < gridTop + gridHeight;
            if (hovered) {
                graphics.fill(x, y, x + CELL, y + CELL, COLOR_HOVER);
            }
            ItemStack resolved = index == 0 ? null : ItemResolver.resolve(shown.get(index - 1));
            ItemStack stack = resolved != null ? resolved : new ItemStack(Items.BARRIER);
            graphics.renderItem(stack, x + (CELL - ICON) / 2, y + (CELL - ICON) / 2);
            if (hovered) {
                hoveredStack = stack;
                hoveredX = mouseX;
                hoveredY = mouseY;
            }
        }
        graphics.disableScissor();

        if (hoveredStack != null) {
            Component name = hoveredStack.getItem() == Items.BARRIER
                    ? Component.translatable("classloadout.gui.item_none")
                    : hoveredStack.getHoverName();
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
