package one.fayaz;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeathLockoutClient implements ClientModInitializer {

    // Client-side state
    private static int clientGoal = 0;
    private static List<String> p1Deaths = new ArrayList<>();
    private static List<String> p2Deaths = new ArrayList<>();
    private static UUID clientP1 = null;
    private static UUID clientP2 = null;

    @Override
    public void onInitializeClient() {
        // 1. Handle Networking Packet
        ClientPlayNetworking.registerGlobalReceiver(LockoutNetworking.SYNC_TYPE, (payload, context) -> {
            context.client().execute(() -> {
                clientGoal = payload.goal();
                p1Deaths = payload.p1Deaths();
                p2Deaths = payload.p2Deaths();
                clientP1 = payload.p1();
                clientP2 = payload.p2();
            });
        });

        // 2. Render HUD
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (clientGoal > 0) renderLockoutHud(drawContext);
        });
    }

    private void renderLockoutHud(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.font == null) return;

        int width = client.getWindow().getGuiScaledWidth();
        int centerX = width / 2;
        int topY = 15;

        // --- Draw Goal Text ---
        String goalText = String.valueOf(clientGoal);
        int textWidth = client.font.width(goalText);
        graphics.drawString(client.font, goalText, centerX - (textWidth / 2), topY + 5, 0xFFFFFF, true);

        // --- Config ---
        int slotSize = 18; // Standard MC Slot size
        int gap = 20;
        int centerMargin = 25;
        int boxesToDraw = clientGoal - 1;

        // --- PLAYER 1 (Left) ---
        for (int i = 0; i < boxesToDraw; i++) {
            int offsetSteps = (boxesToDraw - 1) - i;
            int x = centerX - centerMargin - slotSize - (offsetSteps * gap);
            int y = topY;

            if (i < p1Deaths.size()) {
                // CLAIMED: Draw Red Background + Icon
                // 0x80FF0000 = Semi-transparent Red
                graphics.fill(x, y, x + slotSize, y + slotSize, 0x80FF0000);
                graphics.renderOutline(x, y, slotSize, slotSize, 0xFFFF0000); // Solid Red Border

                // Draw Icon
                String deathCause = p1Deaths.get(i);
                ItemStack icon = getIconForDeath(deathCause, clientP1);
                graphics.renderItem(icon, x + 1, y + 1); // +1 to center 16px item in 18px slot
            } else {
                // EMPTY: Draw Gray Background
                // 0x55000000 = Semi-transparent Black/Gray
                graphics.fill(x, y, x + slotSize, y + slotSize, 0x55000000);
            }
        }

        // --- PLAYER 2 (Right) ---
        for (int i = 0; i < boxesToDraw; i++) {
            int offsetSteps = (boxesToDraw - 1) - i;
            int x = centerX + centerMargin + (offsetSteps * gap);
            int y = topY;

            if (i < p2Deaths.size()) {
                // CLAIMED: Draw Orange Background + Icon
                // 0x80FFAA00 = Semi-transparent Orange
                graphics.fill(x, y, x + slotSize, y + slotSize, 0x80FFAA00);
                graphics.renderOutline(x, y, slotSize, slotSize, 0xFFFFAA00); // Solid Orange Border

                // Draw Icon
                String deathCause = p2Deaths.get(i);
                ItemStack icon = getIconForDeath(deathCause, clientP2);
                graphics.renderItem(icon, x + 1, y + 1);
            } else {
                // EMPTY: Draw Gray Background
                graphics.fill(x, y, x + slotSize, y + slotSize, 0x55000000);
            }
        }
    }

    // --- LOGIC: Convert Death String to Item ---
    private ItemStack getIconForDeath(String cause, UUID playerUuid) {
        String lower = cause.toLowerCase();

        // 1. Environmental
        if (lower.contains("lava")) return new ItemStack(Items.LAVA_BUCKET);
        if (lower.contains("water") || lower.contains("drown")) return new ItemStack(Items.WATER_BUCKET);
        if (lower.contains("fire") || lower.contains("flame") || lower.contains("burnt") || lower.contains("burned")) return new ItemStack(Items.FLINT_AND_STEEL);
        if (lower.contains("fall") || lower.contains("ground") || lower.contains("fell")) return new ItemStack(Items.FEATHER);
        if (lower.contains("cactus") || lower.contains("prick")) return new ItemStack(Items.CACTUS);
        if (lower.contains("berry") || lower.contains("bush")) return new ItemStack(Items.SWEET_BERRIES);
        if (lower.contains("starve")) return new ItemStack(Items.ROTTEN_FLESH);
        if (lower.contains("explosion") || lower.contains("blew up") || lower.contains("tnt")) return new ItemStack(Items.TNT);
        if (lower.contains("magic") || lower.contains("potion")) return new ItemStack(Items.POTION);
        if (lower.contains("withered")) return new ItemStack(Items.WITHER_ROSE);
        if (lower.contains("anvil") || lower.contains("squashed")) return new ItemStack(Items.ANVIL);
        if (lower.contains("arrow") || lower.contains("shot")) return new ItemStack(Items.ARROW);
        if (lower.contains("trident")) return new ItemStack(Items.TRIDENT);
        if (lower.contains("stalagmite") || lower.contains("impaled")) return new ItemStack(Items.POINTED_DRIPSTONE);
        if (lower.contains("freeze") || lower.contains("frozen")) return new ItemStack(Items.POWDER_SNOW_BUCKET);
        if (lower.contains("shriek")) return new ItemStack(Items.WARDEN_SPAWN_EGG);

        // 2. Mobs
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            String entityName = type.getDescription().getString().toLowerCase();
            if (lower.contains(entityName)) {
                SpawnEggItem egg = SpawnEggItem.byId(type);
                if (egg != null) return new ItemStack(egg);
            }
        }

        // 3. Fallback
//        return getPlayerHead(playerUuid);
        return new ItemStack(Items.PLAYER_HEAD);
    }

//    private ItemStack getPlayerHead(UUID uuid) {
//        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
//        if (uuid == null) return stack;
//
//        // 1. Try to get cached profile (contains Skin texture)
//        GameProfile profile = null;
//        Minecraft client = Minecraft.getInstance();
//        if (client.getConnection() != null) {
//            PlayerInfo info = client.getConnection().getPlayerInfo(uuid);
//            if (info != null) {
//                profile = info.getProfile();
//            }
//        }
//
//        // 2. If not found in cache, create a raw profile with just the UUID
//        // Note: We use "unknown" for the name if null, to satisfy the GameProfile constructor
//        if (profile == null) {
//            profile = new GameProfile(uuid, "unknown");
//        }
//
//        // 3. Set the Data Component
//        // In 1.21+, ResolvableProfile is a Record.
//        // Ensure there are NO curly braces { } after this line.
//        stack.set(DataComponents.PROFILE, new ResolvableProfile(profile));
//
//        return stack;
//    }

}