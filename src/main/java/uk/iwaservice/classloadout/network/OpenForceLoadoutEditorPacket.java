package uk.iwaservice.classloadout.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.classloadout.client.ClientPacketHandler;

/**
 * Payload-less trigger sent only after the server has verified the sender's
 * permission level for {@code /class force}, so the client can safely open
 * the force-loadout editor without re-deriving permission from local state.
 * Mirrors {@link OpenHammerBlocksEditorPacket}.
 */
public record OpenForceLoadoutEditorPacket() {

    public static void encode(OpenForceLoadoutEditorPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static OpenForceLoadoutEditorPacket decode(FriendlyByteBuf buf) {
        return new OpenForceLoadoutEditorPacket();
    }

    public static void handle(OpenForceLoadoutEditorPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientPacketHandler::handleOpenForceLoadoutEditor);
    }
}
