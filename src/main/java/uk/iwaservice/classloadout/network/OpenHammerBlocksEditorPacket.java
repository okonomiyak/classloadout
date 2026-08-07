package uk.iwaservice.classloadout.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.classloadout.client.ClientPacketHandler;

/**
 * Payload-less trigger sent only after the server has verified the sender's
 * permission level for {@code /class hammerblocks}, so the client can
 * safely open the hammer-blocks editor without re-deriving permission from
 * local state. Mirrors {@link OpenProtectedItemsEditorPacket}.
 */
public record OpenHammerBlocksEditorPacket() {

    public static void encode(OpenHammerBlocksEditorPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static OpenHammerBlocksEditorPacket decode(FriendlyByteBuf buf) {
        return new OpenHammerBlocksEditorPacket();
    }

    public static void handle(OpenHammerBlocksEditorPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientPacketHandler::handleOpenHammerBlocksEditor);
    }
}
