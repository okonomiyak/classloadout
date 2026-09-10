package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
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
import java.util.UUID;

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
    /** Same row shape as {@link PresetRow}, but for the player's own {@code /class mypreset} entries. */
    private final List<PresetRow> personalPresetRows = new ArrayList<>();
    /** 0 or 1 entries - the player's shared-preset "inbox" slot, see {@code LoadoutClientData#getSharedPreset}. */
    private final List<PresetRow> sharedPresetRows = new ArrayList<>();

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
        List<LoadoutSyncPacket.Entry> myPresets = LoadoutClientData.getPersonalPresets();
        LoadoutSyncPacket.Entry sharedEntry = LoadoutClientData.getSharedPreset();
        panelWidth = Math.min(360, this.width - 16);
        panelHeight = Math.min(HEADER_H + PAD * 2 + 20 + 2 * SLOT + 8 + 34 + 16 + presetShown * PRESET_ROW_H
                        + 14 + myPresets.size() * PRESET_ROW_H + 30
                        + (sharedEntry == null ? 0 : 14 + PRESET_ROW_H) + 30 + 30,
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

        personalPresetRows.clear();
        y += 14;
        for (LoadoutSyncPacket.Entry entry : myPresets) {
            personalPresetRows.add(new PresetRow(entry, y));
            addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.apply"),
                            b -> command("class mypreset select " + entry.id()))
                    .bounds(panelLeft + panelWidth - PAD - 88, y + (PRESET_ROW_H - 20) / 2, 44, 20).build());
            addRenderableWidget(Button.builder(Component.literal("#"), b -> showCode(entry.id()))
                    .bounds(panelLeft + panelWidth - PAD - 42, y + (PRESET_ROW_H - 20) / 2, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("x"),
                            b -> rawCommand("class mypreset delete " + entry.id()))
                    .bounds(panelLeft + panelWidth - PAD - 20, y + (PRESET_ROW_H - 20) / 2, 20, 20).build());
            y += PRESET_ROW_H;
        }
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.mypreset_save"),
                        b -> minecraft.setScreen(new PersonalPresetNameScreen(this)))
                .bounds(panelLeft + PAD, y, panelWidth - 2 * PAD, 20).build());
        y += 20 + 10;

        sharedPresetRows.clear();
        if (sharedEntry != null) {
            y += 14;
            sharedPresetRows.add(new PresetRow(sharedEntry, y));
            addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.apply"),
                            b -> command("class mypreset selectshared"))
                    .bounds(panelLeft + panelWidth - PAD - 78, y + (PRESET_ROW_H - 20) / 2, 56, 20).build());
            addRenderableWidget(Button.builder(Component.literal("x"),
                            b -> rawCommand("class mypreset clearshared"))
                    .bounds(panelLeft + panelWidth - PAD - 20, y + (PRESET_ROW_H - 20) / 2, 20, 20).build());
            y += PRESET_ROW_H;
        }
        y += 10;
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.mypreset_receive_button"),
                        b -> minecraft.setScreen(new ReceiveSharedPresetScreen(this)))
                .bounds(panelLeft + PAD, y, panelWidth - 2 * PAD, 20).build());

        int bottomY = panelTop + panelHeight - PAD - 20;
        int third = (panelWidth - 2 * PAD - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.shop_button"),
                        b -> minecraft.setScreen(new ShopScreen(this)))
                .bounds(panelLeft + PAD, bottomY, third, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.class_unselect"),
                        b -> command("class clear"))
                .bounds(panelLeft + PAD + third + 4, bottomY, third, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.close"),
                        b -> minecraft.setScreen(returnTo))
                .bounds(panelLeft + PAD + (third + 4) * 2, bottomY, third, 20).build());
    }

    @Override
    public void tick() {
        if (dataRevision != LoadoutClientData.getRevision()) {
            this.init(this.minecraft, this.width, this.height);
        }
    }

    /** Picker is unrestricted (full catalog, purchase gate included) while an OP has temporarily disabled whitelist enforcement - see {@code LoadoutManager#isWhitelistEnabled}. */
    private Button slotButton(int x, int y, LoadoutSlot slot) {
        return Button.builder(Component.empty(), b -> minecraft.setScreen(new ItemPickerScreen(this,
                        loc -> command("class assign " + slot.key() + " " + loc),
                        LoadoutClientData.isWhitelistEnabled() ? purchasable(LoadoutClientData.getWhitelist(slot)) : null)))
                .bounds(x, y, SLOT, SLOT).build();
    }

    /** Drops priced items the player hasn't bought yet (see {@code /class buy}) - picking one would just silently fail to equip server-side anyway (see {@code LoadoutManager#canEquip}), so don't offer it as a choice at all. */
    private static List<ResourceLocation> purchasable(List<ResourceLocation> items) {
        List<ResourceLocation> result = new ArrayList<>();
        for (ResourceLocation loc : items) {
            int price = LoadoutClientData.getPrices().getOrDefault(loc, 0);
            if (price <= 0 || LoadoutClientData.isPurchased(loc)) {
                result.add(loc);
            }
        }
        return result;
    }

    private void command(String cmd) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(immediate ? cmd : cmd + " defer");
        }
    }

    /** Same as {@link #command}, but without the immediate/defer suffixing - for commands with no such variant (e.g. {@code /class mypreset delete}, which doesn't equip anything). */
    private void rawCommand(String cmd) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(cmd);
        }
    }

    /**
     * Purely client-side (no server round trip needed - the id is already known locally): prints
     * the preset's own id as a click-to-copy chat message, for the player to hand out however they
     * like (chat, Discord, ...) so someone else can redeem it via {@code /class mypreset receive}.
     */
    private void showCode(UUID id) {
        if (minecraft == null) {
            return;
        }
        Component code = Component.translatable("classloadout.gui.mypreset_code", id.toString())
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, id.toString()))
                        .withColor(ChatFormatting.AQUA));
        minecraft.gui.getChat().addMessage(code);
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
            drawPresetRow(graphics, row);
        }

        if (!personalPresetRows.isEmpty()) {
            int myY = personalPresetRows.get(0).y() - 14;
            graphics.drawString(this.font, Component.translatable("classloadout.gui.mypresets_section"),
                    l + PAD, myY, COLOR_TEXT_DIM);
        }
        for (PresetRow row : personalPresetRows) {
            drawPresetRow(graphics, row);
        }

        if (!sharedPresetRows.isEmpty()) {
            int sharedY = sharedPresetRows.get(0).y() - 14;
            graphics.drawString(this.font, Component.translatable("classloadout.gui.sharedpreset_section"),
                    l + PAD, sharedY, COLOR_TEXT_DIM);
        }
        for (PresetRow row : sharedPresetRows) {
            drawPresetRow(graphics, row);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPresetRow(GuiGraphics graphics, PresetRow row) {
        graphics.drawString(this.font, row.entry().name(), panelLeft + PAD, row.y(), COLOR_TEXT);
        ResourceLocation[] slots = {row.entry().main(), row.entry().sidearm(), row.entry().throwable(),
                row.entry().gadget(), row.entry().gadget2(), row.entry().melee(),
                row.entry().helmet(), row.entry().chestplate(), row.entry().leggings(), row.entry().boots()};
        for (int i = 0; i < slots.length; i++) {
            int x = panelLeft + PAD + i * (ICON + 4);
            int y = row.y() + 14;
            drawSmallIcon(graphics, x, y, slots[i]);
        }
    }

    /**
     * A saved item no longer on {@code slot}'s whitelist (an OP edit or a deleted variant, since
     * the assignment) - or a priced item the player hasn't bought via the {@link ShopScreen} (see
     * {@code LoadoutManager#canEquip}) - renders as empty here too, matching {@code
     * ServerEvents#equipLoadout}: it won't actually be equipped, so showing its icon here would be
     * misleading. Both checks are skipped for a locked slot (OP force-assigned, bypasses both by
     * design - see {@code LoadoutManager#lockSlot}), which instead gets a red outline so the
     * player can see at a glance which slots they can't self-service-change.
     */
    private void drawSlotIcon(GuiGraphics graphics, int x, int y, @Nullable ResourceLocation loc, LoadoutSlot slot,
            String labelKey) {
        boolean locked = LoadoutClientData.isLocked(slot);
        graphics.fill(x, y, x + SLOT, y + SLOT, COLOR_SLOT_BG);
        if (loc != null && !locked) {
            boolean whitelisted = !LoadoutClientData.isWhitelistEnabled() || LoadoutClientData.getWhitelist(slot).contains(loc);
            int price = LoadoutClientData.getPrices().getOrDefault(loc, 0);
            boolean purchased = price <= 0 || LoadoutClientData.isPurchased(loc);
            if (!whitelisted || !purchased) {
                loc = null;
            }
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
