package gascraft.item;

import gascraft.ModContent;
import gascraft.entity.GrenadeEntity;
import gascraft.menu.GrenadeLauncherMenu;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** A charged grenade launcher whose FE and installed efficiency upgrades travel with its stack. */
public final class GrenadeLauncherItem extends Item {
    public static final float LAUNCH_SPEED = 3.0F;
    public static final int ENERGY_CAPACITY = 1_000_000, CHARGE_RATE = 100_000, BASE_SHOT_COST = 100_000, MAX_EFFICIENCY = 9;

    public GrenadeLauncherItem(Properties properties) { super(properties.stacksTo(1)); }

    public static int storedEnergy(ItemStack launcher) { return Math.clamp(launcher.getOrDefault(ModContent.LAUNCHER_ENERGY, 0), 0, ENERGY_CAPACITY); }
    public static int efficiency(ItemStack launcher) { return Math.clamp(launcher.getOrDefault(ModContent.LAUNCHER_EFFICIENCY, 0), 0, MAX_EFFICIENCY); }
    public static int shotCost(ItemStack launcher) {
        return Math.max(1, (int)Math.ceil(BASE_SHOT_COST / Math.pow(gascraft.machine.ProcessingBalance.UPGRADE_MULTIPLIER, efficiency(launcher))));
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable("gui.gascraft.machine.energy", storedEnergy(stack), ENERGY_CAPACITY));
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack launcher = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
                player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new GrenadeLauncherMenu(id, inventory, slot),
                    launcher.getHoverName()), data -> data.writeVarInt(slot));
            }
            return InteractionResultHolder.sidedSuccess(launcher, level.isClientSide);
        }
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.success(launcher);
        ItemStack ammunition = findFirstUsableGrenade(player);
        if (ammunition.isEmpty()) return InteractionResultHolder.fail(launcher);
        boolean creative = player.getAbilities().instabuild;
        int cost = shotCost(launcher);
        if (!creative && storedEnergy(launcher) < cost) return InteractionResultHolder.fail(launcher);
        GrenadeEntity grenade = new GrenadeEntity(server, player, ammunition.copyWithCount(1), GrenadeItem.FUSE_TICKS, !creative);
        grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, LAUNCH_SPEED, 1);
        if (!server.addFreshEntity(grenade)) return InteractionResultHolder.fail(launcher);
        if (!creative) {
            ammunition.shrink(1);
            launcher.set(ModContent.LAUNCHER_ENERGY, storedEnergy(launcher) - cost);
        }
        player.getInventory().setChanged();
        server.playSound(null, player.blockPosition(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.5F, 0.8F);
        return InteractionResultHolder.consume(launcher);
    }

    /** Inventory index order: hotbar, main storage, armor, then offhand. Gas components are preserved. */
    public static ItemStack findFirstUsableGrenade(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof GrenadeItem && GasContainerItem.hasContents(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }
}
