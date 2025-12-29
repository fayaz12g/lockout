package one.fayaz;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class LockoutGame {
    public static final LockoutGame INSTANCE = new LockoutGame();

    public enum GameMode {
        DEATH,
        KILLS
    }

    public enum DeathMatchMode {
        MESSAGE,  // Match by death message string (original behavior)
        SOURCE    // Match by damage source type (more reliable)
    }

    private boolean active = false;
    private boolean paused = false;
    private String pausedPlayerName = "";
    private int countdownTicks = 0;
    private boolean isCountingDown = false;
    private int goal = 5;
    private GameMode mode = GameMode.DEATH;
    private DeathMatchMode deathMatchMode = DeathMatchMode.SOURCE;
    private final Map<UUID, PlayerEntry> players = new LinkedHashMap<>();
    private final Set<String> claimedItems = new HashSet<>();

    // Custom spawn point fields
    private Vec3 customSpawnPos = null;
    private ResourceKey<Level> customSpawnDimension = null;

    public void setGoal(int goal) {
        this.goal = goal;
    }

    public int getGoal() {
        return goal;
    }

    public GameMode getMode() {
        return mode;
    }

    public void setDeathMatchMode(DeathMatchMode deathMatchMode) {
        this.deathMatchMode = deathMatchMode;
    }

    public DeathMatchMode getDeathMatchMode() {
        return deathMatchMode;
    }

    public boolean isPaused() {
        return paused;
    }

    public String getPausedPlayerName() {
        return pausedPlayerName;
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

    public boolean modifyPlayer(ServerPlayer player, int color) {
        UUID uuid = player.getUUID();
        PlayerEntry entry = players.get(uuid);

        if (entry == null) {
            return false;
        }

        // Create new entry with same UUID, name, and claims but new color
        PlayerEntry newEntry = new PlayerEntry(uuid, entry.getName(), color, entry.getClaims());
        players.put(uuid, newEntry);
        return true;
    }

    public boolean removePlayer(ServerPlayer player) {
        if (active) {
            return false;
        }

        UUID uuid = player.getUUID();
        return players.remove(uuid) != null;
    }

    public void syncToPlayer(ServerPlayer player) {
        LockoutNetworking.sendToPlayer(player, goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);
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
        this.paused = false;
        this.pausedPlayerName = "";
        this.claimedItems.clear();
        this.isCountingDown = true;
        this.countdownTicks = 60; // 3 seconds at 20 ticks/second

        // Clear all player claim histories
        for (PlayerEntry entry : players.values()) {
            entry.getClaims().clear();
        }

        // Prepare all players
        for (UUID uuid : players.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                preparePlayer(player);
                freezePlayer(player);
            }
        }

        String modeName = mode == GameMode.DEATH ? "Death" : "Kills";
        broadcastToServer(server, Component.literal("🎮 " + modeName + " Lockout Starting...").withStyle(style -> style.withColor(0xFFFF55).withBold(true)));
        LockoutNetworking.broadcastState(server, goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);
    }

    public void tick(MinecraftServer server) {
        if (!isCountingDown) return;

        countdownTicks--;

        // Broadcast countdown at specific intervals
        if (countdownTicks == 60) { // 3
            broadcastToServer(server, Component.literal("3").withStyle(style -> style.withColor(0xFF5555).withBold(true)));
        } else if (countdownTicks == 40) { // 2
            broadcastToServer(server, Component.literal("2").withStyle(style -> style.withColor(0xFFAA00).withBold(true)));
        } else if (countdownTicks == 20) { // 1
            broadcastToServer(server, Component.literal("1").withStyle(style -> style.withColor(0xFFFF55).withBold(true)));
        } else if (countdownTicks == 0) { // GO!
            broadcastToServer(server, Component.literal("GO!").withStyle(style -> style.withColor(0x55FF55).withBold(true)));
            isCountingDown = false;

            // Unfreeze all players
            for (UUID uuid : players.keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    unfreezePlayer(player);
                }
            }
        }
    }

    private void preparePlayer(ServerPlayer player) {
        // Clear inventory
        player.getInventory().clearContent();

        // Heal and feed
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0f);

        var server = player.level().getServer();

        // Teleport to custom spawn or world spawn
        if (customSpawnPos != null && customSpawnDimension != null) {
            // Use custom spawn point
            ServerLevel targetLevel = server.getLevel(customSpawnDimension);
            if (targetLevel != null) {
                player.teleportTo(
                        customSpawnPos.x,
                        customSpawnPos.y,
                        customSpawnPos.z
                );
            } else {
                // Fallback to overworld spawn if dimension doesn't exist
                teleportToWorldSpawn(player, server);
            }
        } else {
            // Default to world spawn
            teleportToWorldSpawn(player, server);
        }

        // Set to survival
        player.setGameMode(GameType.SURVIVAL);

        // clear effects
        player.getActiveEffects().clear();

        // clear levels
        player.setExperiencePoints(0);
    }

    private void teleportToWorldSpawn(ServerPlayer player, MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        BlockPos spawnPos = overworld.getRespawnData().globalPos().pos();

        player.teleportTo(
                spawnPos.getX() + 0.5,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5
        );
    }

    private void freezePlayer(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Integer.MAX_VALUE, 255, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, Integer.MAX_VALUE, 255, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, Integer.MAX_VALUE, 255, false, false));
    }

    private void unfreezePlayer(ServerPlayer player) {
        player.removeEffect(MobEffects.SLOWNESS);
        player.removeEffect(MobEffects.JUMP_BOOST);
        player.removeEffect(MobEffects.MINING_FATIGUE);
    }

    public void handlePause(MinecraftServer server) {
        if (!active || paused) return;

        // Pause the game
        paused = true;

        broadcastToServer(server, Component.literal("⏸ Game paused").withStyle(style -> style.withColor(0xFFAA00).withBold(true)));

        // Freeze all players
        for (UUID uuid : players.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                freezePlayer(player);
            }
        }

        LockoutNetworking.broadcastState(server, goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);
    }

    public void setMode(GameMode mode) {
        this.mode = mode;
    }

    public void handleUnpause(MinecraftServer server) {
        if (!active || !paused) return;

        // Unpause the game
        paused = false;
        pausedPlayerName = "";

        broadcastToServer(server, Component.literal("▶ Game resumed!").withStyle(style -> style.withColor(0x55FF55).withBold(true)));

        // Unfreeze all players
        for (UUID uuid : players.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                unfreezePlayer(player);
            }
        }

        LockoutNetworking.broadcastState(server, goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);
    }

    public void handlePlayerDisconnect(ServerPlayer player) {
        if (!active || paused) return;

        UUID uuid = player.getUUID();
        if (!players.containsKey(uuid)) return;

        // Pause the game
        paused = true;
        pausedPlayerName = player.getName().getString();

        MinecraftServer server = player.level().getServer();
        broadcastToServer(server, Component.literal("⏸ Game paused - waiting for " + pausedPlayerName + " to reconnect").withStyle(style -> style.withColor(0xFFAA00).withBold(true)));

        // Freeze all other players
        for (UUID otherUuid : players.keySet()) {
            if (!otherUuid.equals(uuid)) {
                ServerPlayer otherPlayer = server.getPlayerList().getPlayer(otherUuid);
                if (otherPlayer != null) {
                    freezePlayer(otherPlayer);
                }
            }
        }

        LockoutNetworking.broadcastState(server, goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);
    }

    public void handlePlayerReconnect(ServerPlayer player) {
        if (!active || !paused) return;

        UUID uuid = player.getUUID();
        if (!players.containsKey(uuid)) return;
        if (!player.getName().getString().equals(pausedPlayerName)) return;

        // Unpause the game
        paused = false;
        pausedPlayerName = "";

        MinecraftServer server = player.level().getServer();
        broadcastToServer(server, Component.literal("▶ Game resumed!").withStyle(style -> style.withColor(0x55FF55).withBold(true)));

        // Unfreeze all players
        for (UUID otherUuid : players.keySet()) {
            ServerPlayer otherPlayer = server.getPlayerList().getPlayer(otherUuid);
            if (otherPlayer != null) {
                unfreezePlayer(otherPlayer);
            }
        }

        LockoutNetworking.broadcastState(server, goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);
    }

    public void setSpawn(ServerPlayer player, Vec3 pos) {
        this.customSpawnPos = pos;
        this.customSpawnDimension = player.level().dimension();
    }

    public String getSpawnInfo() {
        if (customSpawnPos != null && customSpawnDimension != null) {
            String dimensionName = customSpawnDimension.identifier().getPath();
            return String.format("Custom (%.1f, %.1f, %.1f in %s)",
                    customSpawnPos.x, customSpawnPos.y, customSpawnPos.z, dimensionName);
        } else {
            return "World spawn (default)";
        }
    }

    public void stop(MinecraftServer server) {
        // Unfreeze all players before stopping
        if (isCountingDown || paused) {
            for (UUID uuid : players.keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    unfreezePlayer(player);
                }
            }
        }

        this.active = false;
        LockoutNetworking.broadcastState(server, goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);
    }

    public boolean isActive() {
        return active;
    }

    public void handleDeath(ServerPlayer player, DamageSource source) {
        if (!active || paused || isCountingDown || mode != GameMode.DEATH) return;

        UUID uuid = player.getUUID();
        PlayerEntry entry = players.get(uuid);

        if (entry == null) return;

        String uniqueKey;

        if (deathMatchMode == DeathMatchMode.MESSAGE) {
            Component deathMessage = source.getLocalizedDeathMessage(player);
            String rawText = deathMessage.getString();

            uniqueKey = rawText;
            for (PlayerEntry p : players.values()) {
                uniqueKey = uniqueKey.replace(p.getName(), "");
            }
            uniqueKey = uniqueKey.trim();
        } else {
            uniqueKey = source.type().msgId();

            if (source.getEntity() != null) {
                uniqueKey += ":" + source.getEntity().getType().getDescription().getString();
            }
        }

        if (claimedItems.contains(uniqueKey)) {
            player.sendSystemMessage(Component.literal("❌ Someone's already claimed that one!").withStyle(style -> style.withColor(0xFF5555)));
            return;
        }

        claimedItems.add(uniqueKey);
        entry.addClaim(uniqueKey);

        Component deathMessage = source.getLocalizedDeathMessage(player);
        broadcastToServer(player.level().getServer(),
                Component.literal("⬛ " + entry.getName() + " got a point! ").withStyle(style -> style.withColor(entry.getColor()))
                        .append(Component.literal("(" + deathMessage.getString() + ")").withStyle(style -> style.withColor(0xAAAAAA))));

        LockoutNetworking.broadcastState(player.level().getServer(), goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);

        if (entry.getScore() >= goal) {
            win(player, entry);
        }
    }

    /**
     * Check if a color is already taken by another player
     * @param color The color to check
     * @return The name of the player with that color, or null if no one has it
     */
    public String getPlayerWithColor(int color) {
        for (PlayerEntry entry : players.values()) {
            if (entry.getColor() == color) {
                return entry.getName();
            }
        }
        return null;
    }

    public void handleKill(ServerPlayer player, LivingEntity killed) {
        if (!active || paused || isCountingDown || mode != GameMode.KILLS) return;

        UUID uuid = player.getUUID();
        PlayerEntry entry = players.get(uuid);

        if (entry == null) return;

        String entityName = killed.getType().getDescription().getString();

        if (claimedItems.contains(entityName)) {
            player.sendSystemMessage(Component.literal("❌ Someone's already claimed that one!").withStyle(style -> style.withColor(0xFF5555)));
            return;
        }

        claimedItems.add(entityName);
        entry.addClaim(entityName);

        broadcastToServer(player.level().getServer(),
                Component.literal("⚔ " + entry.getName() + " killed a " + entityName + "!").withStyle(style -> style.withColor(entry.getColor())));

        LockoutNetworking.broadcastState(player.level().getServer(), goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);

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