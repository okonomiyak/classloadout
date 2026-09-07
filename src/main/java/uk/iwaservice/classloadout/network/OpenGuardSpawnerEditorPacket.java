package uk.iwaservice.classloadout.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import uk.iwaservice.classloadout.loadout.GuardSpawnerTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
                                            List<ResourceLocation> items, List<GuardSpawnerTemplate> templates)
        implements CustomPacketPayload {

    public static final Type<OpenGuardSpawnerEditorPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.classloadout.ClassLoadoutMod.MODID, "open_guard_spawner_editor"));

    public static final StreamCodec<FriendlyByteBuf, OpenGuardSpawnerEditorPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), OpenGuardSpawnerEditorPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

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
        ResourceLocation entityType = entityTypeStr.isEmpty() ? null : ResourceLocation.parse(entityTypeStr);
        int delaySeconds = buf.readVarInt();
        int count = buf.readVarInt();
        List<ResourceLocation> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(ResourceLocation.parse(buf.readUtf()));
        }
        int templateCount = buf.readVarInt();
        List<GuardSpawnerTemplate> templates = new ArrayList<>(templateCount);
        for (int i = 0; i < templateCount; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf();
            ResourceLocation tEntityType = ResourceLocation.parse(buf.readUtf());
            int tDelay = buf.readVarInt();
            int tItemCount = buf.readVarInt();
            List<ResourceLocation> tItems = new ArrayList<>(tItemCount);
            for (int j = 0; j < tItemCount; j++) {
                tItems.add(ResourceLocation.parse(buf.readUtf()));
            }
            templates.add(new GuardSpawnerTemplate(id, name, tEntityType, tDelay, tItems));
        }
        return new OpenGuardSpawnerEditorPacket(pos, entityType, delaySeconds, items, templates);
    }
}
