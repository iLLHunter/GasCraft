package gascraft.mixin;

import gascraft.gas.GasChainControls;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Includes ordinary replacement datapack recipes without requiring a Gascraft recipe type. */
@Mixin({ShapedRecipe.class,ShapelessRecipe.class})
public abstract class GasCraftingRecipeMixin {
    @Inject(method="matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z",at=@At("HEAD"),cancellable=true)
    private void gascraft$matches(CraftingInput input,Level level,CallbackInfoReturnable<Boolean> result){
        if(GasChainControls.anyDisabled()&&!GasChainControls.itemAllowed(((Recipe<?>)(Object)this).getResultItem(level.registryAccess())))result.setReturnValue(false);
    }
    @Inject(method="assemble(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;",at=@At("HEAD"),cancellable=true)
    private void gascraft$assemble(CraftingInput input,HolderLookup.Provider registries,CallbackInfoReturnable<ItemStack> result){
        if(GasChainControls.anyDisabled()&&!GasChainControls.itemAllowed(((Recipe<?>)(Object)this).getResultItem(registries)))result.setReturnValue(ItemStack.EMPTY);
    }
}
