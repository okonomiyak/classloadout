package uk.iwaservice.classloadout.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.classloadout.client.ClientPacketHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sent only after the server has verified the sender's permission level for
 * right-clicking a guard spawner block, carrying that one block's current
 * config fresh (not synced globally like the whitelist/spawn kit - only the
 * editing OP ever needs it). Mirrors {@link OpenSpawnKitEditorPacket} but
 * with a payload since the editor needs to pre-fill its fields.
 */
public record OpenGuardSpawnerEditorPacket(BlockPos pos, @Nullable ResourceLocation entityType, int delaySeconds,
                                            List<ResourceLocation> items) {

    public static void encode(OpenGuardSpawnerEditorPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos());
        buf.writeUtf(msg.entityType() == null ? "" : msg.entityType().toString());
        buf.writeVarInt(msg.delaySeconds());
        buf.writeVarInt(msg.items().size());
        for (ResourceLocation item : msg.items()) {
            buf.writeUtf(item.toString());
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
        return new OpenGuardSpawnerEditorPacket(pos, entityType, delaySeconds, items);
    }

    public static void handle(OpenGuardSpawnerEditorPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handleOpenGuardSpawnerEditor(msg.pos(), msg.entityType(),
                        msg.delaySeconds(), msg.items()));
    }
}
