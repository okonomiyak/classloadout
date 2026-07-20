package uk.iwaservice.classloadout.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import uk.iwaservice.classloadout.ClassLoadoutMod;

/**
 * Server-to-client only channel. Clients never send loadout packets; every
 * mutation (save/delete/select/clear) goes through {@code /class} commands,
 * which are validated server-side.
 */
public final class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ClassLoadoutMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void register() {
        CHANNEL.messageBuilder(LoadoutSyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LoadoutSyncPacket::encode)
                .decoder(LoadoutSyncPacket::decode)
                .consumerMainThread(LoadoutSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenClassEditorPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenClassEditorPacket::encode)
                .decoder(OpenClassEditorPacket::decode)
                .consumerMainThread(OpenClassEditorPacket::handle)
                .add();
    }

    public static void sendLoadoutSync(ServerPlayer player, LoadoutSyncPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendOpenClassEditor(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenClassEditorPacket());
    }

    private NetworkHandler() {}
}
