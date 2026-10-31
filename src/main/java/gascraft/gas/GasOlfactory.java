package gascraft.gas;

import gascraft.Gascraft;
import gascraft.GascraftConfig;
import gascraft.entity.GasCloudEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/** Olfactory nausea and its matching, finite loss of targeting. */
@EventBusSubscriber(modid = Gascraft.MOD_ID)
public final class GasOlfactory {
    private static final String TARGET_BLOCK_UNTIL = "gascraft:olfactory_target_block_until";
    private static final ThreadLocal<LivingEntity> APPLYING = new ThreadLocal<>();

    public static boolean isBoss(LivingEntity entity) {
        return entity instanceof EnderDragon || entity instanceof WitherBoss || entity.getType().is(Tags.EntityTypes.BOSSES);
    }
    public static boolean blocksTarget(LivingEntity entity) {
        return entity instanceof Mob && !isBoss(entity) && entity.hasEffect(MobEffects.CONFUSION)
            && entity.getPersistentData().getLong(TARGET_BLOCK_UNTIL) > entity.level().getGameTime();
    }
    public static void apply(LivingEntity entity, int duration) {
        if (duration <= 0 || !entity.isAlive() || GasExposure.exemptPlayer(entity)) return;
        APPLYING.set(entity);
        try {
            var nausea = new MobEffectInstance(MobEffects.CONFUSION, duration, 0);
            if (!entity.addEffect(nausea)) {
                // Dragon and wither override addEffect itself. Use the native forced path,
                // preserving longer/stronger existing effects and the applicability event.
                var active = entity.getEffect(MobEffects.CONFUSION);
                if (active == null) entity.forceAddEffect(nausea, null);
                else {
                    var extended = new MobEffectInstance(active);
                    if (extended.update(nausea)) entity.forceAddEffect(extended, null);
                }
            }
        }
        finally { APPLYING.remove(); }
        if (entity instanceof Mob mob && !isBoss(entity) && entity.hasEffect(MobEffects.CONFUSION)) {
            entity.getPersistentData().putLong(TARGET_BLOCK_UNTIL, Math.max(entity.getPersistentData().getLong(TARGET_BLOCK_UNTIL), entity.level().getGameTime() + duration));
            mob.setTarget(null);
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void allowOlfactoryNausea(MobEffectEvent.Applicable event) {
        if (APPLYING.get() == event.getEntity() && event.getEffectInstance().getEffect().equals(MobEffects.CONFUSION)
                && event.getResult() == MobEffectEvent.Applicable.Result.DEFAULT)
            event.setResult(MobEffectEvent.Applicable.Result.APPLY);
    }
    public static void release(GasCloudEntity cloud) {
        if (!cloud.olfactoryActive()) return;
        for (var entity : cloud.level().getEntitiesOfClass(LivingEntity.class, cloud.getBoundingBox().inflate(GascraftConfig.GAS_RANGE_BLOCKS.get()))) {
            if (!cloud.gasAreaContains(entity.position()) || !GasParalysis.acceptsExposure(entity, cloud.gasContents())) continue;
            for (var agent : cloud.gasContents().agents()) if (agent.factory().equals("olfactory")) apply(entity, agent.duration());
        }
    }
    public static void tick(LivingEntity entity) {
        if (!entity.hasEffect(MobEffects.CONFUSION) || entity.getPersistentData().getLong(TARGET_BLOCK_UNTIL) <= entity.level().getGameTime()) clear(entity);
    }
    public static void clear(LivingEntity entity) { entity.getPersistentData().remove(TARGET_BLOCK_UNTIL); }
    private GasOlfactory() {}
}
