package one.fayaz.client;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ArmorIconRegistry {

    private static final Map<String, ItemStack> ARMOR = new LinkedHashMap<>();

    static {
        // Order matters (most specific first)
        ARMOR.put("netherite", new ItemStack(Items.NETHERITE_CHESTPLATE));
        ARMOR.put("diamond", new ItemStack(Items.DIAMOND_CHESTPLATE));
        ARMOR.put("gold", new ItemStack(Items.GOLDEN_CHESTPLATE));
        ARMOR.put("iron", new ItemStack(Items.IRON_CHESTPLATE));
        ARMOR.put("chainmail", new ItemStack(Items.CHAINMAIL_CHESTPLATE));
        ARMOR.put("copper", new ItemStack(Items.COPPER_CHESTPLATE));
        ARMOR.put("leather", new ItemStack(Items.LEATHER_CHESTPLATE));
    }

    public static ItemStack get(String text) {
        if (text == null) return fallback();
        String lower = text.toLowerCase();

        for (var entry : ARMOR.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return fallback();
    }

    private static ItemStack fallback() {
        return new ItemStack(Items.IRON_CHESTPLATE);
    }
}
