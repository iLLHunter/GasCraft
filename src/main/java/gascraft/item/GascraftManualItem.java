package gascraft.item;

import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A read-only guide; the vanilla book viewer lays out the current client language. */
public final class GascraftManualItem extends Item {
    public static Runnable clientOpener = () -> {};
    public GascraftManualItem(Properties properties) { super(properties.stacksTo(16)); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if(!(level.isClientSide?gascraft.GascraftConfig.clientManualEnabled():gascraft.GascraftConfig.manualEnabled()))return InteractionResultHolder.fail(player.getItemInHand(hand));
        if (level.isClientSide) clientOpener.run();
        else player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
