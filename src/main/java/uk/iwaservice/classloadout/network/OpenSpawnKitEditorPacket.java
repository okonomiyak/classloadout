package uk.iwaservice.classloadout.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.classloadout.client.ClientPacketHandler;

/**
 * Payload-less trigger sent only after the server has verified the sender's
 * permission level for {@code /class spawnkit}, so the client can safely
 * open the spawn kit editor without re-deriving permission from local
 * state. Mirrors {@link OpenWhitelistEditorPacket}.
 */
public record OpenSpawnKitEditorPacket() {

    public static void encode(OpenSpawnKitEditorPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static OpenSpawnKitEditorPacket decode(FriendlyByteBuf buf) {
        return new OpenSpawnKitEditorPacket();
    }

    public static void handle(OpenSpawnKitEditorPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientPacketHandler::handleOpenSpawnKitEditor);
    }
}
