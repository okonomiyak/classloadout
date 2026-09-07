package uk.iwaservice.classloadout.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload-less trigger sent only after the server has verified the sender's
 * permission level for {@code /class protect}, so the client can safely
 * open the protected-items editor without re-deriving permission from local
 * state. Mirrors {@link OpenWhitelistEditorPacket}.
 */
public record OpenProtectedItemsEditorPacket() implements CustomPacketPayload {

    public static final Type<OpenProtectedItemsEditorPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.classloadout.ClassLoadoutMod.MODID, "open_protected_items_editor"));

    public static final StreamCodec<FriendlyByteBuf, OpenProtectedItemsEditorPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), OpenProtectedItemsEditorPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(OpenProtectedItemsEditorPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static OpenProtectedItemsEditorPacket decode(FriendlyByteBuf buf) {
        return new OpenProtectedItemsEditorPacket();
    }
}
