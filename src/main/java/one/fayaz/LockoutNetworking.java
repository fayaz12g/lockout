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
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("lockout", "sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GoalType> GOAL_TYPE_CODEC =
            new StreamCodec<>() {
                @Override
                public GoalType decode(RegistryFriendlyByteBuf buf) {
                    return GoalType.valueOf(ByteBufCodecs.STRING_UTF8.decode(buf));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, GoalType value) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, value.name());
                }
            };

    public record ClaimData(String id, GoalType type) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ClaimData> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, ClaimData::id,
                        GOAL_TYPE_CODEC, ClaimData::type,
                        ClaimData::new
                );
    }

    public record PlayerData(UUID uuid, String name, int color, List<ClaimData> claims) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayerData> CODEC =
                StreamCodec.composite(
                        UUIDUtil.STREAM_CODEC, PlayerData::uuid,
                        ByteBufCodecs.STRING_UTF8, PlayerData::name,
                        ByteBufCodecs.INT, PlayerData::color,
                        ByteBufCodecs.collection(ArrayList::new, ClaimData.CODEC), PlayerData::claims,
                        PlayerData::new
                );
    }


    public record LockoutSyncPayload(int goal, List<PlayerData> players, String mode, boolean paused, String pausedPlayerName) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, LockoutSyncPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, LockoutSyncPayload::goal,
                ByteBufCodecs.collection(ArrayList::new, PlayerData.CODEC), LockoutSyncPayload::players,
                ByteBufCodecs.STRING_UTF8, LockoutSyncPayload::mode,
                ByteBufCodecs.BOOL, LockoutSyncPayload::paused,
                ByteBufCodecs.STRING_UTF8, LockoutSyncPayload::pausedPlayerName,
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

    public static void sendToPlayer(
            ServerPlayer player,
            int goal,
            List<PlayerEntry> playerEntries,
            LockoutGame.GameMode mode,
            boolean paused,
            String pausedPlayerName
    ) {
        List<PlayerData> playerDataList = new ArrayList<>();

        for (PlayerEntry entry : playerEntries) {

            // Convert game ClaimData -> network ClaimData
            List<ClaimData> claims = new ArrayList<>();
            for (one.fayaz.ClaimData claim : entry.getClaims()) {
                claims.add(new ClaimData(
                        claim.id(),   // or getId()
                        claim.type()  // GoalType
                ));
            }

            playerDataList.add(new PlayerData(
                    entry.getUuid(),
                    entry.getName(),
                    entry.getColor(),
                    claims
            ));
        }

        LockoutSyncPayload payload =
                new LockoutSyncPayload(goal, playerDataList, mode.toString(), paused, pausedPlayerName);

        ServerPlayNetworking.send(player, payload);
    }


    public static void broadcastState(
            MinecraftServer server,
            int goal,
            List<PlayerEntry> playerEntries,
            LockoutGame.GameMode mode,
            boolean paused,
            String pausedPlayerName
    ) {
        if (server == null) return;

        List<PlayerData> playerDataList = new ArrayList<>();

        for (PlayerEntry entry : playerEntries) {

            List<ClaimData> claims = new ArrayList<>();
            for (one.fayaz.ClaimData claim : entry.getClaims()) {
                claims.add(new ClaimData(
                        claim.id(),
                        claim.type()
                ));
            }

            playerDataList.add(new PlayerData(
                    entry.getUuid(),
                    entry.getName(),
                    entry.getColor(),
                    claims
            ));
        }

        LockoutSyncPayload payload =
                new LockoutSyncPayload(goal, playerDataList, mode.toString(), paused, pausedPlayerName);

        for (ServerPlayer player : PlayerLookup.all(server)) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}