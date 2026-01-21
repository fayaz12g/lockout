package one.fayaz;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.Optional;

import static one.fayaz.LockoutClient.clientMode;

public class ItemStackFinder {

    public static ItemStack getIconForClaim(String claim, GoalType goalType) {
        String lower = claim.toLowerCase();

        // Foods mode - try to parse the item directly
        if (goalType==GoalType.FOOD) {
            // Try to find the item from registry
            var item = BuiltInRegistries.ITEM.stream()
                    .filter(i -> i.toString().equals(claim))
                    .findFirst()
                    .orElse(null);
            if (item != null) return new ItemStack(item);
        }

        // Kills and Breed modes
        if (goalType==GoalType.KILL || goalType==GoalType.BREED) {
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                String entityName = type.getDescription().getString();
                if (entityName.equalsIgnoreCase(claim)) {
                    SpawnEggItem egg = SpawnEggItem.byId(type);
                    if (egg != null) return new ItemStack(egg);
                }
            }
        }

        // Death mode - check for keywords
        if (goalType==GoalType.DEATH) {
            return DeathIconRegistry.get(lower);
        }

        // Armor mode - show chestplate
        if (goalType==GoalType.ARMOR) {
            return one.fayaz.client.ArmorIconRegistry.get(lower);
        }

        // Advancements mode - show relevant icon
        if (goalType==GoalType.ADVANCEMENT) {
            Identifier id = Identifier.tryParse(claim);
            return AdvancementIconRegistry.get(id);
        }

        // Final Fallback
        return new ItemStack(Items.BARRIER);
    }

}
