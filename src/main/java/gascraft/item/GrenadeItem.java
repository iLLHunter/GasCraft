package gascraft.item;

import gascraft.*;
import gascraft.entity.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class GrenadeItem extends GasContainerItem implements ProjectileItem {
    public static final int FUSE_TICKS = 60;
    public GrenadeItem(Properties properties) { super(properties); }
    @Override public net.minecraft.world.entity.projectile.Projectile asProjectile(Level level, net.minecraft.core.Position position, ItemStack stack, net.minecraft.core.Direction direction) {
        return new GrenadeEntity(level, new Vec3(position.x(), position.y(), position.z()), stack);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!hasContents(stack)) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return FUSE_TICKS; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BLOCK; }
    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!hasContents(stack)) return;
        if (!(level instanceof ServerLevel server)) return;
        ItemStack thrown = stack.copyWithCount(1);
        boolean creative = entity instanceof Player player && player.getAbilities().instabuild;
        if (!creative) stack.shrink(1);
        GrenadeEntity grenade = new GrenadeEntity(server, entity, thrown, Math.max(1, Math.min(FUSE_TICKS, timeLeft)), !creative);
        grenade.shootFromRotation(entity, entity.getXRot(), entity.getYRot(), 0, 1.5F, 1);
        server.addFreshEntity(grenade);
        server.playSound(null, entity.blockPosition(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.5F, 0.8F);
    }
    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!hasContents(stack)) return stack;
        if (level instanceof ServerLevel server) spawnGasCloud(server, entity.position(), stack, entity);
        ItemStack empty = createEmptyGrenadeShell(stack);
        if (level.isClientSide) return stack;
        return ItemTransfers.replaceOne(stack, entity, empty);
    }
    public static ItemStack createEmptyGrenadeShell(ItemStack stack) {
        ItemStack empty = new ItemStack(ModContent.GRENADE_SHELL.get());
        return empty;
    }
    @javax.annotation.Nullable
    public static GasCloudEntity spawnGasCloud(ServerLevel level, Vec3 position, ItemStack stack, net.minecraft.world.entity.Entity emitter) {
        if (!hasContents(stack)) return null;
        GasCloudEntity cloud = new GasCloudEntity(level, stack, GascraftConfig.CLOUD_LIFETIME_TICKS.get());
        cloud.setPos(position);
        cloud.setEmitter(emitter);
        if (!cloud.instantPulse()) level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, position.x, position.y, position.z, 20, .5, .5, .5, .2);
        cloud.onCloudReleased();
        level.addFreshEntity(cloud);
        return cloud;
    }
}
