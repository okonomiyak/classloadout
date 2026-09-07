package uk.iwaservice.classloadout.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload-less trigger sent only after the server has verified the sender's
 * permission level for {@code /class editor}, so the client can safely open
 * the editor screen without re-deriving permission from local state.
 */
public record OpenClassEditorPacket() implements CustomPacketPayload {

    public static final Type<OpenClassEditorPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.classloadout.ClassLoadoutMod.MODID, "open_class_editor"));

    public static final StreamCodec<FriendlyByteBuf, OpenClassEditorPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), OpenClassEditorPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(OpenClassEditorPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static OpenClassEditorPacket decode(FriendlyByteBuf buf) {
        return new OpenClassEditorPacket();
    }
}
