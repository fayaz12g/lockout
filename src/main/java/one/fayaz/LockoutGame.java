package one.fayaz;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.*;

public class LockoutGame {
    public static final LockoutGame INSTANCE = new LockoutGame();

    public enum GameMode {
        DEATH,
        KILLS
    }

    private boolean active = false;
    private int goal = 0;
    private GameMode mode = GameMode.DEATH;
    private final Map<UUID, PlayerEntry> players = new LinkedHashMap<>();
    private final Set<String> claimedItems = new HashSet<>(); // Used for both deaths and kills

    public void setGoal(int goal) {
        this.goal = goal;
    }

    public int getGoal() {
        return goal;
    }

    public GameMode getMode() {
        return mode;
    }

    public boolean addPlayer(ServerPlayer player, int color) {
        if (active) {
            player.sendSystemMessage(Component.literal("❌ Cannot add players while game is active!").withStyle(style -> style.withColor(0xFF5555)));
            return false;
        }

        UUID uuid = player.getUUID();
        if (players.containsKey(uuid)) {
            player.sendSystemMessage(Component.literal("❌ Player already added!").withStyle(style -> style.withColor(0xFF5555)));
            return false;
        }

        players.put(uuid, new PlayerEntry(uuid, player.getName().getString(), color));
        player.sendSystemMessage(Component.literal("✓ Added to lockout with color!").withStyle(style -> style.withColor(0x55FF55)));
        return true;
    }

    public int canStart() {
        if (goal < 1) {
            return -1;
        }
        else {
            return players.size();
        }
    }

    public void start(MinecraftServer server, GameMode mode) {
        if (canStart() < 2) {
            broadcastToServer(server, Component.literal("🎮 FAILED TO START").withStyle(style -> style.withColor(0xFF5555).withBold(true)));
            return;
        }

        this.active = true;
        this.mode = mode;
        this.claimedItems.clear();

        // Clear all player claim histories
        for (PlayerEntry entry : players.values()) {
            entry.getClaims().clear();
        }

        String modeName = mode == GameMode.DEATH ? "Death" : "Kills";
        broadcastToServer(server, Component.literal("🎮 " + modeName + " Lockout Started! Goal: " + goal).withStyle(style -> style.withColor(0x55FF55).withBold(true)));
        LockoutNetworking.broadcastState(server, goal, new ArrayList<>(players.values()), mode);
    }

    public void stop(MinecraftServer server) {
        this.active = false;
        this.players.clear();
        this.goal = 0;
        this.mode = GameMode.DEATH;
        LockoutNetworking.broadcastState(server, 0, new ArrayList<>(), GameMode.DEATH);
    }

    public boolean isActive() {
        return active;
    }

    public void handleDeath(ServerPlayer player, DamageSource source) {
        if (!active || mode != GameMode.DEATH) return;

        UUID uuid = player.getUUID();
        PlayerEntry entry = players.get(uuid);

        if (entry == null) return;

        Component deathMessage = source.getLocalizedDeathMessage(player);
        String rawText = deathMessage.getString();

        // Remove all player names from the death message to get unique key
        String uniqueKey = rawText;
        for (PlayerEntry p : players.values()) {
            uniqueKey = uniqueKey.replace(p.getName(), "");
        }
        uniqueKey = uniqueKey.trim();

        if (claimedItems.contains(uniqueKey)) {
            player.sendSystemMessage(Component.literal("❌ Someone's already claimed that one!").withStyle(style -> style.withColor(0xFF5555)));
            return;
        }

        claimedItems.add(uniqueKey);
        entry.addClaim(uniqueKey);

        // Broadcast point gain
        broadcastToServer(player.level().getServer(),
                Component.literal("⬛ " + entry.getName() + " got a point!").withStyle(style -> style.withColor(entry.getColor())));

        LockoutNetworking.broadcastState(player.level().getServer(), goal, new ArrayList<>(players.values()), mode);

        // Check for winner
        if (entry.getScore() >= goal) {
            win(player, entry);
        }
    }

    public void handleKill(ServerPlayer player, LivingEntity killed) {
        if (!active || mode != GameMode.KILLS) return;

        UUID uuid = player.getUUID();
        PlayerEntry entry = players.get(uuid);

        if (entry == null) return;

        // Get the entity type name as the unique key
        String entityName = killed.getType().getDescription().getString();

        if (claimedItems.contains(entityName)) {
            player.sendSystemMessage(Component.literal("❌ Someone's already claimed that one!").withStyle(style -> style.withColor(0xFF5555)));
            return;
        }

        claimedItems.add(entityName);
        entry.addClaim(entityName);

        // Broadcast point gain
        broadcastToServer(player.level().getServer(),
                Component.literal("⚔ " + entry.getName() + " killed a " + entityName + "!").withStyle(style -> style.withColor(entry.getColor())));

        LockoutNetworking.broadcastState(player.level().getServer(), goal, new ArrayList<>(players.values()), mode);

        // Check for winner
        if (entry.getScore() >= goal) {
            win(player, entry);
        }
    }

    private void win(ServerPlayer player, PlayerEntry winner) {
        broadcastToServer(player.level().getServer(),
                Component.literal("🏆 " + winner.getName() + " WINS! 🏆").withStyle(style -> style.withBold(true).withColor(0x55FF55)));
        stop(player.level().getServer());
    }

    private void broadcastToServer(MinecraftServer server, Component msg) {
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(msg, false);
        }
    }

    public Map<UUID, PlayerEntry> getPlayers() {
        return players;
    }
}