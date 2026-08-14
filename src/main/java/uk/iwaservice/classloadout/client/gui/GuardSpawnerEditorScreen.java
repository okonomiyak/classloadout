package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import uk.iwaservice.classloadout.ItemResolver;
import uk.iwaservice.classloadout.client.LoadoutClientData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * OP-only screen for configuring one guard spawner block: the entity type
 * to (re)spawn, the respawn delay in seconds, and which items are inserted
 * into the spawned entity's item-handler capability (see
 * {@code ServerEvents#tickGuardSpawners}). Opened by right-clicking a
 * {@code GuardSpawnerBlock}; unlike the other OP editors this one's data
 * isn't broadcast to every client, so the fields below are seeded once from
 * {@code OpenGuardSpawnerEditorPacket} and then updated optimistically as
 * the OP edits - every mutation here is OP-gated server-side too, so an
 * optimistic update can't drift from reality in practice.
 */
public class GuardSpawnerEditorScreen extends Screen {

    private static final int PAD = 10;
    private static final int HEADER_H = 24;
    private static final int FIELD_H = 20;
    private static final int SEARCH_H = 20;
    private static final int CELL = 20;
    private static final int COLS = 9;
    private static final int ICON = 16;

    private static final int COLOR_PANEL_BG = 0xF4222222;
    private static final int COLOR_HEADER_BG = 0xFF1F2333;
    private static final int COLOR_OUTLINE = 0xFF454A66;
    private static final int COLOR_HOVER = 0x60FFFFFF;

    private final BlockPos pos;
    @Nullable
    private ResourceLocation entityType;
    private int delaySeconds;
    private final List<ResourceLocation> items;

    private List<ResourceLocation> allItems = List.of();
    private List<ResourceLocation> shown = List.of();
    private EditBox entityTypeBox;
    private EditBox delayBox;
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

    public GuardSpawnerEditorScreen(BlockPos pos, @Nullable ResourceLocation entityType, int delaySeconds,
            List<ResourceLocation> items) {
        super(Component.translatable("classloadout.gui.guardspawner_editor_title"));
        this.pos = pos;
        this.entityType = entityType;
        this.delaySeconds = delaySeconds;
        this.items = new ArrayList<>(items);
    }

    @Override
    protected void init() {
        panelWidth = Math.max(PAD * 2 + COLS * CELL, 260);
        panelHeight = Math.min(340, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        if (allItems.isEmpty()) {
            allItems = ItemCatalog.all();
        }

        int fieldsY = panelTop + HEADER_H + 6;
        int fieldWidth = (panelWidth - 2 * PAD - 4) / 2;

        String previousEntityType = entityTypeBox != null ? entityTypeBox.getValue()
                : (entityType == null ? "" : entityType.toString());
        entityTypeBox = new EditBox(this.font, panelLeft + PAD, fieldsY, fieldWidth, FIELD_H,
                Component.translatable("classloadout.gui.guardspawner_entity_type"));
        entityTypeBox.setHint(Component.translatable("classloadout.gui.guardspawner_entity_type"));
        entityTypeBox.setValue(previousEntityType);
        addRenderableWidget(entityTypeBox);

        String previousDelay = delayBox != null ? delayBox.getValue() : Integer.toString(delaySeconds);
        delayBox = new EditBox(this.font, panelLeft + PAD + fieldWidth + 4, fieldsY, fieldWidth, FIELD_H,
                Component.translatable("classloadout.gui.guardspawner_delay"));
        delayBox.setHint(Component.translatable("classloadout.gui.guardspawner_delay"));
        delayBox.setValue(previousDelay);
        addRenderableWidget(delayBox);

        int saveY = fieldsY + FIELD_H + 4;
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.guardspawner_save"), b -> saveConfig())
                .bounds(panelLeft + PAD, saveY, panelWidth - 2 * PAD, 20).build());

        int searchY = saveY + 20 + 6;
        String previousQuery = search != null ? search.getValue() : "";
        search = new EditBox(this.font, panelLeft + PAD, searchY, panelWidth - 2 * PAD, SEARCH_H,
                Component.translatable("classloadout.gui.item_search"));
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
                        b -> addHeldItem())
                .bounds(panelLeft + PAD + closeWidth + 4, panelTop + panelHeight - PAD - 20, addHeldWidth, 20).build());

        updateShown();
    }

    private void saveConfig() {
        String typeStr = entityTypeBox.getValue().trim();
        if (typeStr.isEmpty()) {
            return;
        }
        int delay;
        try {
            delay = Math.max(1, Integer.parseInt(delayBox.getValue().trim()));
        } catch (NumberFormatException e) {
            return;
        }
        entityType = new ResourceLocation(typeStr);
        delaySeconds = delay;
        command("class guardspawner config " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + " " + entityType + " " + delaySeconds);
    }

    /** Registers the OP's held item as a reusable variant and immediately adds it to this spawner's item list. */
    private void addHeldItem() {
        UUID id = UUID.randomUUID();
        command("class whitelist register_held " + id);
        ResourceLocation variant = new ResourceLocation("classloadout", "variant_" + id);
        items.add(variant);
        command("class guardspawner add_item " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + variant);
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
            String posArgs = pos.getX() + " " + pos.getY() + " " + pos.getZ();
            if (items.contains(item)) {
                items.remove(item);
                command("class guardspawner remove_item " + posArgs + " " + item);
            } else {
                items.add(item);
                command("class guardspawner add_item " + posArgs + " " + item);
            }
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
        if (!search.isFocused() && !entityTypeBox.isFocused() && !delayBox.isFocused()
                && HotbarBar.keyPressed(minecraft, keyCode, scanCode)) {
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

        graphics.enableScissor(gridLeft, gridTop, gridLeft + COLS * CELL, gridTop + gridHeight);
        ItemStack hoveredStack = null;
        int hoveredX = 0;
        int hoveredY = 0;
        boolean hoveredIn = false;
        for (int index = 0; index < shown.size(); index++) {
            int col = index % COLS;
            int row = index / COLS;
            int x = gridLeft + col * CELL;
            int y = gridTop + row * CELL - scrollOffset;
            if (y + CELL <= gridTop || y >= gridTop + gridHeight) {
                continue;
            }
            ResourceLocation loc = shown.get(index);
            boolean inList = items.contains(loc);
            boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL
                    && mouseY >= gridTop && mouseY < gridTop + gridHeight;
            if (inList) {
                graphics.fill(x, y, x + CELL, y + CELL, 0x4055AAFF);
                graphics.renderOutline(x, y, CELL, CELL, 0xFF55AAFF);
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
                hoveredIn = inList;
            }
        }
        graphics.disableScissor();

        if (hoveredStack != null) {
            Component name = hoveredStack.getHoverName().copy().append(hoveredIn
                    ? Component.translatable("classloadout.gui.guardspawner_item_on")
                    : Component.translatable("classloadout.gui.guardspawner_item_off"));
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
