package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import uk.iwaservice.classloadout.client.LoadoutClientData;
import uk.iwaservice.classloadout.loadout.LoadoutSlot;
import uk.iwaservice.classloadout.network.LoadoutSyncPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * OP-only class editor, opened exclusively via the {@link uk.iwaservice.classloadout.network.OpenClassEditorPacket}
 * that follows a successful {@code /class editor} command. Left column
 * lists existing classes (from {@link LoadoutClientData}); the right column
 * edits one class at a time in local "pending" state that is only committed
 * to the server, atomically, when Save is pressed.
 */
public class ClassEditorScreen extends Screen {

    private static final int PAD = 10;
    /** Title bar only - the two nav button rows render below it (see {@link #contentTop}), not inside it, so the title text never shares space with a button. */
    private static final int HEADER_H = 24;
    private static final int LEFT_W = 150;
    private static final int ROW_H = 22;
    private static final int MAX_ROWS = 8;
    private static final int SLOT = 36;

    private static final int COLOR_OUTLINE = FlatButton.COLOR_CARD_LINE;
    private static final int COLOR_TEXT = FlatButton.COLOR_TEXT;
    private static final int COLOR_TEXT_DIM = FlatButton.COLOR_TEXT_DIM;
    private static final int COLOR_SLOT_BG = FlatButton.COLOR_CARD_BG;

    private record RowInfo(LoadoutSyncPacket.Entry entry, int y) {}

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    /** Where the left/right columns' content starts - below the title bar AND the two nav button rows (see {@link #init}). */
    private int contentTop;
    private int dataRevision = -1;

    private final List<RowInfo> listRows = new ArrayList<>();

    // --- pending edit state (only sent to the server on Save) ---
    private boolean editing;
    private boolean editingExisting;
    private UUID pendingId;
    private String pendingName = "";
    @Nullable
    private ResourceLocation pendingIcon;
    @Nullable
    private ResourceLocation pendingMain;
    @Nullable
    private ResourceLocation pendingSidearm;
    @Nullable
    private ResourceLocation pendingThrowable;
    @Nullable
    private ResourceLocation pendingGadget;
    @Nullable
    private ResourceLocation pendingGadget2;
    @Nullable
    private ResourceLocation pendingMelee;
    @Nullable
    private ResourceLocation pendingHelmet;
    @Nullable
    private ResourceLocation pendingChestplate;
    @Nullable
    private ResourceLocation pendingLeggings;
    @Nullable
    private ResourceLocation pendingBoots;

    private int iconX;
    private int iconY;
    private final int[] slotX = new int[6];
    private int slotY;
    private final int[] armorX = new int[4];
    private int armorY;
    private EditBox nameBox;

    public ClassEditorScreen() {
        super(Component.translatable("classloadout.gui.class_editor_title"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(520, this.width - 16);
        panelHeight = Math.min(412, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        dataRevision = LoadoutClientData.getRevision();

        // Two rows of three nav buttons, evenly filling the panel width, below the title bar.
        int navGap = 4;
        int navW = (panelWidth - 2 * PAD - 2 * navGap) / 3;
        int navRow1Y = panelTop + HEADER_H + 4;
        int navRow2Y = navRow1Y + 20 + navGap;
        contentTop = navRow2Y + 20 + PAD;
        int nx = panelLeft + PAD;
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.protect_button"), b -> minecraft.setScreen(new ProtectedItemsEditorScreen(this)), nx, navRow1Y, navW, 20));
        nx += navW + navGap;
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.whitelist_button"), b -> minecraft.setScreen(new WhitelistEditorScreen(this)), nx, navRow1Y, navW, 20));
        nx += navW + navGap;
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.spawnkit_button"), b -> minecraft.setScreen(new SpawnKitEditorScreen(this)), nx, navRow1Y, navW, 20));
        nx = panelLeft + PAD;
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.hammerblocks_button"), b -> minecraft.setScreen(new HammerBlocksEditorScreen(this)), nx, navRow2Y, navW, 20));
        nx += navW + navGap;
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.force_button"), b -> minecraft.setScreen(new ForceLoadoutScreen(this)), nx, navRow2Y, navW, 20));
        nx += navW + navGap;
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.price_button"), b -> minecraft.setScreen(new PriceEditorScreen(this)), nx, navRow2Y, navW, 20));

        buildLeftColumn();
        if (editing) {
            buildRightColumn();
        }
    }

    @Override
    public void tick() {
        if (dataRevision != LoadoutClientData.getRevision()) {
            this.init(this.minecraft, this.width, this.height);
        }
    }

    private void buildLeftColumn() {
        listRows.clear();
        List<LoadoutSyncPacket.Entry> classes = LoadoutClientData.getClasses();
        int y = contentTop;
        int shown = Math.min(classes.size(), MAX_ROWS);
        for (int i = 0; i < shown; i++) {
            LoadoutSyncPacket.Entry entry = classes.get(i);
            listRows.add(new RowInfo(entry, y));
            int bx = panelLeft + LEFT_W - 2 * 40;
            addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.edit"), b -> startEdit(entry), bx, y, 40, 18));
            addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.delete"), b -> deleteClass(entry.id()), bx + 40, y, 40, 18));
            y += ROW_H;
        }
        y += 6;
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.class_new"), b -> startNew(), panelLeft + PAD, y, LEFT_W - PAD, 20));
    }

    private void startNew() {
        editing = true;
        editingExisting = false;
        pendingId = UUID.randomUUID();
        pendingName = "";
        pendingIcon = null;
        pendingMain = null;
        pendingSidearm = null;
        pendingThrowable = null;
        pendingGadget = null;
        pendingGadget2 = null;
        pendingMelee = null;
        pendingHelmet = null;
        pendingChestplate = null;
        pendingLeggings = null;
        pendingBoots = null;
        this.init(this.minecraft, this.width, this.height);
    }

    private void startEdit(LoadoutSyncPacket.Entry entry) {
        editing = true;
        editingExisting = true;
        pendingId = entry.id();
        pendingName = entry.name();
        pendingIcon = entry.icon();
        pendingMain = entry.main();
        pendingSidearm = entry.sidearm();
        pendingThrowable = entry.throwable();
        pendingGadget = entry.gadget();
        pendingGadget2 = entry.gadget2();
        pendingMelee = entry.melee();
        pendingHelmet = entry.helmet();
        pendingChestplate = entry.chestplate();
        pendingLeggings = entry.leggings();
        pendingBoots = entry.boots();
        this.init(this.minecraft, this.width, this.height);
    }

    private void cancelEdit() {
        editing = false;
        this.init(this.minecraft, this.width, this.height);
    }

    private void deleteClass(UUID id) {
        command("class preset " + id + " delete");
        if (editing && id.equals(pendingId)) {
            editing = false;
        }
        this.init(this.minecraft, this.width, this.height);
    }

    /**
     * Sent as one command per field (name, then each of the ten slots) rather than a single
     * command carrying all of them - a held-item variant id is long enough (~58 chars) that
     * several of them plus the name in one command could exceed the 256-character limit vanilla
     * enforces on chat/command packets, disconnecting the client. See {@code ClassCommand
     * #saveName}/{@code #saveSlot}.
     */
    private void save() {
        if (pendingName.isBlank()) {
            return;
        }
        command("class preset " + pendingId + " name " + pendingName);
        command("class preset " + pendingId + " slot icon " + rl(pendingIcon));
        command("class preset " + pendingId + " slot main " + rl(pendingMain));
        command("class preset " + pendingId + " slot sidearm " + rl(pendingSidearm));
        command("class preset " + pendingId + " slot throwable " + rl(pendingThrowable));
        command("class preset " + pendingId + " slot gadget " + rl(pendingGadget));
        command("class preset " + pendingId + " slot gadget2 " + rl(pendingGadget2));
        command("class preset " + pendingId + " slot melee " + rl(pendingMelee));
        command("class preset " + pendingId + " slot helmet " + rl(pendingHelmet));
        command("class preset " + pendingId + " slot chestplate " + rl(pendingChestplate));
        command("class preset " + pendingId + " slot leggings " + rl(pendingLeggings));
        command("class preset " + pendingId + " slot boots " + rl(pendingBoots));
        editing = false;
        this.init(this.minecraft, this.width, this.height);
    }

    private static String rl(@Nullable ResourceLocation loc) {
        return loc == null ? "minecraft:air" : loc.toString();
    }

    private void buildRightColumn() {
        int rightX = panelLeft + LEFT_W + PAD;
        int rightWidth = panelWidth - LEFT_W - 2 * PAD;
        int y = contentTop;

        nameBox = new EditBox(this.font, rightX, y, rightWidth, 18, Component.translatable("classloadout.gui.class_name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(pendingName);
        nameBox.setResponder(s -> pendingName = s);
        addRenderableWidget(nameBox);
        y += 26;

        iconX = rightX;
        iconY = y;
        addRenderableWidget(slotButton(iconX, iconY, loc -> pendingIcon = loc, null));
        y += SLOT + 22;

        for (int i = 0; i < slotX.length; i++) {
            slotX[i] = rightX + i * (SLOT + 8);
        }
        slotY = y;
        addRenderableWidget(slotButton(slotX[0], slotY, loc -> pendingMain = loc, LoadoutClientData.getWhitelist(LoadoutSlot.MAIN)));
        addRenderableWidget(slotButton(slotX[1], slotY, loc -> pendingSidearm = loc, LoadoutClientData.getWhitelist(LoadoutSlot.SIDEARM)));
        addRenderableWidget(slotButton(slotX[2], slotY, loc -> pendingThrowable = loc, LoadoutClientData.getWhitelist(LoadoutSlot.THROWABLE)));
        addRenderableWidget(slotButton(slotX[3], slotY, loc -> pendingGadget = loc, LoadoutClientData.getWhitelist(LoadoutSlot.GADGET)));
        addRenderableWidget(slotButton(slotX[4], slotY, loc -> pendingGadget2 = loc, LoadoutClientData.getWhitelist(LoadoutSlot.GADGET2)));
        addRenderableWidget(slotButton(slotX[5], slotY, loc -> pendingMelee = loc, LoadoutClientData.getWhitelist(LoadoutSlot.MELEE)));
        y += SLOT + 22;

        for (int i = 0; i < armorX.length; i++) {
            armorX[i] = rightX + i * (SLOT + 8);
        }
        armorY = y;
        addRenderableWidget(slotButton(armorX[0], armorY, loc -> pendingHelmet = loc, LoadoutClientData.getWhitelist(LoadoutSlot.HELMET)));
        addRenderableWidget(slotButton(armorX[1], armorY, loc -> pendingChestplate = loc, LoadoutClientData.getWhitelist(LoadoutSlot.CHESTPLATE)));
        addRenderableWidget(slotButton(armorX[2], armorY, loc -> pendingLeggings = loc, LoadoutClientData.getWhitelist(LoadoutSlot.LEGGINGS)));
        addRenderableWidget(slotButton(armorX[3], armorY, loc -> pendingBoots = loc, LoadoutClientData.getWhitelist(LoadoutSlot.BOOTS)));
        y += SLOT + 30;

        int bw = (rightWidth - 8) / (editingExisting ? 3 : 2);
        int bx = rightX;
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.save"), b -> save(), bx, y, bw, 20));
        bx += bw + 4;
        if (editingExisting) {
            addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.delete"), b -> deleteClass(pendingId), bx, y, bw, 20));
            bx += bw + 4;
        }
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.cancel"), b -> cancelEdit(), bx, y, bw, 20));
    }

    /** {@code restrictTo} null means unrestricted (only used for the icon slot, which isn't a real loadout slot); the ten gear/armor slots pass that slot's whitelist so a preset can't be built from items players couldn't otherwise be assigned. */
    private Button slotButton(int x, int y, Consumer<ResourceLocation> setter, @Nullable List<ResourceLocation> restrictTo) {
        return new FlatButton(Component.empty(), b -> minecraft.setScreen(new ItemPickerScreen(this, loc -> setter.accept(isAir(loc) ? null : loc), restrictTo)), x, y, SLOT, SLOT);
    }

    private static boolean isAir(ResourceLocation loc) {
        return "minecraft".equals(loc.getNamespace()) && "air".equals(loc.getPath());
    }

    private void command(String cmd) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(cmd);
        }
    }

    // --- rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int l = panelLeft;
        int t = panelTop;
        int r = l + panelWidth;
        int b = t + panelHeight;
        FlatButton.drawPanel(graphics, l, t, panelWidth, panelHeight);
        graphics.fill(l, t + HEADER_H, r, t + HEADER_H + 1, FlatButton.COLOR_CARD_LINE);
        graphics.fill(l + LEFT_W, contentTop - PAD, l + LEFT_W + 1, b, COLOR_OUTLINE);
        graphics.drawString(this.font, this.title, l + PAD, t + 8, FlatButton.COLOR_TEXT_DIM);

        for (RowInfo row : listRows) {
            ResourceLocation icon = row.entry().icon();
            if (icon != null) {
                ItemStack stack = LoadoutClientData.displayStack(icon);
                if (stack != null) {
                    graphics.renderItem(stack, l + PAD, row.y() + 1);
                }
            }
            graphics.drawString(this.font, trim(row.entry().name(), 8), l + PAD + 18, row.y() + 5, COLOR_TEXT);
        }

        if (editing) {
            drawSlotIcon(graphics, iconX, iconY, pendingIcon, "classloadout.gui.slot_icon");
            drawSlotIcon(graphics, slotX[0], slotY, pendingMain, "classloadout.gui.slot_main");
            drawSlotIcon(graphics, slotX[1], slotY, pendingSidearm, "classloadout.gui.slot_sidearm");
            drawSlotIcon(graphics, slotX[2], slotY, pendingThrowable, "classloadout.gui.slot_throwable");
            drawSlotIcon(graphics, slotX[3], slotY, pendingGadget, "classloadout.gui.slot_gadget");
            drawSlotIcon(graphics, slotX[4], slotY, pendingGadget2, "classloadout.gui.slot_gadget2");
            drawSlotIcon(graphics, slotX[5], slotY, pendingMelee, "classloadout.gui.slot_melee");
            drawSlotIcon(graphics, armorX[0], armorY, pendingHelmet, "classloadout.gui.slot_helmet");
            drawSlotIcon(graphics, armorX[1], armorY, pendingChestplate, "classloadout.gui.slot_chestplate");
            drawSlotIcon(graphics, armorX[2], armorY, pendingLeggings, "classloadout.gui.slot_leggings");
            drawSlotIcon(graphics, armorX[3], armorY, pendingBoots, "classloadout.gui.slot_boots");
        } else {
            graphics.drawString(this.font, Component.translatable("classloadout.gui.class_editor_hint"),
                    l + LEFT_W + PAD, contentTop, COLOR_TEXT_DIM);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawSlotIcon(GuiGraphics graphics, int x, int y, @Nullable ResourceLocation loc, String labelKey) {
        graphics.fill(x, y, x + SLOT, y + SLOT, COLOR_SLOT_BG);
        if (loc != null) {
            ItemStack stack = LoadoutClientData.displayStack(loc);
            if (stack != null) {
                graphics.renderItem(stack, x + (SLOT - 16) / 2, y + (SLOT - 16) / 2);
            } else {
                graphics.drawCenteredString(this.font, "?", x + SLOT / 2, y + SLOT / 2 - 4, 0xFFFF5555);
            }
        }
        graphics.drawCenteredString(this.font, Component.translatable(labelKey), x + SLOT / 2, y + SLOT + 3, COLOR_TEXT_DIM);
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
