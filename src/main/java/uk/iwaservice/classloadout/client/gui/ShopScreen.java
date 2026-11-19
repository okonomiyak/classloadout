package uk.iwaservice.classloadout.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import uk.iwaservice.classloadout.client.GuiBlurFix;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import uk.iwaservice.classloadout.client.LoadoutClientData;
import uk.iwaservice.classloadout.loadout.LoadoutSlot;
import uk.iwaservice.classloadout.compat.ItemZoomerCompat;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * <p>Styled like {@link ItemPickerScreen}: a bottom-anchored bar (no full-screen dim) with the
 * title and balance on top, a row of filter chips, and a scrollable grid of cards (item plus
 * price line). Browsable by loadout slot (Main/Sidearm/.../Boots, plus "All") rather than mod
 * namespace - what matters when shopping is which slot an item can go into, and a priced item
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

    /** Screen.render calls this first on 1.21.1 (blur + full-screen dim); the bar draws its own gradient in render(), so draw nothing here. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }


    private static final int MARGIN = 8;
    private static final int PAD = 6;
    private static final int CARD_W = 28;
    private static final int CARD_H = 34;
    private static final int GAP = 2;
    private static final int PITCH_X = CARD_W + GAP;
    private static final int PITCH_Y = CARD_H + GAP;
    private static final int CHIP_H = 16;
    private static final int BTN_H = 24;

    private static final int COLOR_TEXT = FlatButton.COLOR_TEXT;
    private static final int COLOR_TEXT_DIM = FlatButton.COLOR_TEXT_DIM;
    private static final int COLOR_CARD_LINE = FlatButton.COLOR_CARD_LINE;
    private static final int COLOR_OWNED = 0xFF55FF55;
    private static final int COLOR_UNAFFORDABLE = 0xFFFF5555;

    /** One filter chip; {@code x}/{@code w} are laid out in init(). */
    private record Chip(Component label, boolean active, Runnable action, int x, int y, int w) {}

    private final Screen returnTo;
    private final List<Chip> chips = new ArrayList<>();

    @Nullable
    private LoadoutSlot selectedSlot = null;
    private List<ResourceLocation> allShopItems = List.of();
    private List<ResourceLocation> shopItems = List.of();

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int chipY;
    /** Bottom edge of the (two-row) chip area. */
    private int chipBottom;
    private int gridLeft;
    private int gridTop;
    private int gridWidth;
    private int gridHeight;
    private int cols = 1;
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
        // Left of the bar is left free for Item Zoomer's zoomed item (see FlatButton#zoomMargin).
        panelLeft = FlatButton.zoomMargin(this.width, 300);
        panelWidth = this.width - panelLeft - MARGIN;
        panelHeight = Math.max(150, this.height * 6 / 10);
        panelHeight = Math.min(panelHeight, this.height - 2 * MARGIN);
        panelTop = this.height - MARGIN - panelHeight;
        int right = panelLeft + panelWidth;
        dataRevision = LoadoutClientData.getRevision();

        allShopItems = new ArrayList<>(LoadoutClientData.getPrices().keySet());

        chips.clear();
        chipY = panelTop + 20;
        int cx = panelLeft + PAD;
        int cy = chipY;
        cx = addChip(cx, cy, Component.translatable("classloadout.gui.category_all"), selectedSlot == null, () -> selectSlot(null));
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            if (slot == LoadoutSlot.HELMET) { // armor chips wrap to a second row
                cx = panelLeft + PAD;
                cy += CHIP_H + 2;
            }
            cx = addChip(cx, cy, Component.translatable("classloadout.gui.slot_" + slot.key()), selectedSlot == slot, () -> selectSlot(slot));
        }
        chipBottom = cy + CHIP_H;

        int sepY = chipBottom + 4;
        int btnY = panelTop + panelHeight - PAD - BTN_H;
        gridLeft = panelLeft + PAD;
        gridTop = sepY + 4;
        gridWidth = panelWidth - 2 * PAD - 6;
        gridHeight = btnY - 4 - gridTop;
        cols = Math.max(1, (gridWidth + GAP) / PITCH_X);

        int btnW = Math.max(120, (panelWidth - 2 * PAD) / 3);
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.close"),
                b -> onClose(), right - PAD - btnW, btnY, btnW, BTN_H));

        updateShown();
    }

    private int addChip(int x, int y, Component label, boolean active, Runnable action) {
        int w = this.font.width(label) + 12;
        chips.add(new Chip(label, active, action, x, y, w));
        return x + w + 3;
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
        int rows = (shopItems.size() + cols - 1) / cols;
        maxScroll = Math.max(0, rows * PITCH_Y - GAP - gridHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private int cellIndexAt(double mouseX, double mouseY) {
        if (mouseX < gridLeft || mouseX >= gridLeft + cols * PITCH_X || mouseY < gridTop || mouseY >= gridTop + gridHeight) {
            return -1;
        }
        int dx = (int) (mouseX - gridLeft);
        int dy = (int) (mouseY - gridTop + scrollOffset);
        if (dx % PITCH_X >= CARD_W || dy % PITCH_Y >= CARD_H) {
            return -1;
        }
        int index = (dy / PITCH_Y) * cols + dx / PITCH_X;
        return index < shopItems.size() ? index : -1;
    }

    private boolean chipsHit(double mouseX, double mouseY) {
        int clipRight = panelLeft + panelWidth - PAD;
        if (mouseX >= clipRight) {
            return false;
        }
        for (Chip c : chips) {
            if (mouseX >= c.x() && mouseX < c.x() + c.w() && mouseY >= c.y() && mouseY < c.y() + CHIP_H) {
                c.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && chipsHit(mouseX, mouseY)) {
            return true;
        }
        int index = cellIndexAt(mouseX, mouseY);
        if (index >= 0) {
            ResourceLocation item = shopItems.get(index);
            if (button == 1 && !LoadoutClientData.isPurchased(item)) {
                command("class trial " + item);
                return true;
            }
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
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * PITCH_Y)));
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
        // No full-screen dim: the world stays visible, only a gradient behind the bar.
        graphics.fillGradient(0, panelTop - 30, this.width, this.height, 0x00000000, 0xC0000000);

        int l = panelLeft;
        int t = panelTop;
        int r = l + panelWidth;
        graphics.drawString(this.font, this.title, l + PAD, t + 6, COLOR_TEXT_DIM, false);
        FlatButton.drawBrackets(graphics, l, t, panelWidth, panelHeight);

        Component balance = Component.translatable("classloadout.gui.shop_balance", LoadoutClientData.getPoints());
        graphics.drawString(this.font, balance, r - PAD - this.font.width(balance), t + 6, 0xFFFFD700, false);

        // Filter chips, clipped to the bar's inner width.
        graphics.enableScissor(l + PAD, chipY, r - PAD, chipBottom);
        for (Chip c : chips) {
            boolean hover = mouseX >= c.x() && mouseX < c.x() + c.w() && mouseY >= c.y() && mouseY < c.y() + CHIP_H;
            graphics.fill(c.x(), c.y(), c.x() + c.w(), c.y() + CHIP_H, FlatButton.COLOR_CARD_BG);
            graphics.renderOutline(c.x(), c.y(), c.w(), CHIP_H, c.active() || hover ? COLOR_TEXT : COLOR_CARD_LINE);
            graphics.drawString(this.font, c.label(), c.x() + 6, c.y() + 4, c.active() ? COLOR_TEXT : COLOR_TEXT_DIM, false);
        }
        graphics.disableScissor();
        graphics.fill(l + PAD, chipBottom + 3, r - PAD, chipBottom + 4, COLOR_CARD_LINE);

        if (allShopItems.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("classloadout.gui.shop_empty"),
                    l + PAD, gridTop, COLOR_TEXT_DIM, false);
        }

        Map<ResourceLocation, Integer> prices = LoadoutClientData.getPrices();
        int points = LoadoutClientData.getPoints();

        graphics.enableScissor(gridLeft, gridTop, gridLeft + gridWidth, gridTop + gridHeight);
        ItemStack hoveredStack = null;
        int hoveredX = 0;
        int hoveredY = 0;
        ResourceLocation hoveredItem = null;
        for (int index = 0; index < shopItems.size(); index++) {
            int x = gridLeft + (index % cols) * PITCH_X;
            int y = gridTop + (index / cols) * PITCH_Y - scrollOffset;
            if (y + CARD_H <= gridTop || y >= gridTop + gridHeight) {
                continue;
            }
            ResourceLocation loc = shopItems.get(index);
            int price = prices.getOrDefault(loc, 0);
            boolean owned = LoadoutClientData.isPurchased(loc);
            boolean affordable = points >= price;
            boolean hovered = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H
                    && mouseY >= gridTop && mouseY < gridTop + gridHeight;
            int outline = hovered ? COLOR_TEXT : owned ? COLOR_OWNED : affordable ? COLOR_CARD_LINE : COLOR_UNAFFORDABLE;
            graphics.fill(x, y, x + CARD_W, y + CARD_H, FlatButton.COLOR_CARD_BG);
            graphics.renderOutline(x, y, CARD_W, CARD_H, outline);
            ItemStack resolved = LoadoutClientData.displayStack(loc);
            ItemStack stack = resolved != null ? resolved : new ItemStack(Items.BARRIER);
            graphics.renderItem(stack, x + (CARD_W - 16) / 2, y + 3);
            Component priceText = owned ? Component.translatable("classloadout.gui.shop_owned")
                    : Component.literal(Integer.toString(price));
            int priceColor = owned ? COLOR_OWNED : (affordable ? COLOR_TEXT : COLOR_UNAFFORDABLE);
            graphics.drawString(this.font, priceText, x + (CARD_W - this.font.width(priceText)) / 2, y + CARD_H - 11,
                    priceColor, true);
            if (hovered) {
                hoveredStack = stack;
                hoveredX = mouseX;
                hoveredY = mouseY;
                hoveredItem = loc;
            }
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int trackX = gridLeft + gridWidth + 3;
            graphics.fill(trackX, gridTop, trackX + 2, gridTop + gridHeight, COLOR_CARD_LINE);
            int thumbHeight = Math.max(10, gridHeight * gridHeight / Math.max(1, gridHeight + maxScroll));
            int thumbY = gridTop + (gridHeight - thumbHeight) * scrollOffset / Math.max(1, maxScroll);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, COLOR_TEXT);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        ItemZoomerCompat.hover(this, hoveredStack, panelLeft, panelTop, panelWidth, panelHeight);
        if (hoveredStack != null) {
            int price = prices.getOrDefault(hoveredItem, 0);
            Component name = hoveredStack.getHoverName().copy().append(LoadoutClientData.isPurchased(hoveredItem)
                    ? Component.translatable("classloadout.gui.shop_owned_tooltip")
                    : Component.translatable("classloadout.gui.shop_price_tooltip", price));
            List<Component> lines = new ArrayList<>();
            lines.add(name);
            if (!LoadoutClientData.isPurchased(hoveredItem)) {
                lines.add(Component.translatable("classloadout.gui.shop_trial_hint").withStyle(ChatFormatting.GRAY));
            }
            graphics.renderTooltip(this.font, lines, Optional.empty(), hoveredX, hoveredY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
