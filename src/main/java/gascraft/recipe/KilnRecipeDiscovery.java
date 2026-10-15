package gascraft.recipe;

import gascraft.machine.MachineKind;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.Ingredient;

/** Adapts live furnace recipes to the Kiln's ordinary FE processing and experience storage. */
public final class KilnRecipeDiscovery {
    public static SingleInputProcessingRecipe adapt(SmeltingRecipe recipe, ItemStack result) {
        return adapt(recipe, recipe.getIngredients().getFirst(), result);
    }
    public static SingleInputProcessingRecipe adapt(SmeltingRecipe recipe, Ingredient ingredient, ItemStack result) {
        int ticks = recipe.getCookingTime();
        return new SingleInputProcessingRecipe(SingleInputProcessingRecipe.Kind.KILN, ingredient,
            result, ticks, Math.multiplyExact(ticks, MachineKind.BASE_PROCESSING_FE_PER_TICK), recipe.getExperience());
    }
    private KilnRecipeDiscovery() {}
}
