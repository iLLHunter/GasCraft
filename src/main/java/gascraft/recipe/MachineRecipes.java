package gascraft.recipe;

import gascraft.Gascraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Machine recipe types use the normal server recipe manager and its datapack/network lifecycle. */
public final class MachineRecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Gascraft.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Gascraft.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<SingleInputProcessingRecipe>> POWDERIZING = TYPES.register("powderizing", () -> new RecipeType<>() {});
    public static final DeferredHolder<RecipeType<?>, RecipeType<SingleInputProcessingRecipe>> KILN_PROCESSING = TYPES.register("kiln_processing", () -> new RecipeType<>() {});
    public static final DeferredHolder<RecipeType<?>, RecipeType<PowderMixingRecipe>> POWDER_MIXING = TYPES.register("powder_mixing", () -> new RecipeType<>() {});
    public static final DeferredHolder<RecipeType<?>, RecipeType<SingleInputProcessingRecipe>> FERMENTING = TYPES.register("fermenting", () -> new RecipeType<>() {});
    public static final DeferredHolder<RecipeType<?>, RecipeType<SynthesizingRecipe>> SYNTHESIZING = TYPES.register("synthesizing", () -> new RecipeType<>() {});

    public static final DeferredHolder<RecipeSerializer<?>, SingleInputProcessingRecipe.Serializer> POWDERIZING_SERIALIZER = SERIALIZERS.register("powderizing", () -> new SingleInputProcessingRecipe.Serializer(SingleInputProcessingRecipe.Kind.POWDERIZING));
    public static final DeferredHolder<RecipeSerializer<?>, SingleInputProcessingRecipe.Serializer> KILN_SERIALIZER = SERIALIZERS.register("kiln_processing", () -> new SingleInputProcessingRecipe.Serializer(SingleInputProcessingRecipe.Kind.KILN));
    public static final DeferredHolder<RecipeSerializer<?>, PowderMixingRecipe.Serializer> POWDER_MIXING_SERIALIZER = SERIALIZERS.register("powder_mixing", PowderMixingRecipe.Serializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, SingleInputProcessingRecipe.Serializer> FERMENTING_SERIALIZER = SERIALIZERS.register("fermenting", () -> new SingleInputProcessingRecipe.Serializer(SingleInputProcessingRecipe.Kind.FERMENTING));
    public static final DeferredHolder<RecipeSerializer<?>, SynthesizingRecipe.Serializer> SYNTHESIZING_SERIALIZER = SERIALIZERS.register("synthesizing", SynthesizingRecipe.Serializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, MachineTierCraftingRecipe.Serializer> MACHINE_TIER_CRAFTING = SERIALIZERS.register("machine_tier_crafting", MachineTierCraftingRecipe.Serializer::new);

    public static void register(IEventBus bus) { TYPES.register(bus); SERIALIZERS.register(bus); }
    private MachineRecipes() {}
}
