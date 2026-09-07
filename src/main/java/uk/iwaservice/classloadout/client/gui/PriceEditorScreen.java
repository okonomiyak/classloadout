package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import uk.iwaservice.classloadout.client.GuiBlurFix;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import uk.iwaservice.classloadout.ItemResolver;
import uk.iwaservice.classloadout.client.LoadoutClientData;
import uk.iwaservice.classloadout.loadout.LoadoutSlot;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OP-only screen for curating shop prices: item -> point cost (see {@code
 * LoadoutManager#setPrice}), independent of any loadout slot's whitelist.
 * Opened exclusively via {@link uk.iwaservice.classloadout.network.OpenPriceEditorPacket}
 * that follows a successful {@code /class price} command. Structurally the
 * same single global grid as {@link SpawnKitEditorScreen}, but pulled from a
 * narrower pool: unlike the spawn kit/whitelist/protected-items editors,
 * which pick from the full {@link ItemCatalog}, this one only lists items
 * already whitelisted for at least one slot (plus anything already priced,
 * so a price survives being manageable here even if its whitelist entry was
 * since removed) - pricing an item nobody can ever whitelist-select doesn't
 * make sense. Left-click toggles an item in/out at a default price of 1,
 * right-click opens {@link PriceCountScreen} to fine-tune the price. Priced
 * items are what shows up in the player-facing {@link ShopScreen}.
 */
public class PriceEditorScreen extends Screen {
    private int savedBlur = -1;

    @Override
    public void removed() {
        if (savedBlur >= 0) {
            GuiBlurFix.restore(savedBlur);
            savedBlur = -1;
        }
        super.removed();
    }


    private static final int PAD = 10;
    private static final int HEADER_H = 24;
    private static final int CAT_H = 20;
    private static final int SEARCH_H = 20;
    private static final int CELL = 20;
    private static final int COLS = 9;
    private static final int ICON = 16;

    private static final int COLOR_PANEL_BG = 0xF4222222;
    private static final int COLOR_HEADER_BG = 0xFF1F2333;
    private static final int COLOR_OUTLINE = 0xFF454A66;
    private static final int COLOR_HOVER = 0x60FFFFFF;

    @Nullable
    private ItemCatalog.Category selectedCategory = null;
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

    /** Opened directly by {@code /class price} - closing exits the GUI entirely (no parent to return to). */
    public PriceEditorScreen() {
        this(null);
    }

    /** Opened via the class editor's nav bar - closing returns to {@code parent} instead of exiting. */
    public PriceEditorScreen(@Nullable Screen parent) {
        super(Component.translatable("classloadout.gui.price_editor_title"));
        this.parent = parent;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        if (savedBlur < 0) savedBlur = GuiBlurFix.suppress();
        panelWidth = Math.max(PAD * 2 + COLS * CELL, 260);
        panelHeight = Math.min(306, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        dataRevision = LoadoutClientData.getRevision();

        LinkedHashSet<ResourceLocation> pool = new LinkedHashSet<>();
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            pool.addAll(LoadoutClientData.getWhitelist(slot));
        }
        pool.addAll(LoadoutClientData.getPrices().keySet());
        allItems = new ArrayList<>(pool);

        int catY = panelTop + HEADER_H + 4;
        int catCount = ItemCatalog.Category.values().length + 1; // +1 for the "all" tab
        int catWidth = (panelWidth - 2 * PAD) / catCount;
        int cx = panelLeft + PAD;
        Button allBtn = Button.builder(Component.translatable("classloadout.gui.category_all"), btn -> selectCategory(null))
                .bounds(cx, catY, catWidth, CAT_H).build();
        allBtn.active = selectedCategory != null;
        addRenderableWidget(allBtn);
        cx += catWidth;
        for (ItemCatalog.Category category : ItemCatalog.Category.values()) {
            ItemCatalog.Category captured = category;
            Button b = Button.builder(Component.translatable("classloadout.gui.category_" + category.name().toLowerCase(Locale.ROOT)),
                            btn -> selectCategory(captured))
                    .bounds(cx, catY, catWidth, CAT_H).build();
            b.active = selectedCategory != category;
            addRenderableWidget(b);
            cx += catWidth;
        }

        int searchY = catY + CAT_H + 6;
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

    private void selectCategory(@Nullable ItemCatalog.Category category) {
        if (category != selectedCategory) {
            selectedCategory = category;
            this.init(this.minecraft, this.width, this.height);
        }
    }

    private void updateShown() {
        shown = ItemCatalog.search(ItemCatalog.byCategory(allItems, selectedCategory), search.getValue());
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
            if (button == 1) {
                minecraft.setScreen(new PriceCountScreen(this, item));
                return true;
            }
            boolean priced = LoadoutClientData.getPrices().containsKey(item);
            command("class price set " + item + " " + (priced ? 0 : 1));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * CELL)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void command(String cmd) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(cmd);
        }
    }

    // --- rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);

        int l = panelLeft;
        int t = panelTop;
        int r = l + panelWidth;
        int b = t + panelHeight;
        graphics.fill(l - 1, t - 1, r + 1, b + 1, 0x90000000);
        graphics.fill(l, t, r, b, COLOR_PANEL_BG);
        graphics.fill(l, t, r, t + HEADER_H, COLOR_HEADER_BG);
        graphics.renderOutline(l - 1, t - 1, panelWidth + 2, panelHeight + 2, COLOR_OUTLINE);
        graphics.drawString(this.font, this.title, l + PAD, t + 8, 0xFFFFFF);

        if (allItems.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("classloadout.gui.price_pool_empty"),
                    l + PAD, gridTop + 4, 0xA0A8C0);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        Map<ResourceLocation, Integer> prices = LoadoutClientData.getPrices();

        graphics.enableScissor(gridLeft, gridTop, gridLeft + COLS * CELL, gridTop + gridHeight);
        ItemStack hoveredStack = null;
        int hoveredX = 0;
        int hoveredY = 0;
        Integer hoveredPrice = null;
        for (int index = 0; index < shown.size(); index++) {
            int col = index % COLS;
            int row = index / COLS;
            int x = gridLeft + col * CELL;
            int y = gridTop + row * CELL - scrollOffset;
            if (y + CELL <= gridTop || y >= gridTop + gridHeight) {
                continue;
            }
            ResourceLocation loc = shown.get(index);
            Integer price = prices.get(loc);
            boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL
                    && mouseY >= gridTop && mouseY < gridTop + gridHeight;
            if (price != null) {
                graphics.fill(x, y, x + CELL, y + CELL, 0x40FFD700);
                graphics.renderOutline(x, y, CELL, CELL, 0xFFFFD700);
            }
            if (hovered) {
                graphics.fill(x, y, x + CELL, y + CELL, COLOR_HOVER);
            }
            ItemStack resolved = ItemResolver.resolve(loc, LoadoutClientData.getItemVariants());
            ItemStack stack = resolved != null ? resolved : new ItemStack(Items.BARRIER);
            graphics.renderItem(stack, x + (CELL - ICON) / 2, y + (CELL - ICON) / 2);
            if (price != null) {
                Component priceText = Component.literal(Integer.toString(price));
                graphics.drawString(this.font, priceText, x + CELL - this.font.width(priceText) - 1, y + CELL - 8,
                        0xFFFFFF, true);
            }
            if (hovered) {
                hoveredStack = stack;
                hoveredX = mouseX;
                hoveredY = mouseY;
                hoveredPrice = price;
            }
        }
        graphics.disableScissor();

        if (hoveredStack != null) {
            Component name = hoveredStack.getHoverName().copy().append(hoveredPrice != null
                    ? Component.translatable("classloadout.gui.price_on", hoveredPrice)
                    : Component.translatable("classloadout.gui.price_off"));
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
