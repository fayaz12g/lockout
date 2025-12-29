package one.fayaz;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.List;

public class DeathLockoutClient implements ClientModInitializer {

    // Client-side state
    private static int clientGoal = 0;
    private static List<PlayerData> clientPlayers = new ArrayList<>();
    private static String clientMode = "DEATH";

    public static class PlayerData {
        public String name;
        public int color;
        public List<String> claims;

        public PlayerData(String name, int color, List<String> claims) {
            this.name = name;
            this.color = color;
            this.claims = new ArrayList<>(claims);
        }
    }

    @Override
    public void onInitializeClient() {
        // 1. Handle Networking Packet
        ClientPlayNetworking.registerGlobalReceiver(LockoutNetworking.SYNC_TYPE, (payload, context) -> {
            context.client().execute(() -> {
                clientGoal = payload.goal();
                clientMode = payload.mode();
                clientPlayers.clear();

                for (LockoutNetworking.PlayerData pd : payload.players()) {
                    clientPlayers.add(new PlayerData(pd.name(), pd.color(), pd.claims()));
                }
            });
        });

        // 2. Render HUD
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (clientGoal > 0 && !clientPlayers.isEmpty()) {
                renderLockoutHud(drawContext);
            }
        });
    }

    private void renderLockoutHud(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.font == null) return;

        int width = client.getWindow().getGuiScaledWidth();
        int centerX = width / 2;
        int topY = 15;

        // --- Config ---
        int slotSize = 18;
        int playerGap = 25; // Gap between players
        int boxGap = 2; // Gap between boxes within a player
        int boxesToDraw = clientGoal - 1;

        // Calculate total width needed per player
        int playerWidth = (boxesToDraw * slotSize) + ((boxesToDraw - 1) * boxGap);
        int totalWidth = (clientPlayers.size() * playerWidth) + ((clientPlayers.size() - 1) * playerGap);

        // Starting X position to center everything
        int startX = centerX - (totalWidth / 2);

        // Draw goal text at the top with mode
        String modeText = clientMode.equals("DEATH") ? "Deaths" : "Kills";
        String goalText = modeText + " Goal: " + clientGoal;
        int textWidth = client.font.width(goalText);
        graphics.drawString(client.font, goalText, centerX - (textWidth / 2), topY - 12, 0xFFFFFF, true);

        // Draw each player's progress
        int currentX = startX;
        for (PlayerData player : clientPlayers) {
            // Draw player name above their boxes
            String nameText = player.name;
            int nameWidth = client.font.width(nameText);
            int nameCenterX = currentX + (playerWidth / 2) - (nameWidth / 2);
            graphics.drawString(client.font, nameText, nameCenterX, topY + slotSize + 3, player.color, true);

            // Draw boxes for this player
            for (int i = 0; i < boxesToDraw; i++) {
                int x = currentX + (i * (slotSize + boxGap));
                int y = topY;

                if (i < player.claims.size()) {
                    // CLAIMED: Draw colored background + icon
                    int bgColor = (player.color & 0xFFFFFF) | 0x80000000; // Semi-transparent version
                    graphics.fill(x, y, x + slotSize, y + slotSize, bgColor);
                    graphics.renderOutline(x, y, slotSize, slotSize, player.color | 0xFF000000); // Solid border

                    // Draw Icon
                    String claim = player.claims.get(i);
                    ItemStack icon = getIconForClaim(claim);
                    graphics.renderItem(icon, x + 1, y + 1);
                } else {
                    // EMPTY: Draw gray background
                    graphics.fill(x, y, x + slotSize, y + slotSize, 0x55000000);
                }
            }

            currentX += playerWidth + playerGap;
        }
    }

    // --- LOGIC: Convert Claim String to Item ---
    private ItemStack getIconForClaim(String claim) {
        String lower = claim.toLowerCase();

        // For kills mode, try to match entity name directly first
        if (clientMode.equals("KILLS")) {
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                String entityName = type.getDescription().getString();
                if (entityName.equalsIgnoreCase(claim)) {
                    SpawnEggItem egg = SpawnEggItem.byId(type);
                    if (egg != null) return new ItemStack(egg);
                }
            }
        }

        // For death mode or fallback, check for keywords in the claim
        // 1. Environmental
        if (lower.contains("discovered")) return new ItemStack(Items.MAGMA_BLOCK);
        else if (lower.contains("lava")) return new ItemStack(Items.LAVA_BUCKET);
        else if (lower.contains("suffocated")) return new ItemStack(Items.SAND);
        else if (lower.contains("water") || lower.contains("drown")) return new ItemStack(Items.WATER_BUCKET);
        else if (lower.contains("fire") || lower.contains("flame") || lower.contains("burnt") || lower.contains("burned")) return new ItemStack(Items.FLINT_AND_STEEL);
        else if (lower.contains("fall") || lower.contains("ground") || lower.contains("fell")) return new ItemStack(Items.FEATHER);
        else if (lower.contains("cactus") || lower.contains("prick")) return new ItemStack(Items.CACTUS);
        else if (lower.contains("berry") || lower.contains("bush")) return new ItemStack(Items.SWEET_BERRIES);
        else if (lower.contains("starve")) return new ItemStack(Items.ROTTEN_FLESH);
        else if (lower.contains("explosion") || lower.contains("blew up") || lower.contains("tnt")) return new ItemStack(Items.TNT);
        else if (lower.contains("magic") || lower.contains("potion")) return new ItemStack(Items.POTION);
        else if (lower.contains("withered")) return new ItemStack(Items.WITHER_ROSE);
        else if (lower.contains("anvil") || lower.contains("squashed")) return new ItemStack(Items.ANVIL);
        else if (lower.contains("arrow") || lower.contains("shot")) return new ItemStack(Items.ARROW);
        else if (lower.contains("trident")) return new ItemStack(Items.TRIDENT);
        else if (lower.contains("stalagmite") || lower.contains("impaled")) return new ItemStack(Items.POINTED_DRIPSTONE);
        else if (lower.contains("freeze") || lower.contains("frozen")) return new ItemStack(Items.POWDER_SNOW_BUCKET);
        else if (lower.contains("shriek")) return new ItemStack(Items.WARDEN_SPAWN_EGG);

        else {
            // 2. Mobs (for death messages containing mob names)
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                String entityName = type.getDescription().getString().toLowerCase();
                if (lower.contains(entityName)) {
                    SpawnEggItem egg = SpawnEggItem.byId(type);
                    if (egg != null) return new ItemStack(egg);
                }
            }

            // 3. Fallback
            return new ItemStack(clientMode.equals("KILLS") ? Items.IRON_SWORD : Items.PLAYER_HEAD);
        }
    }
}