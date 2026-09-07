package uk.iwaservice.classloadout.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload-less trigger sent only after the server has verified the sender's
 * permission level for {@code /class force}, so the client can safely open
 * the force-loadout editor without re-deriving permission from local state.
 * Mirrors {@link OpenHammerBlocksEditorPacket}.
 */
public record OpenForceLoadoutEditorPacket() implements CustomPacketPayload {

    public static final Type<OpenForceLoadoutEditorPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.classloadout.ClassLoadoutMod.MODID, "open_force_loadout_editor"));

    public static final StreamCodec<FriendlyByteBuf, OpenForceLoadoutEditorPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), OpenForceLoadoutEditorPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(OpenForceLoadoutEditorPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static OpenForceLoadoutEditorPacket decode(FriendlyByteBuf buf) {
        return new OpenForceLoadoutEditorPacket();
    }
}
