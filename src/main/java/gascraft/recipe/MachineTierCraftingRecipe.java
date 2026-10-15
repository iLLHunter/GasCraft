package gascraft.recipe;

import com.mojang.serialization.MapCodec;
import gascraft.machine.MachineBlock;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Standard shaped tier recipes transfer the source machine's stored state into the new tier. */
public final class MachineTierCraftingRecipe implements CraftingRecipe {
    private final ShapedRecipe shaped;
    public MachineTierCraftingRecipe(ShapedRecipe shaped) { this.shaped = shaped; }
    @Override public boolean matches(CraftingInput input, Level level) { return shaped.matches(input, level); }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = shaped.assemble(input, registries);
        for (ItemStack source : input.items()) {
            if (source.getItem() instanceof BlockItem item && item.getBlock() instanceof MachineBlock) {
                if (source.has(DataComponents.BLOCK_ENTITY_DATA)) result.set(DataComponents.BLOCK_ENTITY_DATA, source.get(DataComponents.BLOCK_ENTITY_DATA));
                if (source.has(DataComponents.CUSTOM_NAME)) result.set(DataComponents.CUSTOM_NAME, source.get(DataComponents.CUSTOM_NAME));
                break;
            }
        }
        return result;
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return shaped.canCraftInDimensions(width, height); }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return shaped.getResultItem(registries).copy(); }
    @Override public NonNullList<Ingredient> getIngredients() { return shaped.getIngredients(); }
    @Override public CraftingBookCategory category() { return shaped.category(); }
    @Override public RecipeSerializer<?> getSerializer() { return MachineRecipes.MACHINE_TIER_CRAFTING.get(); }

    public static final class Serializer implements RecipeSerializer<MachineTierCraftingRecipe> {
        private static final MapCodec<MachineTierCraftingRecipe> CODEC = ShapedRecipe.Serializer.CODEC.xmap(MachineTierCraftingRecipe::new, recipe -> recipe.shaped);
        private static final StreamCodec<RegistryFriendlyByteBuf, MachineTierCraftingRecipe> STREAM_CODEC = ShapedRecipe.Serializer.STREAM_CODEC.map(MachineTierCraftingRecipe::new, recipe -> recipe.shaped);
        @Override public MapCodec<MachineTierCraftingRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, MachineTierCraftingRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
