package uk.iwaservice.classloadout.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.classloadout.client.ClientPacketHandler;
import uk.iwaservice.classloadout.loadout.ClassDefinition;
import uk.iwaservice.classloadout.loadout.LoadoutSlot;
import uk.iwaservice.classloadout.loadout.PersonalLoadout;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pushed to a player on login and whenever the admin editor saves/deletes a
 * preset, the slot whitelists change, or the player's own loadout changes:
 * the full preset roster and the five slot whitelists (same for everyone),
 * plus that one recipient's own personal loadout (never someone else's -
 * each player gets a packet built specifically for them).
 */
public record LoadoutSyncPacket(List<Entry> classes, PersonalData personal, Whitelists whitelists) {

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

    public record PersonalData(@Nullable ResourceLocation main,
                               @Nullable ResourceLocation sidearm,
                               @Nullable ResourceLocation throwable,
                               @Nullable ResourceLocation gadget,
                               @Nullable ResourceLocation melee) {

        public static PersonalData of(PersonalLoadout loadout) {
            return new PersonalData(loadout.main(), loadout.sidearm(), loadout.throwable(),
                    loadout.gadget(), loadout.melee());
        }
    }

    /** OP-curated allow-lists, one per slot; an empty list means nothing is assignable yet. */
    public record Whitelists(List<ResourceLocation> main, List<ResourceLocation> sidearm,
                             List<ResourceLocation> throwable, List<ResourceLocation> gadget,
                             List<ResourceLocation> melee) {

        public static Whitelists of(Map<LoadoutSlot, List<ResourceLocation>> bySlot) {
            return new Whitelists(
                    bySlot.getOrDefault(LoadoutSlot.MAIN, List.of()),
                    bySlot.getOrDefault(LoadoutSlot.SIDEARM, List.of()),
                    bySlot.getOrDefault(LoadoutSlot.THROWABLE, List.of()),
                    bySlot.getOrDefault(LoadoutSlot.GADGET, List.of()),
                    bySlot.getOrDefault(LoadoutSlot.MELEE, List.of()));
        }

        public List<ResourceLocation> get(LoadoutSlot slot) {
            return switch (slot) {
                case MAIN -> main;
                case SIDEARM -> sidearm;
                case THROWABLE -> throwable;
                case GADGET -> gadget;
                case MELEE -> melee;
            };
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
        writeOptional(buf, msg.personal.main());
        writeOptional(buf, msg.personal.sidearm());
        writeOptional(buf, msg.personal.throwable());
        writeOptional(buf, msg.personal.gadget());
        writeOptional(buf, msg.personal.melee());
        writeList(buf, msg.whitelists.main());
        writeList(buf, msg.whitelists.sidearm());
        writeList(buf, msg.whitelists.throwable());
        writeList(buf, msg.whitelists.gadget());
        writeList(buf, msg.whitelists.melee());
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
        PersonalData personal = new PersonalData(readOptional(buf), readOptional(buf), readOptional(buf),
                readOptional(buf), readOptional(buf));
        Whitelists whitelists = new Whitelists(readList(buf), readList(buf), readList(buf), readList(buf), readList(buf));
        return new LoadoutSyncPacket(classes, personal, whitelists);
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

    private static void writeList(FriendlyByteBuf buf, List<ResourceLocation> items) {
        buf.writeVarInt(items.size());
        for (ResourceLocation loc : items) {
            buf.writeResourceLocation(loc);
        }
    }

    private static List<ResourceLocation> readList(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<ResourceLocation> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(buf.readResourceLocation());
        }
        return list;
    }

    public static void handle(LoadoutSyncPacket msg, java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleLoadoutSync(msg));
    }
}
