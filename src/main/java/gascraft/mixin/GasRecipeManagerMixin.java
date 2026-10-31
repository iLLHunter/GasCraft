package gascraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import gascraft.gas.GasChainControls;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Filters disabled Gascraft outputs for every normal machine/crafting recipe type. */
@Mixin(RecipeManager.class)
public abstract class GasRecipeManagerMixin {
    @Shadow @Final private HolderLookup.Provider registries;
    @Shadow private <I extends RecipeInput,T extends Recipe<I>> Collection<RecipeHolder<T>> byType(RecipeType<T> type){throw new AssertionError();}
    @ModifyReturnValue(method="byType",at=@At("RETURN"))
    private Collection<RecipeHolder<?>> gascraft$available(Collection<RecipeHolder<?>> recipes){
        return GasChainControls.anyDisabled()?recipes.stream().filter(r->GasChainControls.itemAllowed(r.value().getResultItem(registries))).toList():recipes;
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    @ModifyReturnValue(method="getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",at=@At("RETURN"))
    private Optional<RecipeHolder<?>> gascraft$cached(Optional<RecipeHolder<?>> selected,RecipeType<?> type,RecipeInput input,Level level,RecipeHolder<?> cached){
        if(selected.isEmpty()||!GasChainControls.anyDisabled()||GasChainControls.itemAllowed(selected.get().value().getResultItem(registries)))return selected;
        for(Object value:byType((RecipeType)type)){var holder=(RecipeHolder<?>)value;if(((Recipe)holder.value()).matches(input,level))return Optional.of(holder);}
        return Optional.empty();
    }
}
