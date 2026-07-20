package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import uk.iwaservice.classloadout.client.LoadoutClientData;
import uk.iwaservice.classloadout.loadout.LoadoutSlot;
import uk.iwaservice.classloadout.network.LoadoutSyncPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Player-facing loadout screen, reachable only from the death screen's
 * "Loadout" button (see {@link uk.iwaservice.classloadout.ClientEvents}).
 * Two independent things: "My Loadout" - five slots the player assigns
 * directly by clicking an {@link ItemPickerScreen} - and "Presets", a
 * read-only list of admin-defined classes each of which can be applied as a
 * starting point for the player's own loadout (still freely editable after).
 */
public class LoadoutScreen extends Screen {

    private static final int PAD = 12;
    private static final int HEADER_H = 24;
    private static final int SLOT = 32;
    private static final int PRESET_ROW_H = 40;
    private static final int MAX_PRESET_ROWS = 5;
    private static final int ICON = 16;

    private static final int COLOR_PANEL_BG = 0xF4222222;
    private static final int COLOR_HEADER_BG = 0xFF1F2333;
    private static final int COLOR_OUTLINE = 0xFF454A66;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xA0A8C0;
    private static final int COLOR_SLOT_BG = 0x60000000;
    private static final int COLOR_SEPARATOR = 0x28FFFFFF;

    private record PresetRow(LoadoutSyncPacket.Entry entry, int y) {}

    private final Screen returnTo;
    private final List<PresetRow> presetRows = new ArrayList<>();

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int dataRevision = -1;

    private final int[] slotX = new int[5];
    private int slotY;

    public LoadoutScreen(Screen returnTo) {
        super(Component.translatable("classloadout.gui.loadout_title"));
        this.returnTo = returnTo;
    }

    @Override
    protected void init() {
        List<LoadoutSyncPacket.Entry> classes = LoadoutClientData.getClasses();
        int presetShown = Math.min(classes.size(), MAX_PRESET_ROWS);
        panelWidth = Math.min(360, this.width - 16);
        panelHeight = Math.min(HEADER_H + PAD * 2 + 20 + SLOT + 34 + 16 + presetShown * PRESET_ROW_H + 30,
                this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        dataRevision = LoadoutClientData.getRevision();

        int y = panelTop + HEADER_H + PAD + 14;
        int startX = panelLeft + PAD;
        for (int i = 0; i < 5; i++) {
            slotX[i] = startX + i * (SLOT + 8);
        }
        slotY = y;
        addRenderableWidget(slotButton(slotX[0], slotY, LoadoutSlot.MAIN));
        addRenderableWidget(slotButton(slotX[1], slotY, LoadoutSlot.SIDEARM));
        addRenderableWidget(slotButton(slotX[2], slotY, LoadoutSlot.THROWABLE));
        addRenderableWidget(slotButton(slotX[3], slotY, LoadoutSlot.GADGET));
        addRenderableWidget(slotButton(slotX[4], slotY, LoadoutSlot.MELEE));
        y += SLOT + 34;

        presetRows.clear();
        y += 14;
        for (int i = 0; i < presetShown; i++) {
            LoadoutSyncPacket.Entry entry = classes.get(i);
            presetRows.add(new PresetRow(entry, y));
            addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.apply"),
                            b -> command("class select " + entry.id()))
                    .bounds(panelLeft + panelWidth - PAD - 56, y + (PRESET_ROW_H - 20) / 2, 56, 20).build());
            y += PRESET_ROW_H;
        }

        int bottomY = panelTop + panelHeight - PAD - 20;
        int half = (panelWidth - 2 * PAD - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.class_unselect"),
                        b -> command("class clear"))
                .bounds(panelLeft + PAD, bottomY, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.close"),
                        b -> minecraft.setScreen(returnTo))
                .bounds(panelLeft + PAD + half + 4, bottomY, half, 20).build());
    }

    @Override
    public void tick() {
        if (dataRevision != LoadoutClientData.getRevision()) {
            this.init(this.minecraft, this.width, this.height);
        }
    }

    private Button slotButton(int x, int y, LoadoutSlot slot) {
        return Button.builder(Component.empty(), b -> minecraft.setScreen(new ItemPickerScreen(this,
                        loc -> command("class assign " + slot.key() + " " + loc),
                        LoadoutClientData.getWhitelist(slot))))
                .bounds(x, y, SLOT, SLOT).build();
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
        graphics.drawString(this.font, this.title, l + PAD, t + 8, COLOR_TEXT);

        graphics.drawString(this.font, Component.translatable("classloadout.gui.my_loadout"),
                l + PAD, t + HEADER_H + PAD, COLOR_TEXT_DIM);

        LoadoutSyncPacket.PersonalData personal = LoadoutClientData.getPersonal();
        drawSlotIcon(graphics, slotX[0], slotY, personal.main(), "classloadout.gui.slot_main");
        drawSlotIcon(graphics, slotX[1], slotY, personal.sidearm(), "classloadout.gui.slot_sidearm");
        drawSlotIcon(graphics, slotX[2], slotY, personal.throwable(), "classloadout.gui.slot_throwable");
        drawSlotIcon(graphics, slotX[3], slotY, personal.gadget(), "classloadout.gui.slot_gadget");
        drawSlotIcon(graphics, slotX[4], slotY, personal.melee(), "classloadout.gui.slot_melee");

        int sepY = slotY + SLOT + 20;
        graphics.fill(l + PAD, sepY, r - PAD, sepY + 1, COLOR_SEPARATOR);
        graphics.drawString(this.font, Component.translatable("classloadout.gui.presets_section"),
                l + PAD, sepY + 6, COLOR_TEXT_DIM);

        if (presetRows.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("classloadout.gui.class_none_defined"),
                    l + PAD, sepY + 20, COLOR_TEXT_DIM);
        }
        for (PresetRow row : presetRows) {
            graphics.drawString(this.font, row.entry().name(), l + PAD, row.y(), COLOR_TEXT);
            ResourceLocation[] slots = {row.entry().main(), row.entry().sidearm(), row.entry().throwable(),
                    row.entry().gadget(), row.entry().melee()};
            for (int i = 0; i < slots.length; i++) {
                int x = l + PAD + i * (ICON + 4);
                int y = row.y() + 14;
                drawSmallIcon(graphics, x, y, slots[i]);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawSlotIcon(GuiGraphics graphics, int x, int y, @Nullable ResourceLocation loc, String labelKey) {
        graphics.fill(x, y, x + SLOT, y + SLOT, COLOR_SLOT_BG);
        if (loc != null) {
            Item item = ForgeRegistries.ITEMS.getValue(loc);
            if (item != null) {
                graphics.renderItem(new ItemStack(item), x + (SLOT - ICON) / 2, y + (SLOT - ICON) / 2);
            } else {
                graphics.drawCenteredString(this.font, "?", x + SLOT / 2, y + SLOT / 2 - 4, 0xFFFF5555);
            }
        }
        graphics.drawCenteredString(this.font, Component.translatable(labelKey), x + SLOT / 2, y + SLOT + 3, COLOR_TEXT_DIM);
    }

    private void drawSmallIcon(GuiGraphics graphics, int x, int y, @Nullable ResourceLocation loc) {
        if (loc == null) {
            graphics.fill(x, y, x + ICON, y + ICON, 0x30FFFFFF);
            return;
        }
        Item item = LoadoutClientData.isItemAvailable(loc) ? ForgeRegistries.ITEMS.getValue(loc) : null;
        graphics.renderItem(new ItemStack(item != null ? item : Items.BARRIER), x, y);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
