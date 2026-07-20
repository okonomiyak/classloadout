package uk.iwaservice.classloadout.client.gui;

import net.minecraft.ChatFormatting;
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
import uk.iwaservice.classloadout.network.LoadoutSyncPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Player-facing loadout picker, reachable only from the death screen's
 * "Change Class" button (see {@link uk.iwaservice.classloadout.ClientEvents}).
 * Purely reads {@link LoadoutClientData} (kept in sync by the server) and
 * issues {@code /class select}/{@code clear} - no bespoke networking.
 */
public class ClassSelectScreen extends Screen {

    private static final int PAD = 12;
    private static final int HEADER_H = 24;
    private static final int ROW_H = 40;
    private static final int MAX_ROWS = 6;
    private static final int ICON = 16;

    private static final int COLOR_PANEL_BG = 0xF4222222;
    private static final int COLOR_HEADER_BG = 0xFF1F2333;
    private static final int COLOR_OUTLINE = 0xFF454A66;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xA0A8C0;
    private static final int COLOR_SELECTED = 0x4055FF55;

    private record RowInfo(LoadoutSyncPacket.Entry entry, int y) {}

    private final Screen returnTo;
    private final List<RowInfo> rows = new ArrayList<>();

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int dataRevision = -1;

    public ClassSelectScreen(Screen returnTo) {
        super(Component.translatable("classloadout.gui.class_select_title"));
        this.returnTo = returnTo;
    }

    @Override
    protected void init() {
        List<LoadoutSyncPacket.Entry> classes = LoadoutClientData.getClasses();
        int shown = Math.min(classes.size(), MAX_ROWS);
        panelWidth = Math.min(360, this.width - 16);
        panelHeight = Math.min(HEADER_H + PAD * 2 + shown * ROW_H + 30, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        dataRevision = LoadoutClientData.getRevision();

        rows.clear();
        int y = panelTop + HEADER_H + PAD;
        UUID selectedId = LoadoutClientData.getSelectedId();
        for (int i = 0; i < shown; i++) {
            LoadoutSyncPacket.Entry entry = classes.get(i);
            rows.add(new RowInfo(entry, y));
            addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.select"),
                            b -> select(entry.id()))
                    .bounds(panelLeft + panelWidth - PAD - 60, y + (ROW_H - 20) / 2, 60, 20).build());
            y += ROW_H;
        }

        int bottomY = panelTop + panelHeight - PAD - 20;
        int half = (panelWidth - 2 * PAD - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.class_unselect"), b -> clearSelection())
                .bounds(panelLeft + PAD, bottomY, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.close"), b -> minecraft.setScreen(returnTo))
                .bounds(panelLeft + PAD + half + 4, bottomY, half, 20).build());
    }

    @Override
    public void tick() {
        if (dataRevision != LoadoutClientData.getRevision()) {
            this.init(this.minecraft, this.width, this.height);
        }
    }

    private void select(UUID id) {
        command("class select " + id);
        minecraft.setScreen(returnTo);
    }

    private void clearSelection() {
        command("class clear");
        minecraft.setScreen(returnTo);
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

        UUID selectedId = LoadoutClientData.getSelectedId();
        if (rows.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("classloadout.gui.class_none_defined"),
                    l + PAD, t + HEADER_H + PAD, COLOR_TEXT_DIM);
        }
        for (RowInfo row : rows) {
            boolean selected = row.entry().id().equals(selectedId);
            if (selected) {
                graphics.fill(l + 2, row.y() - 4, r - 2, row.y() + ROW_H - 8, COLOR_SELECTED);
            }
            Component name = Component.literal(row.entry().name());
            if (selected) {
                name = name.copy().append(" ").append(Component.translatable("classloadout.gui.class_selected_tag")
                        .withStyle(ChatFormatting.GREEN));
            }
            graphics.drawString(this.font, name, l + PAD, row.y(), COLOR_TEXT);

            ResourceLocation[] slots = {row.entry().main(), row.entry().sidearm(), row.entry().throwable(),
                    row.entry().gadget(), row.entry().melee()};
            for (int i = 0; i < slots.length; i++) {
                int x = l + PAD + i * (ICON + 4);
                int y = row.y() + 14;
                drawSlotIcon(graphics, x, y, slots[i]);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawSlotIcon(GuiGraphics graphics, int x, int y, @Nullable ResourceLocation loc) {
        if (loc == null) {
            graphics.fill(x, y, x + ICON, y + ICON, 0x30FFFFFF);
            return;
        }
        Item item = LoadoutClientData.isItemAvailable(loc) ? ForgeRegistries.ITEMS.getValue(loc) : null;
        if (item != null) {
            graphics.renderItem(new ItemStack(item), x, y);
        } else {
            graphics.renderItem(new ItemStack(Items.BARRIER), x, y);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
