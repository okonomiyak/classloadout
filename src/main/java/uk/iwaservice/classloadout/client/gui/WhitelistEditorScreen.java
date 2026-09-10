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
import uk.iwaservice.classloadout.loadout.LoadoutSlot;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * OP-only screen for curating, per slot, the set of items players are
 * allowed to self-assign via {@link LoadoutScreen}. Opened exclusively via
 * {@link uk.iwaservice.classloadout.network.OpenWhitelistEditorPacket} that
 * follows a successful {@code /class whitelist} command. Unlike
 * {@link ItemPickerScreen}, clicking a cell here toggles membership rather
 * than picking-and-closing, so the screen stays open for repeated edits.
 * Also doubles as the ban editor: hovering a cell and pressing B toggles
 * that item's global ban (see {@code /class ban}), independent of slot -
 * a ban applies everywhere, not just to {@link #selectedSlot}.
 *
 * <p>In the Held-items category (with no active search), variants grouped
 * into a folder (see {@code /class whitelist set_folder}) are collapsed
 * behind one chest-icon tile per folder instead of listing every item
 * inline; clicking a tile pops a small flyout grid of that folder's items
 * out beside it (see {@link #expandedFolder}). A non-blank search query
 * flattens this back to a plain list across every held item, ignoring
 * folders, so searching always finds everything regardless of grouping.
 */
public class WhitelistEditorScreen extends Screen {
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

    private LoadoutSlot selectedSlot = LoadoutSlot.MAIN;
    @Nullable
    private ItemCatalog.Category selectedCategory = null;
    private List<ResourceLocation> allItems = List.of();
    private List<ResourceLocation> shown = List.of();
    /** Synthetic per-render tile id -> folder name, populated only in the Held-items top-level view (see {@link #buildHeldItemsTopLevel}). Empty everywhere else. */
    private Map<ResourceLocation, String> folderTiles = Map.of();
    /** The folder whose flyout is currently popped out, or null if none. Reset whenever the top-level view changes shape (category switch, search typed). */
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
    private int dataRevision = -1;
    /** Item currently under the mouse, updated every frame in {@link #render}; used by {@link #keyPressed} to toggle its ban state, since key events carry no cursor position. Never a folder tile. */
    @Nullable
    private ResourceLocation hoveredItem;

    @Nullable
    private final Screen parent;

    /** Opened directly by {@code /class whitelist} - closing exits the GUI entirely (no parent to return to). */
    public WhitelistEditorScreen() {
        this(null);
    }

    /** Opened via the class editor's nav bar - closing returns to {@code parent} instead of exiting. */
    public WhitelistEditorScreen(@Nullable Screen parent) {
        super(Component.translatable("classloadout.gui.whitelist_editor_title"));
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
        panelHeight = Math.min(326, this.height - 32);
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

        int catY = tabY + TAB_H + 6;
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

        String previousQuery = search != null ? search.getValue() : "";
        search = new EditBox(this.font, panelLeft + PAD, catY + CAT_H + 6,
                panelWidth - 2 * PAD, SEARCH_H, Component.translatable("classloadout.gui.item_search"));
        search.setHint(Component.translatable("classloadout.gui.item_search"));
        search.setValue(previousQuery);
        search.setResponder(s -> updateShown());
        addRenderableWidget(search);

        gridLeft = panelLeft + PAD;
        gridTop = catY + CAT_H + 6 + SEARCH_H + 6;
        gridHeight = panelTop + panelHeight - PAD - 24 - gridTop;

        int closeWidth = (panelWidth - 2 * PAD - 4) * 2 / 3;
        int addHeldWidth = panelWidth - 2 * PAD - 4 - closeWidth;
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.close"), b -> onClose())
                .bounds(panelLeft + PAD, panelTop + panelHeight - PAD - 20, closeWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.whitelist_add_held"),
                        b -> command("class whitelist add_held " + selectedSlot.key()))
                .bounds(panelLeft + PAD + closeWidth + 4, panelTop + panelHeight - PAD - 20, addHeldWidth, 20).build());

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

    private void selectCategory(@Nullable ItemCatalog.Category category) {
        if (category != selectedCategory) {
            selectedCategory = category;
            this.init(this.minecraft, this.width, this.height);
        }
    }

    private void updateShown() {
        List<ResourceLocation> categoryFiltered = ItemCatalog.byCategory(allItems, selectedCategory);
        String query = search.getValue();
        boolean topLevelHeldView = selectedCategory == ItemCatalog.Category.HELD_ITEMS && query.isBlank();
        if (topLevelHeldView) {
            categoryFiltered = buildHeldItemsTopLevel(categoryFiltered);
        } else {
            folderTiles = Map.of();
            expandedFolder = null;
            expandedFolderItems = List.of();
        }
        shown = ItemCatalog.search(categoryFiltered, query);
        int rows = (shown.size() + COLS - 1) / COLS;
        int contentHeight = rows * CELL;
        maxScroll = Math.max(0, contentHeight - gridHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    /** One chest-icon tile per folder in use (see {@link #folderTiles}), followed by every held item with no folder assigned. */
    private List<ResourceLocation> buildHeldItemsTopLevel(List<ResourceLocation> heldItems) {
        List<String> folders = LoadoutClientData.getVariantFolders();
        Map<ResourceLocation, String> tiles = new LinkedHashMap<>();
        List<ResourceLocation> result = new ArrayList<>();
        for (int i = 0; i < folders.size(); i++) {
            ResourceLocation tileId = ResourceLocation.fromNamespaceAndPath(ClassLoadoutMod.MODID, "folder_" + i);
            tiles.put(tileId, folders.get(i));
            result.add(tileId);
        }
        for (ResourceLocation loc : heldItems) {
            if (LoadoutClientData.getVariantFolder(loc).isEmpty()) {
                result.add(loc);
            }
        }
        folderTiles = tiles;
        return result;
    }

    private int countInFolder(String folder) {
        int count = 0;
        for (ResourceLocation loc : LoadoutClientData.getItemVariants().keySet()) {
            if (folder.equals(LoadoutClientData.getVariantFolder(loc))) {
                count++;
            }
        }
        return count;
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

    /** While a folder's flyout is open, everything else in the main grid is hidden (icons were overlapping the flyout) - only the open folder's own tile stays visible/clickable, so it can still be clicked again to close. */
    private boolean isMainGridCellVisible(ResourceLocation loc) {
        if (expandedFolder == null) {
            return true;
        }
        return expandedFolder.equals(folderTiles.get(loc));
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

    private void toggleFolderFlyout(String folder, int tileIndex) {
        if (folder.equals(expandedFolder)) {
            expandedFolder = null;
            expandedFolderItems = List.of();
            return;
        }
        expandedFolder = folder;
        List<ResourceLocation> items = new ArrayList<>();
        for (ResourceLocation loc : LoadoutClientData.getItemVariants().keySet()) {
            if (items.size() >= FLYOUT_MAX_ITEMS) {
                break;
            }
            if (folder.equals(LoadoutClientData.getVariantFolder(loc))) {
                items.add(loc);
            }
        }
        expandedFolderItems = items;
        int col = tileIndex % COLS;
        int row = tileIndex / COLS;
        expandedFlyoutX = gridLeft + col * CELL + CELL + 4;
        expandedFlyoutY = gridTop + row * CELL - scrollOffset;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (HotbarBar.mouseClicked(minecraft, mouseX, mouseY)) {
            return true;
        }
        int flyoutIndex = flyoutIndexAt(mouseX, mouseY);
        if (flyoutIndex >= 0) {
            return handleItemClick(expandedFolderItems.get(flyoutIndex), button);
        }
        int index = cellIndexAt(mouseX, mouseY);
        if (index >= 0) {
            ResourceLocation loc = shown.get(index);
            if (!isMainGridCellVisible(loc)) {
                return true;
            }
            String folder = folderTiles.get(loc);
            if (folder != null) {
                toggleFolderFlyout(folder, index);
                return true;
            }
            return handleItemClick(loc, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Left-click toggles whitelist membership, shift+left deletes a held-item variant, right-click opens the ammo-grant popup (whitelisting first if needed). Shared by the main grid and the folder flyout. */
    private boolean handleItemClick(ResourceLocation item, int button) {
        if (button == 0 && hasShiftDown() && LoadoutClientData.getItemVariants().containsKey(item)) {
            command("class whitelist delete_variant " + item);
            return true;
        }
        boolean whitelisted = LoadoutClientData.getWhitelist(selectedSlot).contains(item);
        if (button == 1) {
            // right-click: configure (or re-configure) an ammo grant, whitelisting first if needed
            if (!whitelisted) {
                command("class whitelist add " + selectedSlot.key() + " " + item);
            }
            minecraft.setScreen(new AmmoGrantScreen(this, selectedSlot, item));
            return true;
        }
        String cmd = "class whitelist " + (whitelisted ? "remove " : "add ") + selectedSlot.key() + " " + item;
        command(cmd);
        return true;
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
        // Don't hijack digit keys (or "B"/"F") while the search box is focused (e.g. typing "9x19" or "bow").
        if (!search.isFocused()) {
            if (HotbarBar.keyPressed(minecraft, keyCode, scanCode)) {
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_B && hoveredItem != null) {
                String cmd = "class ban " + (LoadoutClientData.isBanned(hoveredItem) ? "remove " : "add ") + hoveredItem;
                command(cmd);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_F && hoveredItem != null
                    && LoadoutClientData.getItemVariants().containsKey(hoveredItem)) {
                minecraft.setScreen(new VariantFolderScreen(this, hoveredItem));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Every slot key this variant id is currently whitelisted under, comma-joined ("-" if none). */
    private static String variantSlotsText(ResourceLocation id) {
        List<String> slots = new ArrayList<>();
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            if (LoadoutClientData.getWhitelist(slot).contains(id)) {
                slots.add(slot.key());
            }
        }
        return slots.isEmpty() ? "-" : String.join(", ", slots);
    }

    private static String variantRegisteredText(ResourceLocation id) {
        long millis = LoadoutClientData.getVariantRegisteredAt(id);
        return millis <= 0 ? "-" : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(millis));
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

        super.render(graphics, mouseX, mouseY, partialTick);

        List<ResourceLocation> whitelist = LoadoutClientData.getWhitelist(selectedSlot);

        ItemStack hoveredStack = null;
        ResourceLocation hoveredLoc = null;
        int hoveredX = 0;
        int hoveredY = 0;
        boolean hoveredWhitelisted = false;
        boolean hoveredHasAmmoGrant = false;
        boolean hoveredIsVariant = false;
        boolean hoveredBanned = false;
        String hoveredFolder = null;
        int hoveredFolderCount = 0;

        graphics.enableScissor(gridLeft, gridTop, gridLeft + COLS * CELL, gridTop + gridHeight);
        for (int index = 0; index < shown.size(); index++) {
            int col = index % COLS;
            int row = index / COLS;
            int x = gridLeft + col * CELL;
            int y = gridTop + row * CELL - scrollOffset;
            if (y + CELL <= gridTop || y >= gridTop + gridHeight) {
                continue;
            }
            ResourceLocation loc = shown.get(index);
            if (!isMainGridCellVisible(loc)) {
                continue;
            }
            boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL
                    && mouseY >= gridTop && mouseY < gridTop + gridHeight;

            String folder = folderTiles.get(loc);
            if (folder != null) {
                boolean open = folder.equals(expandedFolder);
                if (open) {
                    graphics.fill(x, y, x + CELL, y + CELL, 0x4055AAFF);
                }
                if (hovered) {
                    graphics.fill(x, y, x + CELL, y + CELL, COLOR_HOVER);
                }
                graphics.renderItem(new ItemStack(Items.CHEST), x + (CELL - ICON) / 2, y + (CELL - ICON) / 2);
                graphics.renderOutline(x, y, CELL, CELL, open ? 0xFF55AAFF : COLOR_OUTLINE);
                if (hovered) {
                    hoveredFolder = folder;
                    hoveredFolderCount = countInFolder(folder);
                    hoveredX = mouseX;
                    hoveredY = mouseY;
                }
                continue;
            }

            boolean whitelisted = whitelist.contains(loc);
            boolean banned = LoadoutClientData.isBanned(loc);
            boolean hasAmmoGrant = !LoadoutClientData.getAmmoGrants(selectedSlot, loc).isEmpty();
            if (whitelisted) {
                graphics.fill(x, y, x + CELL, y + CELL, 0x4055FF55);
                graphics.renderOutline(x, y, CELL, CELL, 0xFF55FF55);
            }
            if (hovered) {
                graphics.fill(x, y, x + CELL, y + CELL, COLOR_HOVER);
            }
            int iconX = x + (CELL - ICON) / 2;
            int iconY = y + (CELL - ICON) / 2;
            ItemStack resolved = ItemResolver.resolve(loc, LoadoutClientData.getItemVariants());
            ItemStack stack = resolved != null ? resolved : new ItemStack(Items.BARRIER);
            graphics.renderItem(stack, iconX, iconY);
            // Drawn tight around the icon (not the whole cell) and after it, so the ban reads as a
            // clear "forbidden" ring on the icon itself rather than a wash that fights the sprite.
            if (banned) {
                graphics.renderOutline(iconX - 1, iconY - 1, ICON + 2, ICON + 2, 0xFFFF3333);
            }
            if (hasAmmoGrant) {
                graphics.fill(x + CELL - 5, y + 1, x + CELL - 1, y + 5, 0xFFFFAA00);
            }
            if (LoadoutClientData.getItemVariants().containsKey(loc)) {
                graphics.fill(x + 1, y + CELL - 5, x + 5, y + CELL - 1, 0xFF55AAFF);
            }
            if (hovered) {
                hoveredStack = stack;
                hoveredLoc = loc;
                hoveredX = mouseX;
                hoveredY = mouseY;
                hoveredWhitelisted = whitelisted;
                hoveredHasAmmoGrant = hasAmmoGrant;
                hoveredIsVariant = LoadoutClientData.getItemVariants().containsKey(loc);
                hoveredBanned = banned;
            }
        }
        graphics.disableScissor();

        // Flyout: the expanded folder's contents, popped out beside the tile that opened it. Not
        // scissored (it deliberately draws outside the main grid's clip rect, and often outside the
        // panel entirely) and not scrollable (see FLYOUT_MAX_ITEMS) - a small, self-contained grid
        // reusing the exact same per-cell rendering/hover rules as the main grid above.
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
                boolean whitelisted = whitelist.contains(loc);
                boolean banned = LoadoutClientData.isBanned(loc);
                boolean hasAmmoGrant = !LoadoutClientData.getAmmoGrants(selectedSlot, loc).isEmpty();
                boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
                if (whitelisted) {
                    graphics.fill(x, y, x + CELL, y + CELL, 0x4055FF55);
                    graphics.renderOutline(x, y, CELL, CELL, 0xFF55FF55);
                }
                if (hovered) {
                    graphics.fill(x, y, x + CELL, y + CELL, COLOR_HOVER);
                }
                int iconX = x + (CELL - ICON) / 2;
                int iconY = y + (CELL - ICON) / 2;
                ItemStack resolved = ItemResolver.resolve(loc, LoadoutClientData.getItemVariants());
                ItemStack stack = resolved != null ? resolved : new ItemStack(Items.BARRIER);
                graphics.renderItem(stack, iconX, iconY);
                if (banned) {
                    graphics.renderOutline(iconX - 1, iconY - 1, ICON + 2, ICON + 2, 0xFFFF3333);
                }
                if (hasAmmoGrant) {
                    graphics.fill(x + CELL - 5, y + 1, x + CELL - 1, y + 5, 0xFFFFAA00);
                }
                if (hovered) {
                    hoveredStack = stack;
                    hoveredLoc = loc;
                    hoveredX = mouseX;
                    hoveredY = mouseY;
                    hoveredWhitelisted = whitelisted;
                    hoveredHasAmmoGrant = hasAmmoGrant;
                    hoveredIsVariant = true;
                    hoveredBanned = banned;
                }
            }
        }

        this.hoveredItem = hoveredLoc;

        if (hoveredFolder != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(hoveredFolder));
            lines.add(Component.translatable("classloadout.gui.folder_item_count", hoveredFolderCount));
            graphics.renderTooltip(this.font, lines, Optional.empty(), hoveredX, hoveredY);
        } else if (hoveredStack != null) {
            List<Component> lines = new ArrayList<>(hoveredStack.getTooltipLines(
                    Item.TooltipContext.of(this.minecraft.level), this.minecraft.player, TooltipFlag.Default.NORMAL));
            lines.addAll(TaczCompat.describeGunTooltip(hoveredStack));
            lines.addAll(TaczCompat.describeAmmoBoxTooltip(hoveredStack));
            lines.addAll(TaczCompat.describeAmmoTooltip(hoveredStack));
            lines.add(hoveredWhitelisted
                    ? Component.translatable("classloadout.gui.whitelist_on")
                    : Component.translatable("classloadout.gui.whitelist_off"));
            lines.add(hoveredBanned
                    ? Component.translatable("classloadout.gui.ban_on")
                    : Component.translatable("classloadout.gui.ban_off"));
            if (hoveredHasAmmoGrant) {
                lines.add(Component.translatable("classloadout.gui.ammo_grant_marker"));
            }
            if (hoveredIsVariant) {
                lines.add(Component.translatable("classloadout.gui.held_item_marker"));
                lines.add(Component.translatable("classloadout.gui.held_item_delete_hint"));
                lines.add(Component.translatable("classloadout.gui.variant_slots", variantSlotsText(hoveredLoc)));
                lines.add(Component.translatable("classloadout.gui.variant_registered", variantRegisteredText(hoveredLoc)));
                String folder = LoadoutClientData.getVariantFolder(hoveredLoc);
                lines.add(folder.isEmpty()
                        ? Component.translatable("classloadout.gui.variant_folder_hint")
                        : Component.translatable("classloadout.gui.variant_folder_current", folder));
            }
            graphics.renderTooltip(this.font, lines, Optional.empty(), hoveredX, hoveredY);
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
