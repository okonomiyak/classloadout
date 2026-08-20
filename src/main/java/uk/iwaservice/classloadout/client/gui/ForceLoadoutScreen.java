package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import uk.iwaservice.classloadout.ItemResolver;
import uk.iwaservice.classloadout.client.LoadoutClientData;
import uk.iwaservice.classloadout.loadout.LoadoutSlot;
import uk.iwaservice.classloadout.network.LoadoutSyncPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OP-only GUI front end for {@code /class forceselect}/{@code forceassign}
 * and their every-online-player ({@code forceselectall}/{@code forceassignall})
 * and per-team ({@code forceselectteam}/{@code forceassignteam})
 * counterparts: fill in a target player name and/or a scoreboard team name,
 * then either click a preset's Apply button (forceselect - the whole
 * ten-slot loadout, not whitelist-restricted, same as the preset editor) or
 * click one of the slot icons below to force just that slot (forceassign)
 * via an {@link ItemPickerScreen} restricted to that slot's whitelist - the
 * same choices the target's own loadout screen would offer them. A
 * non-blank team name takes priority over the player name (targets every
 * currently-online team member); with both blank, it applies to every
 * currently-online player instead of one target.
 * Opened exclusively via {@link uk.iwaservice.classloadout.network.OpenForceLoadoutEditorPacket}
 * that follows a successful {@code /class force} command.
 *
 * <p>The slot icons show whatever was last force-assigned <b>from this
 * screen</b>, not the target's actual current loadout (this mod doesn't
 * sync other players' loadouts to an OP's client) - purely a local
 * "what did I just set" reminder, reset whenever the target name changes.
 */
public class ForceLoadoutScreen extends Screen {

    private static final int PAD = 12;
    private static final int HEADER_H = 24;
    private static final int SLOT = 32;
    private static final int PRESET_ROW_H = 40;
    private static final int MAX_PRESET_ROWS = 5;
    private static final int ICON = 16;
    private static final int TEAM_BTN_H = 16;
    private static final int TEAM_BTN_GAP = 4;
    private static final int TEAM_BTN_MAX_ROWS = 2;

    private static final int COLOR_PANEL_BG = 0xF4222222;
    private static final int COLOR_HEADER_BG = 0xFF1F2333;
    private static final int COLOR_OUTLINE = 0xFF454A66;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xA0A8C0;
    private static final int COLOR_SLOT_BG = 0x60000000;
    private static final int COLOR_SEPARATOR = 0x28FFFFFF;

    private record PresetRow(LoadoutSyncPacket.Entry entry, int y) {}

    private final List<PresetRow> presetRows = new ArrayList<>();
    private final Map<LoadoutSlot, ResourceLocation> lastAssigned = new EnumMap<>(LoadoutSlot.class);

    @Nullable
    private final Screen parent;
    private String targetName = "";
    private EditBox targetBox;
    private String teamName = "";
    private EditBox teamBox;

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int dataRevision = -1;

    private final int[] slotX = new int[6];
    private int slotY;
    private final int[] armorX = new int[4];
    private int armorY;

    /** Opened directly by {@code /class force} - closing exits the GUI entirely (no parent to return to). */
    public ForceLoadoutScreen() {
        this(null);
    }

    /** Opened via the class editor's nav bar - closing returns to {@code parent} instead of exiting. */
    public ForceLoadoutScreen(@Nullable Screen parent) {
        super(Component.translatable("classloadout.gui.force_loadout_title"));
        this.parent = parent;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        List<LoadoutSyncPacket.Entry> classes = LoadoutClientData.getClasses();
        int presetShown = Math.min(classes.size(), MAX_PRESET_ROWS);
        int teamRowsH = TEAM_BTN_MAX_ROWS * (TEAM_BTN_H + TEAM_BTN_GAP);
        panelWidth = Math.min(360, this.width - 16);
        panelHeight = Math.min(HEADER_H + PAD * 2 + 18 + 6 + teamRowsH + 6 + 2 * SLOT + 8 + 34 + 16
                        + presetShown * PRESET_ROW_H + 30,
                this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        dataRevision = LoadoutClientData.getRevision();

        int y = panelTop + HEADER_H + PAD;
        int halfWidth = (panelWidth - 2 * PAD - 4) / 2;
        String previousTarget = targetBox != null ? targetBox.getValue() : targetName;
        targetBox = new EditBox(this.font, panelLeft + PAD, y, halfWidth, 18,
                Component.translatable("classloadout.gui.force_target_player"));
        targetBox.setHint(Component.translatable("classloadout.gui.force_target_player"));
        targetBox.setMaxLength(16);
        targetBox.setValue(previousTarget);
        targetBox.setResponder(s -> targetName = s);
        addRenderableWidget(targetBox);

        String previousTeam = teamBox != null ? teamBox.getValue() : teamName;
        teamBox = new EditBox(this.font, panelLeft + PAD + halfWidth + 4, y, halfWidth, 18,
                Component.translatable("classloadout.gui.force_target_team"));
        teamBox.setHint(Component.translatable("classloadout.gui.force_target_team"));
        teamBox.setMaxLength(16);
        teamBox.setValue(previousTeam);
        teamBox.setResponder(s -> teamName = s);
        addRenderableWidget(teamBox);
        y += 18 + 6;

        y = addTeamButtons(y);
        y += 6;

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
                            b -> forceSelect(entry))
                    .bounds(panelLeft + panelWidth - PAD - 56, y + (PRESET_ROW_H - 20) / 2, 56, 20).build());
            y += PRESET_ROW_H;
        }

        int bottomY = panelTop + panelHeight - PAD - 20;
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.close"),
                        b -> onClose())
                .bounds(panelLeft + PAD, bottomY, panelWidth - 2 * PAD, 20).build());
    }

    /** Adds one small button per existing scoreboard team (client-side, already synced via vanilla), wrapping into rows; clicking fills the team-name box. Returns the y just past the last row used. */
    private int addTeamButtons(int y0) {
        if (minecraft == null || minecraft.level == null) {
            return y0;
        }
        List<String> names = minecraft.level.getScoreboard().getPlayerTeams().stream()
                .map(PlayerTeam::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

        int x0 = panelLeft + PAD;
        int maxX = panelLeft + panelWidth - PAD;
        int x = x0;
        int y = y0;
        int maxY = y0 + TEAM_BTN_MAX_ROWS * (TEAM_BTN_H + TEAM_BTN_GAP);
        for (String name : names) {
            int w = Math.min(90, this.font.width(name) + 10);
            if (x + w > maxX) {
                x = x0;
                y += TEAM_BTN_H + TEAM_BTN_GAP;
            }
            if (y >= maxY) {
                break;
            }
            addRenderableWidget(Button.builder(Component.literal(name), b -> selectTeam(name))
                    .bounds(x, y, w, TEAM_BTN_H).build());
            x += w + TEAM_BTN_GAP;
        }
        return names.isEmpty() ? y0 : y + TEAM_BTN_H;
    }

    private void selectTeam(String name) {
        teamName = name;
        teamBox.setValue(name);
    }

    @Override
    public void tick() {
        if (dataRevision != LoadoutClientData.getRevision()) {
            this.init(this.minecraft, this.width, this.height);
        }
    }

    /**
     * Picker is restricted to {@code slot}'s whitelist - same choices the target's own loadout
     * screen would offer - even though the server-side {@code forceassign} command itself
     * doesn't enforce it. Always opens the picker regardless of the target fields' state - see
     * {@link #forceAssignCommand} for how a blank team/player name is resolved.
     */
    private Button slotButton(int x, int y, LoadoutSlot slot) {
        return Button.builder(Component.empty(), b -> minecraft.setScreen(new ItemPickerScreen(this, loc -> {
            lastAssigned.put(slot, loc);
            command(forceAssignCommand(slot, loc));
        }, LoadoutClientData.getWhitelist(slot)))).bounds(x, y, SLOT, SLOT).build();
    }

    /** A non-blank team name wins over the player name (targets every online team member); with both blank, targets every online player. */
    private String forceAssignCommand(LoadoutSlot slot, ResourceLocation item) {
        if (!teamName.isBlank()) {
            return "class forceassignteam " + teamName.trim() + " " + slot.key() + " " + item;
        }
        if (!targetName.isBlank()) {
            return "class forceassign " + targetName.trim() + " " + slot.key() + " " + item;
        }
        return "class forceassignall " + slot.key() + " " + item;
    }

    /** See {@link #forceAssignCommand} for how a blank team/player name is resolved. */
    private void forceSelect(LoadoutSyncPacket.Entry entry) {
        lastAssigned.clear();
        putIfPresent(LoadoutSlot.MAIN, entry.main());
        putIfPresent(LoadoutSlot.SIDEARM, entry.sidearm());
        putIfPresent(LoadoutSlot.THROWABLE, entry.throwable());
        putIfPresent(LoadoutSlot.GADGET, entry.gadget());
        putIfPresent(LoadoutSlot.GADGET2, entry.gadget2());
        putIfPresent(LoadoutSlot.MELEE, entry.melee());
        putIfPresent(LoadoutSlot.HELMET, entry.helmet());
        putIfPresent(LoadoutSlot.CHESTPLATE, entry.chestplate());
        putIfPresent(LoadoutSlot.LEGGINGS, entry.leggings());
        putIfPresent(LoadoutSlot.BOOTS, entry.boots());
        if (!teamName.isBlank()) {
            command("class forceselectteam " + teamName.trim() + " " + entry.id());
        } else if (!targetName.isBlank()) {
            command("class forceselect " + targetName.trim() + " " + entry.id());
        } else {
            command("class forceselectall " + entry.id());
        }
    }

    private void putIfPresent(LoadoutSlot slot, @Nullable ResourceLocation loc) {
        if (loc != null) {
            lastAssigned.put(slot, loc);
        }
    }

    private void command(String cmd) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(cmd);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Don't hijack digit keys while a name box is focused.
        if (!targetBox.isFocused() && !teamBox.isFocused() && HotbarBar.keyPressed(minecraft, keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (HotbarBar.mouseClicked(minecraft, mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

        drawSlotIcon(graphics, slotX[0], slotY, LoadoutSlot.MAIN, "classloadout.gui.slot_main");
        drawSlotIcon(graphics, slotX[1], slotY, LoadoutSlot.SIDEARM, "classloadout.gui.slot_sidearm");
        drawSlotIcon(graphics, slotX[2], slotY, LoadoutSlot.THROWABLE, "classloadout.gui.slot_throwable");
        drawSlotIcon(graphics, slotX[3], slotY, LoadoutSlot.GADGET, "classloadout.gui.slot_gadget");
        drawSlotIcon(graphics, slotX[4], slotY, LoadoutSlot.GADGET2, "classloadout.gui.slot_gadget2");
        drawSlotIcon(graphics, slotX[5], slotY, LoadoutSlot.MELEE, "classloadout.gui.slot_melee");
        drawSlotIcon(graphics, armorX[0], armorY, LoadoutSlot.HELMET, "classloadout.gui.slot_helmet");
        drawSlotIcon(graphics, armorX[1], armorY, LoadoutSlot.CHESTPLATE, "classloadout.gui.slot_chestplate");
        drawSlotIcon(graphics, armorX[2], armorY, LoadoutSlot.LEGGINGS, "classloadout.gui.slot_leggings");
        drawSlotIcon(graphics, armorX[3], armorY, LoadoutSlot.BOOTS, "classloadout.gui.slot_boots");

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
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        HotbarBar.render(graphics, this.minecraft);
    }

    private void drawSlotIcon(GuiGraphics graphics, int x, int y, LoadoutSlot slot, String labelKey) {
        graphics.fill(x, y, x + SLOT, y + SLOT, COLOR_SLOT_BG);
        ResourceLocation loc = lastAssigned.get(slot);
        if (loc != null) {
            ItemStack stack = ItemResolver.resolve(loc, LoadoutClientData.getItemVariants());
            if (stack != null) {
                graphics.renderItem(stack, x + (SLOT - ICON) / 2, y + (SLOT - ICON) / 2);
            }
        }
        graphics.drawCenteredString(this.font, Component.translatable(labelKey), x + SLOT / 2, y + SLOT + 3, COLOR_TEXT_DIM);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
