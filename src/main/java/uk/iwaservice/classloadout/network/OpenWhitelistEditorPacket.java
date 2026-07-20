package uk.iwaservice.classloadout.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.classloadout.client.ClientPacketHandler;

/**
 * Payload-less trigger sent only after the server has verified the sender's
 * permission level for {@code /class whitelist}, so the client can safely
 * open the whitelist editor without re-deriving permission from local state.
 */
public record OpenWhitelistEditorPacket() {

    public static void encode(OpenWhitelistEditorPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static OpenWhitelistEditorPacket decode(FriendlyByteBuf buf) {
        return new OpenWhitelistEditorPacket();
    }

    public static void handle(OpenWhitelistEditorPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientPacketHandler::handleOpenWhitelistEditor);
    }
}
