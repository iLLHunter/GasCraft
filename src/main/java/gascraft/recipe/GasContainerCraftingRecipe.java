package gascraft.recipe;

import gascraft.ModContent;
import gascraft.gas.GasCatalog;
import gascraft.item.GasCanisterItem;
import gascraft.item.GasContainerItem;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Crafts canister emptying, gas-preserving grenade filling and reusable shell recycling. */
public final class GasContainerCraftingRecipe extends CustomRecipe {
    public GasContainerCraftingRecipe(CraftingBookCategory category) { super(category); }
    @Override public boolean matches(CraftingInput input, Level level) { return !result(input).isEmpty(); }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) { return result(input); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 1; }
    @Override public RecipeSerializer<?> getSerializer() { return ModContent.GAS_CONTAINER_CRAFTING.get(); }
    private static ItemStack result(CraftingInput input) {
        List<ItemStack> items = input.items().stream().filter(stack -> !stack.isEmpty()).toList();
        if (items.size() == 1 && GasCanisterItem.isCanister(items.getFirst()) && GasContainerItem.hasContents(items.getFirst()))
            return new ItemStack(ModContent.EMPTY_CANISTER.get());
        if (items.size() == 1 && items.getFirst().is(ModContent.GRENADE_SHELL.get())) {
            ItemStack result = new ItemStack(ModContent.GRENADE_DIFFUSER.get());
            return result;
        }
        if (items.size() != 2) return ItemStack.EMPTY;
        for (int orientation = 0; orientation < 2; orientation++) {
            ItemStack source = items.get(orientation), reagent = items.get(1 - orientation);
            if ((reagent.is(ModContent.GRENADE_DIFFUSER.get()) || reagent.is(ModContent.GRENADE_SHELL.get())) && GasCanisterItem.isCanister(source) && GasContainerItem.hasContents(source)) {
                ItemStack result = GasContainerItem.createFilledContainer(ModContent.CHEMICAL_GRENADE.get(), GasContainerItem.containedGas(source).id());
                return result;
            }
        }
        return ItemStack.EMPTY;
    }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        if (result(input).is(ModContent.CHEMICAL_GRENADE.get())) return remaining;
        for (int index = 0; index < input.size(); index++) if (input.getItem(index).hasCraftingRemainingItem()) remaining.set(index, input.getItem(index).getCraftingRemainingItem());
        return remaining;
    }
}
