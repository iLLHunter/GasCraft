package gascraft.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** A shaped machine recipe; it is never exposed as a crafting-table recipe. */
public final class PowderMixingRecipe implements Recipe<CraftingInput> {
    private final ShapedRecipePattern pattern;
    private final ItemStack result;
    private final int processingTicks;
    private final int energyPerOperation;

    public PowderMixingRecipe(ShapedRecipePattern pattern, ItemStack result, int processingTicks, int energyPerOperation) {
        this.pattern = java.util.Objects.requireNonNull(pattern);
        if (pattern.width() > 3 || pattern.height() > 3) throw new IllegalArgumentException("Powder Mixer recipes must fit a 3x3 grid");
        if (result.isEmpty()) throw new IllegalArgumentException("Mixing result must not be empty");
        if (processingTicks < 1 || energyPerOperation < 1) throw new IllegalArgumentException("Processing time and energy must be positive");
        this.result = result.copy();
        this.processingTicks = processingTicks;
        this.energyPerOperation = energyPerOperation;
    }

    public ShapedRecipePattern pattern() { return pattern; }
    public int processingTicks() { return processingTicks; }
    public int energyPerOperation() { return energyPerOperation; }
    @Override public boolean matches(CraftingInput input, Level level) { return pattern.matches(input); }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) { return result.copy(); }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width >= pattern.width() && height >= pattern.height(); }
    @Override public NonNullList<Ingredient> getIngredients() { return pattern.ingredients(); }
    @Override public RecipeType<?> getType() { return MachineRecipes.POWDER_MIXING.get(); }
    @Override public RecipeSerializer<?> getSerializer() { return MachineRecipes.POWDER_MIXING_SERIALIZER.get(); }

    public static final class Serializer implements RecipeSerializer<PowderMixingRecipe> {
        private static final MapCodec<PowderMixingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ShapedRecipePattern.MAP_CODEC.forGetter(PowderMixingRecipe::pattern),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("processing_ticks").forGetter(PowderMixingRecipe::processingTicks),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("energy_per_operation").forGetter(PowderMixingRecipe::energyPerOperation)
        ).apply(instance, PowderMixingRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, PowderMixingRecipe> STREAM_CODEC = StreamCodec.composite(
            ShapedRecipePattern.STREAM_CODEC, PowderMixingRecipe::pattern,
            ItemStack.STREAM_CODEC, recipe -> recipe.result,
            ByteBufCodecs.VAR_INT, PowderMixingRecipe::processingTicks,
            ByteBufCodecs.VAR_INT, PowderMixingRecipe::energyPerOperation,
            PowderMixingRecipe::new
        );

        @Override public MapCodec<PowderMixingRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, PowderMixingRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
