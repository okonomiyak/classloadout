package uk.iwaservice.classloadout.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.classloadout.client.ClientPacketHandler;
import uk.iwaservice.classloadout.loadout.ClassDefinition;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Full loadout-class roster pushed to every online player on login and
 * whenever the admin editor saves or deletes a class, plus the recipient's
 * own current selection (each player gets a packet built specifically for
 * them, so this field is never someone else's data).
 */
public record LoadoutSyncPacket(List<Entry> classes, @Nullable UUID selectedId) {

    public record Entry(UUID id, String name,
                        @Nullable ResourceLocation icon,
                        @Nullable ResourceLocation main,
                        @Nullable ResourceLocation sidearm,
                        @Nullable ResourceLocation throwable,
                        @Nullable ResourceLocation gadget,
                        @Nullable ResourceLocation melee) {

        public static Entry of(ClassDefinition def) {
            return new Entry(def.id(), def.name(), def.icon(), def.main(), def.sidearm(),
                    def.throwable(), def.gadget(), def.melee());
        }
    }

    public static void encode(LoadoutSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.classes.size());
        for (Entry e : msg.classes) {
            buf.writeUUID(e.id());
            buf.writeUtf(e.name());
            writeOptional(buf, e.icon());
            writeOptional(buf, e.main());
            writeOptional(buf, e.sidearm());
            writeOptional(buf, e.throwable());
            writeOptional(buf, e.gadget());
            writeOptional(buf, e.melee());
        }
        buf.writeBoolean(msg.selectedId != null);
        if (msg.selectedId != null) {
            buf.writeUUID(msg.selectedId);
        }
    }

    public static LoadoutSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> classes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf();
            ResourceLocation icon = readOptional(buf);
            ResourceLocation main = readOptional(buf);
            ResourceLocation sidearm = readOptional(buf);
            ResourceLocation throwable = readOptional(buf);
            ResourceLocation gadget = readOptional(buf);
            ResourceLocation melee = readOptional(buf);
            classes.add(new Entry(id, name, icon, main, sidearm, throwable, gadget, melee));
        }
        UUID selectedId = buf.readBoolean() ? buf.readUUID() : null;
        return new LoadoutSyncPacket(classes, selectedId);
    }

    private static void writeOptional(FriendlyByteBuf buf, @Nullable ResourceLocation loc) {
        buf.writeBoolean(loc != null);
        if (loc != null) {
            buf.writeResourceLocation(loc);
        }
    }

    @Nullable
    private static ResourceLocation readOptional(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readResourceLocation() : null;
    }

    public static void handle(LoadoutSyncPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleLoadoutSync(msg));
    }
}
