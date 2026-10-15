package gascraft.entity;

import gascraft.*;
import gascraft.gas.GasCatalog;
import gascraft.gas.TimedCloudEffect;
import gascraft.item.GasContainerItem;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class GasCloudEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> CONTENTS = SynchedEntityData.defineId(GasCloudEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(GasCloudEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFETIME = SynchedEntityData.defineId(GasCloudEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> TIMED = SynchedEntityData.defineId(GasCloudEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DISPLAY_COLOR = SynchedEntityData.defineId(GasCloudEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FADE_START = SynchedEntityData.defineId(GasCloudEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FADE_DURATION = SynchedEntityData.defineId(GasCloudEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> INSTANT_PUFF = SynchedEntityData.defineId(GasCloudEntity.class, EntityDataSerializers.BOOLEAN);
    private List<TimedCloudEffect> timedEffects = List.of();
    private int nextTimedEffect;
    private boolean releasePuffEmitted;
    @FunctionalInterface public interface CloudParticleSpawner {
        void spawn(GasCloudEntity cloud, ColorParticleOption options, double x, double y, double z);
    }
    public static CloudParticleSpawner clientParticleSpawner = (cloud, options, x, y, z) -> cloud.level().addParticle(options, x, y, z, 0, 0, 0);
    private UUID emitter;
    private Map<BlockPos, Integer> paths = Map.of();
    private long pathTime = Long.MIN_VALUE;
    private BlockPos pathOrigin = BlockPos.ZERO;
    private int pathRange;
    public GasCloudEntity(EntityType<? extends GasCloudEntity> type, Level level) { super(type, level); noPhysics = true; }
    public GasCloudEntity(ServerLevel level, ItemStack contents, int lifetime) {
        this(ModContent.GAS_CLOUD.get(), level);
        entityData.set(CONTENTS, contents.copyWithCount(1));
        entityData.set(LIFETIME, Math.max(1, lifetime));
        timedEffects = gasContents().timedEffects();
        entityData.set(TIMED, !timedEffects.isEmpty());
        entityData.set(INSTANT_PUFF, timedEffects.stream().anyMatch(effect -> effect.type().equals("instant_potion")));
        entityData.set(DISPLAY_COLOR, gasContents().color());
        long trigger = 0;
        for (var effect : timedEffects) {
            trigger += effect.delayTicks();
            if (effect.type().equals("fade")) {
                entityData.set(LIFETIME, (int)Math.min(cloudLifetime(), trigger + effect.durationTicks()));
                break;
            }
            if (effect.type().equals("end")) {
                entityData.set(LIFETIME, (int)Math.min(cloudLifetime(), Math.max(1, trigger)));
                break;
            }
        }
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CONTENTS, ItemStack.EMPTY);
        builder.define(AGE, 0); builder.define(LIFETIME, 600);
        builder.define(TIMED, false); builder.define(DISPLAY_COLOR, 0);
        builder.define(FADE_START, -1); builder.define(FADE_DURATION, 0);
        builder.define(INSTANT_PUFF, false);
    }
    public GasCatalog.GasDefinition gasContents() { return GasContainerItem.containedGas(entityData.get(CONTENTS)); }
    public PotionContents potionContents() { return entityData.get(CONTENTS).getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY); }
    public int cloudAge() { return entityData.get(AGE); }
    public int cloudLifetime() { return entityData.get(LIFETIME); }
    public boolean hasTimedEffects() { return entityData.get(TIMED); }
    public boolean instantPulse() { return entityData.get(INSTANT_PUFF); }
    public void onCloudReleased() {
        if (level() instanceof ServerLevel server) {
            runTimedEffects(server);
            if (paralysisActive()) gascraft.gas.GasParalysis.refreshCloud(this);
        }
    }
    public boolean paralysisActive() {
        if (isRemoved()) return false;
        long trigger = 0;
        for (var effect : timedEffects) {
            trigger += effect.delayTicks();
            if (effect.type().equals("paralysis") && cloudAge() >= trigger && cloudAge() < trigger + effect.durationTicks()) return true;
        }
        return false;
    }
    /** The reachable footprint, independent of visual age or concentration. */
    public boolean gasAreaContains(Vec3 point) {
        int range = GascraftConfig.GAS_RANGE_BLOCKS.get();
        if (point.distanceToSqr(position()) >= range * range) return false;
        double distance = GascraftConfig.STRAIGHT_LINE_PROPAGATION.get() ? point.distanceTo(position()) : gasPathDistance(BlockPos.containing(point), range);
        return distance >= 0 && distance < range;
    }
    public int displayColor() { return hasTimedEffects() ? entityData.get(DISPLAY_COLOR) : gasContents().color(); }
    public float fadeOpacity() {
        int start = entityData.get(FADE_START), duration = entityData.get(FADE_DURATION);
        return start < 0 || duration == 0 ? 1 : Math.max(0, 1 - (cloudAge() - start) / (float)duration);
    }
    public int completedTimedEffects() { return nextTimedEffect; }
    public void setEmitter(Entity entity) { emitter = entity == null ? null : entity.getUUID(); }
    public void detachEmitter(Entity source) {
        if (!source.getUUID().equals(emitter)) return;
        if (source.level() == level()) setPos(source.position());
        emitter = null;
    }
    public double gasConcentrationAt(Vec3 eye) {
        int range = GascraftConfig.GAS_RANGE_BLOCKS.get();
        if (cloudAge() >= cloudLifetime() || eye.distanceToSqr(position()) >= range * range) return 0;
        double distance = GascraftConfig.STRAIGHT_LINE_PROPAGATION.get() ? eye.distanceTo(position()) : gasPathDistance(BlockPos.containing(eye), range);
        return distance < 0 ? 0 : Math.max(0, (1.0 - cloudAge() / (double) cloudLifetime()) * (1.0 - distance / range));
    }
    public int gasPathDistance(BlockPos target, int range) {
        BlockPos origin = blockPosition();
        if (!origin.equals(pathOrigin) || range != pathRange || level().getGameTime() - pathTime >= 10 || pathTime == Long.MIN_VALUE) {
            pathOrigin = origin; pathRange = range; pathTime = level().getGameTime();
            Map<BlockPos, Integer> distance = new HashMap<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            distance.put(origin, 0); queue.add(origin);
            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                int cost = distance.get(current) + 1;
                if (cost > range) continue;
                for (Direction direction : Direction.values()) {
                    BlockPos next = current.relative(direction);
                    if (distance.containsKey(next) || !level().hasChunkAt(next) || !level().getFluidState(next).isEmpty() || !level().getBlockState(next).getCollisionShape(level(), next).isEmpty()) continue;
                    distance.put(next, cost); queue.addLast(next);
                }
            }
            paths = distance;
        }
        return paths.getOrDefault(target, -1);
    }
    @Override public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            if (!GasContainerItem.hasContents(entityData.get(CONTENTS))) { discard(); return; }
            if (emitter != null) {
                Entity source = server.getEntity(emitter);
                if (source instanceof LivingEntity living && !living.isAlive()) detachEmitter(source);
                else if (source != null) setPos(source.position());
            }
            entityData.set(AGE, cloudAge() + 1);
            runTimedEffects(server);
            if (cloudAge() >= cloudLifetime()) discard();
            gascraft.gas.GasParalysis.refreshAffected(this);
        } else if (instantPulse() ? !releasePuffEmitted : tickCount % 10 == 0) {
            releasePuffEmitted = true;
            int color = displayColor();
            if ((color >>> 24) == 0) return;
            var particle = switch (gasContents().particleType()) { case "MISTED_MEAT" -> ModContent.MISTED_MEAT_PARTICLE.get(); case "SMOKE" -> ModContent.SMOKE_PUFF_PARTICLE.get(); default -> ModContent.GAS_MIST_PARTICLE.get(); };
            int amount = instantPulse() ? 20 : gasContents().particleType().equals("GAS") ? 1 : 5;
            float radius = Math.max(1, GascraftConfig.GAS_RANGE_BLOCKS.get() - 2);
            for (int index = 0; index < amount; index++) clientParticleSpawner.spawn(this, ColorParticleOption.create(particle, color), getX() + random.nextGaussian() * radius % radius, getY() + random.nextGaussian() * 2 % 2, getZ() + random.nextGaussian() * radius % radius);
        }
    }
    private void runTimedEffects(ServerLevel server) {
        long trigger = 0;
        for (int index = 0; index < timedEffects.size(); index++) {
            var effect = timedEffects.get(index);
            trigger += effect.delayTicks();
            if (index < nextTimedEffect) continue;
            if (cloudAge() < trigger) break;
            nextTimedEffect = index + 1;
            switch (effect.type()) {
                case "detonation" -> detonate(server, effect);
                case "instant_potion" -> instantPotion(server, effect);
                case "flash" -> gascraft.gas.GasFlash.release(server, this, effect.durationTicks());
                case "paralysis" -> {} // Its active window is checked before each living entity tick.
                case "melee_damage" -> meleeDamage(server, effect);
                case "end" -> discard();
                case "color" -> entityData.set(DISPLAY_COLOR, effect.color());
                case "fade" -> {
                    entityData.set(FADE_START, (int)trigger);
                    entityData.set(FADE_DURATION, effect.durationTicks());
                }
                default -> throw new IllegalStateException("Unknown timed cloud effect " + effect.type());
            }
        }
    }
    private void meleeDamage(ServerLevel server, TimedCloudEffect effect) {
        int range = GascraftConfig.GAS_RANGE_BLOCKS.get();
        Entity source = emitter == null ? null : server.getEntity(emitter);
        Entity owner = source instanceof net.minecraft.world.entity.projectile.Projectile projectile ? projectile.getOwner()
            : source instanceof LivingEntity ? source : null;
        for (var target : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range),
            target -> target.isAlive() && (target instanceof Mob || target instanceof net.minecraft.world.entity.player.Player))) {
            if (!gasAreaContains(target.position()) || !gascraft.gas.GasParalysis.acceptsExposure(target, gasContents())) continue;
            var damage = gascraft.gas.GasDamage.tetrodotoxinDamage(target, this, owner);
            if (target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon) dragon.hurt(dragon.head, damage, effect.damage());
            else target.hurt(damage, effect.damage());
        }
    }
    private void instantPotion(ServerLevel server, TimedCloudEffect effect) {
        int range = GascraftConfig.GAS_RANGE_BLOCKS.get();
        Entity source = emitter == null ? null : server.getEntity(emitter);
        Entity owner = source instanceof net.minecraft.world.entity.projectile.Projectile projectile ? projectile.getOwner()
            : source instanceof LivingEntity ? source : null;
        var potion = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(net.minecraft.resources.ResourceLocation.parse(effect.potion())).orElseThrow();
        if (!potion.value().isInstantenous()) throw new IllegalArgumentException("Timed instant_potion requires an instant effect: " + effect.potion());
        for (var target : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range),
            target -> target.isAlive() && (target instanceof Mob || target instanceof net.minecraft.world.entity.player.Player))) {
            if (gasConcentrationAt(target.position()) > 0)
                gascraft.gas.GasExposure.applyInstantPotionPulse(target, gasContents(), potion, effect.amplifier(), this, owner);
        }
    }
    private void detonate(ServerLevel server, TimedCloudEffect effect) {
        int range = GascraftConfig.GAS_RANGE_BLOCKS.get();
        Entity source = emitter == null ? null : server.getEntity(emitter);
        Entity owner = source instanceof net.minecraft.world.entity.projectile.Projectile projectile ? projectile.getOwner()
            : source instanceof LivingEntity ? source : null;
        var damageSource = server.damageSources().explosion(this, owner);
        for (var target : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range),
            target -> target.isAlive() && (target instanceof Mob || target instanceof net.minecraft.world.entity.player.Player))) {
            if (gascraft.gas.GasExposure.exemptPlayer(target) || gasConcentrationAt(target.position()) <= 0) continue;
            // The configured timer is exact; native fire immunity, extinguishing and damage protections still apply.
            if (effect.fireTicks() > 0) target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), effect.fireTicks()));
            if (effect.damage() > 0) target.hurt(damageSource, effect.damage());
            if (effect.knockbackLevel() > 0) {
                target.knockback(effect.knockbackLevel() * 0.5, getX() - target.getX(), getZ() - target.getZ());
                target.hurtMarked = true;
            }
        }
        // Only entities receive gameplay effects; the fireball never destroys or ignites blocks.
        server.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
            net.minecraft.sounds.SoundSource.BLOCKS, 4, 1);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        server.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), Math.min(512, range * range * 4), range * .5, range * .25, range * .5, .05);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("gas_contents", entityData.get(CONTENTS).saveOptional(registryAccess()));
        tag.putInt("age_ticks", cloudAge()); tag.putInt("lifetime_ticks", cloudLifetime());
        tag.putString("timed_effects", TimedCloudEffect.toJson(timedEffects).toString());
        tag.putInt("next_timed_effect", nextTimedEffect);
        tag.putInt("display_color", displayColor());
        tag.putInt("fade_start", entityData.get(FADE_START)); tag.putInt("fade_duration", entityData.get(FADE_DURATION));
        if (emitter != null) tag.putUUID("emitter_uuid", emitter);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(CONTENTS, ItemStack.parseOptional(registryAccess(), tag.getCompound("gas_contents")));
        entityData.set(AGE, Math.max(0, tag.getInt("age_ticks")));
        entityData.set(LIFETIME, Math.max(1, tag.getInt("lifetime_ticks")));
        timedEffects = tag.contains("timed_effects") ? TimedCloudEffect.parseEffects(com.google.gson.JsonParser.parseString(tag.getString("timed_effects")).getAsJsonArray()) : List.of();
        nextTimedEffect = Math.max(0, Math.min(timedEffects.size(), tag.getInt("next_timed_effect")));
        entityData.set(TIMED, !timedEffects.isEmpty()); entityData.set(DISPLAY_COLOR, tag.getInt("display_color"));
        entityData.set(INSTANT_PUFF, timedEffects.stream().anyMatch(effect -> effect.type().equals("instant_potion")));
        entityData.set(FADE_START, tag.contains("fade_start") ? tag.getInt("fade_start") : -1);
        entityData.set(FADE_DURATION, Math.max(0, tag.getInt("fade_duration")));
        emitter = tag.hasUUID("emitter_uuid") ? tag.getUUID("emitter_uuid") : null;
    }
}
