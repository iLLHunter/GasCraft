package gascraft.entity;

import gascraft.ModContent;
import gascraft.item.GrenadeItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A reusable grenade whose remaining fuse follows the time spent holding it. */
public final class GrenadeEntity extends Projectile implements ItemSupplier {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> FUSE = SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.INT);
    private int cloudLifetime;
    private boolean recoverable;
    private int ignoreCollisionTicks;
    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) { super(type, level); }
    public GrenadeEntity(ServerLevel level, LivingEntity owner, ItemStack stack, int fuse, boolean recoverable) {
        this(ModContent.GRENADE_ENTITY.get(), level);
        setOwner(owner);
        setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        ItemStack item = stack.copyWithCount(1); item.set(ModContent.PIN_REMOVED, true);
        entityData.set(ITEM, item);
        entityData.set(FUSE, fuse);
        this.recoverable = recoverable;
    }
    public GrenadeEntity(Level level, Vec3 position, ItemStack stack) {
        this(ModContent.GRENADE_ENTITY.get(), level);
        setPos(position);
        ItemStack item = stack.copyWithCount(1); item.set(ModContent.PIN_REMOVED, true);
        entityData.set(ITEM, item);
        recoverable = true;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ITEM, new ItemStack(ModContent.CHEMICAL_GRENADE.get()));
        builder.define(FUSE, 60);
    }
    @Override public ItemStack getItem() { return entityData.get(ITEM); }
    public int remainingFuseTicks() { return entityData.get(FUSE); }
    @Override public void tick() {
        super.tick();
        if (!gascraft.item.GasContainerItem.hasContents(getItem())) {
            if (!level().isClientSide) {
                if (recoverable) spawnAtLocation(GrenadeItem.createEmptyGrenadeShell(getItem()));
                discard();
            }
            return;
        }
        Vec3 movement = getDeltaMovement();
        var hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit instanceof net.minecraft.world.phys.EntityHitResult) movement = movement.scale(-.05);
        else if (hit instanceof net.minecraft.world.phys.BlockHitResult block && level().getBlockState(block.getBlockPos()).isCollisionShapeFullBlock(level(), block.getBlockPos()) && ignoreCollisionTicks-- <= 0) {
            var normal = block.getDirection().getNormal();
            movement = new Vec3(movement.x * (normal.getX() * -.4 + .2), movement.y * (normal.getY() * -.4 + .2), movement.z * (normal.getZ() * -.4 + .2));
            movement = new Vec3(Math.abs(movement.x) < .2 ? 0 : movement.x, Math.abs(movement.y) < .3 ? 0 : movement.y, Math.abs(movement.z) < .2 ? 0 : movement.z);
            ignoreCollisionTicks = 2;
        }
        move(MoverType.SELF, movement);
        setDeltaMovement(movement.scale(isInWater() ? .8 : .99).add(0, -.05, 0));
        if (!(level() instanceof ServerLevel server)) return;
        int fuse = remainingFuseTicks();
        if (fuse == 1) {
            var cloud = GrenadeItem.spawnGasCloud(server, position().add(0, 0.1, 0), getItem(), this);
            cloudLifetime = cloud == null ? 0 : cloud.cloudLifetime();
        }
        entityData.set(FUSE, fuse - 1);
        if (fuse <= -cloudLifetime) {
            if (recoverable) spawnAtLocation(GrenadeItem.createEmptyGrenadeShell(getItem()));
            discard();
        }
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("grenade_item", getItem().save(registryAccess()));
        tag.putInt("fuse_ticks", remainingFuseTicks());
        tag.putInt("cloud_lifetime", cloudLifetime);
        tag.putBoolean("recoverable", recoverable);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ItemStack stack = ItemStack.parse(registryAccess(), tag.getCompound("grenade_item")).orElse(new ItemStack(ModContent.CHEMICAL_GRENADE.get()));
        if (!stack.is(ModContent.CHEMICAL_GRENADE.get())) stack = new ItemStack(ModContent.CHEMICAL_GRENADE.get());
        entityData.set(ITEM, stack.copyWithCount(1));
        entityData.set(FUSE, tag.getInt("fuse_ticks"));
        cloudLifetime = Math.max(0, tag.getInt("cloud_lifetime"));
        recoverable = tag.getBoolean("recoverable");
    }
}
