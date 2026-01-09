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

        // Armor mode - show chestplate
        if (clientMode.equals("ARMOR")) {
            return one.fayaz.client.ArmorIconRegistry.get(lower);
        }

        // Advancements mode - show relevant icon
        if (clientMode.equals("ADVANCEMENTS")) {
            Identifier id = Identifier.tryParse(claim);
            return AdvancementIconRegistry.get(id);
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
        if (clientMode.equals("DEATH")) {
            return DeathIconRegistry.get(lower);
        }
        // Final Fallback
        return new ItemStack(Items.BARRIER);
    }

}
