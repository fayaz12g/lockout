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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public class LockoutClient implements ClientModInitializer {

    // Client-side state
    private static int clientGoal = 0;
    private static List<PlayerData> clientPlayers = new ArrayList<>();
    private static String clientMode = "DEATH";
    private static boolean clientPaused = false;
    private static String clientPausedPlayerName = "";

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
                clientPaused = payload.paused();
                clientPausedPlayerName = payload.pausedPlayerName();
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
        int height = client.getWindow().getGuiScaledHeight();
        int centerX = width / 2;
        int topY = 15;

        // --- Config ---
        int slotSize = 18;
        int playerGap = 25;
        int boxGap = 2;
        int boxesToDraw = clientGoal - 1;

        // Calculate total width needed per player
        int playerWidth = (boxesToDraw * slotSize) + ((boxesToDraw - 1) * boxGap);
        int totalWidth = (clientPlayers.size() * playerWidth) + ((clientPlayers.size() - 1) * playerGap);

        // Starting X position to center everything
        int startX = centerX - (totalWidth / 2);

        // Draw goal text at the top with mode
        String modeText = switch (clientMode) {
            case "DEATH" -> "Deaths";
            case "KILLS" -> "Kills";
            case "ARMOR" -> "Armor Sets";
            case "ADVANCEMENTS" -> "Advancements";
            case "FOODS" -> "Foods";
            default -> clientMode;
        };
        String goalText = modeText + " Goal: " + clientGoal;
        int textWidth = client.font.width(goalText);
        graphics.drawString(client.font, goalText, centerX - (textWidth / 2), topY - 12, 0xFFFFFF, true);

        // Draw pause message if paused
        if (clientPaused) {
            String pauseText = "⏸ PAUSED - Waiting for " + clientPausedPlayerName + " to reconnect";
            int pauseWidth = client.font.width(pauseText);
            int pauseY = height / 2 - 20;

            // Draw background
            graphics.fill(centerX - pauseWidth / 2 - 5, pauseY - 3, centerX + pauseWidth / 2 + 5, pauseY + 12, 0xAA000000);

            // Draw text
            graphics.drawString(client.font, pauseText, centerX - pauseWidth / 2, pauseY, 0xFFAA00, true);
        }

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
                    int bgColor = (player.color & 0xFFFFFF) | 0x80000000;
                    graphics.fill(x, y, x + slotSize, y + slotSize, bgColor);
                    graphics.renderOutline(x, y, slotSize, slotSize, player.color | 0xFF000000);

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

    private ItemStack getIconForClaim(String claim) {
        String lower = claim.toLowerCase();

        // Armor mode - show chestplate
        if (clientMode.equals("ARMOR")) {
            if (lower.contains("leather")) return new ItemStack(Items.LEATHER_CHESTPLATE);
            if (lower.contains("chainmail")) return new ItemStack(Items.CHAINMAIL_CHESTPLATE);
            if (lower.contains("iron")) return new ItemStack(Items.IRON_CHESTPLATE);
            if (lower.contains("gold")) return new ItemStack(Items.GOLDEN_CHESTPLATE);
            if (lower.contains("diamond")) return new ItemStack(Items.DIAMOND_CHESTPLATE);
            if (lower.contains("netherite")) return new ItemStack(Items.NETHERITE_CHESTPLATE);
            return new ItemStack(Items.IRON_CHESTPLATE); // Fallback
        }

        // Advancements mode - show relevant icon
        if (clientMode.equals("ADVANCEMENTS")) {
            // Nether advancements
            if (lower.contains("nether/root")) return new ItemStack(Blocks.RED_NETHER_BRICKS);
            if (lower.contains("nether/return_to_sender")) return new ItemStack(Items.FIRE_CHARGE);
            if (lower.contains("nether/find_fortress")) return new ItemStack(Blocks.NETHER_BRICKS);
            if (lower.contains("nether/fast_travel")) return new ItemStack(Items.MAP);
            if (lower.contains("nether/uneasy_alliance")) return new ItemStack(Items.GHAST_TEAR);
            if (lower.contains("nether/get_wither_skull")) return new ItemStack(Blocks.WITHER_SKELETON_SKULL);
            if (lower.contains("nether/summon_wither")) return new ItemStack(Items.NETHER_STAR);
            if (lower.contains("nether/obtain_blaze_rod")) return new ItemStack(Items.BLAZE_ROD);
            if (lower.contains("nether/create_beacon")) return new ItemStack(Blocks.BEACON);
            if (lower.contains("nether/create_full_beacon")) return new ItemStack(Blocks.BEACON);
            if (lower.contains("nether/brew_potion")) return new ItemStack(Items.POTION);
            if (lower.contains("nether/all_potions")) return new ItemStack(Items.MILK_BUCKET);
            if (lower.contains("nether/all_effects")) return new ItemStack(Items.BUCKET);
            if (lower.contains("nether/obtain_ancient_debris")) return new ItemStack(Items.ANCIENT_DEBRIS);
            if (lower.contains("nether/netherite_armor")) return new ItemStack(Items.NETHERITE_CHESTPLATE);
            if (lower.contains("nether/obtain_crying_obsidian")) return new ItemStack(Items.CRYING_OBSIDIAN);
            if (lower.contains("nether/charge_respawn_anchor")) return new ItemStack(Items.RESPAWN_ANCHOR);
            if (lower.contains("nether/ride_strider")) return new ItemStack(Items.WARPED_FUNGUS_ON_A_STICK);
            if (lower.contains("nether/ride_strider_in_overworld_lava")) return new ItemStack(Items.WARPED_FUNGUS_ON_A_STICK);
            if (lower.contains("nether/explore_nether")) return new ItemStack(Items.NETHERITE_BOOTS);
            if (lower.contains("nether/find_bastion")) return new ItemStack(Items.POLISHED_BLACKSTONE_BRICKS);
            if (lower.contains("nether/loot_bastion")) return new ItemStack(Blocks.CHEST);
            if (lower.contains("nether/distract_piglin")) return new ItemStack(Items.GOLD_INGOT);

            // Adventure advancements
            if (lower.contains("adventure/root")) return new ItemStack(Items.MAP);
            if (lower.contains("adventure/sleep_in_bed")) return new ItemStack(Blocks.RED_BED);
            if (lower.contains("adventure/trade")) return new ItemStack(Items.EMERALD);
            if (lower.contains("adventure/trade_at_world_height")) return new ItemStack(Items.EMERALD);
            if (lower.contains("adventure/shoot_arrow")) return new ItemStack(Items.BOW);
            if (lower.contains("adventure/throw_trident")) return new ItemStack(Items.TRIDENT);
            if (lower.contains("adventure/very_very_frightening")) return new ItemStack(Items.TRIDENT);
            if (lower.contains("adventure/summon_iron_golem")) return new ItemStack(Blocks.CARVED_PUMPKIN);
            if (lower.contains("adventure/sniper_duel")) return new ItemStack(Items.ARROW);
            if (lower.contains("adventure/totem_of_undying")) return new ItemStack(Items.TOTEM_OF_UNDYING);
            if (lower.contains("adventure/spear_many_mobs")) return new ItemStack(Items.IRON_SPEAR);
            if (lower.contains("adventure/ol_betsy")) return new ItemStack(Items.CROSSBOW);
            if (lower.contains("adventure/whos_the_pillager_now")) return new ItemStack(Items.CROSSBOW);
            if (lower.contains("adventure/two_birds_one_arrow")) return new ItemStack(Items.CROSSBOW);
            if (lower.contains("adventure/arbalistic")) return new ItemStack(Items.CROSSBOW);
            if (lower.contains("adventure/honey_block_slide")) return new ItemStack(Blocks.HONEY_BLOCK);
            if (lower.contains("adventure/bullseye")) return new ItemStack(Blocks.TARGET);
            if (lower.contains("adventure/walk_on_powder_snow_with_leather_boots")) return new ItemStack(Items.LEATHER_BOOTS);
            if (lower.contains("adventure/lightning_rod_with_villager_no_fire")) return new ItemStack(Items.LIGHTNING_ROD);
            if (lower.contains("adventure/spyglass_at_parrot")) return new ItemStack(Items.SPYGLASS);
            if (lower.contains("adventure/spyglass_at_ghast")) return new ItemStack(Items.SPYGLASS);
            if (lower.contains("adventure/play_jukebox_in_meadows")) return new ItemStack(Items.JUKEBOX);
            if (lower.contains("adventure/spyglass_at_dragon")) return new ItemStack(Items.SPYGLASS);
            if (lower.contains("adventure/fall_from_world_height")) return new ItemStack(Items.WATER_BUCKET);
            if (lower.contains("adventure/kill_mob_near_sculk_catalyst")) return new ItemStack(Blocks.SCULK_CATALYST);
            if (lower.contains("adventure/avoid_vibration")) return new ItemStack(Blocks.SCULK_SENSOR);
            if (lower.contains("adventure/salvage_sherd")) return new ItemStack(Items.BRUSH);
            if (lower.contains("adventure/read_power_of_chiseled_bookshelf")) return new ItemStack(Items.CHISELED_BOOKSHELF);
            if (lower.contains("adventure/brush_armadillo")) return new ItemStack(Items.ARMADILLO_SCUTE);
            if (lower.contains("adventure/minecraft_trials_edition")) return new ItemStack(Blocks.CHISELED_TUFF);
            if (lower.contains("adventure/lighten_up")) return new ItemStack(Items.COPPER_BULB);
            if (lower.contains("adventure/under_lock_and_key")) return new ItemStack(Items.TRIAL_KEY);
            if (lower.contains("adventure/revaulting")) return new ItemStack(Items.OMINOUS_TRIAL_KEY);
            if (lower.contains("adventure/blowback")) return new ItemStack(Items.WIND_CHARGE);
            if (lower.contains("adventure/crafters_crafting_crafters")) return new ItemStack(Items.CRAFTER);
            if (lower.contains("adventure/use_lodestone")) return new ItemStack(Items.LODESTONE);
            if (lower.contains("adventure/who_needs_rockets")) return new ItemStack(Items.WIND_CHARGE);
            if (lower.contains("adventure/overoverkill")) return new ItemStack(Items.MACE);
            if (lower.contains("adventure/heart_transplanter")) return new ItemStack(Blocks.CREAKING_HEART);
            if (lower.contains("adventure/adventuring_time")) return new ItemStack(Items.DIAMOND_BOOTS);
            if (lower.contains("adventure/kill_all_mobs")) return new ItemStack(Items.DIAMOND_SWORD);
            if (lower.contains("adventure/kill_a_mob")) return new ItemStack(Items.IRON_SWORD);

            // Husbandry advancements
            if (lower.contains("husbandry/root")) return new ItemStack(Blocks.HAY_BLOCK);
            if (lower.contains("husbandry/plant_seed")) return new ItemStack(Items.WHEAT);
            if (lower.contains("husbandry/breed_an_animal")) return new ItemStack(Items.WHEAT);
            if (lower.contains("husbandry/balanced_diet")) return new ItemStack(Items.APPLE);
            if (lower.contains("husbandry/obtain_netherite_hoe")) return new ItemStack(Items.NETHERITE_HOE);
            if (lower.contains("husbandry/tame_an_animal")) return new ItemStack(Items.LEAD);
            if (lower.contains("husbandry/fishy_business")) return new ItemStack(Items.FISHING_ROD);
            if (lower.contains("husbandry/tactical_fishing")) return new ItemStack(Items.PUFFERFISH_BUCKET);
            if (lower.contains("husbandry/axolotl_in_a_bucket")) return new ItemStack(Items.AXOLOTL_BUCKET);
            if (lower.contains("husbandry/kill_axolotl_target")) return new ItemStack(Items.TROPICAL_FISH_BUCKET);
            if (lower.contains("husbandry/complete_catalogue")) return new ItemStack(Items.COD);
            if (lower.contains("husbandry/whole_pack")) return new ItemStack(Items.BONE);
            if (lower.contains("husbandry/safely_harvest_honey")) return new ItemStack(Items.HONEY_BOTTLE);
            if (lower.contains("husbandry/wax_on")) return new ItemStack(Items.HONEYCOMB);
            if (lower.contains("husbandry/wax_off")) return new ItemStack(Items.STONE_AXE);
            if (lower.contains("husbandry/tadpole_in_a_bucket")) return new ItemStack(Items.TADPOLE_BUCKET);
            if (lower.contains("husbandry/leash_all_frog_variants")) return new ItemStack(Items.LEAD);
            if (lower.contains("husbandry/froglights")) return new ItemStack(Items.VERDANT_FROGLIGHT);
            if (lower.contains("husbandry/silk_touch_nest")) return new ItemStack(Blocks.BEE_NEST);
            if (lower.contains("husbandry/ride_a_boat_with_a_goat")) return new ItemStack(Items.OAK_BOAT);
            if (lower.contains("husbandry/make_a_sign_glow")) return new ItemStack(Items.GLOW_INK_SAC);
            if (lower.contains("husbandry/allay_deliver_item_to_player")) return new ItemStack(Items.COOKIE);
            if (lower.contains("husbandry/allay_deliver_cake_to_note_block")) return new ItemStack(Items.NOTE_BLOCK);
            if (lower.contains("husbandry/obtain_sniffer_egg")) return new ItemStack(Items.SNIFFER_EGG);
            if (lower.contains("husbandry/feed_snifflet")) return new ItemStack(Items.TORCHFLOWER_SEEDS);
            if (lower.contains("husbandry/plant_any_sniffer_seed")) return new ItemStack(Items.PITCHER_POD);
            if (lower.contains("husbandry/remove_wolf_armor")) return new ItemStack(Items.SHEARS);
            if (lower.contains("husbandry/repair_wolf_armor")) return new ItemStack(Items.WOLF_ARMOR);
            if (lower.contains("husbandry/place_dried_ghast_in_water")) return new ItemStack(Items.DRIED_GHAST);
            if (lower.contains("husbandry/bred_all_animals")) return new ItemStack(Items.GOLDEN_CARROT);

            // Story advancements
            if (lower.contains("story/root")) return new ItemStack(Blocks.GRASS_BLOCK);
            if (lower.contains("story/mine_stone")) return new ItemStack(Items.WOODEN_PICKAXE);
            if (lower.contains("story/upgrade_tools")) return new ItemStack(Items.STONE_PICKAXE);
            if (lower.contains("story/smelt_iron")) return new ItemStack(Items.IRON_INGOT);
            if (lower.contains("story/iron_tools")) return new ItemStack(Items.IRON_PICKAXE);
            if (lower.contains("story/mine_diamond")) return new ItemStack(Items.DIAMOND);
            if (lower.contains("story/lava_bucket")) return new ItemStack(Items.LAVA_BUCKET);
            if (lower.contains("story/obtain_armor")) return new ItemStack(Items.IRON_CHESTPLATE);
            if (lower.contains("story/enchant_item")) return new ItemStack(Items.ENCHANTED_BOOK);
            if (lower.contains("story/form_obsidian")) return new ItemStack(Blocks.OBSIDIAN);
            if (lower.contains("story/deflect_arrow")) return new ItemStack(Items.SHIELD);
            if (lower.contains("story/shiny_gear")) return new ItemStack(Items.DIAMOND_CHESTPLATE);
            if (lower.contains("story/enter_the_nether")) return new ItemStack(Items.FLINT_AND_STEEL);
            if (lower.contains("story/cure_zombie_villager")) return new ItemStack(Items.GOLDEN_APPLE);
            if (lower.contains("story/follow_ender_eye")) return new ItemStack(Items.ENDER_EYE);
            if (lower.contains("story/enter_the_end")) return new ItemStack(Blocks.END_STONE);

            // End advancements
            if (lower.contains("end/root")) return new ItemStack(Blocks.END_STONE);
            if (lower.contains("end/kill_dragon")) return new ItemStack(Blocks.DRAGON_HEAD);
            if (lower.contains("end/enter_end_gateway")) return new ItemStack(Items.ENDER_PEARL);
            if (lower.contains("end/respawn_dragon")) return new ItemStack(Items.END_CRYSTAL);
            if (lower.contains("end/find_end_city")) return new ItemStack(Blocks.PURPUR_BLOCK);
            if (lower.contains("end/dragon_breath")) return new ItemStack(Items.DRAGON_BREATH);
            if (lower.contains("end/levitate")) return new ItemStack(Items.SHULKER_SHELL);
            if (lower.contains("end/elytra")) return new ItemStack(Items.ELYTRA);
            if (lower.contains("end/dragon_egg")) return new ItemStack(Blocks.DRAGON_EGG);

            return new ItemStack(Items.KNOWLEDGE_BOOK); // Default for advancements
        }

        // Foods mode - try to parse the item directly
        if (clientMode.equals("FOODS")) {
            // Try to find the item from registry
            var item = BuiltInRegistries.ITEM.stream()
                    .filter(i -> i.toString().equals(claim))
                    .findFirst()
                    .orElse(null);
            if (item != null) return new ItemStack(item);
            return new ItemStack(Items.APPLE); // Fallback
        }

        // Kills mode
        if (clientMode.equals("KILLS")) {
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                String entityName = type.getDescription().getString();
                if (entityName.equalsIgnoreCase(claim)) {
                    SpawnEggItem egg = SpawnEggItem.byId(type);
                    if (egg != null) return new ItemStack(egg);
                }
            }
        }

        // Death mode - check for keywords
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
            // Check for mob names
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                String entityName = type.getDescription().getString().toLowerCase();
                if (lower.contains(entityName)) {
                    SpawnEggItem egg = SpawnEggItem.byId(type);
                    if (egg != null) return new ItemStack(egg);
                }
            }

            // Fallback based on mode
            return switch (clientMode) {
                case "KILLS" -> new ItemStack(Items.IRON_SWORD);
                case "ARMOR" -> new ItemStack(Items.IRON_CHESTPLATE);
                case "ADVANCEMENTS" -> new ItemStack(Items.KNOWLEDGE_BOOK);
                case "FOODS" -> new ItemStack(Items.APPLE);
                default -> new ItemStack(Items.PLAYER_HEAD);
            };
        }
    }
}