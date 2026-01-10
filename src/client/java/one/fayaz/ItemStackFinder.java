package one.fayaz;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import static one.fayaz.LockoutClient.clientMode;

public class ItemStackFinder {

    public static ItemStack getIconForClaim(String claim) {
        String lower = claim.toLowerCase();

        String mode = clientMode;

        if(mode.equals("MIXED")) {
            if (lower.contains("minecraft:")) {
                mode = "ADVANCEMENTS";
            }
        }

        // Foods mode - try to parse the item directly
        if (mode.equals("FOODS") || mode.equals("MIXED")) {
            // Try to find the item from registry
            var item = BuiltInRegistries.ITEM.stream()
                    .filter(i -> i.toString().equals(claim))
                    .findFirst()
                    .orElse(null);
            if (item != null) return new ItemStack(item);
        }

        // Kills mode
        if (mode.equals("KILLS") || mode.equals("MIXED")) {
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                String entityName = type.getDescription().getString();
                if (entityName.equalsIgnoreCase(claim)) {
                    SpawnEggItem egg = SpawnEggItem.byId(type);
                    if (egg != null) return new ItemStack(egg);
                }
            }
        }

        // Death mode - check for keywords
        if (mode.equals("DEATH") || mode.equals("MIXED")) {
            return DeathIconRegistry.get(lower);
        }

        // Armor mode - show chestplate
        if (mode.equals("ARMOR")) {
            return one.fayaz.client.ArmorIconRegistry.get(lower);
        }

        // Advancements mode - show relevant icon
        if (mode.equals("ADVANCEMENTS")) {
            Identifier id = Identifier.tryParse(claim);
            return AdvancementIconRegistry.get(id);
        }

        // Final Fallback
        return new ItemStack(Items.BARRIER);
    }

}
