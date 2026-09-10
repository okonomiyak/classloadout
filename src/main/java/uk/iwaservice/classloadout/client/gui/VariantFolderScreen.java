package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import uk.iwaservice.classloadout.client.GuiBlurFix;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import uk.iwaservice.classloadout.ItemResolver;
import uk.iwaservice.classloadout.client.LoadoutClientData;

/**
 * OP-only popup, opened by hovering a held-item cell in {@link WhitelistEditorScreen} (Held
 * items tab only) and pressing F, that assigns or clears that variant's organizational folder
 * (see {@code /class whitelist set_folder}/{@code clear_folder}). Mirrors {@link PriceCountScreen}.
 */
public class VariantFolderScreen extends Screen {
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

    private static final int COLOR_PANEL_BG = 0xF4222222;
    private static final int COLOR_HEADER_BG = 0xFF1F2333;
    private static final int COLOR_OUTLINE = 0xFF454A66;
    private static final int COLOR_TEXT_DIM = 0xA0A8C0;

    private final Screen parent;
    private final ResourceLocation item;

    private String folderValue;
    private EditBox folderBox;

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;

    public VariantFolderScreen(Screen parent, ResourceLocation item) {
        super(Component.translatable("classloadout.gui.variant_folder_title"));
        this.parent = parent;
        this.item = item;
        this.folderValue = LoadoutClientData.getVariantFolder(item);
    }

    @Override
    protected void init() {
        if (savedBlur < 0) savedBlur = GuiBlurFix.suppress();
        panelWidth = Math.min(220, this.width - 16);
        panelHeight = Math.min(120, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        int y = panelTop + HEADER_H + PAD + 12;
        folderBox = new EditBox(this.font, panelLeft + PAD, y, panelWidth - 2 * PAD, 18,
                Component.translatable("classloadout.gui.variant_folder_name"));
        folderBox.setMaxLength(32);
        folderBox.setValue(folderValue);
        folderBox.setResponder(s -> folderValue = s);
        addRenderableWidget(folderBox);
        setInitialFocus(folderBox);
        y += 18 + 8;

        int bw = (panelWidth - 2 * PAD - 8) / 3;
        int bx = panelLeft + PAD;
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.save"), b -> save())
                .bounds(bx, y, bw, 20).build());
        bx += bw + 4;
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.clear"), b -> clear())
                .bounds(bx, y, bw, 20).build());
        bx += bw + 4;
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.cancel"),
                        b -> minecraft.setScreen(parent))
                .bounds(bx, y, bw, 20).build());
    }

    private void save() {
        if (folderValue.isBlank()) {
            clear();
            return;
        }
        command("class whitelist set_folder " + item + " " + folderValue);
        minecraft.setScreen(parent);
    }

    private void clear() {
        command("class whitelist clear_folder " + item);
        minecraft.setScreen(parent);
    }

    private void command(String cmd) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(cmd);
        }
    }

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

        ItemStack stack = ItemResolver.resolve(item, LoadoutClientData.getItemVariants());
        graphics.drawString(this.font, stack != null ? stack.getHoverName() : Component.literal(item.toString()),
                l + PAD, t + HEADER_H + 2, COLOR_TEXT_DIM);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
