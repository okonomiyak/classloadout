package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import uk.iwaservice.classloadout.ItemResolver;
import uk.iwaservice.classloadout.client.LoadoutClientData;
import uk.iwaservice.classloadout.loadout.LoadoutSlot;
import uk.iwaservice.classloadout.network.LoadoutSyncPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Player-facing loadout screen, reachable only from the death screen's
 * "Loadout" button (see {@link uk.iwaservice.classloadout.ClientEvents}).
 * Two independent things: "My Loadout" - ten slots the player assigns
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
    private static final int COLOR_LOCKED_OUTLINE = 0xFFFF5555;

    private record PresetRow(LoadoutSyncPacket.Entry entry, int y) {}

    private final Screen returnTo;
    /** True (the regular loadout station / death screen): changes equip into the hotbar right away. False (the deferred loadout locker): only the saved data changes, taking effect on the next respawn. */
    private final boolean immediate;
    private final List<PresetRow> presetRows = new ArrayList<>();

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int dataRevision = -1;

    private final int[] slotX = new int[6];
    private int slotY;
    private final int[] armorX = new int[4];
    private int armorY;

    /** Immediate mode (regular loadout station / death screen). */
    public LoadoutScreen(Screen returnTo) {
        this(returnTo, true);
    }

    public LoadoutScreen(Screen returnTo, boolean immediate) {
        super(Component.translatable(immediate ? "classloadout.gui.loadout_title" : "classloadout.gui.loadout_title_deferred"));
        this.returnTo = returnTo;
        this.immediate = immediate;
    }

    @Override
    protected void init() {
        List<LoadoutSyncPacket.Entry> classes = LoadoutClientData.getClasses();
        int presetShown = Math.min(classes.size(), MAX_PRESET_ROWS);
        panelWidth = Math.min(360, this.width - 16);
        panelHeight = Math.min(HEADER_H + PAD * 2 + 20 + 2 * SLOT + 8 + 34 + 16 + presetShown * PRESET_ROW_H + 30,
                this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        dataRevision = LoadoutClientData.getRevision();

        int y = panelTop + HEADER_H + PAD + 14;
        int startX = panelLeft + PAD;
        for (int i = 0; i < slotX.length; i++) {
            slotX[i] = startX + i * (SLOT + 8);
        }
        slotY = y;
        addRenderableWidget(slotButton(slotX[0], slotY, LoadoutSlot.MAIN));
        addRenderableWidget(slotButton(slotX[1], slotY, LoadoutSlot.SIDEARM));
        addRenderableWidget(slotButton(slotX[2], slotY, LoadoutSlot.THROWABLE));
        addRenderableWidget(slotButton(slotX[3], slotY, LoadoutSlot.GADGET));
        addRenderableWidget(slotButton(slotX[4], slotY, LoadoutSlot.GADGET2));
        addRenderableWidget(slotButton(slotX[5], slotY, LoadoutSlot.MELEE));
        y += SLOT + 8;

        for (int i = 0; i < armorX.length; i++) {
            armorX[i] = startX + i * (SLOT + 8);
        }
        armorY = y;
        addRenderableWidget(slotButton(armorX[0], armorY, LoadoutSlot.HELMET));
        addRenderableWidget(slotButton(armorX[1], armorY, LoadoutSlot.CHESTPLATE));
        addRenderableWidget(slotButton(armorX[2], armorY, LoadoutSlot.LEGGINGS));
        addRenderableWidget(slotButton(armorX[3], armorY, LoadoutSlot.BOOTS));
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
            minecraft.player.connection.sendCommand(immediate ? cmd : cmd + " defer");
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
        drawSlotIcon(graphics, slotX[0], slotY, personal.main(), LoadoutSlot.MAIN, "classloadout.gui.slot_main");
        drawSlotIcon(graphics, slotX[1], slotY, personal.sidearm(), LoadoutSlot.SIDEARM, "classloadout.gui.slot_sidearm");
        drawSlotIcon(graphics, slotX[2], slotY, personal.throwable(), LoadoutSlot.THROWABLE, "classloadout.gui.slot_throwable");
        drawSlotIcon(graphics, slotX[3], slotY, personal.gadget(), LoadoutSlot.GADGET, "classloadout.gui.slot_gadget");
        drawSlotIcon(graphics, slotX[4], slotY, personal.gadget2(), LoadoutSlot.GADGET2, "classloadout.gui.slot_gadget2");
        drawSlotIcon(graphics, slotX[5], slotY, personal.melee(), LoadoutSlot.MELEE, "classloadout.gui.slot_melee");
        drawSlotIcon(graphics, armorX[0], armorY, personal.helmet(), LoadoutSlot.HELMET, "classloadout.gui.slot_helmet");
        drawSlotIcon(graphics, armorX[1], armorY, personal.chestplate(), LoadoutSlot.CHESTPLATE, "classloadout.gui.slot_chestplate");
        drawSlotIcon(graphics, armorX[2], armorY, personal.leggings(), LoadoutSlot.LEGGINGS, "classloadout.gui.slot_leggings");
        drawSlotIcon(graphics, armorX[3], armorY, personal.boots(), LoadoutSlot.BOOTS, "classloadout.gui.slot_boots");

        int sepY = armorY + SLOT + 20;
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
                    row.entry().gadget(), row.entry().gadget2(), row.entry().melee(),
                    row.entry().helmet(), row.entry().chestplate(), row.entry().leggings(), row.entry().boots()};
            for (int i = 0; i < slots.length; i++) {
                int x = l + PAD + i * (ICON + 4);
                int y = row.y() + 14;
                drawSmallIcon(graphics, x, y, slots[i]);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * A saved item no longer on {@code slot}'s whitelist (an OP edit or a deleted variant, since
     * the assignment) renders as empty here too, matching {@code ServerEvents#equipLoadout} - it
     * won't actually be equipped, so showing its icon here would be misleading. That whitelist
     * check is skipped for a locked slot (OP force-assigned, bypasses the whitelist by design -
     * see {@code LoadoutManager#lockSlot}), which instead gets a red outline so the player can
     * see at a glance which slots they can't self-service-change.
     */
    private void drawSlotIcon(GuiGraphics graphics, int x, int y, @Nullable ResourceLocation loc, LoadoutSlot slot,
            String labelKey) {
        boolean locked = LoadoutClientData.isLocked(slot);
        graphics.fill(x, y, x + SLOT, y + SLOT, COLOR_SLOT_BG);
        if (loc != null && !locked && !LoadoutClientData.getWhitelist(slot).contains(loc)) {
            loc = null;
        }
        if (loc != null) {
            ItemStack stack = ItemResolver.resolve(loc, LoadoutClientData.getItemVariants());
            if (stack != null) {
                graphics.renderItem(stack, x + (SLOT - ICON) / 2, y + (SLOT - ICON) / 2);
            } else {
                graphics.drawCenteredString(this.font, "?", x + SLOT / 2, y + SLOT / 2 - 4, 0xFFFF5555);
            }
        }
        if (locked) {
            graphics.renderOutline(x - 1, y - 1, SLOT + 2, SLOT + 2, COLOR_LOCKED_OUTLINE);
        }
        graphics.drawCenteredString(this.font, Component.translatable(labelKey), x + SLOT / 2, y + SLOT + 3, COLOR_TEXT_DIM);
    }

    private void drawSmallIcon(GuiGraphics graphics, int x, int y, @Nullable ResourceLocation loc) {
        if (loc == null) {
            graphics.fill(x, y, x + ICON, y + ICON, 0x30FFFFFF);
            return;
        }
        ItemStack resolved = ItemResolver.resolve(loc, LoadoutClientData.getItemVariants());
        graphics.renderItem(resolved != null ? resolved : new ItemStack(Items.BARRIER), x, y);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
