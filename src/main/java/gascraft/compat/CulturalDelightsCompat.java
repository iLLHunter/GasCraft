package gascraft.compat;

import gascraft.gas.GasCatalog;
import gascraft.item.GasContainerItem;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Optional meal and gas content; no Cultural Delights or Farmer's Delight classes are linked. */
public final class CulturalDelightsCompat {
    private static final Set<String> ITEMS = Set.of("taco_chime_grande_box", "powdered_fart_gas",
        "powdered_fart_gas_concentrated", "taco_chime_gas_pellet_composite", "taco_chime_gas_pellet", "taco_chime_gas_canister");

    public static boolean loaded() { return ModList.get().isLoaded("culturaldelights"); }
    public static boolean hidden(ItemStack stack) {
        if (loaded()) return false;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("gascraft") && ITEMS.contains(id.getPath())
            || stack.getItem() instanceof GasContainerItem && GasCatalog.id("taco_chime_gas").equals(stack.get(gascraft.ModContent.GAS_CONTENTS));
    }
    private CulturalDelightsCompat() {}
}
