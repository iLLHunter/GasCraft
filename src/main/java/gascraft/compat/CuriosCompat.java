package gascraft.compat;
import gascraft.ModContent;
import gascraft.gas.GasExposure;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import top.theillusivec4.curios.api.*;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class CuriosCompat {
    public static boolean equipped(LivingEntity wearer, Item item) {
        return CuriosApi.getCuriosInventory(wearer).map(h -> {
            for (var entry : h.getCurios().entrySet()) {
                var stacks=entry.getValue().getStacks();
                for (int slot=0;slot<stacks.getSlots();slot++) if (h.isSlotActive(entry.getKey(),slot) && stacks.getStackInSlot(slot).is(item)) return true;
            }
            return false;
        }).orElse(false);
    }
    public static void initialize() {
        CuriosApi.registerCurio(ModContent.GRENADE_HOLDER.get(), new ICurioItem() {
            public void curioTick(SlotContext context, ItemStack stack) {
                if (!context.entity().level().isClientSide && context.entity() instanceof Player player) GasExposure.updateGrenadeHolder(player);
            }
            public void onUnequip(SlotContext context, ItemStack newStack, ItemStack stack) {
                // The player tick sees the updated slot and splits surplus stacks once the belt is absent.
            }
        });
        CuriosApi.registerCurio(ModContent.GAS_MASK.get(), new ICurioItem() {});
    }
    private CuriosCompat() {}
}
