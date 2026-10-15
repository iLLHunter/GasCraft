package gascraft.recipe;

import gascraft.ModContent;
import gascraft.gas.GasCatalog;
import gascraft.item.GasContainerItem;
import net.minecraft.core.registries.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/** Legacy mix matcher retained for compatibility; Gascraft does not register it with brewing stands. */
public record GasMixBrewingRecipe(GasCatalog.BrewingMix mix) implements IBrewingRecipe {
    public static void register(RegisterBrewingRecipesEvent event) {
        // Resolve against the catalog on every query so config/datapack reloads also update existing stands.
        event.getBuilder().addRecipe(new IBrewingRecipe() {
            public boolean isInput(ItemStack input) { return recipes().stream().anyMatch(r -> r.isInput(input)); }
            public boolean isIngredient(ItemStack ingredient) { return recipes().stream().anyMatch(r -> r.isIngredient(ingredient)); }
            public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
                return recipes().stream().map(r -> r.getOutput(input, ingredient)).filter(s -> !s.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
            }
        });
    }
    public static java.util.List<GasMixBrewingRecipe> recipes() { return GasCatalog.brewingMixes().stream().map(GasMixBrewingRecipe::new).toList(); }
    @Override public boolean isInput(ItemStack input) {
        if (!GasCatalog.contains(mix.result())) return false;
        if (!mix.inputGas().isEmpty()) {
            ResourceLocation gas = ResourceLocation.parse(mix.inputGas());
            return GasCatalog.contains(gas) && gascraft.item.GasCanisterItem.isCanister(input) && GasContainerItem.hasContents(input) && GasContainerItem.containedGas(input).id().equals(gas);
        }
        if (!input.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse(mix.inputItem())))) return false;
        return mix.inputPotion().isEmpty() || BuiltInRegistries.POTION.getHolder(ResourceLocation.parse(mix.inputPotion()))
            .map(potion -> input.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(potion)).orElse(false);
    }
    public Ingredient ingredient() {
        return !mix.ingredientItem().isEmpty() ? Ingredient.of(BuiltInRegistries.ITEM.get(ResourceLocation.parse(mix.ingredientItem())))
            : Ingredient.of(TagKey.create(Registries.ITEM, ResourceLocation.parse(mix.ingredientTag())));
    }
    public ItemStack input() {
        if (!mix.inputPotion().isEmpty()) return PotionContents.createItemStack(
            BuiltInRegistries.ITEM.get(ResourceLocation.parse(mix.inputItem())),
            BuiltInRegistries.POTION.getHolder(ResourceLocation.parse(mix.inputPotion())).orElseThrow());
        return mix.inputGas().isEmpty() ? new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(mix.inputItem())))
            : gascraft.item.GasCanisterItem.createFilledCanister(ResourceLocation.parse(mix.inputGas()));
    }
    @Override public boolean isIngredient(ItemStack ingredient) { return ingredient().test(ingredient); }
    @Override public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        return isInput(input) && isIngredient(ingredient) ? output() : ItemStack.EMPTY;
    }
    public ItemStack output() { return gascraft.item.GasCanisterItem.createFilledCanister(mix.result()); }
}
