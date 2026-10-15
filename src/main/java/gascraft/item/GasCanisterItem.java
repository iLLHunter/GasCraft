package gascraft.item;

import gascraft.ModContent;
import gascraft.gas.GasCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Each built-in gas has a named item; reloadable custom gases retain their contents component. */
public final class GasCanisterItem extends GasContainerItem {
    public GasCanisterItem(Properties properties) { super(properties); }

    public static boolean isCanister(ItemStack stack) { return stack.getItem() instanceof GasCanisterItem; }

    public static ItemStack createFilledCanister(ResourceLocation gas) {
        var item = ModContent.CANISTERS.get(gas);
        return GasContainerItem.createFilledContainer(item == null ? ModContent.CUSTOM_GAS_CANISTER.get() : item.get(), gas);
    }
}
