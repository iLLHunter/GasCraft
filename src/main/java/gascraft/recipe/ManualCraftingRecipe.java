package gascraft.recipe;

import com.mojang.serialization.MapCodec;
import gascraft.GascraftConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** A normal shapeless recipe whose availability follows the synchronized server option. */
public final class ManualCraftingRecipe extends ShapelessRecipe {
    public ManualCraftingRecipe(ShapelessRecipe source){super(source.getGroup(),source.category(),source.getResultItem(null),source.getIngredients());}
    @Override public boolean matches(CraftingInput input,Level level){return (level.isClientSide?GascraftConfig.clientManualEnabled():GascraftConfig.manualEnabled())&&super.matches(input,level);}
    @Override public boolean isSpecial(){return !GascraftConfig.manualEnabled();}
    @Override public RecipeSerializer<ShapelessRecipe> getSerializer(){return MachineRecipes.MANUAL_CRAFTING.get();}
    public static final class Serializer implements RecipeSerializer<ShapelessRecipe> {
        private static final ShapelessRecipe.Serializer BASE=new ShapelessRecipe.Serializer();
        @Override public MapCodec<ShapelessRecipe> codec(){return BASE.codec().xmap(source->(ShapelessRecipe)new ManualCraftingRecipe(source),source->source);}
        @Override public StreamCodec<RegistryFriendlyByteBuf,ShapelessRecipe> streamCodec(){return BASE.streamCodec().map(source->(ShapelessRecipe)new ManualCraftingRecipe(source),source->source);}
    }
}
