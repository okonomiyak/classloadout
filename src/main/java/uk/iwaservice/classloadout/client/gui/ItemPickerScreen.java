package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import uk.iwaservice.classloadout.ItemResolver;
import uk.iwaservice.classloadout.client.LoadoutClientData;
import uk.iwaservice.classloadout.compat.TaczCompat;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Generic item-grid picker used for icon/slot assignment. With no
 * restriction it lists the full {@link ItemCatalog} (used by the OP-only
 * preset editor, which is trusted with any item, and the ammo grant popup's
 * ammo picker) - and, unrestricted only, shows the same mod-category tabs as
 * {@link WhitelistEditorScreen}, plus an "Add Held Item" button (registers
 * the OP's held item as a reusable variant via {@code /class whitelist
 * register_held}, OP-only server-side, and immediately picks it - same idea
 * as {@link WhitelistEditorScreen}'s and {@link AmmoGrantScreen}'s own Add
 * Held Item buttons), so a specific TACZ ammo type or an exact NBT-bearing
 * item is easy to find or add among everything else; when constructed with a
 * {@code restrictTo} set it shows only those items with no category tabs or
 * Add Held Item button (used by the player-facing loadout screen, restricted
 * to that slot's OP-curated whitelist - an empty set means nothing is
 * assignable yet; letting a non-OP player self-register a variant here would
 * be pointless since the server-side command is OP-gated and the resulting
 * id wouldn't be whitelisted anyway). No server round trip for browsing
 * either way: the item registry is already fully populated on the client
 * after login. Cell 0 is a fixed "none" entry that reports
 * {@code minecraft:air}, the sentinel the save/assign commands treat as
 * "unset".
 */
public class ItemPickerScreen extends Screen {

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

    private final Screen parent;
    private final Consumer<ResourceLocation> onPick;
    @Nullable
    private final List<ResourceLocation> restrictTo;
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
        boolean showCategories = restrictTo == null;
        panelWidth = Math.max(PAD * 2 + COLS * CELL, showCategories ? 260 : 0);
        panelHeight = Math.min(showCategories ? 306 : 280, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        allItems = restrictTo != null ? restrictTo : ItemCatalog.all();

        int searchY = panelTop + HEADER_H + 4;
        if (showCategories) {
            int catCount = ItemCatalog.Category.values().length + 1; // +1 for the "all" tab
            int catWidth = (panelWidth - 2 * PAD) / catCount;
            int cx = panelLeft + PAD;
            Button allBtn = Button.builder(Component.translatable("classloadout.gui.category_all"), b -> selectCategory(null))
                    .bounds(cx, searchY, catWidth, CAT_H).build();
            allBtn.active = selectedCategory != null;
            addRenderableWidget(allBtn);
            cx += catWidth;
            for (ItemCatalog.Category category : ItemCatalog.Category.values()) {
                ItemCatalog.Category captured = category;
                Button b = Button.builder(Component.translatable("classloadout.gui.category_" + category.name().toLowerCase(Locale.ROOT)),
                                btn -> selectCategory(captured))
                        .bounds(cx, searchY, catWidth, CAT_H).build();
                b.active = selectedCategory != category;
                addRenderableWidget(b);
                cx += catWidth;
            }
            searchY += CAT_H + 6;
        }

        String previousQuery = search != null ? search.getValue() : "";
        search = new EditBox(this.font, panelLeft + PAD, searchY,
                panelWidth - 2 * PAD, SEARCH_H, Component.translatable("classloadout.gui.item_search"));
        search.setHint(Component.translatable("classloadout.gui.item_search"));
        search.setValue(previousQuery);
        search.setResponder(s -> updateShown());
        addRenderableWidget(search);
        setInitialFocus(search);

        gridLeft = panelLeft + PAD;
        gridTop = searchY + SEARCH_H + 6;
        gridHeight = panelTop + panelHeight - PAD - 24 - gridTop;

        int bottomY = panelTop + panelHeight - PAD - 20;
        if (showCategories) {
            int cancelWidth = (panelWidth - 2 * PAD - 4) * 2 / 3;
            int addHeldWidth = panelWidth - 2 * PAD - 4 - cancelWidth;
            addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.cancel"),
                            b -> minecraft.setScreen(parent))
                    .bounds(panelLeft + PAD, bottomY, cancelWidth, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.whitelist_add_held"),
                            b -> addHeldItem())
                    .bounds(panelLeft + PAD + cancelWidth + 4, bottomY, addHeldWidth, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.cancel"),
                            b -> minecraft.setScreen(parent))
                    .bounds(panelLeft + PAD, bottomY, panelWidth - 2 * PAD, 20).build());
        }

        updateShown();
    }

    /** Registers the OP's held item as a reusable variant and immediately picks it, same as clicking a catalog cell. */
    private void addHeldItem() {
        UUID id = UUID.randomUUID();
        command("class whitelist register_held " + id);
        onPick.accept(new ResourceLocation("classloadout", "variant_" + id));
        minecraft.setScreen(parent);
    }

    private void command(String cmd) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(cmd);
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
        if (restrictTo == null && HotbarBar.mouseClicked(minecraft, mouseX, mouseY)) {
            return true;
        }
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Don't hijack digit keys while the search box is focused (e.g. typing "9x19").
        if (restrictTo == null && !search.isFocused() && HotbarBar.keyPressed(minecraft, keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
            ItemStack resolved = index == 0 ? null : ItemResolver.resolve(shown.get(index - 1), LoadoutClientData.getItemVariants());
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
            if (hoveredStack.getItem() == Items.BARRIER) {
                graphics.renderTooltip(this.font, Component.translatable("classloadout.gui.item_none"), hoveredX, hoveredY);
            } else {
                List<Component> lines = new ArrayList<>(hoveredStack.getTooltipLines(
                        this.minecraft.player, TooltipFlag.Default.NORMAL));
                lines.addAll(TaczCompat.describeGunTooltip(hoveredStack));
                lines.addAll(TaczCompat.describeAmmoBoxTooltip(hoveredStack));
                lines.addAll(TaczCompat.describeAmmoTooltip(hoveredStack));
                graphics.renderTooltip(this.font, lines, Optional.empty(), hoveredX, hoveredY);
            }
        }

        if (maxScroll > 0) {
            int trackX = gridLeft + COLS * CELL + 4;
            graphics.fill(trackX, gridTop, trackX + 2, gridTop + gridHeight, 0x40FFFFFF);
            int thumbHeight = Math.max(10, gridHeight * gridHeight / Math.max(1, gridHeight + maxScroll));
            int thumbY = gridTop + (gridHeight - thumbHeight) * scrollOffset / Math.max(1, maxScroll);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xB0FFFFFF);
        }

        if (restrictTo == null) {
            HotbarBar.render(graphics, this.minecraft);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
