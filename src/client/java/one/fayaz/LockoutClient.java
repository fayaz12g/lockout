package one.fayaz;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import java.util.ArrayList;
import java.util.List;

public class LockoutClient implements ClientModInitializer {

    // ================= CLIENT STATE =================

    public static int clientGoal = 0;
    public static String clientMode = "DEATH";
    public static boolean clientPaused = false;
    public static String clientPausedPlayerName = "";

    public static final List<PlayerData> clientPlayers = new ArrayList<>();

    // ================= KEYBIND =================

    private static KeyMapping OPEN_UI_KEY;

    // ================= PLAYER DATA =================

    public static class PlayerData {
        public final String name;
        public final int color;
        public final List<String> claims = new ArrayList<>();
        public final List<ItemStack> icons = new ArrayList<>();

        public PlayerData(String name, int color, List<String> claims) {
            this.name = name;
            this.color = color;
            setClaims(claims);
        }

        public void setClaims(List<String> newClaims) {
            claims.clear();
            icons.clear();

            for (String claim : newClaims) {
                claims.add(claim);
                icons.add(ItemStackFinder.getIconForClaim(claim));
            }
        }
    }

    // ================= INIT =================

    @Override
    public void onInitializeClient() {

        // ---- Networking ----
        ClientPlayNetworking.registerGlobalReceiver(
                LockoutNetworking.SYNC_TYPE,
                (payload, context) -> context.client().execute(() -> {
                    clientGoal = payload.goal();
                    clientMode = payload.mode();
                    clientPaused = payload.paused();
                    clientPausedPlayerName = payload.pausedPlayerName();

                    clientPlayers.clear();
                    for (LockoutNetworking.PlayerData pd : payload.players()) {
                        clientPlayers.add(new PlayerData(pd.name(), pd.color(), pd.claims()));
                    }
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
        int victoryBoxSize = 24;
        int victoryBoxX = (screenWidth - victoryBoxSize) / 2;
        int victoryBoxY = centerY;

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
                }
            }
        }

        // Check if anyone has won and render their icon in victory box
        for (PlayerData player : clientPlayers) {
            if (player.icons.size() >= clientGoal && clientGoal > 0) {
                ItemStack winningIcon = player.icons.get(player.icons.size() - 1);

                // Add winning player's color glow
                int winTint = (player.color & 0xFFFFFF) | 0x88_000000;
                graphics.fill(
                        victoryBoxX + 1,
                        victoryBoxY + 1,
                        victoryBoxX + victoryBoxSize - 1,
                        victoryBoxY + victoryBoxSize - 1,
                        winTint
                );
                graphics.renderItem(winningIcon, victoryBoxX + 3, victoryBoxY + 3);
                break;
            }
        }
    }

    private void renderSlotBackground(GuiGraphics graphics, int x, int y, int size, int tint, boolean isClaimed) {
        // Dark slot background
        graphics.fill(x, y, x + size, y + size, 0xFF_000000);

        // Border - brighter if claimed, darker if not
        int borderBright = isClaimed ? 0xFF_FFFFFF : 0xFF_8B8B8B;
        int borderDark = isClaimed ? 0xFF_555555 : 0xFF_373737;

        graphics.fill(x, y, x + size, y + 1, borderBright); // Top
        graphics.fill(x, y, x + 1, y + size, borderBright); // Left
        graphics.fill(x + size - 1, y, x + size, y + size, borderDark); // Right
        graphics.fill(x, y + size - 1, x + size, y + size, borderDark); // Bottom

        // Inner background with tint only if claimed
        if (isClaimed) {
            // Use lighter tint (88 instead of 55) for better visibility
            graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, tint);
        }
    }
}