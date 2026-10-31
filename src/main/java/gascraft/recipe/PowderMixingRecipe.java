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
    private final boolean unorderedSquare;

    public PowderMixingRecipe(ShapedRecipePattern pattern, ItemStack result, int processingTicks, int energyPerOperation) {
        this(pattern,result,processingTicks,energyPerOperation,false);
    }
    public PowderMixingRecipe(ShapedRecipePattern pattern, ItemStack result, int processingTicks, int energyPerOperation, boolean unorderedSquare) {
        this.pattern = java.util.Objects.requireNonNull(pattern);
        if (pattern.width() > 3 || pattern.height() > 3) throw new IllegalArgumentException("Powder Mixer recipes must fit a 3x3 grid");
        if (result.isEmpty()) throw new IllegalArgumentException("Mixing result must not be empty");
        if (processingTicks < 1 || energyPerOperation < 1) throw new IllegalArgumentException("Processing time and energy must be positive");
        this.result = result.copy();
        this.processingTicks = processingTicks;
        this.energyPerOperation = energyPerOperation;
        if(unorderedSquare&&(pattern.width()!=2||pattern.height()!=2||pattern.ingredients().stream().anyMatch(Ingredient::isEmpty)))throw new IllegalArgumentException("An unordered square must contain four ingredients");
        this.unorderedSquare=unorderedSquare;
    }

    public ShapedRecipePattern pattern() { return pattern; }
    public int processingTicks() { return processingTicks; }
    public int energyPerOperation() { return energyPerOperation; }
    public boolean unorderedSquare() {return unorderedSquare;}
    @Override public boolean matches(CraftingInput input, Level level) {
        if(!gascraft.gas.GasChainControls.itemAllowed(result))return false;
        if(input.items().stream().anyMatch(stack->!gascraft.gas.GasChainControls.itemAllowed(stack)))return false;
        if(!unorderedSquare)return pattern.matches(input);
        if(input.width()!=2||input.height()!=2||input.items().stream().anyMatch(ItemStack::isEmpty))return false;
        return matchSquare(input,0,new boolean[4]);
    }
    private boolean matchSquare(CraftingInput input,int slot,boolean[] used){
        if(slot==4)return true;
        for(int i=0;i<4;i++)if(!used[i]&&pattern.ingredients().get(i).test(input.getItem(slot))){used[i]=true;if(matchSquare(input,slot+1,used))return true;used[i]=false;}
        return false;
    }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) { return gascraft.gas.GasChainControls.itemAllowed(result)?result.copy():ItemStack.EMPTY; }
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
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("energy_per_operation").forGetter(PowderMixingRecipe::energyPerOperation),
            Codec.BOOL.optionalFieldOf("unordered_square",false).forGetter(PowderMixingRecipe::unorderedSquare)
        ).apply(instance, PowderMixingRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, PowderMixingRecipe> STREAM_CODEC = StreamCodec.composite(
            ShapedRecipePattern.STREAM_CODEC, PowderMixingRecipe::pattern,
            ItemStack.STREAM_CODEC, recipe -> recipe.result,
            ByteBufCodecs.VAR_INT, PowderMixingRecipe::processingTicks,
            ByteBufCodecs.VAR_INT, PowderMixingRecipe::energyPerOperation,
            ByteBufCodecs.BOOL, PowderMixingRecipe::unorderedSquare,
            PowderMixingRecipe::new
        );

        @Override public MapCodec<PowderMixingRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, PowderMixingRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
