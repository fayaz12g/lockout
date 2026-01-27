package one.fayaz.icon;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DeathIconRegistry {

    private static final Map<List<String>, ItemStack> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put(List.of("discovered"), new ItemStack(Items.MAGMA_BLOCK));
        KEYWORDS.put(List.of("lava"), new ItemStack(Items.LAVA_BUCKET));
        KEYWORDS.put(List.of("suffocated"), new ItemStack(Items.SAND));
        KEYWORDS.put(List.of("fire", "flame", "burnt", "burned"), new ItemStack(Items.FLINT_AND_STEEL));
        KEYWORDS.put(List.of("fall", "ground", "fell"), new ItemStack(Items.FEATHER));
        KEYWORDS.put(List.of("cactus", "prick"), new ItemStack(Items.CACTUS));
        KEYWORDS.put(List.of("berry", "bush"), new ItemStack(Items.SWEET_BERRIES));
        KEYWORDS.put(List.of("starve"), new ItemStack(Items.ROTTEN_FLESH));
        KEYWORDS.put(List.of("explosion", "blew up", "tnt"), new ItemStack(Items.TNT));
        KEYWORDS.put(List.of("magic", "potion"), new ItemStack(Items.POTION));
        KEYWORDS.put(List.of("withered"), new ItemStack(Items.WITHER_ROSE));
        KEYWORDS.put(List.of("anvil", "squashed"), new ItemStack(Items.ANVIL));
        KEYWORDS.put(List.of("arrow", "shot"), new ItemStack(Items.ARROW));
        KEYWORDS.put(List.of("trident"), new ItemStack(Items.TRIDENT));
        KEYWORDS.put(List.of("stalagmite", "impaled"), new ItemStack(Items.POINTED_DRIPSTONE));
        KEYWORDS.put(List.of("freeze", "frozen"), new ItemStack(Items.POWDER_SNOW_BUCKET));
        KEYWORDS.put(List.of("shriek"), new ItemStack(Items.WARDEN_SPAWN_EGG));
    }

    public static ItemStack get(String text) {
        if (text == null) return fallback();

        String lower = text.toLowerCase();

        // Keyword-based deaths
        for (var entry : KEYWORDS.entrySet()) {
            for (String keyword : entry.getKey()) {
                if (lower.contains(keyword)) {
                    return entry.getValue();
                }
            }
        }

        // Mob-based deaths → spawn eggs
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            String entityName = type.getDescription().getString().toLowerCase();
            if (lower.contains(entityName)) {

                Optional<Holder<Item>> eggHolderOpt = SpawnEggItem.byId(type);
                if (eggHolderOpt.isPresent()) {
                    Item item = eggHolderOpt.get().value();
                    if (item instanceof SpawnEggItem) {
                        return new ItemStack(item);
                    }
                }

            }
        }

        // this will never get called. IDK how to support both drowned mobs and drowning
        if (lower.contains("drowned")) {
            return new ItemStack(Items.WATER_BUCKET);
        }

        return fallback();
    }

    private static ItemStack fallback() {
        return new ItemStack(Items.PLAYER_HEAD);
    }
}
