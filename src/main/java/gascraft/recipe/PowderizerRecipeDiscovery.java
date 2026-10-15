package gascraft.recipe;

import gascraft.GascraftConfig;
import gascraft.ModContent;
import gascraft.machine.MachineKind;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;

/** Resolve existing tagged ore/ingot materials to one existing dust, using the live tag snapshot. */
public final class PowderizerRecipeDiscovery {
    public static Optional<SingleInputProcessingRecipe> findRecipe(ItemStack input) {
        if (input.isEmpty()) return Optional.empty();
        if (BuiltInRegistries.ITEM.getKey(input.getItem()).equals(ResourceLocation.withDefaultNamespace("potent_sulfur"))) {
            int ticks = MachineKind.POWDERIZER.baseProcessingTicks();
            return Optional.of(new SingleInputProcessingRecipe(SingleInputProcessingRecipe.Kind.POWDERIZING,
                Ingredient.of(input.getItem()), new ItemStack(ModContent.POWDERED_SULFUR_POTENT.get()), ticks, ticks * MachineKind.BASE_PROCESSING_FE_PER_TICK));
        }
        var materials = new ArrayList<>(input.getTags().map(TagKey::location)
            .filter(id -> id.getNamespace().equals("c") && (id.getPath().startsWith("ores/") || id.getPath().startsWith("ingots/")))
            .map(id -> id.getPath().substring(id.getPath().indexOf('/') + 1)).distinct().sorted().toList());
        if (BuiltInRegistries.ITEM.getKey(input.getItem()).equals(ResourceLocation.withDefaultNamespace("sulfur"))) materials.add("sulfur");
        for (String material : materials) {
            List<Item> candidates = new ArrayList<>();
            for (String category : List.of("dusts", "powders")) {
                var tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", category + "/" + material));
                BuiltInRegistries.ITEM.getTag(tag).ifPresent(items -> items.stream().map(holder -> holder.value())
                    .filter(item -> item != Items.AIR && (!material.equals("sulfur") || item != ModContent.SULFUR.get())).forEach(candidates::add));
            }
            var output = selectOutput(material, candidates, GascraftConfig.POWDERIZER_PREFERRED_OUTPUTS.get());
            if (output.isPresent()) {
                int ticks = MachineKind.POWDERIZER.baseProcessingTicks();
                return Optional.of(new SingleInputProcessingRecipe(SingleInputProcessingRecipe.Kind.POWDERIZING,
                    Ingredient.of(input.getItem()), new ItemStack(output.get()), ticks, ticks * MachineKind.BASE_PROCESSING_FE_PER_TICK));
            }
        }
        return Optional.empty();
    }

    public static Optional<Item> selectOutput(String material, Collection<Item> candidates, List<? extends String> preferences) {
        var sorted = candidates.stream().distinct().sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
        for (String preference : preferences) {
            if (!isPreference(preference)) continue;
            int separator = preference.indexOf('=');
            if (!preference.substring(0, separator).trim().equals(material)) continue;
            var desired = ResourceLocation.parse(preference.substring(separator + 1).trim());
            for (Item candidate : sorted) if (BuiltInRegistries.ITEM.getKey(candidate).equals(desired)) return Optional.of(candidate);
        }
        return sorted.stream().filter(item -> (!material.equals("iron") || item != ModContent.POWDERED_IRON.get())
            && (!material.equals("sulfur") || item != ModContent.POWDERED_SULFUR.get())).findFirst()
            .or(() -> sorted.stream().findFirst());
    }

    public static boolean isPreference(Object value) {
        if (!(value instanceof String text)) return false;
        int separator = text.indexOf('=');
        return separator > 0 && text.substring(0, separator).trim().matches("[a-z0-9_./-]+")
            && ResourceLocation.tryParse(text.substring(separator + 1).trim()) != null;
    }

    private PowderizerRecipeDiscovery() {}
}
