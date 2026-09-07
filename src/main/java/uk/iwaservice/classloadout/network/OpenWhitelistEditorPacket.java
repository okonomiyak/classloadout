package uk.iwaservice.classloadout.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload-less trigger sent only after the server has verified the sender's
 * permission level for {@code /class whitelist}, so the client can safely
 * open the whitelist editor without re-deriving permission from local state.
 */
public record OpenWhitelistEditorPacket() implements CustomPacketPayload {

    public static final Type<OpenWhitelistEditorPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.classloadout.ClassLoadoutMod.MODID, "open_whitelist_editor"));

    public static final StreamCodec<FriendlyByteBuf, OpenWhitelistEditorPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), OpenWhitelistEditorPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(OpenWhitelistEditorPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static OpenWhitelistEditorPacket decode(FriendlyByteBuf buf) {
        return new OpenWhitelistEditorPacket();
    }
}
