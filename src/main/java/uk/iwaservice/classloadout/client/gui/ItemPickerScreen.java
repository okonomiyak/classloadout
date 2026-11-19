package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import uk.iwaservice.classloadout.client.LoadoutClientData;
import uk.iwaservice.classloadout.compat.TaczCompat;
import uk.iwaservice.classloadout.compat.ItemZoomerCompat;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Generic item-grid picker used for icon/slot assignment, styled like
 * {@link LoadoutScreen}: a bottom-anchored bar (no full-screen dim) with a
 * title and search box on top, a horizontal row of filter chips, and a
 * scrollable grid of dark item cards. With no restriction it lists the full
 * {@link ItemCatalog} (used by the OP-only preset editor, which is trusted with
 * any item, and the ammo grant popup's ammo picker) - and, unrestricted only,
 * the chips are the same mod categories as {@link WhitelistEditorScreen}, plus
 * an "Add Held Item" button (registers the OP's held item as a reusable
 * variant via {@code /class variant register}, OP-only server-side, and
 * immediately picks it - same idea as {@link WhitelistEditorScreen}'s and
 * {@link AmmoGrantScreen}'s own Add Held Item buttons) and a {@link HotbarBar}
 * below the bar, so a specific TACZ ammo type or an exact NBT-bearing item is
 * easy to find or add among everything else. When constructed with a
 * {@code restrictTo} set it shows only those items with no Add Held Item
 * button or hotbar (used by the player-facing loadout screen, restricted to
 * that slot's OP-curated whitelist - an empty set means nothing is assignable
 * yet; letting a non-OP player self-register a variant here would be pointless
 * since the server-side command is OP-gated and the resulting id wouldn't be
 * whitelisted anyway). No server round trip for browsing either way: the item
 * registry is already fully populated on the client after login. Cell 0 is a
 * fixed "none" entry that reports {@code minecraft:air}, the sentinel the
 * save/assign commands treat as "unset".
 *
 * <p>Restricted mode only: held-item variants grouped into a folder (see
 * {@code /class variant <item> folder}) become filter chips (chest icon +
 * folder name) after an "All" chip, same idea as {@link WhitelistEditorScreen}'s
 * Held-items view - selecting a folder chip shows only that folder's items
 * (within {@code restrictTo} only, not every variant in that folder
 * server-wide); "All" shows everything flattened. Typing a search query
 * ignores the selected folder and searches the whole list.
 */
public class ItemPickerScreen extends Screen {

    private static final int MARGIN = 8;
    private static final int PAD = 6;
    private static final int CARD = 26;
    private static final int GAP = 2;
    private static final int PITCH = CARD + GAP;
    private static final int ICON = 16;
    private static final int CHIP_H = 16;
    private static final int BTN_H = 24;
    private static final int SEARCH_W = 120;

    private static final int COLOR_TEXT = FlatButton.COLOR_TEXT;
    private static final int COLOR_TEXT_DIM = FlatButton.COLOR_TEXT_DIM;
    private static final int COLOR_CARD_LINE = FlatButton.COLOR_CARD_LINE;

    /** One filter chip; {@code x}/{@code w} are laid out in init(). */
    private record Chip(Component label, boolean chest, boolean active, Runnable action, int x, int w) {}

    private final Screen parent;
    private final Consumer<ResourceLocation> onPick;
    @Nullable
    private final List<ResourceLocation> restrictTo;
    @Nullable
    private ItemCatalog.Category selectedCategory = null;
    /** Restricted mode only: the selected folder chip, or null for "All". */
    @Nullable
    private String selectedFolder = null;

    private List<ResourceLocation> allItems = List.of();
    private List<ResourceLocation> shown = List.of();
    /** Restricted mode only: folder name -> its items within {@code restrictTo}. Empty in unrestricted mode. */
    private Map<String, List<ResourceLocation>> folderContents = Map.of();
    private final List<Chip> chips = new ArrayList<>();
    private EditBox search;

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int chipY;
    private int gridLeft;
    private int gridTop;
    private int gridWidth;
    private int gridHeight;
    private int cols = 1;
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
        boolean unrestricted = restrictTo == null;
        allItems = restrictTo != null ? restrictTo : ItemCatalog.all();
        folderContents = unrestricted ? Map.of() : buildFolders(ItemCatalog.byCategory(allItems, null, ItemCatalog::allowedInAnySlot));
        if (selectedFolder != null && !folderContents.containsKey(selectedFolder)) {
            selectedFolder = null;
        }

        // Unrestricted mode leaves room below the bar for the HotbarBar (fixed at the window bottom).
        int bottomMargin = MARGIN + (unrestricted ? 24 : 0);
        // Left of the bar is left free for Item Zoomer's zoomed item (see FlatButton#zoomMargin).
        panelLeft = FlatButton.zoomMargin(this.width, 300);
        panelWidth = this.width - panelLeft - MARGIN;
        panelHeight = Math.max(150, this.height * 6 / 10);
        panelHeight = Math.min(panelHeight, this.height - bottomMargin - MARGIN);
        panelTop = this.height - bottomMargin - panelHeight;
        int right = panelLeft + panelWidth;

        String previousQuery = search != null ? search.getValue() : "";
        search = new EditBox(this.font, right - PAD - SEARCH_W, panelTop + 3, SEARCH_W, 14,
                Component.translatable("classloadout.gui.item_search"));
        search.setHint(Component.translatable("classloadout.gui.item_search"));
        search.setValue(previousQuery);
        search.setResponder(s -> updateShown());
        addRenderableWidget(search);
        setInitialFocus(search);

        // Filter chips.
        chips.clear();
        chipY = panelTop + 20;
        int cx = panelLeft + PAD;
        if (unrestricted) {
            cx = addChip(cx, Component.translatable("classloadout.gui.category_all"), false, selectedCategory == null, () -> selectCategory(null));
            for (ItemCatalog.Category category : ItemCatalog.Category.values()) {
                ItemCatalog.Category captured = category;
                cx = addChip(cx, Component.translatable("classloadout.gui.category_" + category.name().toLowerCase(Locale.ROOT)),
                        false, selectedCategory == category, () -> selectCategory(captured));
            }
        } else {
            cx = addChip(cx, Component.translatable("classloadout.gui.category_all"), false, selectedFolder == null, () -> selectFolder(null));
            for (String folder : folderContents.keySet()) {
                cx = addChip(cx, Component.literal(folder), true, folder.equals(selectedFolder), () -> selectFolder(folder));
            }
        }

        int sepY = chipY + CHIP_H + 4;
        int btnY = panelTop + panelHeight - PAD - BTN_H;
        gridLeft = panelLeft + PAD;
        gridTop = sepY + 4;
        gridWidth = panelWidth - 2 * PAD - 6;
        gridHeight = btnY - 4 - gridTop;
        cols = Math.max(1, (gridWidth + GAP) / PITCH);

        int btnW = Math.max(120, (panelWidth - 2 * PAD) / 3);
        int bx = right - PAD - btnW;
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.cancel"),
                b -> minecraft.setScreen(parent), bx, btnY, btnW, BTN_H));
        if (unrestricted) {
            Component addLabel = Component.translatable("classloadout.gui.whitelist_add_held");
            int addW = Math.max(btnW, this.font.width(addLabel) + 16);
            addRenderableWidget(new FlatButton(addLabel, b -> addHeldItem(), bx - GAP * 2 - addW, btnY, addW, BTN_H));
        }

        updateShown();
    }

    private int addChip(int x, Component label, boolean chest, boolean active, Runnable action) {
        int w = this.font.width(label) + 12 + (chest ? 14 : 0);
        chips.add(new Chip(label, chest, active, action, x, w));
        return x + w + 3;
    }

    /** Folder name -> items, folders sorted by name; items with no folder are left out. */
    private static Map<String, List<ResourceLocation>> buildFolders(List<ResourceLocation> items) {
        Map<String, List<ResourceLocation>> byFolder = new LinkedHashMap<>();
        for (ResourceLocation loc : items) {
            String folder = LoadoutClientData.getVariantFolder(loc);
            if (!folder.isEmpty()) {
                byFolder.computeIfAbsent(folder, f -> new ArrayList<>()).add(loc);
            }
        }
        Map<String, List<ResourceLocation>> sorted = new LinkedHashMap<>();
        List<String> names = new ArrayList<>(byFolder.keySet());
        Collections.sort(names);
        for (String n : names) {
            sorted.put(n, byFolder.get(n));
        }
        return sorted;
    }

    /** Registers the OP's held item as a reusable variant and immediately picks it, same as clicking a catalog cell. */
    private void addHeldItem() {
        UUID id = UUID.randomUUID();
        command("class variant register " + id);
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
            scrollOffset = 0;
            this.init(this.minecraft, this.width, this.height);
        }
    }

    private void selectFolder(@Nullable String folder) {
        if (!Objects.equals(folder, selectedFolder)) {
            selectedFolder = folder;
            scrollOffset = 0;
            this.init(this.minecraft, this.width, this.height);
        }
    }

    private void updateShown() {
        List<ResourceLocation> base = ItemCatalog.byCategory(allItems, selectedCategory, ItemCatalog::allowedInAnySlot);
        String query = search.getValue();
        if (selectedFolder != null && query.isBlank()) {
            base = folderContents.getOrDefault(selectedFolder, List.of());
        }
        shown = ItemCatalog.search(base, query);
        int rows = (shown.size() + 1 + cols - 1) / cols; // +1 for the "none" cell
        maxScroll = Math.max(0, rows * PITCH - GAP - gridHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    /** Cell index 0 is "none"; index n>0 maps to shown.get(n - 1). Returns -1 if out of range. */
    private int cellIndexAt(double mouseX, double mouseY) {
        if (mouseX < gridLeft || mouseX >= gridLeft + cols * PITCH || mouseY < gridTop || mouseY >= gridTop + gridHeight) {
            return -1;
        }
        int dx = (int) (mouseX - gridLeft);
        int dy = (int) (mouseY - gridTop + scrollOffset);
        if (dx % PITCH >= CARD || dy % PITCH >= CARD) {
            return -1;
        }
        int index = (dy / PITCH) * cols + dx / PITCH;
        return index <= shown.size() ? index : -1;
    }

    private boolean chipsHit(double mouseX, double mouseY) {
        int clipRight = panelLeft + panelWidth - PAD;
        if (mouseY < chipY || mouseY >= chipY + CHIP_H || mouseX >= clipRight) {
            return false;
        }
        for (Chip c : chips) {
            if (mouseX >= c.x() && mouseX < c.x() + c.w()) {
                c.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (restrictTo == null && HotbarBar.mouseClicked(minecraft, mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && chipsHit(mouseX, mouseY)) {
            return true;
        }
        int index = cellIndexAt(mouseX, mouseY);
        if (index == 0) {
            onPick.accept(new ResourceLocation("minecraft", "air"));
            minecraft.setScreen(parent);
            return true;
        } else if (index > 0) {
            ResourceLocation loc = shown.get(index - 1);
            if (!LoadoutClientData.isBanned(loc)) {
                onPick.accept(loc);
                minecraft.setScreen(parent);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (delta * PITCH)));
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
        // No full-screen dim: the world stays visible, only a gradient behind the bar.
        graphics.fillGradient(0, panelTop - 30, this.width, this.height, 0x00000000, 0xC0000000);

        int l = panelLeft;
        int t = panelTop;
        int r = l + panelWidth;
        graphics.drawString(this.font, this.title, l + PAD, t + 6, COLOR_TEXT_DIM, false);
        FlatButton.drawBrackets(graphics, l, t, panelWidth, panelHeight);

        // Filter chips, clipped to the bar's inner width.
        graphics.enableScissor(l + PAD, chipY, r - PAD, chipY + CHIP_H);
        for (Chip c : chips) {
            boolean hover = mouseX >= c.x() && mouseX < c.x() + c.w() && mouseY >= chipY && mouseY < chipY + CHIP_H;
            graphics.fill(c.x(), chipY, c.x() + c.w(), chipY + CHIP_H, FlatButton.COLOR_CARD_BG);
            graphics.renderOutline(c.x(), chipY, c.w(), CHIP_H, c.active() || hover ? COLOR_TEXT : COLOR_CARD_LINE);
            int tx = c.x() + 6;
            if (c.chest()) {
                graphics.pose().pushPose();
                graphics.pose().translate(c.x() + 3, chipY + 2, 0);
                graphics.pose().scale(0.75F, 0.75F, 1);
                graphics.renderItem(new ItemStack(Items.CHEST), 0, 0);
                graphics.pose().popPose();
                tx += 14;
            }
            graphics.drawString(this.font, c.label(), tx, chipY + 4, c.active() ? COLOR_TEXT : COLOR_TEXT_DIM, false);
        }
        graphics.disableScissor();
        graphics.fill(l + PAD, chipY + CHIP_H + 3, r - PAD, chipY + CHIP_H + 4, COLOR_CARD_LINE);

        if (restrictTo != null && restrictTo.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("classloadout.gui.whitelist_empty"),
                    gridLeft + PITCH + 4, gridTop + 9, COLOR_TEXT_DIM, false);
        }

        graphics.enableScissor(gridLeft, gridTop, gridLeft + gridWidth, gridTop + gridHeight);
        ItemStack hoveredStack = null;
        int hoveredX = 0;
        int hoveredY = 0;
        boolean hoveredBanned = false;
        int total = shown.size() + 1;
        for (int index = 0; index < total; index++) {
            int x = gridLeft + (index % cols) * PITCH;
            int y = gridTop + (index / cols) * PITCH - scrollOffset;
            if (y + CARD <= gridTop || y >= gridTop + gridHeight) {
                continue;
            }
            boolean hovered = mouseX >= x && mouseX < x + CARD && mouseY >= y && mouseY < y + CARD
                    && mouseY >= gridTop && mouseY < gridTop + gridHeight;
            FlatButton.drawCardFrame(graphics, x, y, CARD, CARD, hovered);
            ResourceLocation loc = index == 0 ? null : shown.get(index - 1);
            ItemStack resolved = loc == null ? null : LoadoutClientData.displayStack(loc);
            ItemStack stack = resolved != null ? resolved : new ItemStack(Items.BARRIER);
            int iconX = x + (CARD - ICON) / 2;
            int iconY = y + (CARD - ICON) / 2;
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

        if (maxScroll > 0) {
            int trackX = gridLeft + gridWidth + 3;
            graphics.fill(trackX, gridTop, trackX + 2, gridTop + gridHeight, COLOR_CARD_LINE);
            int thumbHeight = Math.max(10, gridHeight * gridHeight / Math.max(1, gridHeight + maxScroll));
            int thumbY = gridTop + (gridHeight - thumbHeight) * scrollOffset / Math.max(1, maxScroll);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, COLOR_TEXT);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (restrictTo == null) {
            HotbarBar.render(graphics, this.minecraft);
        }

        ItemZoomerCompat.hover(this, hoveredStack != null && hoveredStack.getItem() != Items.BARRIER ? hoveredStack : null, panelLeft, panelTop, panelWidth, panelHeight);
        if (hoveredStack != null) {
            if (hoveredStack.getItem() == Items.BARRIER) {
                graphics.renderTooltip(this.font, Component.translatable("classloadout.gui.item_none"), hoveredX, hoveredY);
            } else {
                List<Component> lines = new ArrayList<>(hoveredStack.getTooltipLines(
                        this.minecraft.player, TooltipFlag.Default.NORMAL));
                lines.addAll(TaczCompat.describeGunTooltip(hoveredStack));
                lines.addAll(TaczCompat.describeAmmoBoxTooltip(hoveredStack));
                lines.addAll(TaczCompat.describeAmmoTooltip(hoveredStack));
                if (hoveredBanned) {
                    lines.add(Component.translatable("classloadout.gui.item_banned"));
                }
                graphics.renderTooltip(this.font, lines, Optional.empty(), hoveredX, hoveredY);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
