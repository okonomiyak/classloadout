package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import uk.iwaservice.classloadout.client.LoadoutClientData;
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
    private static final int HEADER_H = 24;
    private static final int LEFT_W = 150;
    private static final int ROW_H = 22;
    private static final int MAX_ROWS = 8;
    private static final int SLOT = 36;

    private static final int COLOR_PANEL_BG = 0xF4222222;
    private static final int COLOR_HEADER_BG = 0xFF1F2333;
    private static final int COLOR_OUTLINE = 0xFF454A66;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xA0A8C0;
    private static final int COLOR_SLOT_BG = 0x60000000;

    private record RowInfo(LoadoutSyncPacket.Entry entry, int y) {}

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
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
    private ResourceLocation pendingMelee;

    private int iconX;
    private int iconY;
    private final int[] slotX = new int[5];
    private int slotY;
    private EditBox nameBox;

    public ClassEditorScreen() {
        super(Component.translatable("classloadout.gui.class_editor_title"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(420, this.width - 16);
        panelHeight = Math.min(300, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        dataRevision = LoadoutClientData.getRevision();

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
        int y = panelTop + HEADER_H + PAD;
        int shown = Math.min(classes.size(), MAX_ROWS);
        for (int i = 0; i < shown; i++) {
            LoadoutSyncPacket.Entry entry = classes.get(i);
            listRows.add(new RowInfo(entry, y));
            int bx = panelLeft + LEFT_W - 2 * 40;
            addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.edit"),
                            b -> startEdit(entry))
                    .bounds(bx, y, 40, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.delete"),
                            b -> deleteClass(entry.id()))
                    .bounds(bx + 40, y, 40, 18).build());
            y += ROW_H;
        }
        y += 6;
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.class_new"), b -> startNew())
                .bounds(panelLeft + PAD, y, LEFT_W - PAD, 20).build());
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
        pendingMelee = null;
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
        pendingMelee = entry.melee();
        this.init(this.minecraft, this.width, this.height);
    }

    private void cancelEdit() {
        editing = false;
        this.init(this.minecraft, this.width, this.height);
    }

    private void deleteClass(UUID id) {
        command("class delete " + id);
        if (editing && id.equals(pendingId)) {
            editing = false;
        }
        this.init(this.minecraft, this.width, this.height);
    }

    private void save() {
        if (pendingName.isBlank()) {
            return;
        }
        String cmd = "class save " + pendingId + " "
                + rl(pendingIcon) + " " + rl(pendingMain) + " " + rl(pendingSidearm) + " "
                + rl(pendingThrowable) + " " + rl(pendingGadget) + " " + rl(pendingMelee) + " " + pendingName;
        command(cmd);
        editing = false;
        this.init(this.minecraft, this.width, this.height);
    }

    private static String rl(@Nullable ResourceLocation loc) {
        return loc == null ? "minecraft:air" : loc.toString();
    }

    private void buildRightColumn() {
        int rightX = panelLeft + LEFT_W + PAD;
        int rightWidth = panelWidth - LEFT_W - 2 * PAD;
        int y = panelTop + HEADER_H + PAD;

        nameBox = new EditBox(this.font, rightX, y, rightWidth, 18, Component.translatable("classloadout.gui.class_name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(pendingName);
        nameBox.setResponder(s -> pendingName = s);
        addRenderableWidget(nameBox);
        y += 26;

        iconX = rightX;
        iconY = y;
        addRenderableWidget(slotButton(iconX, iconY, loc -> pendingIcon = loc));
        y += SLOT + 22;

        for (int i = 0; i < 5; i++) {
            slotX[i] = rightX + i * (SLOT + 8);
        }
        slotY = y;
        addRenderableWidget(slotButton(slotX[0], slotY, loc -> pendingMain = loc));
        addRenderableWidget(slotButton(slotX[1], slotY, loc -> pendingSidearm = loc));
        addRenderableWidget(slotButton(slotX[2], slotY, loc -> pendingThrowable = loc));
        addRenderableWidget(slotButton(slotX[3], slotY, loc -> pendingGadget = loc));
        addRenderableWidget(slotButton(slotX[4], slotY, loc -> pendingMelee = loc));
        y += SLOT + 30;

        int bw = (rightWidth - 8) / (editingExisting ? 3 : 2);
        int bx = rightX;
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.save"), b -> save())
                .bounds(bx, y, bw, 20).build());
        bx += bw + 4;
        if (editingExisting) {
            addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.delete"),
                            b -> deleteClass(pendingId))
                    .bounds(bx, y, bw, 20).build());
            bx += bw + 4;
        }
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.cancel"), b -> cancelEdit())
                .bounds(bx, y, bw, 20).build());
    }

    private Button slotButton(int x, int y, Consumer<ResourceLocation> setter) {
        return Button.builder(Component.empty(), b -> minecraft.setScreen(new ItemPickerScreen(this,
                        loc -> setter.accept(isAir(loc) ? null : loc))))
                .bounds(x, y, SLOT, SLOT).build();
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
        renderBackground(graphics);

        int l = panelLeft;
        int t = panelTop;
        int r = l + panelWidth;
        int b = t + panelHeight;
        graphics.fill(l - 1, t - 1, r + 1, b + 1, 0x90000000);
        graphics.fill(l, t, r, b, COLOR_PANEL_BG);
        graphics.fill(l, t, r, t + HEADER_H, COLOR_HEADER_BG);
        graphics.renderOutline(l - 1, t - 1, panelWidth + 2, panelHeight + 2, COLOR_OUTLINE);
        graphics.fill(l + LEFT_W, t + HEADER_H, l + LEFT_W + 1, b, COLOR_OUTLINE);
        graphics.drawString(this.font, this.title, l + PAD, t + 8, COLOR_TEXT);

        for (RowInfo row : listRows) {
            ResourceLocation icon = row.entry().icon();
            if (icon != null && LoadoutClientData.isItemAvailable(icon)) {
                Item item = ForgeRegistries.ITEMS.getValue(icon);
                if (item != null) {
                    graphics.renderItem(new ItemStack(item), l + PAD, row.y() + 1);
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
            drawSlotIcon(graphics, slotX[4], slotY, pendingMelee, "classloadout.gui.slot_melee");
        } else {
            graphics.drawString(this.font, Component.translatable("classloadout.gui.class_editor_hint"),
                    l + LEFT_W + PAD, t + HEADER_H + PAD, COLOR_TEXT_DIM);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawSlotIcon(GuiGraphics graphics, int x, int y, @Nullable ResourceLocation loc, String labelKey) {
        graphics.fill(x, y, x + SLOT, y + SLOT, COLOR_SLOT_BG);
        if (loc != null) {
            Item item = ForgeRegistries.ITEMS.getValue(loc);
            if (item != null) {
                graphics.renderItem(new ItemStack(item), x + (SLOT - 16) / 2, y + (SLOT - 16) / 2);
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
