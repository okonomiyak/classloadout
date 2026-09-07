package uk.iwaservice.classloadout.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import uk.iwaservice.classloadout.client.ClientPacketHandler;

/**
 * Server-to-client only channel. Clients never send loadout packets; every
 * mutation (save/delete/select/clear) goes through {@code /class} commands,
 * which are validated server-side.
 */
public final class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(LoadoutSyncPacket.TYPE, LoadoutSyncPacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handleLoadoutSync(msg));
        registrar.playToClient(OpenClassEditorPacket.TYPE, OpenClassEditorPacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handleOpenClassEditor());
        registrar.playToClient(OpenWhitelistEditorPacket.TYPE, OpenWhitelistEditorPacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handleOpenWhitelistEditor());
        registrar.playToClient(OpenProtectedItemsEditorPacket.TYPE, OpenProtectedItemsEditorPacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handleOpenProtectedItemsEditor());
        registrar.playToClient(OpenSpawnKitEditorPacket.TYPE, OpenSpawnKitEditorPacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handleOpenSpawnKitEditor());
        registrar.playToClient(OpenHammerBlocksEditorPacket.TYPE, OpenHammerBlocksEditorPacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handleOpenHammerBlocksEditor());
        registrar.playToClient(OpenGuardSpawnerEditorPacket.TYPE, OpenGuardSpawnerEditorPacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handleOpenGuardSpawnerEditor(msg.pos(), msg.entityType(),
                        msg.delaySeconds(), msg.items(), msg.templates()));
        registrar.playToClient(OpenForceLoadoutEditorPacket.TYPE, OpenForceLoadoutEditorPacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handleOpenForceLoadoutEditor());
        registrar.playToClient(OpenPriceEditorPacket.TYPE, OpenPriceEditorPacket.STREAM_CODEC,
                (msg, ctx) -> ClientPacketHandler.handleOpenPriceEditor());
    }

    public static void sendLoadoutSync(ServerPlayer player, LoadoutSyncPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendOpenClassEditor(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenClassEditorPacket());
    }

    public static void sendOpenWhitelistEditor(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenWhitelistEditorPacket());
    }

    public static void sendOpenProtectedItemsEditor(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenProtectedItemsEditorPacket());
    }

    public static void sendOpenSpawnKitEditor(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenSpawnKitEditorPacket());
    }

    public static void sendOpenHammerBlocksEditor(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenHammerBlocksEditorPacket());
    }

    public static void sendOpenGuardSpawnerEditor(ServerPlayer player, net.minecraft.core.BlockPos pos,
            @javax.annotation.Nullable ResourceLocation entityType, int delaySeconds,
            java.util.List<ResourceLocation> items,
            java.util.List<uk.iwaservice.classloadout.loadout.GuardSpawnerTemplate> templates) {
        PacketDistributor.sendToPlayer(player,
                new OpenGuardSpawnerEditorPacket(pos, entityType, delaySeconds, items, templates));
    }

    public static void sendOpenForceLoadoutEditor(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenForceLoadoutEditorPacket());
    }

    public static void sendOpenPriceEditor(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenPriceEditorPacket());
    }

    private NetworkHandler() {}
}
