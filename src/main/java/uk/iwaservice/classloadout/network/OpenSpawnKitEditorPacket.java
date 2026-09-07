package uk.iwaservice.classloadout.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload-less trigger sent only after the server has verified the sender's
 * permission level for {@code /class spawnkit}, so the client can safely
 * open the spawn kit editor without re-deriving permission from local
 * state. Mirrors {@link OpenWhitelistEditorPacket}.
 */
public record OpenSpawnKitEditorPacket() implements CustomPacketPayload {

    public static final Type<OpenSpawnKitEditorPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(uk.iwaservice.classloadout.ClassLoadoutMod.MODID, "open_spawn_kit_editor"));

    public static final StreamCodec<FriendlyByteBuf, OpenSpawnKitEditorPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), OpenSpawnKitEditorPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(OpenSpawnKitEditorPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static OpenSpawnKitEditorPacket decode(FriendlyByteBuf buf) {
        return new OpenSpawnKitEditorPacket();
    }
}
