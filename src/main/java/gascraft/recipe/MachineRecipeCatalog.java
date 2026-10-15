package gascraft.recipe;

import gascraft.gas.GasCatalog;
import gascraft.item.GasCanisterItem;
import gascraft.machine.MachineKind;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** The same live recipes supply JEI displays and validated server-side item transfers. */
public final class MachineRecipeCatalog {
    public record Entry(ResourceLocation id, MachineKind kind, SingleInputProcessingRecipe single,
                        PowderMixingRecipe mixing, SynthesizingRecipe synthesizing, ItemStack fuel, int fuelTicks) {
        public ItemStack result(Level level) {
            if (single != null) return single.getResultItem(level.registryAccess());
            if (mixing != null) return mixing.getResultItem(level.registryAccess());
            if (synthesizing != null) return synthesizing.getResultItem(level.registryAccess());
            return fuel.getCraftingRemainingItem();
        }
        public int ticks() { return single != null ? single.processingTicks() : mixing != null ? mixing.processingTicks() : fuelTicks; }
        public int energy() { return single != null ? single.energyPerOperation() : mixing != null ? mixing.energyPerOperation() : fuelTicks * 32; }
        public List<Ingredient> inputs() {
            if (single != null) return List.of(single.ingredient());
            if (mixing != null) {
                List<Ingredient> grid = new ArrayList<>(Collections.nCopies(9, Ingredient.EMPTY));
                var pattern = mixing.pattern();
                for (int y = 0; y < pattern.height(); y++) for (int x = 0; x < pattern.width(); x++)
                    grid.set(y * 3 + x, pattern.ingredients().get(y * pattern.width() + x));
                return grid;
            }
            return List.of(Ingredient.of(fuel));
        }
        public List<ItemStack> canisters() {
            var recipe = synthesizing;
            if (!recipe.inputGas().isEmpty()) return GasCatalog.contains(ResourceLocation.parse(recipe.inputGas()))
                ? List.of(GasCanisterItem.createFilledCanister(ResourceLocation.parse(recipe.inputGas()))) : List.of();
            if (recipe.input().hasNoItems()) return List.of();
            return Arrays.stream(recipe.input().getItems()).map(ItemStack::copy).filter(stack -> {
                if (recipe.inputPotion().isEmpty()) return true;
                var potion = BuiltInRegistries.POTION.getHolder(ResourceLocation.parse(recipe.inputPotion()));
                potion.ifPresent(holder -> stack.set(DataComponents.POTION_CONTENTS, new PotionContents(holder)));
                return potion.isPresent();
            }).filter(recipe::baseMatches).toList();
        }
    }

    public static List<Entry> recipes(Level level, MachineKind kind) {
        List<Entry> entries = new ArrayList<>();
        if (kind.isSynthesizer()) {
            level.getRecipeManager().getAllRecipesFor(MachineRecipes.SYNTHESIZING.get()).forEach(holder ->
                entries.add(new Entry(holder.id(), MachineKind.STONE_SYNTHESIZER, null, null, holder.value(), ItemStack.EMPTY, 0)));
            var mixes = GasCatalog.brewingMixes();
            for (int i = 0; i < mixes.size(); i++) {
                try { entries.add(new Entry(GasCatalog.id("synthesizing/legacy/" + i), MachineKind.STONE_SYNTHESIZER,
                    null, null, SynthesizingRecipe.fromLegacy(mixes.get(i)), ItemStack.EMPTY, 0)); }
                catch (IllegalArgumentException ignored) { /* Mirrors the machine's legacy adapter. */ }
            }
        } else if (kind == MachineKind.GENERATOR) {
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack fuel = new ItemStack(item);
                int ticks = fuel.getBurnTime(RecipeType.SMELTING);
                if (ticks > 0) entries.add(new Entry(generatedId("generating", item), kind, null, null, null, fuel, ticks));
            }
        } else if (kind == MachineKind.POWDER_MIXER) {
            level.getRecipeManager().getAllRecipesFor(MachineRecipes.POWDER_MIXING.get()).forEach(holder ->
                entries.add(new Entry(holder.id(), kind, null, holder.value(), null, ItemStack.EMPTY, 0)));
        } else {
            var type = kind == MachineKind.POWDERIZER ? MachineRecipes.POWDERIZING.get()
                : kind == MachineKind.FERMENTER ? MachineRecipes.FERMENTING.get() : MachineRecipes.KILN_PROCESSING.get();
            var explicit = level.getRecipeManager().getAllRecipesFor(type);
            explicit.forEach(holder -> entries.add(new Entry(holder.id(), kind, holder.value(), null, null, ItemStack.EMPTY, 0)));
            if (kind == MachineKind.MATERIALS_KILN) level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING).forEach(holder -> {
                var inputs = Arrays.stream(holder.value().getIngredients().getFirst().getItems())
                    .filter(input -> explicit.stream().noneMatch(recipe -> recipe.value().ingredient().test(input))).toArray(ItemStack[]::new);
                if (inputs.length > 0) entries.add(new Entry(GasCatalog.id("kiln_processing/smelting/" + holder.id().getNamespace() + "/" + holder.id().getPath()), kind,
                    KilnRecipeDiscovery.adapt(holder.value(), Ingredient.of(inputs), holder.value().getResultItem(level.registryAccess())), null, null, ItemStack.EMPTY, 0));
            });
            if (kind == MachineKind.POWDERIZER) for (Item item : BuiltInRegistries.ITEM) {
                ItemStack input = new ItemStack(item);
                if (explicit.stream().anyMatch(holder -> holder.value().ingredient().test(input))) continue;
                PowderizerRecipeDiscovery.findRecipe(input).ifPresent(recipe ->
                    entries.add(new Entry(generatedId("powderizing", item), kind, recipe, null, null, ItemStack.EMPTY, 0)));
            }
        }
        return entries.stream().sorted(Comparator.comparing(entry -> entry.id().toString())).toList();
    }
    private static ResourceLocation generatedId(String category, Item item) {
        var id = BuiltInRegistries.ITEM.getKey(item);
        return GasCatalog.id(category + "/generated/" + id.getNamespace() + "/" + id.getPath());
    }
    private MachineRecipeCatalog() {}
}
