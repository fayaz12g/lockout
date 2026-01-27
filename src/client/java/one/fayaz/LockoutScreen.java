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

        // Render the lockout display and get hover info
        LockoutHud.HoverInfo hoverInfo = LockoutHud.renderLockout(
                graphics,
                players,
                mode,
                goal,
                width,
                50, // centerY for screen
                font,
                mouseX,
                mouseY
        );

        // Render tooltip if hovering over something
        if (hoverInfo != null) {
            renderTooltip(graphics, hoverInfo);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Don't pause the game
    }

    // ================= TOOLTIP =================

    private void renderTooltip(GuiGraphics graphics, LockoutHud.HoverInfo hoverInfo) {
        LockoutClient.PlayerData player = hoverInfo.player;
        int index = hoverInfo.slotIndex;

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

        graphics.setComponentTooltipForNextFrame(font, tooltip, hoverInfo.mouseX, hoverInfo.mouseY);
    }
}