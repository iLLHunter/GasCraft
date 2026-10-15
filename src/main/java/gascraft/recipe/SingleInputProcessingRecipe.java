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

/** Datapack recipe data for the Powderizer and Kiln, independent of crafting/furnace recipes. */
public final class SingleInputProcessingRecipe implements Recipe<SingleRecipeInput> {
    public enum Kind { POWDERIZING, KILN, FERMENTING }

    private final Kind kind;
    private final Ingredient ingredient;
    private final ItemStack result;
    private final int processingTicks;
    private final int energyPerOperation;
    private final float experience;

    public SingleInputProcessingRecipe(Kind kind, Ingredient ingredient, ItemStack result, int processingTicks, int energyPerOperation) {
        this(kind, ingredient, result, processingTicks, energyPerOperation, 0);
    }

    public SingleInputProcessingRecipe(Kind kind, Ingredient ingredient, ItemStack result, int processingTicks, int energyPerOperation, float experience) {
        this.kind = java.util.Objects.requireNonNull(kind);
        this.ingredient = java.util.Objects.requireNonNull(ingredient);
        if (result.isEmpty()) throw new IllegalArgumentException("Processing result must not be empty");
        if (kind == Kind.POWDERIZING && result.getCount() != 1) throw new IllegalArgumentException("Powderizer recipes must produce exactly one item");
        if (processingTicks < 1 || (kind == Kind.FERMENTING ? energyPerOperation != 0 : energyPerOperation < 1))
            throw new IllegalArgumentException("Processing time must be positive; fermentation uses no energy");
        if (!Float.isFinite(experience) || experience < 0) throw new IllegalArgumentException("Recipe experience must be finite and nonnegative");
        this.result = result.copy();
        this.processingTicks = processingTicks;
        this.energyPerOperation = energyPerOperation;
        this.experience = experience;
    }

    public Kind kind() { return kind; }
    public Ingredient ingredient() { return ingredient; }
    public int processingTicks() { return processingTicks; }
    public int energyPerOperation() { return energyPerOperation; }
    public float experience() { return experience; }
    @Override public boolean matches(SingleRecipeInput input, Level level) { return ingredient.test(input.item()); }
    @Override public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) { return result.copy(); }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 1; }
    @Override public NonNullList<Ingredient> getIngredients() { return NonNullList.of(Ingredient.EMPTY, ingredient); }
    @Override public RecipeType<?> getType() { return switch (kind) {
        case POWDERIZING -> MachineRecipes.POWDERIZING.get(); case KILN -> MachineRecipes.KILN_PROCESSING.get(); case FERMENTING -> MachineRecipes.FERMENTING.get();
    }; }
    @Override public RecipeSerializer<?> getSerializer() { return switch (kind) {
        case POWDERIZING -> MachineRecipes.POWDERIZING_SERIALIZER.get(); case KILN -> MachineRecipes.KILN_SERIALIZER.get(); case FERMENTING -> MachineRecipes.FERMENTING_SERIALIZER.get();
    }; }

    public static final class Serializer implements RecipeSerializer<SingleInputProcessingRecipe> {
        private final MapCodec<SingleInputProcessingRecipe> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, SingleInputProcessingRecipe> streamCodec;

        public Serializer(Kind kind) {
            codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(SingleInputProcessingRecipe::ingredient),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("processing_ticks").forGetter(SingleInputProcessingRecipe::processingTicks),
                Codec.intRange(kind == Kind.FERMENTING ? 0 : 1, kind == Kind.FERMENTING ? 0 : Integer.MAX_VALUE).fieldOf("energy_per_operation").forGetter(SingleInputProcessingRecipe::energyPerOperation),
                Codec.floatRange(0, Float.MAX_VALUE).optionalFieldOf("experience", 0F).forGetter(SingleInputProcessingRecipe::experience)
            ).apply(instance, (input, output, ticks, energy, experience) -> new SingleInputProcessingRecipe(kind, input, output, ticks, energy, experience)));
            streamCodec = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, SingleInputProcessingRecipe::ingredient,
                ItemStack.STREAM_CODEC, recipe -> recipe.result,
                ByteBufCodecs.VAR_INT, SingleInputProcessingRecipe::processingTicks,
                ByteBufCodecs.VAR_INT, SingleInputProcessingRecipe::energyPerOperation,
                ByteBufCodecs.FLOAT, SingleInputProcessingRecipe::experience,
                (input, output, ticks, energy, experience) -> new SingleInputProcessingRecipe(kind, input, output, ticks, energy, experience)
            );
        }

        @Override public MapCodec<SingleInputProcessingRecipe> codec() { return codec; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, SingleInputProcessingRecipe> streamCodec() { return streamCodec; }
    }
}
