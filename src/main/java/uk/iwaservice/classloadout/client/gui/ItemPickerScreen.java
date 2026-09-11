package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import uk.iwaservice.classloadout.client.GuiBlurFix;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import uk.iwaservice.classloadout.ClassLoadoutMod;
import uk.iwaservice.classloadout.ItemResolver;
import uk.iwaservice.classloadout.client.LoadoutClientData;
import uk.iwaservice.classloadout.compat.TaczCompat;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 *
 * <p>Restricted mode only: held-item variants grouped into a folder (see
 * {@code /class whitelist set_folder}) collapse into one chest-icon tile,
 * same idea as {@link WhitelistEditorScreen}'s Held-items view - clicking
 * a tile pops a flyout of that folder's items (within {@code restrictTo}
 * only, not every variant in that folder server-wide) out beside it;
 * clicking a flyout item picks it and closes, same as any other cell.
 * Typing a search query flattens back to a plain list, ignoring folders.
 */
public class ItemPickerScreen extends Screen {
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
    private static final int FLYOUT_COLS = 4;
    /** No scrolling in the flyout (kept simple) - a folder past this size just shows its first items. */
    private static final int FLYOUT_MAX_ITEMS = 16;

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
    /** Restricted mode only: synthetic per-render tile id -> folder name (see class doc). Empty in unrestricted mode. */
    private Map<ResourceLocation, String> folderTiles = Map.of();
    /** Restricted mode only: folder name -> its items within {@code restrictTo}, populated alongside {@link #folderTiles}. */
    private Map<String, List<ResourceLocation>> folderContents = Map.of();
    @Nullable
    private String expandedFolder;
    private List<ResourceLocation> expandedFolderItems = List.of();
    private int expandedFlyoutX;
    private int expandedFlyoutY;
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
        if (savedBlur < 0) savedBlur = GuiBlurFix.suppress();
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
        onPick.accept(ResourceLocation.fromNamespaceAndPath("classloadout", "variant_" + id));
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
        List<ResourceLocation> base = ItemCatalog.byCategory(allItems, selectedCategory);
        String query = search.getValue();
        if (restrictTo != null && query.isBlank()) {
            base = buildTopLevel(base);
        } else {
            folderTiles = Map.of();
            folderContents = Map.of();
            expandedFolder = null;
            expandedFolderItems = List.of();
        }
        shown = ItemCatalog.search(base, query);
        int rows = (shown.size() + 1 + COLS - 1) / COLS; // +1 for the "none" cell
        int contentHeight = rows * CELL;
        maxScroll = Math.max(0, contentHeight - gridHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    /** One chest-icon tile per folder in use within {@code items} (see {@link #folderTiles}/{@link #folderContents}), followed by every item with no folder assigned. */
    private List<ResourceLocation> buildTopLevel(List<ResourceLocation> items) {
        Map<String, List<ResourceLocation>> byFolder = new LinkedHashMap<>();
        List<ResourceLocation> unfoldered = new ArrayList<>();
        for (ResourceLocation loc : items) {
            String folder = LoadoutClientData.getVariantFolder(loc);
            if (folder.isEmpty()) {
                unfoldered.add(loc);
            } else {
                byFolder.computeIfAbsent(folder, f -> new ArrayList<>()).add(loc);
            }
        }
        List<String> folderNames = new ArrayList<>(byFolder.keySet());
        Collections.sort(folderNames);
        Map<ResourceLocation, String> tiles = new LinkedHashMap<>();
        List<ResourceLocation> result = new ArrayList<>();
        for (int i = 0; i < folderNames.size(); i++) {
            ResourceLocation tileId = ResourceLocation.fromNamespaceAndPath(ClassLoadoutMod.MODID, "folder_" + i);
            tiles.put(tileId, folderNames.get(i));
            result.add(tileId);
        }
        result.addAll(unfoldered);
        folderTiles = tiles;
        folderContents = byFolder;
        return result;
    }

    /** While a folder's flyout is open, everything else in the main grid (including the "none" cell) is hidden - only the open folder's own tile stays visible/clickable, so it can still be clicked again to close. {@code totalIndex} is the grid index including the "none" cell at 0. */
    private boolean isMainGridCellVisible(int totalIndex) {
        if (expandedFolder == null) {
            return true;
        }
        if (totalIndex == 0) {
            return false;
        }
        return expandedFolder.equals(folderTiles.get(shown.get(totalIndex - 1)));
    }

    private void toggleFolderFlyout(String folder, int totalIndex) {
        if (folder.equals(expandedFolder)) {
            expandedFolder = null;
            expandedFolderItems = List.of();
            return;
        }
        expandedFolder = folder;
        List<ResourceLocation> items = folderContents.getOrDefault(folder, List.of());
        expandedFolderItems = items.size() > FLYOUT_MAX_ITEMS ? items.subList(0, FLYOUT_MAX_ITEMS) : items;
        int col = totalIndex % COLS;
        int row = totalIndex / COLS;
        expandedFlyoutX = gridLeft + col * CELL + CELL + 4;
        expandedFlyoutY = gridTop + row * CELL - scrollOffset;
    }

    /** Mirrors {@link #cellIndexAt}, but against the flyout's own (unscrolled) grid. -1 when no flyout is open. */
    private int flyoutIndexAt(double mouseX, double mouseY) {
        if (expandedFolder == null || expandedFolderItems.isEmpty()) {
            return -1;
        }
        int[] origin = flyoutOrigin();
        int rows = (expandedFolderItems.size() + FLYOUT_COLS - 1) / FLYOUT_COLS;
        int w = FLYOUT_COLS * CELL;
        int h = rows * CELL;
        if (mouseX < origin[0] || mouseX >= origin[0] + w || mouseY < origin[1] || mouseY >= origin[1] + h) {
            return -1;
        }
        int col = (int) ((mouseX - origin[0]) / CELL);
        int row = (int) ((mouseY - origin[1]) / CELL);
        int index = row * FLYOUT_COLS + col;
        return index < expandedFolderItems.size() ? index : -1;
    }

    /** Top-left of the flyout, clamped so it never draws off-screen. */
    private int[] flyoutOrigin() {
        int rows = (expandedFolderItems.size() + FLYOUT_COLS - 1) / FLYOUT_COLS;
        int w = FLYOUT_COLS * CELL;
        int h = rows * CELL;
        int fx = Math.max(4, Math.min(expandedFlyoutX, this.width - w - 4));
        int fy = Math.max(4, Math.min(expandedFlyoutY, this.height - h - 4));
        return new int[]{fx, fy};
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
        int flyoutIndex = flyoutIndexAt(mouseX, mouseY);
        if (flyoutIndex >= 0) {
            onPick.accept(expandedFolderItems.get(flyoutIndex));
            minecraft.setScreen(parent);
            return true;
        }
        int index = cellIndexAt(mouseX, mouseY);
        if (index >= 0 && !isMainGridCellVisible(index)) {
            return true;
        }
        if (index == 0) {
            onPick.accept(ResourceLocation.fromNamespaceAndPath("minecraft", "air"));
            minecraft.setScreen(parent);
            return true;
        } else if (index > 0) {
            ResourceLocation loc = shown.get(index - 1);
            String folder = folderTiles.get(loc);
            if (folder != null) {
                toggleFolderFlyout(folder, index);
                return true;
            }
            onPick.accept(loc);
            minecraft.setScreen(parent);
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

        if (restrictTo != null && restrictTo.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("classloadout.gui.whitelist_empty"),
                    l + PAD, gridTop + 4, 0xA0A8C0);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.enableScissor(gridLeft, gridTop, gridLeft + COLS * CELL, gridTop + gridHeight);
        ItemStack hoveredStack = null;
        int hoveredX = 0;
        int hoveredY = 0;
        String hoveredFolder = null;
        int hoveredFolderCount = 0;
        boolean hoveredBanned = false;
        int total = shown.size() + 1;
        for (int index = 0; index < total; index++) {
            int col = index % COLS;
            int row = index / COLS;
            int x = gridLeft + col * CELL;
            int y = gridTop + row * CELL - scrollOffset;
            if (y + CELL <= gridTop || y >= gridTop + gridHeight) {
                continue;
            }
            if (!isMainGridCellVisible(index)) {
                continue;
            }
            boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL
                    && mouseY >= gridTop && mouseY < gridTop + gridHeight;
            if (hovered) {
                graphics.fill(x, y, x + CELL, y + CELL, COLOR_HOVER);
            }
            ResourceLocation loc = index == 0 ? null : shown.get(index - 1);
            String folder = loc == null ? null : folderTiles.get(loc);
            if (folder != null) {
                boolean open = folder.equals(expandedFolder);
                if (open) {
                    graphics.fill(x, y, x + CELL, y + CELL, 0x4055AAFF);
                }
                graphics.renderItem(new ItemStack(Items.CHEST), x + (CELL - ICON) / 2, y + (CELL - ICON) / 2);
                graphics.renderOutline(x, y, CELL, CELL, open ? 0xFF55AAFF : COLOR_OUTLINE);
                if (hovered) {
                    hoveredFolder = folder;
                    hoveredFolderCount = folderContents.getOrDefault(folder, List.of()).size();
                    hoveredX = mouseX;
                    hoveredY = mouseY;
                }
                continue;
            }
            ItemStack resolved = loc == null ? null : ItemResolver.resolve(loc, LoadoutClientData.getItemVariants());
            ItemStack stack = resolved != null ? resolved : new ItemStack(Items.BARRIER);
            int iconX = x + (CELL - ICON) / 2;
            int iconY = y + (CELL - ICON) / 2;
            graphics.renderItem(stack, iconX, iconY);
            boolean banned = loc != null && LoadoutClientData.isBanned(loc);
            // Same tight ring as WhitelistEditorScreen - drawn on the icon, not the whole cell, so it
            // doesn't get confused with the "none" cell's barrier icon or fight the item sprite.
            if (banned) {
                graphics.renderOutline(iconX - 1, iconY - 1, ICON + 2, ICON + 2, 0xFFFF3333);
            }
            if (hovered) {
                hoveredStack = stack;
                hoveredX = mouseX;
                hoveredY = mouseY;
                hoveredBanned = banned;
            }
        }
        graphics.disableScissor();

        // Flyout: the expanded folder's contents, popped out beside the tile that opened it. Not
        // scissored or scrollable (see FLYOUT_MAX_ITEMS) - clicking a cell here picks it directly,
        // same as any other cell.
        if (expandedFolder != null && !expandedFolderItems.isEmpty()) {
            int[] origin = flyoutOrigin();
            int fx = origin[0];
            int fy = origin[1];
            int rows = (expandedFolderItems.size() + FLYOUT_COLS - 1) / FLYOUT_COLS;
            int fw = FLYOUT_COLS * CELL;
            int fh = rows * CELL;
            graphics.fill(fx - 2, fy - 2, fx + fw + 2, fy + fh + 2, 0xF0222222);
            graphics.renderOutline(fx - 2, fy - 2, fw + 4, fh + 4, 0xFF55AAFF);
            for (int index = 0; index < expandedFolderItems.size(); index++) {
                int col = index % FLYOUT_COLS;
                int row = index / FLYOUT_COLS;
                int x = fx + col * CELL;
                int y = fy + row * CELL;
                ResourceLocation loc = expandedFolderItems.get(index);
                boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
                if (hovered) {
                    graphics.fill(x, y, x + CELL, y + CELL, COLOR_HOVER);
                }
                ItemStack resolved = ItemResolver.resolve(loc, LoadoutClientData.getItemVariants());
                ItemStack stack = resolved != null ? resolved : new ItemStack(Items.BARRIER);
                int iconX = x + (CELL - ICON) / 2;
                int iconY = y + (CELL - ICON) / 2;
                graphics.renderItem(stack, iconX, iconY);
                boolean banned = LoadoutClientData.isBanned(loc);
                if (banned) {
                    graphics.renderOutline(iconX - 1, iconY - 1, ICON + 2, ICON + 2, 0xFFFF3333);
                }
                if (hovered) {
                    hoveredStack = stack;
                    hoveredX = mouseX;
                    hoveredY = mouseY;
                    hoveredBanned = banned;
                }
            }
        }

        if (hoveredFolder != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(hoveredFolder));
            lines.add(Component.translatable("classloadout.gui.folder_item_count", hoveredFolderCount));
            graphics.renderTooltip(this.font, lines, Optional.empty(), hoveredX, hoveredY);
        } else if (hoveredStack != null) {
            if (hoveredStack.getItem() == Items.BARRIER) {
                graphics.renderTooltip(this.font, Component.translatable("classloadout.gui.item_none"), hoveredX, hoveredY);
            } else {
                List<Component> lines = new ArrayList<>(hoveredStack.getTooltipLines(
                        Item.TooltipContext.of(this.minecraft.level), this.minecraft.player, TooltipFlag.Default.NORMAL));
                lines.addAll(TaczCompat.describeGunTooltip(hoveredStack));
                lines.addAll(TaczCompat.describeAmmoBoxTooltip(hoveredStack));
                lines.addAll(TaczCompat.describeAmmoTooltip(hoveredStack));
                if (hoveredBanned) {
                    lines.add(Component.translatable("classloadout.gui.item_banned"));
                }
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
