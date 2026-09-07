package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
import java.util.List;
import java.util.Map;

/**
 * Player-facing screen (everyone, no permission check - like {@link LoadoutScreen}): shows the
 * local player's point balance and a grid of every OP-priced item (see {@code
 * LoadoutManager#itemPrices} / {@link PriceEditorScreen}). Clicking an unowned, affordable item
 * spends points to permanently unlock it ({@code /class buy}, see {@code LoadoutManager#buy}) -
 * an owned item is outlined green and does nothing on click, an unaffordable one is outlined red
 * and does nothing either (the server would reject it anyway; this just avoids a round trip for
 * an obviously-doomed click). Buying doesn't equip anything - go to {@link LoadoutScreen}
 * afterward to assign the now-unlocked item to a whitelisted slot.
 *
 * <p>Browsable by loadout slot (Main/Sidearm/.../Boots, two rows of tabs, plus "All") rather than
 * mod namespace - what matters when shopping is which slot an item can go into, and a priced item
 * shows under every slot it's whitelisted for. Unlike {@link PriceEditorScreen}'s mod-category
 * tabs (which mirror {@link WhitelistEditorScreen}'s pool-browsing concern), this is a different
 * axis entirely, so it isn't reused here.
 */
public class ShopScreen extends Screen {
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
    private static final int TAB_H = 20;
    private static final int CELL = 24;
    private static final int COLS = 8;
    private static final int ICON = 16;
    /** Tab row 1: "All" plus the six gear slots. Row 2: the four armor slots, left-aligned under the first four columns of row 1. */
    private static final int ROW1_TABS = LoadoutSlot.values().length / 2 + 2;

    private static final int COLOR_PANEL_BG = 0xF4222222;
    private static final int COLOR_HEADER_BG = 0xFF1F2333;
    private static final int COLOR_OUTLINE = 0xFF454A66;
    private static final int COLOR_HOVER = 0x60FFFFFF;
    private static final int COLOR_OWNED = 0xFF55FF55;
    private static final int COLOR_UNAFFORDABLE = 0xFFFF5555;

    private final Screen returnTo;

    @Nullable
    private LoadoutSlot selectedSlot = null;
    private List<ResourceLocation> allShopItems = List.of();
    private List<ResourceLocation> shopItems = List.of();

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

    public ShopScreen(Screen returnTo) {
        super(Component.translatable("classloadout.gui.shop_title"));
        this.returnTo = returnTo;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(returnTo);
    }

    @Override
    protected void init() {
        if (savedBlur < 0) savedBlur = GuiBlurFix.suppress();
        panelWidth = Math.min(Math.max(PAD * 2 + COLS * CELL, 400), this.width - 16);
        panelHeight = Math.min(310, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        dataRevision = LoadoutClientData.getRevision();

        allShopItems = new ArrayList<>(LoadoutClientData.getPrices().keySet());

        int tabWidth = (panelWidth - 2 * PAD) / ROW1_TABS;
        int row1Y = panelTop + HEADER_H + PAD + 4;
        int row2Y = row1Y + TAB_H + 4;
        int tx = panelLeft + PAD;
        Button allBtn = Button.builder(Component.translatable("classloadout.gui.category_all"), btn -> selectSlot(null))
                .bounds(tx, row1Y, tabWidth, TAB_H).build();
        allBtn.active = selectedSlot != null;
        addRenderableWidget(allBtn);
        tx += tabWidth;
        LoadoutSlot[] slots = LoadoutSlot.values();
        for (int i = 0; i < slots.length; i++) {
            LoadoutSlot slot = slots[i];
            boolean secondRow = i >= ROW1_TABS - 1;
            if (i == ROW1_TABS - 1) {
                tx = panelLeft + PAD;
            }
            Button b = Button.builder(Component.translatable("classloadout.gui.slot_" + slot.key()),
                            btn -> selectSlot(slot))
                    .bounds(tx, secondRow ? row2Y : row1Y, tabWidth, TAB_H).build();
            b.active = selectedSlot != slot;
            addRenderableWidget(b);
            tx += tabWidth;
        }

        gridLeft = panelLeft + PAD;
        gridTop = row2Y + TAB_H + 8;
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

    private void selectSlot(@Nullable LoadoutSlot slot) {
        if (slot != selectedSlot) {
            selectedSlot = slot;
            this.init(this.minecraft, this.width, this.height);
        }
    }

    private void updateShown() {
        if (selectedSlot == null) {
            shopItems = allShopItems;
        } else {
            List<ResourceLocation> whitelist = LoadoutClientData.getWhitelist(selectedSlot);
            List<ResourceLocation> filtered = new ArrayList<>();
            for (ResourceLocation loc : allShopItems) {
                if (whitelist.contains(loc)) {
                    filtered.add(loc);
                }
            }
            shopItems = filtered;
        }
        int rows = (shopItems.size() + COLS - 1) / COLS;
        maxScroll = Math.max(0, rows * CELL - gridHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private int cellIndexAt(double mouseX, double mouseY) {
        if (mouseX < gridLeft || mouseX >= gridLeft + COLS * CELL || mouseY < gridTop || mouseY >= gridTop + gridHeight) {
            return -1;
        }
        int col = (int) ((mouseX - gridLeft) / CELL);
        int row = (int) ((mouseY - gridTop + scrollOffset) / CELL);
        int index = row * COLS + col;
        return index < shopItems.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = cellIndexAt(mouseX, mouseY);
        if (index >= 0) {
            ResourceLocation item = shopItems.get(index);
            if (!LoadoutClientData.isPurchased(item)
                    && LoadoutClientData.getPoints() >= LoadoutClientData.getPrices().getOrDefault(item, 0)) {
                command("class buy " + item);
            }
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

        Component balance = Component.translatable("classloadout.gui.shop_balance", LoadoutClientData.getPoints());
        graphics.drawString(this.font, balance, r - PAD - this.font.width(balance), t + 8, 0xFFFFD700);

        if (allShopItems.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("classloadout.gui.shop_empty"),
                    l + PAD, gridTop, 0xA0A8C0);
        }

        Map<ResourceLocation, Integer> prices = LoadoutClientData.getPrices();
        int points = LoadoutClientData.getPoints();

        graphics.enableScissor(gridLeft, gridTop, gridLeft + COLS * CELL, gridTop + gridHeight);
        ItemStack hoveredStack = null;
        int hoveredX = 0;
        int hoveredY = 0;
        ResourceLocation hoveredItem = null;
        for (int index = 0; index < shopItems.size(); index++) {
            int col = index % COLS;
            int row = index / COLS;
            int x = gridLeft + col * CELL;
            int y = gridTop + row * CELL - scrollOffset;
            if (y + CELL <= gridTop || y >= gridTop + gridHeight) {
                continue;
            }
            ResourceLocation loc = shopItems.get(index);
            int price = prices.getOrDefault(loc, 0);
            boolean owned = LoadoutClientData.isPurchased(loc);
            boolean affordable = points >= price;
            boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL
                    && mouseY >= gridTop && mouseY < gridTop + gridHeight;
            int outline = owned ? COLOR_OWNED : (affordable ? COLOR_OUTLINE : COLOR_UNAFFORDABLE);
            graphics.renderOutline(x, y, CELL, CELL, outline);
            if (hovered) {
                graphics.fill(x, y, x + CELL, y + CELL, COLOR_HOVER);
            }
            ItemStack resolved = ItemResolver.resolve(loc, LoadoutClientData.getItemVariants());
            ItemStack stack = resolved != null ? resolved : new ItemStack(Items.BARRIER);
            graphics.renderItem(stack, x + (CELL - ICON) / 2, y + 2);
            Component priceText = owned ? Component.translatable("classloadout.gui.shop_owned")
                    : Component.literal(Integer.toString(price));
            int priceColor = owned ? COLOR_OWNED : (affordable ? 0xFFFFFFFF : COLOR_UNAFFORDABLE);
            graphics.drawString(this.font, priceText, x + (CELL - this.font.width(priceText)) / 2, y + CELL - 9,
                    priceColor, true);
            if (hovered) {
                hoveredStack = stack;
                hoveredX = mouseX;
                hoveredY = mouseY;
                hoveredItem = loc;
            }
        }
        graphics.disableScissor();

        if (hoveredStack != null) {
            int price = prices.getOrDefault(hoveredItem, 0);
            Component name = hoveredStack.getHoverName().copy().append(LoadoutClientData.isPurchased(hoveredItem)
                    ? Component.translatable("classloadout.gui.shop_owned_tooltip")
                    : Component.translatable("classloadout.gui.shop_price_tooltip", price));
            graphics.renderTooltip(this.font, name, hoveredX, hoveredY);
        }

        if (maxScroll > 0) {
            int trackX = gridLeft + COLS * CELL + 4;
            graphics.fill(trackX, gridTop, trackX + 2, gridTop + gridHeight, 0x40FFFFFF);
            int thumbHeight = Math.max(10, gridHeight * gridHeight / Math.max(1, gridHeight + maxScroll));
            int thumbY = gridTop + (gridHeight - thumbHeight) * scrollOffset / Math.max(1, maxScroll);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xB0FFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
