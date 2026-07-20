package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Generic item-grid picker used by the class editor for icon/slot
 * assignment. Lists every registered item in {@link #ALLOWED_NAMESPACES}
 * (no server round trip - the item registry is already fully populated on
 * the client after login). Cell 0 is a fixed "none" entry that reports
 * {@code minecraft:air}, the sentinel the save command treats as "unset".
 */
public class ItemPickerScreen extends Screen {

    private static final Set<String> ALLOWED_NAMESPACES = Set.of("tacz", "superbwarfare", "minecraft");

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

    public ItemPickerScreen(Screen parent, Consumer<ResourceLocation> onPick) {
        super(Component.translatable("classloadout.gui.item_picker_title"));
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        panelWidth = PAD * 2 + COLS * CELL;
        panelHeight = Math.min(280, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        if (allItems.isEmpty()) {
            allItems = buildItemList();
        }

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

    private static List<ResourceLocation> buildItemList() {
        List<ResourceLocation> list = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            ResourceLocation loc = ForgeRegistries.ITEMS.getKey(item);
            if (loc == null || item == Items.AIR || !ALLOWED_NAMESPACES.contains(loc.getNamespace())) {
                continue;
            }
            list.add(loc);
        }
        list.sort(Comparator.comparing(ResourceLocation::toString));
        return list;
    }

    private void updateShown() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            shown = allItems;
        } else {
            List<ResourceLocation> filtered = new ArrayList<>();
            for (ResourceLocation loc : allItems) {
                Item item = ForgeRegistries.ITEMS.getValue(loc);
                String displayName = item == null ? "" : new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
                if (loc.getPath().contains(query) || displayName.contains(query)) {
                    filtered.add(loc);
                }
            }
            shown = filtered;
        }
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
            ItemStack stack = index == 0 ? new ItemStack(Items.BARRIER) : new ItemStack(ForgeRegistries.ITEMS.getValue(shown.get(index - 1)));
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
