package gascraft.compat;

import gascraft.recipe.SynthesizingRecipe;
import java.util.List;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

/** Called only with Mekanism present; reuses its registered JEI ingredient identity and renderer. */
public final class JeiChemicalIngredients {
    public static List<ChemicalStack> stacks(SynthesizingRecipe.TankIngredient tank) {
        if (!tank.id().isEmpty()) return MekanismAPI.CHEMICAL_REGISTRY.getOptional(ResourceLocation.parse(tank.id()))
            .map(chemical -> List.of(new ChemicalStack(chemical, tank.amount()))).orElse(List.of());
        return MekanismAPI.CHEMICAL_REGISTRY.getTag(TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, ResourceLocation.parse(tank.tag())))
            .map(tag -> tag.stream().map(holder -> new ChemicalStack(holder.value(), tank.amount())).toList()).orElse(List.of());
    }
    public static boolean available(SynthesizingRecipe.TankIngredient tank, IIngredientManager manager) {
        return manager.getIngredientTypeChecked(ChemicalStack.class).isPresent() && !stacks(tank).isEmpty();
    }
    public static void add(IRecipeSlotBuilder slot, SynthesizingRecipe.TankIngredient tank, IIngredientManager manager) {
        manager.getIngredientTypeChecked(ChemicalStack.class).ifPresent(type ->
            slot.addIngredients(type, stacks(tank)).setCustomRenderer(type, manager.getIngredientRenderer(type)));
    }
    private JeiChemicalIngredients() {}
}
