package gascraft.compat;
import gascraft.ModContent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;

/** Keeps the optional accessory API out of class loading when it is absent. */
public final class AccessoryCompat {
    public static boolean equipped(LivingEntity wearer, Item item) {
        return ModList.get().isLoaded("curios") && CuriosCompat.equipped(wearer, item);
    }
    public static void initialize() { if (ModList.get().isLoaded("curios")) CuriosCompat.initialize(); }
    private AccessoryCompat() {}
}
