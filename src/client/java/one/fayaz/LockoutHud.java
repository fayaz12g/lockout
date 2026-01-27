package one.fayaz;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class LockoutHud {

    private static final int SLOT_SIZE = 18;
    private static final int GAP = 2;
    private static final int VICTORY_BOX_SIZE = 28;
    private static final int GAP_BETWEEN_ROW_AND_GOAL = GAP * 2;

    /**
     * Renders the lockout progress display
     *
     * @param graphics The graphics context
     * @param players List of player data
     * @param mode Game mode (MIXED, etc)
     * @param goal Win condition number
     * @param screenWidth Width of the screen/window
     * @param centerY Vertical center position
     * @param font Font for rendering text (null for HUD mode)
     * @param mouseX Mouse X coordinate (-1 to disable hover)
     * @param mouseY Mouse Y coordinate (-1 to disable hover)
     * @return HoverInfo if mouse is over an item, null otherwise
     */
    public static HoverInfo renderLockout(
            GuiGraphics graphics,
            List<LockoutClient.PlayerData> players,
            String mode,
            int goal,
            int screenWidth,
            int centerY,
            Font font,
            int mouseX,
            int mouseY
    ) {
        // Apply HUD offset to centerY if needed (for HUD mode, adjust starting position)
        int adjustedCenterY = centerY - (font != null ? 0 : 5);

        // Calculate slots per player (goal - 1)
        int slotsPerPlayer = Math.max(1, goal - 1);

        // Calculate available space on each side
        int victoryBoxX = (screenWidth - VICTORY_BOX_SIZE) / 2;
        int availableSpacePerSide = victoryBoxX - 20; // Leave some margin

        // Calculate how many slots can fit on one side
        int maxSlotsPerRow = (availableSpacePerSide - GAP_BETWEEN_ROW_AND_GOAL) / (SLOT_SIZE + GAP);

        // Determine if we need wrapping
        boolean needsWrapping = slotsPerPlayer > maxSlotsPerRow;
        int slotsPerRow = needsWrapping ? maxSlotsPerRow : slotsPerPlayer;
        int numRows = needsWrapping ? (int) Math.ceil((double) slotsPerPlayer / maxSlotsPerRow) : 1;

        // Calculate the center point of all rows
        // First row starts at adjustedCenterY, last row is at adjustedCenterY + (numRows-1) * (SLOT_SIZE + GAP)
        // The center of the slots span is at the middle of the first and last slot centers
        int firstRowCenter = adjustedCenterY + SLOT_SIZE / 2;
        int lastRowCenter = adjustedCenterY + (numRows - 1) * (SLOT_SIZE + GAP) + SLOT_SIZE / 2;
        int slotsVerticalCenter = (firstRowCenter + lastRowCenter) / 2;

        // Position victory box so its center aligns with the slots' vertical center
        int victoryBoxY = slotsVerticalCenter - VICTORY_BOX_SIZE / 2;

        // Render victory box
        renderSlotBackground(graphics, victoryBoxX, victoryBoxY, VICTORY_BOX_SIZE, 0x88000000, false);

        // Calculate player row width (for the first/main row)
        int playerRowWidth = slotsPerRow * (SLOT_SIZE + GAP) - GAP;

        HoverInfo hoverInfo = null;

        // Render each player's row(s)
        for (int playerIndex = 0; playerIndex < players.size(); playerIndex++) {
            LockoutClient.PlayerData player = players.get(playerIndex);

            // Determine which side this player is on (even = left, odd = right)
            boolean isLeftSide = (playerIndex % 2 == 0);

            // Calculate vertical position (players stack vertically within their side)
            int sideIndex = playerIndex / 2;
            int baseY = adjustedCenterY + sideIndex * (SLOT_SIZE + (font != null ? 24 : 4));

            // Draw player name if font is provided (screen mode)
            if (font != null) {
                int nameY = baseY - 12;
                int nameX;

                if (isLeftSide) {
                    nameX = victoryBoxX - GAP_BETWEEN_ROW_AND_GOAL - playerRowWidth;
                } else {
                    nameX = victoryBoxX + VICTORY_BOX_SIZE + GAP_BETWEEN_ROW_AND_GOAL + playerRowWidth -
                            font.width(player.name + " (" + player.icons.size() + "/" + goal + ")");
                }

                graphics.drawString(
                        font,
                        player.name + " (" + player.icons.size() + "/" + goal + ")",
                        nameX,
                        nameY,
                        player.color
                );
            }

            // Render slots for this player (with potential wrapping)
            for (int i = 0; i < slotsPerPlayer; i++) {
                int rowIndex = i / slotsPerRow;
                int colIndex = i % slotsPerRow;

                // Calculate Y position for this row
                int y = baseY + rowIndex * (SLOT_SIZE + GAP);

                int tint = (player.color & 0xFFFFFF) | 0x88000000;
                int slotIndex = i;

                int x;
                if (isLeftSide) {
                    // Left side: progress left to right
                    int rowWidth = Math.min(slotsPerRow, slotsPerPlayer - rowIndex * slotsPerRow) * (SLOT_SIZE + GAP) - GAP;
                    x = victoryBoxX - GAP_BETWEEN_ROW_AND_GOAL - rowWidth + colIndex * (SLOT_SIZE + GAP);
                } else {
                    // Right side: progress right to left
                    int slotsInThisRow = Math.min(slotsPerRow, slotsPerPlayer - rowIndex * slotsPerRow);
                    x = victoryBoxX + VICTORY_BOX_SIZE + GAP_BETWEEN_ROW_AND_GOAL +
                            (slotsInThisRow - 1 - colIndex) * (SLOT_SIZE + GAP);
                }

                // Check if this specific slot is claimed
                boolean thisSlotClaimed = slotIndex < player.claims.size();

                // Slot background
                renderSlotBackground(graphics, x, y, SLOT_SIZE, tint, thisSlotClaimed);

                // Item if claimed
                if (slotIndex < player.icons.size()) {
                    graphics.renderItem(player.icons.get(slotIndex), x + 1, y + 1);

                    // Render overlay for mixed mode
                    if (mode.equals("MIXED") && slotIndex < player.claims.size()) {
                        LockoutNetworking.ClaimData claim = player.claims.get(slotIndex);
                        renderMixedModeOverlay(graphics, claim, x, y, SLOT_SIZE);
                    }

                    // Check for hover
                    if (mouseX >= 0 && isMouseOver(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) {
                        hoverInfo = new HoverInfo(player, slotIndex, mouseX, mouseY);
                    }
                }
            }
        }

        // Check if anyone has won and render their icon in victory box
        for (LockoutClient.PlayerData player : players) {
            if (player.icons.size() >= goal && goal > 0) {
                ItemStack winningIcon = player.icons.get(player.icons.size() - 1);
                LockoutNetworking.ClaimData claim = player.claims.get(player.icons.size() - 1);

                // Add winning player's color glow
                int winTint = (player.color & 0xFFFFFF) | 0x88000000;
                graphics.fill(
                        victoryBoxX + 1,
                        victoryBoxY + 1,
                        victoryBoxX + VICTORY_BOX_SIZE - 1,
                        victoryBoxY + VICTORY_BOX_SIZE - 1,
                        winTint
                );

                // Render the winning item scaled up
                graphics.pose().pushMatrix();
                graphics.pose().translate(victoryBoxX + 2, victoryBoxY + 2);
                graphics.pose().scale(1.5F, 1.5F);
                graphics.renderItem(winningIcon, 0, 0);
                graphics.pose().popMatrix();

                // Render overlay for mixed mode
                if (mode.equals("MIXED")) {
                    renderMixedModeOverlay(graphics, claim, victoryBoxX, victoryBoxY, VICTORY_BOX_SIZE);
                }

                // Check for hover on victory box
                if (mouseX >= 0 && isMouseOver(mouseX, mouseY, victoryBoxX, victoryBoxY, VICTORY_BOX_SIZE, VICTORY_BOX_SIZE)) {
                    hoverInfo = new HoverInfo(player, player.icons.size() - 1, mouseX, mouseY);
                }

                break;
            }
        }

        return hoverInfo;
    }

    private static void renderSlotBackground(GuiGraphics graphics, int x, int y, int size, int tint, boolean isClaimed) {
        // Use the vanilla container slot sprite
        Identifier slotSprite = Identifier.withDefaultNamespace("container/slot");

        // Render the slot background using the sprite with proper alpha blending and white color
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, slotSprite, x, y, size, size, 0xFFFFFFFF);

        // Add colored tint overlay only if claimed
        if (isClaimed) {
            graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, tint);
        }
    }

    private static void renderMixedModeOverlay(GuiGraphics graphics, LockoutNetworking.ClaimData claim, int x, int y, int slotSize) {
        // Define overlay based on claim type
        Identifier overlay = switch (claim.type()) {
            case GoalType.KILL -> Identifier.fromNamespaceAndPath("lockout", "textures/gui/sword.png");
            case GoalType.DEATH -> Identifier.fromNamespaceAndPath("lockout", "textures/gui/skull.png");
            case GoalType.ADVANCEMENT -> Identifier.fromNamespaceAndPath("lockout", "textures/gui/recipe_book.png");
            case GoalType.FOOD -> Identifier.fromNamespaceAndPath("lockout", "textures/gui/food.png");
            case GoalType.ARMOR -> Identifier.fromNamespaceAndPath("lockout", "textures/gui/armor.png");
            case GoalType.BREED -> Identifier.fromNamespaceAndPath("lockout", "textures/gui/heart.png");
        };

        // Scale and render the 16x16 texture as 8x8 in the corner
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + slotSize - 8, y + slotSize - 8);
        graphics.pose().scale(0.5F, 0.5F);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                overlay,
                0,
                0,
                0,
                0,
                16,
                16,
                16,
                16
        );
        graphics.pose().popMatrix();
    }

    private static boolean isMouseOver(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w &&
                mouseY >= y && mouseY < y + h;
    }

    /**
     * Contains information about what the mouse is hovering over
     */
    public static class HoverInfo {
        public final LockoutClient.PlayerData player;
        public final int slotIndex;
        public final int mouseX;
        public final int mouseY;

        public HoverInfo(LockoutClient.PlayerData player, int slotIndex, int mouseX, int mouseY) {
            this.player = player;
            this.slotIndex = slotIndex;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }
    }
}
