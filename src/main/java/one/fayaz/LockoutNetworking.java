package one.fayaz;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LockoutNetworking {

    public static final CustomPacketPayload.Type<LockoutSyncPayload> SYNC_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("deathlockout", "sync"));

    // Changed s1/s2 to Lists
    public record LockoutSyncPayload(int goal, List<String> p1Deaths, List<String> p2Deaths, UUID p1, UUID p2) implements CustomPacketPayload {

        public static final StreamCodec<RegistryFriendlyByteBuf, LockoutSyncPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, LockoutSyncPayload::goal,
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), LockoutSyncPayload::p1Deaths,
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), LockoutSyncPayload::p2Deaths,
                UUIDUtil.STREAM_CODEC, LockoutSyncPayload::p1,
                UUIDUtil.STREAM_CODEC, LockoutSyncPayload::p2,
                LockoutSyncPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return SYNC_TYPE;
        }
    }

    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(SYNC_TYPE, LockoutSyncPayload.CODEC);
    }

    // Update broadcast to take lists
    public static void broadcastState(MinecraftServer server, int goal, List<String> d1, List<String> d2, UUID p1, UUID p2) {
        if (server == null) return;
        UUID safeP1 = p1 == null ? UUID.randomUUID() : p1;
        UUID safeP2 = p2 == null ? UUID.randomUUID() : p2;

        LockoutSyncPayload payload = new LockoutSyncPayload(goal, d1, d2, safeP1, safeP2);
        for (ServerPlayer player : PlayerLookup.all(server)) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}