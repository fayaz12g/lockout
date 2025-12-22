package one.fayaz;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import java.util.*;

public class LockoutGame {
    public static final LockoutGame INSTANCE = new LockoutGame();

    private boolean active = false;
    private int goal = 10;

    private UUID player1;
    private UUID player2;
    private String p1Name = "";
    private String p2Name = "";

    // Changed to Lists to track history
    private final List<String> p1History = new ArrayList<>();
    private final List<String> p2History = new ArrayList<>();

    private final Set<String> claimedDeaths = new HashSet<>();

    public void start(int goal, ServerPlayer p1, ServerPlayer p2) {
        this.active = true;
        this.goal = goal;
        this.player1 = p1.getUUID();
        this.player2 = p2.getUUID();
        this.p1Name = p1.getName().getString();
        this.p2Name = p2.getName().getString();
        this.p1History.clear();
        this.p2History.clear();
        this.claimedDeaths.clear();

        LockoutNetworking.broadcastState(p1.level().getServer(), goal, p1History, p2History, player1, player2);
    }

    public void stop(MinecraftServer server) {
        this.active = false;
        LockoutNetworking.broadcastState(server, 0, new ArrayList<>(), new ArrayList<>(), player1, player2);
    }

    public boolean isActive() {
        return active;
    }

    public void handleDeath(ServerPlayer player, DamageSource source) {
        if (!active) return;

        boolean isP1 = player.getUUID().equals(player1);
        boolean isP2 = player.getUUID().equals(player2);
        if (!isP1 && !isP2) return;

        Component deathMessage = source.getLocalizedDeathMessage(player);
        String rawText = deathMessage.getString();
        String uniqueKey = rawText.replace(p1Name, "").replace(p2Name, "").trim();

        if (claimedDeaths.contains(uniqueKey)) {
            player.sendSystemMessage(Component.literal("❌ That death is already claimed!").withStyle(style -> style.withColor(0xFF5555)));
            return;
        }

        claimedDeaths.add(uniqueKey);

        if (isP1) {
            p1History.add(uniqueKey); // Store the "clean" message for the icon logic
            broadcast(player, Component.literal("🟦 Player 1: " + uniqueKey));
        } else {
            p2History.add(uniqueKey);
            broadcast(player, Component.literal("🟧 Player 2: " + uniqueKey));
        }

        LockoutNetworking.broadcastState(player.level().getServer(), goal, p1History, p2History, player1, player2);

        if (p1History.size() >= goal) win(player, p1Name);
        else if (p2History.size() >= goal) win(player, p2Name);
    }

    private void win(ServerPlayer player, String winner) {
        broadcast(player, Component.literal("🏆 " + winner + " WINS! 🏆").withStyle(style -> style.withBold(true).withColor(0x55FF55)));
        stop(player.level().getServer());
    }

    private void broadcast(ServerPlayer player, Component msg) {
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(msg, false);
        }
    }
}