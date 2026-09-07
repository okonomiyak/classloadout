package uk.iwaservice.classloadout.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload-less trigger sent only after the server has verified the sender's
 * permission level for {@code /class price}, so the client can safely open
 * the shop price editor without re-deriving permission from local state.
 * Mirrors {@link OpenHammerBlocksEditorPacket}.
 */
public record OpenPriceEditorPacket() implements CustomPacketPayload {

    public static final Type<OpenPriceEditorPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.classloadout.ClassLoadoutMod.MODID, "open_price_editor"));

    public static final StreamCodec<FriendlyByteBuf, OpenPriceEditorPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), OpenPriceEditorPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(OpenPriceEditorPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static OpenPriceEditorPacket decode(FriendlyByteBuf buf) {
        return new OpenPriceEditorPacket();
    }
}
