package one.fayaz;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

import static one.fayaz.GoalType.*;

public class LockoutScreen extends Screen {

    private final List<LockoutClient.PlayerData> players;
    private final String mode;
    private final int goal;

    public LockoutScreen(List<LockoutClient.PlayerData> players, String mode, int goal) {
        super(Component.literal("Lockout Progress"));
        this.players = players;
        this.mode = mode;
        this.goal = goal;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderTransparentBackground(graphics);

        // Title
        graphics.drawCenteredString(
                font,
                "Lockout – " + mode + " (First to " + goal + ")",
                width / 2,
                15,
                0xFFFFFF
        );

        renderPlayers(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Don't pause the game
    }

    // ================= RENDER =================

    private void renderPlayers(GuiGraphics graphics, int mouseX, int mouseY) {
        int slotSize = 18;
        int gap = 2;
        int centerY = 50;

        // Calculate slots per player (goal - 1)
        int slotsPerPlayer = Math.max(1, goal - 1);

        // Victory box in the center
        int victoryBoxSize = 28;
        int victoryBoxX = (width - victoryBoxSize) / 2;
        int victoryBoxY = centerY;

        // Render victory box with lighter background
        renderSlotBackground(graphics, victoryBoxX, victoryBoxY, victoryBoxSize, 0x88000000, false);

        // Calculate player row width
        int playerRowWidth = slotsPerPlayer * (slotSize + gap) - gap;
        int gapBetweenRowAndGoal = gap * 2;

        // Render each player's row
        for (int playerIndex = 0; playerIndex < players.size(); playerIndex++) {
            LockoutClient.PlayerData player = players.get(playerIndex);

            // Determine which side this player is on (even = left, odd = right)
            boolean isLeftSide = (playerIndex % 2 == 0);

            // Calculate vertical position (players stack vertically within their side)
            int sideIndex = playerIndex / 2; // 0,1 -> 0; 2,3 -> 1; etc.
            int y = centerY + sideIndex * (slotSize + 24);

            // Player name position
            int nameY = y - 12;
            int nameX;

            if (isLeftSide) {
                // Left side: name aligned to left of row
                nameX = victoryBoxX - gapBetweenRowAndGoal - playerRowWidth;
            } else {
                // Right side: name aligned to right of row
                nameX = victoryBoxX + victoryBoxSize + gapBetweenRowAndGoal + playerRowWidth - font.width(player.name + " (" + player.icons.size() + "/" + goal + ")");
            }

            // Draw player name
            graphics.drawString(
                    font,
                    player.name + " (" + player.icons.size() + "/" + goal + ")",
                    nameX,
                    nameY,
                    player.color
            );

            // Render each slot for this player
            for (int i = 0; i < slotsPerPlayer; i++) {
                int tint = (player.color & 0xFFFFFF) | 0x88000000;

                int x;
                int slotIndex;

                if (isLeftSide) {
                    // Left side: progress left to right
                    x = victoryBoxX - gapBetweenRowAndGoal - playerRowWidth + i * (slotSize + gap);
                    slotIndex = i;
                } else {
                    // Right side: progress right to left
                    x = victoryBoxX + victoryBoxSize + gapBetweenRowAndGoal + (slotsPerPlayer - 1 - i) * (slotSize + gap);
                    slotIndex = i;
                }

                // Check if this specific slot is claimed
                boolean thisSlotClaimed = slotIndex < player.claims.size();

                // Slot background with lighter color
                renderSlotBackground(graphics, x, y, slotSize, tint, thisSlotClaimed);

                // Item if claimed
                if (slotIndex < player.icons.size()) {
                    ItemStack stack = player.icons.get(slotIndex);
                    graphics.renderItem(stack, x + 1, y + 1);

                    // Render overlay for mixed mode
                    if (mode.equals("MIXED") && slotIndex < player.claims.size()) {
                        LockoutNetworking.ClaimData claim = player.claims.get(slotIndex);
                        renderMixedModeOverlay(graphics, claim, x, y, slotSize);
                    }

                    // Check for hover
                    if (isMouseOver(mouseX, mouseY, x, y, slotSize, slotSize)) {
                        renderTooltip(graphics, player, slotIndex, mouseX, mouseY);
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
                        victoryBoxX + victoryBoxSize - 1,
                        victoryBoxY + victoryBoxSize - 1,
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
                    renderMixedModeOverlay(graphics, claim, victoryBoxX, victoryBoxY, victoryBoxSize);
                }

                // Check for hover on victory box
                if (isMouseOver(mouseX, mouseY, victoryBoxX, victoryBoxY, victoryBoxSize, victoryBoxSize)) {
                    renderTooltip(graphics, player, player.icons.size() - 1, mouseX, mouseY);
                }

                break;
            }
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

    private void renderTooltip(
            GuiGraphics graphics,
            LockoutClient.PlayerData player,
            int index,
            int mouseX,
            int mouseY
    ) {
        if (index >= player.claims.size()) return;

        ItemStack stack = player.icons.get(index);
        LockoutNetworking.ClaimData claim = player.claims.get(index);
        String claimText = claim.id();


        // Get the item's tooltip lines and add our custom claim text
        List<Component> tooltip = stack.getTooltipLines(
                Item.TooltipContext.EMPTY,
                this.minecraft.player,
                this.minecraft.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL
        );

        // Add the claim text at the bottom
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal(player.name).withStyle(ChatFormatting.BOLD));
        tooltip.add(Component.literal(claimText).withStyle(ChatFormatting.GRAY));

        graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
    }

    // Fixed parameter order!
    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w &&
                mouseY >= y && mouseY < y + h;
    }
}