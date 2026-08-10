package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import uk.iwaservice.classloadout.ItemResolver;
import uk.iwaservice.classloadout.client.LoadoutClientData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * OP-only screen for curating the global protected-items list: items on
 * this list survive the on-death inventory clear (see
 * {@code Config.CLEAR_INVENTORY_ON_DEATH}). Opened exclusively via
 * {@link uk.iwaservice.classloadout.network.OpenProtectedItemsEditorPacket}
 * that follows a successful {@code /class protect} command. Structurally
 * the same toggle-grid as {@link WhitelistEditorScreen} minus the per-slot
 * tabs (this is one global list, not per-slot) and the ammo-grant popup.
 *
 * <p>OP-registered "exact held item" variants (see
 * {@link uk.iwaservice.classloadout.loadout.LoadoutManager#addHeldItemToWhitelist})
 * are deliberately left out of the grid here: protection is matched by
 * base item type against the stack actually sitting in the player's
 * inventory (see {@code ServerEvents}), so a variant's synthetic id could
 * never match anything and would just be a dead toggle.
 */
public class ProtectedItemsEditorScreen extends Screen {

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

    @Nullable
    private final Screen parent;

    /** Opened directly by {@code /class protect} - closing exits the GUI entirely (no parent to return to). */
    public ProtectedItemsEditorScreen() {
        this(null);
    }

    /** Opened via the class editor's nav bar - closing returns to {@code parent} instead of exiting. */
    public ProtectedItemsEditorScreen(@Nullable Screen parent) {
        super(Component.translatable("classloadout.gui.protect_editor_title"));
        this.parent = parent;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        panelWidth = Math.max(PAD * 2 + COLS * CELL, 260);
        panelHeight = Math.min(280, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        dataRevision = LoadoutClientData.getRevision();

        if (allItems.isEmpty()) {
            List<ResourceLocation> variants = new ArrayList<>(LoadoutClientData.getItemVariants().keySet());
            allItems = new ArrayList<>();
            for (ResourceLocation loc : ItemCatalog.all()) {
                if (!variants.contains(loc)) {
                    allItems.add(loc);
                }
            }
        }

        int searchY = panelTop + HEADER_H + 6;
        String previousQuery = search != null ? search.getValue() : "";
        search = new EditBox(this.font, panelLeft + PAD, searchY,
                panelWidth - 2 * PAD, SEARCH_H, Component.translatable("classloadout.gui.item_search"));
        search.setHint(Component.translatable("classloadout.gui.item_search"));
        search.setValue(previousQuery);
        search.setResponder(s -> updateShown());
        addRenderableWidget(search);

        gridLeft = panelLeft + PAD;
        gridTop = searchY + SEARCH_H + 6;
        gridHeight = panelTop + panelHeight - PAD - 24 - gridTop;

        int closeWidth = (panelWidth - 2 * PAD - 4) * 2 / 3;
        int addHeldWidth = panelWidth - 2 * PAD - 4 - closeWidth;
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.close"), b -> onClose())
                .bounds(panelLeft + PAD, panelTop + panelHeight - PAD - 20, closeWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.whitelist_add_held"),
                        b -> protectHeldItem())
                .bounds(panelLeft + PAD + closeWidth + 4, panelTop + panelHeight - PAD - 20, addHeldWidth, 20).build());

        updateShown();
    }

    /**
     * Unlike {@link WhitelistEditorScreen}'s Add Held Item (which registers a full NBT-bearing
     * variant), this just protects the base item type currently in hand - protection is matched
     * by base type against the stack in the player's inventory (see class doc above), so a
     * synthetic variant id would never match anything and would just be a dead toggle here.
     */
    private void protectHeldItem() {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        ItemStack held = minecraft.player.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }
        ResourceLocation item = ForgeRegistries.ITEMS.getKey(held.getItem());
        if (item != null && !LoadoutClientData.getProtectedItems().contains(item)) {
            command("class protect add " + item);
        }
    }

    @Override
    public void tick() {
        if (dataRevision != LoadoutClientData.getRevision()) {
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
        if (HotbarBar.mouseClicked(minecraft, mouseX, mouseY)) {
            return true;
        }
        int index = cellIndexAt(mouseX, mouseY);
        if (index >= 0) {
            ResourceLocation item = shown.get(index);
            boolean protectedNow = LoadoutClientData.getProtectedItems().contains(item);
            command("class protect " + (protectedNow ? "remove " : "add ") + item);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Don't hijack digit keys while the search box is focused.
        if (!search.isFocused() && HotbarBar.keyPressed(minecraft, keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

        List<ResourceLocation> protectedItems = LoadoutClientData.getProtectedItems();

        graphics.enableScissor(gridLeft, gridTop, gridLeft + COLS * CELL, gridTop + gridHeight);
        ItemStack hoveredStack = null;
        int hoveredX = 0;
        int hoveredY = 0;
        boolean hoveredProtected = false;
        for (int index = 0; index < shown.size(); index++) {
            int col = index % COLS;
            int row = index / COLS;
            int x = gridLeft + col * CELL;
            int y = gridTop + row * CELL - scrollOffset;
            if (y + CELL <= gridTop || y >= gridTop + gridHeight) {
                continue;
            }
            ResourceLocation loc = shown.get(index);
            boolean protectedItem = protectedItems.contains(loc);
            boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL
                    && mouseY >= gridTop && mouseY < gridTop + gridHeight;
            if (protectedItem) {
                graphics.fill(x, y, x + CELL, y + CELL, 0x40FFAA00);
                graphics.renderOutline(x, y, CELL, CELL, 0xFFFFAA00);
            }
            if (hovered) {
                graphics.fill(x, y, x + CELL, y + CELL, COLOR_HOVER);
            }
            ItemStack resolved = ItemResolver.resolve(loc, LoadoutClientData.getItemVariants());
            ItemStack stack = resolved != null ? resolved : new ItemStack(Items.BARRIER);
            graphics.renderItem(stack, x + (CELL - ICON) / 2, y + (CELL - ICON) / 2);
            if (hovered) {
                hoveredStack = stack;
                hoveredX = mouseX;
                hoveredY = mouseY;
                hoveredProtected = protectedItem;
            }
        }
        graphics.disableScissor();

        if (hoveredStack != null) {
            Component name = hoveredStack.getHoverName().copy().append(hoveredProtected
                    ? Component.translatable("classloadout.gui.protect_on")
                    : Component.translatable("classloadout.gui.protect_off"));
            graphics.renderTooltip(this.font, name, hoveredX, hoveredY);
        }

        if (maxScroll > 0) {
            int trackX = gridLeft + COLS * CELL + 4;
            graphics.fill(trackX, gridTop, trackX + 2, gridTop + gridHeight, 0x40FFFFFF);
            int thumbHeight = Math.max(10, gridHeight * gridHeight / Math.max(1, gridHeight + maxScroll));
            int thumbY = gridTop + (gridHeight - thumbHeight) * scrollOffset / Math.max(1, maxScroll);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xB0FFFFFF);
        }

        HotbarBar.render(graphics, this.minecraft);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
