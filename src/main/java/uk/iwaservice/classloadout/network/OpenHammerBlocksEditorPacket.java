package uk.iwaservice.classloadout.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload-less trigger sent only after the server has verified the sender's
 * permission level for {@code /class hammerblocks}, so the client can
 * safely open the hammer-blocks editor without re-deriving permission from
 * local state. Mirrors {@link OpenProtectedItemsEditorPacket}.
 */
public record OpenHammerBlocksEditorPacket() implements CustomPacketPayload {

    public static final Type<OpenHammerBlocksEditorPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.classloadout.ClassLoadoutMod.MODID, "open_hammer_blocks_editor"));

    public static final StreamCodec<FriendlyByteBuf, OpenHammerBlocksEditorPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), OpenHammerBlocksEditorPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(OpenHammerBlocksEditorPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static OpenHammerBlocksEditorPacket decode(FriendlyByteBuf buf) {
        return new OpenHammerBlocksEditorPacket();
    }
}
