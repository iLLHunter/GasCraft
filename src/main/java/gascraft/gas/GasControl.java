package gascraft.gas;

import gascraft.api.SicknessState;
import java.lang.ref.WeakReference;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.*;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

/** Gas-specific control follows the existing persistent eye irritation, including serialized copies. */
public final class GasControl {
    private static final ResourceLocation SLOW = GasCatalog.id("gas_irritation_slow");
    private static final Map<Mob, WeakReference<MustardWanderGoal>> WANDERING = new WeakHashMap<>();
    private static final String RAID_GLOW_BASELINE = "gascraft:raid_outline_previous_glowing_tag";

    private static boolean isNonBossControlTarget(LivingEntity entity) {
        return (entity instanceof Player || entity instanceof Mob) && !(entity instanceof EnderDragon || entity instanceof WitherBoss);
    }
    private static SicknessState irritation(LivingEntity entity) {
        return SicknessStateStore.readSicknessEffects(entity).get(GasCatalog.id("chemical_eye_irritation"));
    }
    private static boolean irritationFromGas(SicknessState effect, String gas) {
        return effect != null && effect.getSeverity() > 0 && effect.comesFromGas(GasCatalog.id(gas).toString());
    }
    public static boolean blocksTarget(LivingEntity entity, LivingEntity target) {
        if (target == null || !isNonBossControlTarget(entity) || GasExposure.exemptPlayer(entity)) return false;
        SicknessState eye = irritation(entity);
        return irritationFromGas(eye, "mustard_gas") || target instanceof Player && (irritationFromGas(eye, "tear_gas") || irritationFromGas(eye, "raid_gas"));
    }
    public static void onGasTargetChange(LivingChangeTargetEvent event) {
        if (blocksTarget(event.getEntity(), event.getNewAboutToBeSetTarget())) event.setNewAboutToBeSetTarget(null);
    }
    public static void clearGasControlEffects(LivingEntity entity) {
        updateRaidGlow(entity, false);
        updateSpeed(entity.getAttribute(Attributes.MOVEMENT_SPEED), 0);
        updateSpeed(entity.getAttribute(Attributes.FLYING_SPEED), 0);
        if (entity instanceof Mob mob) {
            WeakReference<MustardWanderGoal> reference = WANDERING.remove(mob);
            MustardWanderGoal goal = reference == null ? null : reference.get();
            if (goal != null) { mob.goalSelector.removeGoal(goal); goal.stop(); }
        }
    }
    public static void tick(LivingEntity entity, boolean mustardDamageApplied) {
        SicknessState eye = irritation(entity);
        boolean eligible = isNonBossControlTarget(entity) && !GasExposure.exemptPlayer(entity);
        boolean tear = eligible && (irritationFromGas(eye, "tear_gas") || irritationFromGas(eye, "raid_gas")), mustard = eligible && irritationFromGas(eye, "mustard_gas");
        updateRaidGlow(entity, entity.isAlive() && entity instanceof net.minecraft.world.entity.monster.Enemy && irritationFromGas(eye, "raid_gas"));
        double slow = tear && mustard ? .25 : mustard ? .15 : tear ? .10 : 0;
        updateSpeed(entity.getAttribute(Attributes.MOVEMENT_SPEED), slow);
        updateSpeed(entity.getAttribute(Attributes.FLYING_SPEED), slow);
        if (entity instanceof Mob mob) {
            if (blocksTarget(mob, mob.getTarget())) mob.setTarget(null);
            var brain = mob.getBrain();
            if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT)
                    && blocksTarget(mob, brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null))) {
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
            WeakReference<MustardWanderGoal> reference = WANDERING.get(mob);
            MustardWanderGoal goal = reference == null ? null : reference.get();
            if (mustard && !mob.isNoAi()) {
                if (goal == null) {
                    goal = new MustardWanderGoal(mob);
                    WANDERING.put(mob, new WeakReference<>(goal)); mob.goalSelector.addGoal(0, goal);
                }
                goal.enforceNavigation();
            } else if (goal != null) {
                mob.goalSelector.removeGoal(goal); goal.stop(); WANDERING.remove(mob);
            }
        }
        if (!mustardDamageApplied && SicknessStateStore.sicknessSeverity(entity, "mustard_lung_damage") > 0) {
            for (var agent : GasCatalog.gasDefinition(GasCatalog.id("mustard_gas")).agents()) {
                if (agent.factory().equals("damage")) entity.hurt(GasDamage.pulmonaryGasDamage(entity, GasCatalog.gasDefinition(GasCatalog.id("mustard_gas"))), agent.damage() * agent.potency());
            }
        }
    }
    private static void updateRaidGlow(LivingEntity entity, boolean active) {
        var data = entity.getPersistentData();
        if (active) {
            if (!data.contains(RAID_GLOW_BASELINE)) {
                // Capture the native tag separately from external Glowing potion effects.
                var snapshot = new net.minecraft.nbt.CompoundTag();
                entity.saveWithoutId(snapshot);
                data.putBoolean(RAID_GLOW_BASELINE, snapshot.getBoolean("Glowing"));
            }
            entity.setGlowingTag(true);
        } else if (data.contains(RAID_GLOW_BASELINE)) {
            entity.setGlowingTag(data.getBoolean(RAID_GLOW_BASELINE));
            data.remove(RAID_GLOW_BASELINE);
        }
    }
    public static void clearRaidOutline(LivingEntity entity) { updateRaidGlow(entity, false); }
    private static void updateSpeed(AttributeInstance attribute, double slow) {
        if (attribute == null) return;
        AttributeModifier current = attribute.getModifier(SLOW);
        if (current != null && current.amount() == -slow) return;
        if (current != null) attribute.removeModifier(SLOW);
        if (slow > 0) attribute.addTransientModifier(new AttributeModifier(SLOW, -slow, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    private static final class MustardWanderGoal extends Goal {
        private final Mob mob;
        private Vec3 destination;
        private int nextDestination, nextStumble;
        private boolean wroteWalkTarget;
        MustardWanderGoal(Mob mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return irritationFromGas(irritation(mob), "mustard_gas"); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { nextDestination = 0; nextStumble = 0; }
        @Override public void tick() {
            if (--nextDestination <= 0) {
                destination = mob instanceof PathfinderMob pathfinder ? DefaultRandomPos.getPos(pathfinder, 3, 2) : null;
                if (destination == null) destination = mob.position().add(mob.getRandom().nextInt(7) - 3, 0, mob.getRandom().nextInt(7) - 3);
                nextDestination = 20;
            }
            enforceNavigation();
            if (--nextStumble <= 0) {
                Vec3 movement = mob.getDeltaMovement();
                if (movement.horizontalDistanceSqr() > .0001) {
                    Vec3 sideways = new Vec3(-movement.z, 0, movement.x).normalize().scale(mob.getRandom().nextBoolean() ? .04 : -.04);
                    mob.push(sideways.x, 0, sideways.z);
                }
                nextStumble = 5;
            }
        }
        void enforceNavigation() {
            if (destination == null) return;
            mob.getLookControl().setLookAt(destination.x, destination.y, destination.z);
            if (mob.getNavigation().isDone() || mob.getNavigation().getTargetPos() == null
                    || !mob.getNavigation().getTargetPos().equals(net.minecraft.core.BlockPos.containing(destination))) {
                mob.getNavigation().moveTo(destination.x, destination.y, destination.z, 1);
            }
            // Flying and hopping mobs can have movement controllers that do not follow navigation paths.
            if (mob.getNavigation().isDone()) {
                mob.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, 1);
                Vec3 direction = destination.subtract(mob.position());
                Vec3 movement = mob.getDeltaMovement();
                if (direction.horizontalDistanceSqr() > .1 && movement.horizontalDistanceSqr() > .0001) {
                    Vec3 horizontal = new Vec3(direction.x, 0, direction.z).normalize().scale(movement.horizontalDistance());
                    mob.setDeltaMovement(horizontal.x, movement.y, horizontal.z);
                }
            }
            var brain = mob.getBrain();
            if (brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
                brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(destination, 1, 0)); wroteWalkTarget = true;
            }
        }
        @Override public void stop() {
            mob.getNavigation().stop();
            if (wroteWalkTarget) mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            destination = null; wroteWalkTarget = false;
        }
    }
    private GasControl() {}
}
