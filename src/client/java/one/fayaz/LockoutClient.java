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
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        public final List<ItemStack> icons = new ArrayList<>();

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
        KeyMapping.Category LOCKOUT_CATEGORY =
                KeyMapping.Category.register(
                        Identifier.fromNamespaceAndPath("one.fayaz", "lockout")
                );

        OPEN_UI_KEY = new KeyMapping(
                "one.fayaz.lockout.open_ui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                LOCKOUT_CATEGORY
        );

        KeyMappingHelper.registerKeyMapping(OPEN_UI_KEY);

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
        int slotSize = 18;
        int gap = 2;
        int centerY = 12;

        // Calculate slots per player (goal - 1)
        int slotsPerPlayer = Math.max(1, clientGoal - 1);

        // Victory box in the center
        int victoryBoxSize = 28;
        int victoryBoxX = (screenWidth - victoryBoxSize) / 2;
        int victoryBoxY = centerY - 5;

        // Render victory box
        renderSlotBackground(graphics, victoryBoxX, victoryBoxY, victoryBoxSize, 0x88000000, false);

        // Calculate player row width
        int playerRowWidth = slotsPerPlayer * (slotSize + gap) - gap;
        int gapBetweenRowAndGoal = gap * 2;

        // Render each player's row
        for (int playerIndex = 0; playerIndex < clientPlayers.size(); playerIndex++) {
            PlayerData player = clientPlayers.get(playerIndex);

            // Determine which side this player is on (even = left, odd = right)
            boolean isLeftSide = (playerIndex % 2 == 0);

            // Calculate vertical position (players stack vertically within their side)
            int sideIndex = playerIndex / 2; // 0,1 -> 0; 2,3 -> 1; etc.
            int y = centerY + sideIndex * (slotSize + 4);

            // Render each slot for this player
            for (int i = 0; i < slotsPerPlayer; i++) {
                boolean isClaimed = i < player.claims.size();
                int tint = (player.color & 0xFFFFFF) | 0x88000000;

                int x;
                int slotIndex; // Which slot to render (for right side, reverse order)

                if (isLeftSide) {
                    // Left side: progress left to right (slot 0 is leftmost)
                    x = victoryBoxX - gapBetweenRowAndGoal - playerRowWidth + i * (slotSize + gap);
                    slotIndex = i;
                } else {
                    // Right side: progress right to left (slot 0 is rightmost, closest to goal)
                    x = victoryBoxX + victoryBoxSize + gapBetweenRowAndGoal + (slotsPerPlayer - 1 - i) * (slotSize + gap);
                    slotIndex = i;
                }

                // Check if this specific slot is claimed
                boolean thisSlotClaimed = slotIndex < player.claims.size();

                // Slot background
                renderSlotBackground(graphics, x, y, slotSize, tint, thisSlotClaimed);

                // Item if claimed
                if (slotIndex < player.icons.size()) {
                    graphics.renderItem(player.icons.get(slotIndex), x + 1, y + 1);

                    // Render overlay for mixed mode
                    if (clientMode.equals("MIXED") && slotIndex < player.claims.size()) {
                        LockoutNetworking.ClaimData claim = player.claims.get(slotIndex);
                        renderMixedModeOverlay(graphics, claim, x, y, slotSize);
                    }
                }

            }
        }

        // Check if anyone has won and render their icon in victory box
        for (PlayerData player : clientPlayers) {
            if (player.icons.size() >= clientGoal && clientGoal > 0) {
                ItemStack winningIcon = player.icons.get(player.icons.size() - 1);
                LockoutNetworking.ClaimData claim = player.claims.get(player.icons.size() - 1);

                // Add winning player's color glow
                int winTint = (player.color & 0xFFFFFF) | 0x88000000;
                graphics.fill(
                        victoryBoxX + 1,
                        victoryBoxY + 1,
                        victoryBoxX + victoryBoxSize - 1,
                        victoryBoxY + victoryBoxSize - 1,
                        winTint
                );

                // Render the winning item scaled up to fit the larger victory box
                graphics.pose().pushMatrix();
                graphics.pose().translate(victoryBoxX + 2, victoryBoxY + 2);
                graphics.pose().scale(1.5F, 1.5F);
                graphics.renderItem(winningIcon, 0, 0);
                graphics.pose().popMatrix();

                // Render overlay for mixed mode
                if (clientMode.equals("MIXED")) {
                    renderMixedModeOverlay(graphics, claim, victoryBoxX, victoryBoxY, victoryBoxSize);
                }
            }
            break;
        }
    }


    private void renderSlotBackground(GuiGraphics graphics, int x, int y, int size, int tint, boolean isClaimed) {
        // Use the vanilla container slot sprite
        Identifier slotSprite = Identifier.withDefaultNamespace("container/slot");

        // Render the slot background using the sprite with proper alpha blending and white color
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, slotSprite, x, y, size, size, 0xFFFFFFFF);

        // Add colored tint overlay only if claimed
        if (isClaimed) {
            graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, tint);
        }
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