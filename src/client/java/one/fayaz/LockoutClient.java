package one.fayaz;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import one.fayaz.icon.ItemStackFinder;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import java.util.ArrayList;
import java.util.List;

import static one.fayaz.GoalType.*;

public class LockoutClient implements ClientModInitializer {

    // ================= CLIENT STATE =================

    public static int clientGoal = 0;
    public static String clientMode = "DEATH";
    public static String trueMode = "ARMOR";
    public static boolean clientPaused = false;
    public static String clientPausedPlayerName = "";

    public static final List<PlayerData> clientPlayers = new ArrayList<>();

    // ================= KEYBIND =================

    private static KeyMapping OPEN_UI_KEY;

    // ================= SOUND TRACKING =================

    // Track previous claim counts to detect new goals
    private final List<Integer> previousClaimCounts = new ArrayList<>();

    // Track pause state for countdown
    private boolean wasPaused = false;

    // Track if game has ended to prevent multiple victory sounds
    private boolean gameEnded = false;

    // ================= PLAYER DATA =================

    public static class PlayerData {
        public final String name;
        public final int color;

        public final List<LockoutNetworking.ClaimData> claims = new ArrayList<>();
        public final List<net.minecraft.world.item.ItemStack> icons = new ArrayList<>();

        public PlayerData(String name, int color, List<LockoutNetworking.ClaimData> claims) {
            this.name = name;
            this.color = color;
            setClaims(claims);
        }

        public void setClaims(List<LockoutNetworking.ClaimData> newClaims) {
            claims.clear();
            icons.clear();

            for (LockoutNetworking.ClaimData claim : newClaims) {
                claims.add(claim);
                icons.add(
                        ItemStackFinder.getIconForClaim(
                                claim.id(),
                                claim.type()
                        )
                );
            }
        }
    }

    // ================= INIT =================

    @Override
    public void onInitializeClient() {

        // ---- System Message Listener ----
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String messageText = message.getString();
            if (messageText.startsWith("❌")) {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null && client.level != null) {
                    client.level.playLocalSound(
                            client.player.getX(),
                            client.player.getY(),
                            client.player.getZ(),
                            SoundEvents.WITCH_CELEBRATE,
                            SoundSource.PLAYERS,
                            1.0F,
                            1.0F,
                            false
                    );
                }
            }
        });

        // ---- Networking ----
        ClientPlayNetworking.registerGlobalReceiver(
                LockoutNetworking.SYNC_TYPE,
                (payload, context) -> context.client().execute(() -> {
                    clientGoal = payload.goal();
                    clientMode = payload.mode();

                    // Detect unpause (game start/resume)
                    boolean wasJustUnpaused = wasPaused && !payload.paused();

                    clientPaused = payload.paused();
                    clientPausedPlayerName = payload.pausedPlayerName();

                    // Store old player data for sound comparison
                    List<PlayerData> oldPlayers = new ArrayList<>(clientPlayers);

                    clientPlayers.clear();
                    for (LockoutNetworking.PlayerData pd : payload.players()) {
                        clientPlayers.add(
                                new PlayerData(
                                        pd.name(),
                                        pd.color(),
                                        pd.claims()
                                )
                        );
                    }

                    // Play sounds for new goals
                    playSoundsForGoals(oldPlayers);

                    // Check for game end (someone reached the goal)
                    checkForGameEnd();

                    // Play countdown sound when game unpauses
                    if (wasJustUnpaused) {
                        playCountdownSound();
                    }

                    // Update pause tracking
                    wasPaused = payload.paused();
                })
        );

        // ---- HUD ----
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            if (!clientPlayers.isEmpty() && clientGoal > 0) {
                renderHudStrip(graphics);
            }
        });

        // ---- Keybind ----
        net.minecraft.resources.Identifier LOCKOUT_CATEGORY_ID =
                net.minecraft.resources.Identifier.fromNamespaceAndPath("one.fayaz", "lockout");

        KeyMapping.Category LOCKOUT_CATEGORY = KeyMapping.Category.register(LOCKOUT_CATEGORY_ID);

        OPEN_UI_KEY = new KeyMapping(
                "one.fayaz.lockout.open_ui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                LOCKOUT_CATEGORY
        );

        KeyBindingHelper.registerKeyBinding(OPEN_UI_KEY);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_UI_KEY.consumeClick()) {
                client.setScreen(new LockoutScreen(clientPlayers, clientMode, clientGoal));
            }
        });
    }

    // ================= SOUND EFFECTS =================

    private void checkForGameEnd() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || gameEnded) return;

        // Check if any player has reached the goal
        for (PlayerData player : clientPlayers) {
            if (player.claims.size() >= clientGoal && clientGoal > 0) {
                // Game has ended! Play dramatic end portal sound
                client.level.playLocalSound(
                        client.player.getX(),
                        client.player.getY(),
                        client.player.getZ(),
                        SoundEvents.END_PORTAL_SPAWN,
                        SoundSource.PLAYERS,
                        1.0F,
                        1.0F,
                        false
                );
                gameEnded = true;
                break;
            }
        }
    }

    private void playCountdownSound() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        // Play an exciting countdown/start sound
        client.level.playLocalSound(
                client.player.getX(),
                client.player.getY(),
                client.player.getZ(),
                SoundEvents.NOTE_BLOCK_BELL.value(),
                SoundSource.PLAYERS,
                1.0F,
                2.0F, // Higher pitch for excitement
                false
        );
    }

    private void playSoundsForGoals(List<PlayerData> oldPlayers) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        String localPlayerName = client.player.getName().getString();

        // Compare old and new player data
        for (int i = 0; i < clientPlayers.size(); i++) {
            PlayerData newPlayer = clientPlayers.get(i);

            // Find matching player in old data
            PlayerData oldPlayer = null;
            for (PlayerData old : oldPlayers) {
                if (old.name.equals(newPlayer.name)) {
                    oldPlayer = old;
                    break;
                }
            }

            // Check if this player got a new goal
            int oldClaimCount = (oldPlayer != null) ? oldPlayer.claims.size() : 0;
            int newClaimCount = newPlayer.claims.size();

            if (newClaimCount > oldClaimCount) {
                // This player got a new goal!
                boolean isLocalPlayer = newPlayer.name.equals(localPlayerName);

                if (isLocalPlayer) {
                    // Play positive sound for local player
                    client.level.playLocalSound(
                            client.player.getX(),
                            client.player.getY(),
                            client.player.getZ(),
                            SoundEvents.PLAYER_LEVELUP,
                            SoundSource.PLAYERS,
                            0.5F,
                            1.0F,
                            false
                    );
                } else {
                    // Play negative sound for opponent
                    client.level.playLocalSound(
                            client.player.getX(),
                            client.player.getY(),
                            client.player.getZ(),
                            SoundEvents.ENDERMAN_DEATH,
                            SoundSource.PLAYERS,
                            0.5F,
                            1.0F,
                            false
                    );
                }
            }
        }
    }

    // ================= HUD RENDER =================

    private void renderHudStrip(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        int screenWidth = client.getWindow().getGuiScaledWidth();
        LockoutHud.renderLockout(
                graphics,
                clientPlayers,
                clientMode,
                clientGoal,
                screenWidth,
                12, // centerY for HUD
                null, // no font needed for HUD
                -1, -1 // no mouse tracking for HUD
        );
    }
    private void renderMixedModeOverlay(GuiGraphics graphics, LockoutNetworking.ClaimData claim, int x, int y, int slotSize) {
        // Define overlay based on claim type
        Identifier overlay = switch (claim.type()) {
            case KILL -> Identifier.fromNamespaceAndPath("lockout", "textures/gui/sword.png");
            case DEATH -> Identifier.fromNamespaceAndPath("lockout", "textures/gui/skull.png");
            case ADVANCEMENT -> Identifier.fromNamespaceAndPath("lockout", "textures/gui/recipe_book.png");
            case FOOD -> Identifier.fromNamespaceAndPath("lockout", "textures/gui/food.png");
            case ARMOR -> Identifier.fromNamespaceAndPath("lockout", "textures/gui/armor.png");
            case BREED -> Identifier.fromNamespaceAndPath("lockout", "textures/gui/heart.png");
        };

        // Scale and render the 16x16 texture as 8x8 in the corner
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + slotSize - 8, y + slotSize - 8);
        graphics.pose().scale(0.5F, 0.5F);  // Scale down to 50% (16x16 -> 8x8)
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                overlay,
                0,
                0,
                0,    // u (texture x)
                0,    // v (texture y)
                16,   // width on screen (will be scaled to 8)
                16,   // height on screen (will be scaled to 8)
                16,   // texture width
                16    // texture height
        );
        graphics.pose().popMatrix();
    }
}