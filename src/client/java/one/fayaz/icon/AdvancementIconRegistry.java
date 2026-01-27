package one.fayaz.icon;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

public final class AdvancementIconRegistry {

    // section -> (advancement -> icon)
    private static final Map<String, Map<String, ItemStack>> ICONS = new HashMap<>();

    static {
        /* ---------------- Nether ---------------- */
        section("nether",
                entry("root", Blocks.RED_NETHER_BRICKS),
                entry("return_to_sender", Items.FIRE_CHARGE),
                entry("find_fortress", Blocks.NETHER_BRICKS),
                entry("fast_travel", Items.MAP),
                entry("uneasy_alliance", Items.GHAST_TEAR),
                entry("get_wither_skull", Blocks.WITHER_SKELETON_SKULL),
                entry("summon_wither", Items.NETHER_STAR),
                entry("obtain_blaze_rod", Items.BLAZE_ROD),
                entry("create_beacon", Blocks.BEACON),
                entry("create_full_beacon", Blocks.BEACON),
                entry("brew_potion", Items.POTION),
                entry("all_potions", Items.MILK_BUCKET),
                entry("all_effects", Items.BUCKET),
                entry("obtain_ancient_debris", Items.ANCIENT_DEBRIS),
                entry("netherite_armor", Items.NETHERITE_CHESTPLATE),
                entry("obtain_crying_obsidian", Items.CRYING_OBSIDIAN),
                entry("charge_respawn_anchor", Items.RESPAWN_ANCHOR),
                entry("ride_strider", Items.WARPED_FUNGUS_ON_A_STICK),
                entry("ride_strider_in_overworld_lava", Items.WARPED_FUNGUS_ON_A_STICK),
                entry("explore_nether", Items.NETHERITE_BOOTS),
                entry("find_bastion", Items.POLISHED_BLACKSTONE_BRICKS),
                entry("loot_bastion", Blocks.CHEST),
                entry("distract_piglin", Items.GOLD_INGOT)
        );

        /* ---------------- Adventure ---------------- */
        section("adventure",
                entry("root", Items.MAP),
                entry("sleep_in_bed", Blocks.RED_BED),
                entry("trade", Items.EMERALD),
                entry("trade_at_world_height", Items.EMERALD),
                entry("shoot_arrow", Items.BOW),
                entry("throw_trident", Items.TRIDENT),
                entry("very_very_frightening", Items.TRIDENT),
                entry("summon_iron_golem", Blocks.CARVED_PUMPKIN),
                entry("sniper_duel", Items.ARROW),
                entry("totem_of_undying", Items.TOTEM_OF_UNDYING),
                entry("spear_many_mobs", Items.TRIDENT),
                entry("ol_betsy", Items.CROSSBOW),
                entry("whos_the_pillager_now", Items.CROSSBOW),
                entry("two_birds_one_arrow", Items.CROSSBOW),
                entry("arbalistic", Items.CROSSBOW),
                entry("honey_block_slide", Blocks.HONEY_BLOCK),
                entry("bullseye", Blocks.TARGET),
                entry("walk_on_powder_snow_with_leather_boots", Items.LEATHER_BOOTS),
                entry("lightning_rod_with_villager_no_fire", Items.LIGHTNING_ROD),
                entry("spyglass_at_parrot", Items.SPYGLASS),
                entry("spyglass_at_ghast", Items.SPYGLASS),
                entry("spyglass_at_dragon", Items.SPYGLASS),
                entry("play_jukebox_in_meadows", Items.JUKEBOX),
                entry("fall_from_world_height", Items.WATER_BUCKET),
                entry("kill_mob_near_sculk_catalyst", Blocks.SCULK_CATALYST),
                entry("avoid_vibration", Blocks.SCULK_SENSOR),
                entry("salvage_sherd", Items.BRUSH),
                entry("read_power_of_chiseled_bookshelf", Items.CHISELED_BOOKSHELF),
                entry("brush_armadillo", Items.ARMADILLO_SCUTE),
                entry("minecraft_trials_edition", Blocks.CHISELED_TUFF),
                entry("lighten_up", Items.COPPER_BULB),
                entry("under_lock_and_key", Items.TRIAL_KEY),
                entry("revaulting", Items.OMINOUS_TRIAL_KEY),
                entry("blowback", Items.WIND_CHARGE),
                entry("crafters_crafting_crafters", Items.CRAFTER),
                entry("use_lodestone", Items.LODESTONE),
                entry("who_needs_rockets", Items.WIND_CHARGE),
                entry("overoverkill", Items.MACE),
                entry("heart_transplanter", Blocks.CREAKING_HEART),
                entry("adventuring_time", Items.DIAMOND_BOOTS),
                entry("kill_all_mobs", Items.DIAMOND_SWORD),
                entry("kill_a_mob", Items.IRON_SWORD)
        );

        /* ---------------- Husbandry ---------------- */
        section("husbandry",
                entry("root", Blocks.HAY_BLOCK),
                entry("plant_seed", Items.WHEAT),
                entry("breed_an_animal", Items.WHEAT),
                entry("balanced_diet", Items.APPLE),
                entry("obtain_netherite_hoe", Items.NETHERITE_HOE),
                entry("tame_an_animal", Items.LEAD),
                entry("fishy_business", Items.FISHING_ROD),
                entry("tactical_fishing", Items.PUFFERFISH_BUCKET),
                entry("axolotl_in_a_bucket", Items.AXOLOTL_BUCKET),
                entry("kill_axolotl_target", Items.TROPICAL_FISH_BUCKET),
                entry("complete_catalogue", Items.COD),
                entry("whole_pack", Items.BONE),
                entry("safely_harvest_honey", Items.HONEY_BOTTLE),
                entry("wax_on", Items.HONEYCOMB),
                entry("wax_off", Items.STONE_AXE),
                entry("tadpole_in_a_bucket", Items.TADPOLE_BUCKET),
                entry("leash_all_frog_variants", Items.LEAD),
                entry("froglights", Items.VERDANT_FROGLIGHT),
                entry("silk_touch_nest", Blocks.BEE_NEST),
                entry("ride_a_boat_with_a_goat", Items.OAK_BOAT),
                entry("make_a_sign_glow", Items.GLOW_INK_SAC),
                entry("allay_deliver_item_to_player", Items.COOKIE),
                entry("allay_deliver_cake_to_note_block", Items.NOTE_BLOCK),
                entry("obtain_sniffer_egg", Items.SNIFFER_EGG),
                entry("feed_snifflet", Items.TORCHFLOWER_SEEDS),
                entry("plant_any_sniffer_seed", Items.PITCHER_POD),
                entry("remove_wolf_armor", Items.SHEARS),
                entry("repair_wolf_armor", Items.WOLF_ARMOR),
                entry("place_dried_ghast_in_water", Items.DRIED_GHAST),
                entry("bred_all_animals", Items.GOLDEN_CARROT)
        );

        /* ---------------- Story ---------------- */
        section("story",
                entry("root", Blocks.GRASS_BLOCK),
                entry("mine_stone", Items.WOODEN_PICKAXE),
                entry("upgrade_tools", Items.STONE_PICKAXE),
                entry("smelt_iron", Items.IRON_INGOT),
                entry("iron_tools", Items.IRON_PICKAXE),
                entry("mine_diamond", Items.DIAMOND),
                entry("lava_bucket", Items.LAVA_BUCKET),
                entry("obtain_armor", Items.IRON_CHESTPLATE),
                entry("enchant_item", Items.ENCHANTED_BOOK),
                entry("form_obsidian", Blocks.OBSIDIAN),
                entry("deflect_arrow", Items.SHIELD),
                entry("shiny_gear", Items.DIAMOND_CHESTPLATE),
                entry("enter_the_nether", Items.FLINT_AND_STEEL),
                entry("cure_zombie_villager", Items.GOLDEN_APPLE),
                entry("follow_ender_eye", Items.ENDER_EYE),
                entry("enter_the_end", Blocks.END_STONE)
        );

        /* ---------------- End ---------------- */
        section("end",
                entry("root", Blocks.END_STONE),
                entry("kill_dragon", Blocks.DRAGON_HEAD),
                entry("enter_end_gateway", Items.ENDER_PEARL),
                entry("respawn_dragon", Items.END_CRYSTAL),
                entry("find_end_city", Blocks.PURPUR_BLOCK),
                entry("dragon_breath", Items.DRAGON_BREATH),
                entry("levitate", Items.SHULKER_SHELL),
                entry("elytra", Items.ELYTRA),
                entry("dragon_egg", Blocks.DRAGON_EGG)
        );
    }

    /* ================= API ================= */

    public static ItemStack get(Identifier id) {
        if (id == null) return fallback();

        String path = id.getPath(); // section/name
        int slash = path.indexOf('/');
        if (slash == -1) return fallback();

        String section = path.substring(0, slash);
        String name = path.substring(slash + 1);

        return ICONS
                .getOrDefault(section, Map.of())
                .getOrDefault(name, fallback());
    }

    private static ItemStack fallback() {
        return new ItemStack(Items.KNOWLEDGE_BOOK);
    }

    /* ================= Helpers ================= */

    private static void section(String name, Entry... entries) {
        Map<String, ItemStack> map = new HashMap<>();
        for (Entry e : entries) map.put(e.name, e.stack);
        ICONS.put(name, map);
    }

    private static Entry entry(String name, ItemLike item) {
        return new Entry(name, new ItemStack(item));
    }

    private static Entry entry(String name, net.minecraft.world.level.block.Block block) {
        return new Entry(name, new ItemStack(block));
    }

    private record Entry(String name, ItemStack stack) {}
}
