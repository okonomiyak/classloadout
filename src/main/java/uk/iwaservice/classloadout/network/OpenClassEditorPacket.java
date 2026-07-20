package uk.iwaservice.classloadout.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.classloadout.client.ClientPacketHandler;

/**
 * Payload-less trigger sent only after the server has verified the sender's
 * permission level for {@code /class editor}, so the client can safely open
 * the editor screen without re-deriving permission from local state.
 */
public record OpenClassEditorPacket() {

    public static void encode(OpenClassEditorPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static OpenClassEditorPacket decode(FriendlyByteBuf buf) {
        return new OpenClassEditorPacket();
    }

    public static void handle(OpenClassEditorPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientPacketHandler::handleOpenClassEditor);
    }
}
