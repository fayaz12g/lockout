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

    public record PlayerData(UUID uuid, String name, int color, List<String> claims) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayerData> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, PlayerData::uuid,
                ByteBufCodecs.STRING_UTF8, PlayerData::name,
                ByteBufCodecs.INT, PlayerData::color,
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), PlayerData::claims,
                PlayerData::new
        );
    }

    public record LockoutSyncPayload(int goal, List<PlayerData> players, String mode) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, LockoutSyncPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, LockoutSyncPayload::goal,
                ByteBufCodecs.collection(ArrayList::new, PlayerData.CODEC), LockoutSyncPayload::players,
                ByteBufCodecs.STRING_UTF8, LockoutSyncPayload::mode,
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

    public static void sendToPlayer(ServerPlayer player, int goal, List<PlayerEntry> playerEntries, LockoutGame.GameMode mode) {
        List<PlayerData> playerDataList = new ArrayList<>();
        for (PlayerEntry entry : playerEntries) {
            playerDataList.add(new PlayerData(
                    entry.getUuid(),
                    entry.getName(),
                    entry.getColor(),
                    new ArrayList<>(entry.getClaims())
            ));
        }

        LockoutSyncPayload payload = new LockoutSyncPayload(goal, playerDataList, mode.toString());
        ServerPlayNetworking.send(player, payload);
    }

    public static void broadcastState(MinecraftServer server, int goal, List<PlayerEntry> playerEntries, LockoutGame.GameMode mode) {
        if (server == null) return;

        List<PlayerData> playerDataList = new ArrayList<>();
        for (PlayerEntry entry : playerEntries) {
            playerDataList.add(new PlayerData(
                    entry.getUuid(),
                    entry.getName(),
                    entry.getColor(),
                    new ArrayList<>(entry.getClaims())
            ));
        }

        LockoutSyncPayload payload = new LockoutSyncPayload(goal, playerDataList, mode.toString());

        for (ServerPlayer player : PlayerLookup.all(server)) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}