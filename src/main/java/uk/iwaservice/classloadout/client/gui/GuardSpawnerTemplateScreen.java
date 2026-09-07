package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import uk.iwaservice.classloadout.client.GuiBlurFix;
import net.minecraft.network.chat.Component;
import uk.iwaservice.classloadout.loadout.GuardSpawnerTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * OP-only screen listing every saved {@link GuardSpawnerTemplate}, opened via the "Templates"
 * button in {@link GuardSpawnerEditorScreen}. Each row has an Apply (copies that template onto
 * the spawner {@code parent} is editing, via {@link GuardSpawnerEditorScreen#applyTemplate}) and
 * Delete button; the bottom row saves the spawner's current config under a new name. Not synced
 * globally - the template roster rides along in {@code OpenGuardSpawnerEditorPacket} and is
 * mutated optimistically here exactly like {@code parent}'s own item grid, since this data isn't
 * broadcast to every client either.
 */
public class GuardSpawnerTemplateScreen extends Screen {
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
    private static final int ROW_H = 22;
    private static final int MAX_ROWS = 6;

    private static final int COLOR_PANEL_BG = 0xF4222222;
    private static final int COLOR_HEADER_BG = 0xFF1F2333;
    private static final int COLOR_OUTLINE = 0xFF454A66;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFA0A8C0;

    private record Row(GuardSpawnerTemplate template, int y) {}

    private final GuardSpawnerEditorScreen parent;
    private final List<GuardSpawnerTemplate> templates;
    private final List<Row> rows = new ArrayList<>();
    private EditBox nameBox;

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;

    public GuardSpawnerTemplateScreen(GuardSpawnerEditorScreen parent, List<GuardSpawnerTemplate> templates) {
        super(Component.translatable("classloadout.gui.guardspawner_templates_title"));
        this.parent = parent;
        this.templates = templates;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        if (savedBlur < 0) savedBlur = GuiBlurFix.suppress();
        panelWidth = Math.min(280, this.width - 16);
        int rowsHeight = Math.max(1, Math.min(templates.size(), MAX_ROWS)) * ROW_H;
        panelHeight = Math.min(HEADER_H + PAD * 2 + rowsHeight + 26 + 20 + 20, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        rows.clear();
        int y = panelTop + HEADER_H + PAD;
        int shown = Math.min(templates.size(), MAX_ROWS);
        int btnW = 56;
        for (int i = 0; i < shown; i++) {
            GuardSpawnerTemplate template = templates.get(i);
            rows.add(new Row(template, y));
            int bx = panelLeft + panelWidth - PAD - 2 * btnW;
            addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.guardspawner_template_apply"),
                            b -> apply(template))
                    .bounds(bx, y, btnW, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.delete"),
                            b -> delete(template))
                    .bounds(bx + btnW, y, btnW, 18).build());
            y += ROW_H;
        }
        if (shown == 0) {
            y += ROW_H;
        }
        y += 4;

        String previousName = nameBox != null ? nameBox.getValue() : "";
        nameBox = new EditBox(this.font, panelLeft + PAD, y, panelWidth - 2 * PAD, 18,
                Component.translatable("classloadout.gui.guardspawner_template_name"));
        nameBox.setHint(Component.translatable("classloadout.gui.guardspawner_template_name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(previousName);
        addRenderableWidget(nameBox);
        y += 18 + 4;

        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.guardspawner_template_save_as"),
                        b -> saveAsNew())
                .bounds(panelLeft + PAD, y, panelWidth - 2 * PAD, 20).build());
        y += 20 + 4;

        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.close"), b -> onClose())
                .bounds(panelLeft + PAD, y, panelWidth - 2 * PAD, 20).build());
    }

    private void apply(GuardSpawnerTemplate template) {
        parent.applyTemplate(template);
        onClose();
    }

    private void delete(GuardSpawnerTemplate template) {
        templates.remove(template);
        command("class guardspawner template_delete " + template.id());
        this.init(this.minecraft, this.width, this.height);
    }

    /** Saves the spawner's currently-shown fields (as typed, not just what's been Saved) as a brand-new template - a no-op if the entity type/delay fields aren't currently valid. */
    private void saveAsNew() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        GuardSpawnerEditorScreen.ParsedConfig parsed = parent.parseFields();
        if (parsed == null) {
            return;
        }
        UUID id = UUID.randomUUID();
        command("class guardspawner template_save " + id + " " + parsed.entityType() + " " + parsed.delaySeconds()
                + " " + name);
        for (var item : parsed.items()) {
            command("class guardspawner template_add_item " + id + " " + item);
        }
        templates.add(new GuardSpawnerTemplate(id, name, parsed.entityType(), parsed.delaySeconds(),
                new ArrayList<>(parsed.items())));
        nameBox.setValue("");
        this.init(this.minecraft, this.width, this.height);
    }

    private void command(String cmd) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(cmd);
        }
    }

    // --- rendering ---

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
        graphics.drawString(this.font, this.title, l + PAD, t + 8, COLOR_TEXT);

        if (rows.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("classloadout.gui.guardspawner_template_none"),
                    l + PAD, t + HEADER_H + PAD, COLOR_TEXT_DIM);
        }
        for (Row row : rows) {
            graphics.drawString(this.font, trim(row.template().name(), 16), l + PAD, row.y() + 5, COLOR_TEXT);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
