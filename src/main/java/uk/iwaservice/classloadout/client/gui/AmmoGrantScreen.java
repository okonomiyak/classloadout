package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
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
import java.util.UUID;

/**
 * OP-only popup, opened by right-clicking a whitelisted cell in
 * {@link WhitelistEditorScreen}, that manages this (slot, item) whitelist
 * entry's ammo grants: equipping the item (on respawn, or immediately via
 * the loadout station/locker) also gives the player each configured ammo
 * item's count into their general inventory. An item can carry any number
 * of distinct ammo grants (e.g. two different magazine types for the same
 * gun) - the top grid lists the existing ones (click an icon to remove it),
 * and the picker below adds a new one. Mutates through the same {@code
 * /class whitelist ammo} command surface as everything else - no C2S
 * packets. To change an existing entry's count, remove it and re-add it;
 * there's no separate in-place count editor here (keeps this popup small).
 */
public class AmmoGrantScreen extends Screen {

    private static final int PAD = 10;
    private static final int HEADER_H = 24;
    private static final int SLOT = 24;
    private static final int CELL = 20;
    private static final int ICON = 16;
    private static final int GRID_COLS = 8;

    private static final int COLOR_PANEL_BG = 0xF4222222;
    private static final int COLOR_HEADER_BG = 0xFF1F2333;
    private static final int COLOR_OUTLINE = 0xFF454A66;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xA0A8C0;
    private static final int COLOR_SLOT_BG = 0x60000000;
    private static final int COLOR_HOVER = 0x60FFFFFF;

    private final Screen parent;
    private final LoadoutSlot slot;
    private final ResourceLocation item;
    private List<Map.Entry<ResourceLocation, Integer>> entries = List.of();

    @Nullable
    private ResourceLocation pendingAmmoItem;
    private String countValue = "";
    private EditBox countBox;
    private int dataRevision = -1;

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int gridTop;
    private int gridRows;
    private int pickerX;
    private int pickerY;

    public AmmoGrantScreen(Screen parent, LoadoutSlot slot, ResourceLocation item) {
        super(Component.translatable("classloadout.gui.ammo_grant_title"));
        this.parent = parent;
        this.slot = slot;
        this.item = item;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void tick() {
        if (dataRevision != LoadoutClientData.getRevision()) {
            this.init(this.minecraft, this.width, this.height);
        }
    }

    @Override
    protected void init() {
        dataRevision = LoadoutClientData.getRevision();
        entries = new ArrayList<>(LoadoutClientData.getAmmoGrants(slot, item).entrySet());
        gridRows = Math.max(1, (entries.size() + GRID_COLS - 1) / GRID_COLS);
        panelWidth = Math.min(Math.max(PAD * 2 + GRID_COLS * CELL, 220), this.width - 16);
        panelHeight = Math.min(HEADER_H + PAD + 14 + gridRows * CELL + 8 + SLOT + 6 + 18 + 6 + 20 + PAD,
                this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        int y = panelTop + HEADER_H + PAD + 14;
        gridTop = y;
        y += gridRows * CELL + 8;

        pickerX = panelLeft + PAD;
        pickerY = y;
        addRenderableWidget(Button.builder(Component.empty(),
                        b -> minecraft.setScreen(new ItemPickerScreen(this, loc -> {
                            pendingAmmoItem = isAir(loc) ? null : loc;
                            minecraft.setScreen(this);
                        })))
                .bounds(pickerX, pickerY, SLOT, SLOT).build());

        countBox = new EditBox(this.font, pickerX + SLOT + 8, pickerY + 4,
                panelWidth - 2 * PAD - SLOT - 8, 18, Component.translatable("classloadout.gui.ammo_grant_count"));
        countBox.setMaxLength(5);
        countBox.setValue(countValue);
        countBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        countBox.setResponder(s -> countValue = s);
        addRenderableWidget(countBox);
        y += SLOT + 6;

        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.ammo_grant_add"), b -> addEntry())
                .bounds(panelLeft + PAD, y, panelWidth - 2 * PAD, 18).build());
        y += 18 + 6;

        int half = (panelWidth - 2 * PAD - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.whitelist_add_held"),
                        b -> registerHeldAsAmmo())
                .bounds(panelLeft + PAD, y, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.close"), b -> onClose())
                .bounds(panelLeft + PAD + half + 4, y, half, 20).build());
    }

    /** Sends the currently-picked item + count as a new (or updated) ammo grant entry; the grid picks it up once the server's sync echo arrives (see {@link #tick()}). */
    private void addEntry() {
        if (pendingAmmoItem == null) {
            return;
        }
        int count = countValue.isBlank() ? 1 : Integer.parseInt(countValue);
        if (count <= 0) {
            return;
        }
        command("class whitelist ammo " + slot.key() + " " + item + " " + pendingAmmoItem + " " + count);
        pendingAmmoItem = null;
        countValue = "";
    }

    private void removeEntry(ResourceLocation ammoItem) {
        command("class whitelist ammo " + slot.key() + " " + item + " " + ammoItem + " 0");
    }

    /** Registers the item currently in the OP's hand (full NBT included) as a new ammo grant entry at count 1. */
    private void registerHeldAsAmmo() {
        UUID id = UUID.randomUUID();
        command("class whitelist register_held " + id);
        ResourceLocation variant = new ResourceLocation("classloadout", "variant_" + id);
        command("class whitelist ammo " + slot.key() + " " + item + " " + variant + " 1");
    }

    private static boolean isAir(ResourceLocation loc) {
        return "minecraft".equals(loc.getNamespace()) && "air".equals(loc.getPath());
    }

    private void command(String cmd) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(cmd);
        }
    }

    private int cellIndexAt(double mouseX, double mouseY) {
        int gridLeft = panelLeft + PAD;
        if (mouseX < gridLeft || mouseX >= gridLeft + GRID_COLS * CELL || mouseY < gridTop || mouseY >= gridTop + gridRows * CELL) {
            return -1;
        }
        int col = (int) ((mouseX - gridLeft) / CELL);
        int row = (int) ((mouseY - gridTop) / CELL);
        int index = row * GRID_COLS + col;
        return index < entries.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (HotbarBar.mouseClicked(minecraft, mouseX, mouseY)) {
            return true;
        }
        int index = cellIndexAt(mouseX, mouseY);
        if (index >= 0) {
            removeEntry(entries.get(index).getKey());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Don't hijack digit keys while the count box is focused.
        if (!countBox.isFocused() && HotbarBar.keyPressed(minecraft, keyCode, scanCode)) {
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
        graphics.drawString(this.font, this.title, l + PAD, t + 8, COLOR_TEXT);

        ItemStack itemStack = ItemResolver.resolve(item, LoadoutClientData.getItemVariants());
        graphics.drawString(this.font, itemStack != null ? itemStack.getHoverName() : Component.literal(item.toString()),
                l + PAD, t + HEADER_H + 2, COLOR_TEXT_DIM);

        int gridLeft = l + PAD;
        ItemStack hoveredStack = null;
        int hoveredX = 0;
        int hoveredY = 0;
        int hoveredCount = 0;
        for (int index = 0; index < entries.size(); index++) {
            int col = index % GRID_COLS;
            int row = index / GRID_COLS;
            int x = gridLeft + col * CELL;
            int y = gridTop + row * CELL;
            boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
            if (hovered) {
                graphics.fill(x, y, x + CELL, y + CELL, COLOR_HOVER);
            }
            Map.Entry<ResourceLocation, Integer> entry = entries.get(index);
            ItemStack resolved = ItemResolver.resolve(entry.getKey(), LoadoutClientData.getItemVariants());
            ItemStack stack = resolved != null ? resolved : new ItemStack(Items.BARRIER);
            graphics.renderItem(stack, x + (CELL - ICON) / 2, y + (CELL - ICON) / 2);
            Component countText = Component.literal(Integer.toString(entry.getValue()));
            graphics.drawString(this.font, countText, x + CELL - this.font.width(countText) - 1, y + CELL - 8,
                    0xFFFFFF, true);
            if (hovered) {
                hoveredStack = stack;
                hoveredX = mouseX;
                hoveredY = mouseY;
                hoveredCount = entry.getValue();
            }
        }
        if (entries.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("classloadout.gui.ammo_grant_none"),
                    gridLeft, gridTop + 4, COLOR_TEXT_DIM);
        }

        graphics.fill(pickerX, pickerY, pickerX + SLOT, pickerY + SLOT, COLOR_SLOT_BG);
        if (pendingAmmoItem != null) {
            ItemStack ammoStack = ItemResolver.resolve(pendingAmmoItem, LoadoutClientData.getItemVariants());
            if (ammoStack != null) {
                graphics.renderItem(ammoStack, pickerX + (SLOT - 16) / 2, pickerY + (SLOT - 16) / 2);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (hoveredStack != null) {
            Component name = hoveredStack.getHoverName().copy()
                    .append(Component.translatable("classloadout.gui.ammo_grant_remove", hoveredCount));
            graphics.renderTooltip(this.font, name, hoveredX, hoveredY);
        }

        HotbarBar.render(graphics, this.minecraft);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
