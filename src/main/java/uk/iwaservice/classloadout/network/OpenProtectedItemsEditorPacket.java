package uk.iwaservice.classloadout.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.classloadout.client.ClientPacketHandler;

/**
 * Payload-less trigger sent only after the server has verified the sender's
 * permission level for {@code /class protect}, so the client can safely
 * open the protected-items editor without re-deriving permission from local
 * state. Mirrors {@link OpenWhitelistEditorPacket}.
 */
public record OpenProtectedItemsEditorPacket() {

    public static void encode(OpenProtectedItemsEditorPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static OpenProtectedItemsEditorPacket decode(FriendlyByteBuf buf) {
        return new OpenProtectedItemsEditorPacket();
    }

    public static void handle(OpenProtectedItemsEditorPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientPacketHandler::handleOpenProtectedItemsEditor);
    }
}
