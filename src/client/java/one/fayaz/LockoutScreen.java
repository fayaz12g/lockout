package one.fayaz;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

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
        int startX = 40;
        int startY = 50;
        int slot = 18;
        int gap = 2;

        for (LockoutClient.PlayerData player : players) {

            // Player name with their color
            graphics.drawString(
                    font,
                    player.name + " (" + player.icons.size() + "/" + goal + ")",
                    startX,
                    startY - 12,
                    player.color
            );

            int x = startX;
            int bg = (player.color & 0xFFFFFF) | 0x88000000;

            // Render all slots (including empty ones)
            for (int i = 0; i < goal; i++) {
                boolean isClaimed = i < player.icons.size();

                // Slot background
                graphics.fill(x, startY, x + slot, startY + slot, 0xFF_000000);

                // Border - brighter if claimed
                int borderBright = isClaimed ? 0xFF_FFFFFF : 0xFF_8B8B8B;
                int borderDark = isClaimed ? 0xFF_555555 : 0xFF_373737;

                graphics.fill(x, startY, x + slot, startY + 1, borderBright);
                graphics.fill(x, startY, x + 1, startY + slot, borderBright);
                graphics.fill(x + slot - 1, startY, x + slot, startY + slot, borderDark);
                graphics.fill(x, startY + slot - 1, x + slot, startY + slot, borderDark);

                // Tinted background only if claimed
                if (isClaimed) {
                    graphics.fill(x + 1, startY + 1, x + slot - 1, startY + slot - 1, bg);
                }

                // Item if claimed
                if (isClaimed) {
                    ItemStack stack = player.icons.get(i);
                    graphics.renderItem(stack, x + 1, startY + 1);

                    // Check for hover
                    if (isMouseOver(mouseX, mouseY, x, startY, slot, slot)) {
                        renderTooltip(graphics, player, i, mouseX, mouseY);
                    }
                }

                x += slot + gap;
            }

            startY += slot + 24;
        }
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
        String claimText = player.claims.get(index);

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