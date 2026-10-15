package gascraft.item;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
public final class ItemTransfers {
    public static ItemStack replaceOne(ItemStack source, LivingEntity user, ItemStack replacement) {
        source.shrink(1);
        if (source.isEmpty()) return replacement;
        if (user instanceof Player player) { if (!player.getInventory().add(replacement)) player.drop(replacement,false); }
        else user.spawnAtLocation(replacement);
        return source;
    }
    private ItemTransfers() {}
}
