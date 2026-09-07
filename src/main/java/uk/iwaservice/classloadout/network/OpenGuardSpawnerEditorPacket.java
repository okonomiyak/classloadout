package uk.iwaservice.classloadout.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.classloadout.client.ClientPacketHandler;
import uk.iwaservice.classloadout.loadout.GuardSpawnerTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sent only after the server has verified the sender's permission level for
 * right-clicking a guard spawner block, carrying that one block's current
 * config fresh (not synced globally like the whitelist/spawn kit - only the
 * editing OP ever needs it). Mirrors {@link OpenSpawnKitEditorPacket} but
 * with a payload since the editor needs to pre-fill its fields. Also carries
 * the full {@link GuardSpawnerTemplate} roster (same reasoning: only an OP
 * mid-edit ever needs it, so it rides along here rather than getting its own
 * globally-broadcast sync channel).
 */
public record OpenGuardSpawnerEditorPacket(BlockPos pos, @Nullable ResourceLocation entityType, int delaySeconds,
                                            List<ResourceLocation> items, List<GuardSpawnerTemplate> templates) {

    public static void encode(OpenGuardSpawnerEditorPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos());
        buf.writeUtf(msg.entityType() == null ? "" : msg.entityType().toString());
        buf.writeVarInt(msg.delaySeconds());
        buf.writeVarInt(msg.items().size());
        for (ResourceLocation item : msg.items()) {
            buf.writeUtf(item.toString());
        }
        buf.writeVarInt(msg.templates().size());
        for (GuardSpawnerTemplate t : msg.templates()) {
            buf.writeUUID(t.id());
            buf.writeUtf(t.name());
            buf.writeUtf(t.entityType().toString());
            buf.writeVarInt(t.delaySeconds());
            buf.writeVarInt(t.items().size());
            for (ResourceLocation item : t.items()) {
                buf.writeUtf(item.toString());
            }
        }
    }

    public static OpenGuardSpawnerEditorPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String entityTypeStr = buf.readUtf();
        ResourceLocation entityType = entityTypeStr.isEmpty() ? null : new ResourceLocation(entityTypeStr);
        int delaySeconds = buf.readVarInt();
        int count = buf.readVarInt();
        List<ResourceLocation> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(new ResourceLocation(buf.readUtf()));
        }
        int templateCount = buf.readVarInt();
        List<GuardSpawnerTemplate> templates = new ArrayList<>(templateCount);
        for (int i = 0; i < templateCount; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf();
            ResourceLocation tEntityType = new ResourceLocation(buf.readUtf());
            int tDelay = buf.readVarInt();
            int tItemCount = buf.readVarInt();
            List<ResourceLocation> tItems = new ArrayList<>(tItemCount);
            for (int j = 0; j < tItemCount; j++) {
                tItems.add(new ResourceLocation(buf.readUtf()));
            }
            templates.add(new GuardSpawnerTemplate(id, name, tEntityType, tDelay, tItems));
        }
        return new OpenGuardSpawnerEditorPacket(pos, entityType, delaySeconds, items, templates);
    }

    public static void handle(OpenGuardSpawnerEditorPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handleOpenGuardSpawnerEditor(msg.pos(), msg.entityType(),
                        msg.delaySeconds(), msg.items(), msg.templates()));
    }
}
