package gascraft.gas;

import gascraft.*;
import gascraft.entity.GasCloudEntity;
import gascraft.api.event.GasExposureEvent;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Server-authoritative physical fields. Cosmetic coverage never gates gameplay coverage. */
@EventBusSubscriber(modid=Gascraft.MOD_ID)
public final class GasFields {
    private static final String DATA="gascraft:field_controls";
    private static final Map<Mob,java.lang.ref.WeakReference<ControlGoal>> GOALS=new WeakHashMap<>();
    private static final net.minecraft.resources.ResourceLocation GRAVITY_JUMP=GasCatalog.id("gravity_jump");
    public static final ResourceKey<net.minecraft.world.damagesource.DamageType> SPECTRAL=ResourceKey.create(Registries.DAMAGE_TYPE,GasCatalog.id("spectral"));
    private static CompoundTag data(LivingEntity e) { return e.getPersistentData().getCompound(DATA); }
    public static boolean undead(LivingEntity e) { return e.getType().is(EntityTypeTags.UNDEAD); }
    private static List<GasCloudEntity> clouds(LivingEntity e) {
        return e.level().getEntitiesOfClass(GasCloudEntity.class,e.getBoundingBox().inflate(GascraftConfig.GAS_RANGE_BLOCKS.get()),c->c.fieldActive()&&c.gasAreaContains(e.position()));
    }
    public static boolean inField(Entity e,String gas) {
        if(!(e instanceof LivingEntity living)||!living.isAlive()||GasExposure.exemptPlayer(living))return false;
        return clouds(living).stream().anyMatch(c->c.gasContents().id().equals(GasCatalog.id(gas)));
    }
    public static boolean blocksMagic(Entity e) { return inField(e,"null_magic_gas"); }
    public static boolean blocksTeleport(Entity e) { if(inField(e,"voidrot_gas"))return true;for(var passenger:e.getIndirectPassengers())if(inField(passenger,"voidrot_gas"))return true;return false; }
    public static boolean gravityHeld(Entity e) { return e instanceof Mob&&!(e instanceof WitherBoss||e instanceof EnderDragon)&&inField(e,"gravity_gas"); }
    public static boolean maskProtects(LivingEntity e,GasCatalog.GasDefinition gas) {
        return NeoForge.EVENT_BUS.post(new GasExposureEvent.GasExposureImmunityEvent(e,GasExposure.breathingHandler(e),gas,1,GasExposure.protectedByMask(e))).isImmune();
    }
    public static boolean paralyzed(LivingEntity e) {
        if(!e.isAlive())return false;
        if(data(e).getLong("stun_until")>e.level().getGameTime())return true;
        return (e instanceof WitherBoss||e instanceof EnderDragon)&&inField(e,"gravity_gas")
            || !GasOlfactory.isBoss(e)&&undead(e)&&inField(e,"misted_sunburst");
    }
    private static boolean dancing(LivingEntity e) {
        long now=e.level().getGameTime();
        for(var tag:data(e).getList("boogie",10)) {
            var state=(CompoundTag)tag;
            if(state.getLong("until")>now) {
                long age=now-state.getLong("start");
                // Entity ticking order may put the cloud one tick ahead of the mob's last refresh.
                if(e.level() instanceof net.minecraft.server.level.ServerLevel level&&state.hasUUID("cloud")&&level.getEntity(state.getUUID("cloud")) instanceof GasCloudEntity cloud)age=cloud.cloudAge();
                if(age<60||age>=100)return true;
            }
        }
        return false;
    }
    public static boolean blocksAttacks(LivingEntity e) { return e instanceof Mob&&!GasOlfactory.isBoss(e)&&(data(e).getLong("fear_until")>e.level().getGameTime()||dancing(e)); }
    public static boolean blocksTarget(LivingEntity e) { return blocksAttacks(e)||e instanceof Mob&&!GasOlfactory.isBoss(e)&&inField(e,"misted_sunburst"); }
    public static boolean cologneControls(LivingEntity e) { return e instanceof Mob mob&&!GasOlfactory.isBoss(e)&&(mob.getAttribute(Attributes.ATTACK_DAMAGE)!=null||mob instanceof net.minecraft.world.entity.monster.RangedAttackMob||mob instanceof net.minecraft.world.entity.monster.Enemy)&&inField(e,"misted_cologne"); }
    public static LivingEntity cologneTarget(LivingEntity e) {
        LivingEntity closest=null;double distance=Double.MAX_VALUE;
        for(var candidate:((net.minecraft.server.level.ServerLevel)e.level()).getAllEntities())if(candidate instanceof LivingEntity t&&t!=e&&t.isAlive()&&(t instanceof Mob||t instanceof Player)&&!(t instanceof Player p&&(p.isCreative()||p.isSpectator()))) {
            double next=e.distanceToSqr(t);if(next<distance){closest=t;distance=next;}
        }
        return closest;
    }
    public static void release(GasCloudEntity cloud) {
        if(!cloud.fieldActive())return;
        for(var e:cloud.level().getEntitiesOfClass(LivingEntity.class,cloud.getBoundingBox().inflate(GascraftConfig.GAS_RANGE_BLOCKS.get())))if(cloud.gasAreaContains(e.position()))refresh(e,cloud);
        GasParalysis.refreshCloud(cloud);
    }
    private static void potion(LivingEntity e,Holder<MobEffect> effect,int ticks,int amp) { e.addEffect(new MobEffectInstance(effect,ticks,amp)); }
    private static void boogieGlow(LivingEntity entity) {
        var incoming=new MobEffectInstance(MobEffects.GLOWING,100,0);
        if(entity.addEffect(incoming)||!GasOlfactory.isBoss(entity))return;
        // Vanilla bosses reject addEffect itself. Preserve stronger/longer external glow.
        var active=entity.getEffect(MobEffects.GLOWING);
        if(active==null)entity.forceAddEffect(incoming,null);
        else {var extended=new MobEffectInstance(active);if(extended.update(incoming))entity.forceAddEffect(extended,null);}
    }
    private static void indicator(LivingEntity e,String name) { var effect=net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(GasCatalog.id(name)).orElseThrow();e.forceAddEffect(new MobEffectInstance(effect,2,0,false,false,true),null); }
    private static void boogie(LivingEntity e,GasCloudEntity c) {
        var states=data(e).getList("boogie",10);long now=e.level().getGameTime();CompoundTag state=null;
        for(var value:states)if(((CompoundTag)value).getUUID("cloud").equals(c.getUUID()))state=(CompoundTag)value;
        if(state==null) {state=new CompoundTag();state.putUUID("cloud",c.getUUID());state.putLong("start",now-c.cloudAge());states.add(state);}
        state.putLong("until",now+100);data(e).put("boogie",states);
        state.putLong("start",now-c.cloudAge());
    }
    public static void refresh(LivingEntity e) { refresh(e,null); }
    private static void refresh(LivingEntity e,GasCloudEntity released) {
        if(e.level().isClientSide)return;
        if(!e.isAlive()||GasExposure.exemptPlayer(e)) {GasFieldPotions.finish(e,Set.of());var jump=e.getAttribute(Attributes.JUMP_STRENGTH);if(jump!=null)jump.removeModifier(GRAVITY_JUMP);restoreGravity(e);e.getPersistentData().remove(DATA);return;}
        long now=e.level().getGameTime();var active=clouds(e);
        if(released!=null&&!active.contains(released)) {active=new ArrayList<>(active);active.add(released);}
        if(active.isEmpty()&&!e.getPersistentData().contains(DATA))return;
        if(!e.getPersistentData().contains(DATA))e.getPersistentData().put(DATA,new CompoundTag());
        var state=data(e);
        boolean nullMagic=active.stream().anyMatch(c->c.gasContents().id().equals(GasCatalog.id("null_magic_gas")));
        if(nullMagic)clearPotions(e);
        Set<Holder<MobEffect>> leases=new HashSet<>();
        boolean gravity=false,espresso=false,cologne=false;
        boolean boss=GasOlfactory.isBoss(e);
        for(var c:active) {
            String gas=c.gasContents().id().getPath();
            switch(gas) {
                case "gravity_gas" -> {
                    gravity=true;
                    if(!nullMagic)GasFieldPotions.apply(e,MobEffects.MOVEMENT_SLOWDOWN,e instanceof Player?0:1,leases);
                    if(e instanceof Mob&&!(e instanceof WitherBoss||e instanceof EnderDragon)) {
                        e.setDeltaMovement(Vec3.ZERO);
                        e.hurtMarked=true;
                    }
                }
                case "misted_sunburst" -> {
                    if(e instanceof Player) {
                        if(!maskProtects(e,c.gasContents())&&c.triggerFieldOnce(e)&&e instanceof net.minecraft.server.level.ServerPlayer p&&p.connection!=null)GasFlash.sunburst(p,c);
                    } else if(e instanceof Mob&&(!boss||undead(e))) {
                        if((!undead(e)||boss)&&c.triggerFieldOnce(e))state.putLong("stun_until",Math.max(state.getLong("stun_until"),now+40));
                        if(undead(e)&&!boss&&!e.fireImmune()&&!e.isInWaterRainOrBubble())e.igniteForSeconds(8);
                    }
                }
                case "soul_ward_gas" -> {
                    if(e instanceof Player)indicator(e,"soul_ward");
                    else if(undead(e)&&!(e instanceof EnderDragon)) {
                        if(c.triggerFieldOnce(e))e.hurt(new DamageSource(e.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SPECTRAL),c),12);
                        indicator(e,"vulnerability");
                    }
                }
                case "null_magic_gas" -> { indicator(e,"null_magic");gascraft.compat.GasMagicCompat.interrupt(e); }
                case "voidrot_gas" -> indicator(e,"voidrot");
                case "misted_moonlight" -> {
                    if(boss||nullMagic)break;
                    if(e instanceof Player) {potion(e,MobEffects.NIGHT_VISION,3600,0);if(e.level().isNight()){potion(e,MobEffects.MOVEMENT_SPEED,3600,0);potion(e,MobEffects.LUCK,3600,0);}}
                    else if(undead(e))potion(e,MobEffects.WEAKNESS,3600,e.level().isNight()?1:0);
                    else if(e instanceof Mob)potion(e,MobEffects.MOVEMENT_SPEED,3600,0);
                }
                case "boogie_gas" -> {
                    if(e instanceof Player&&!nullMagic){potion(e,MobEffects.MOVEMENT_SPEED,100,1);potion(e,MobEffects.JUMP,100,0);potion(e,MobEffects.GLOWING,100,0);}
                    if(e instanceof Mob) {boogie(e,c);if(!nullMagic)boogieGlow(e);}
                }
                case "espresso_cloud" -> {
                    if(boss)break;espresso=true;
                    if(!nullMagic) {
                        if(e instanceof Player){potion(e,MobEffects.MOVEMENT_SPEED,2400,0);potion(e,MobEffects.DIG_SPEED,2400,0);}
                        else GasFieldPotions.apply(e,MobEffects.MOVEMENT_SPEED,0,leases);
                    }
                }
                case "chicken_gas" -> {
                    if(boss)break;
                    if(e instanceof Player) {
                        if(!nullMagic){GasFieldPotions.apply(e,MobEffects.MOVEMENT_SPEED,1,leases);if(!maskProtects(e,c.gasContents()))GasFieldPotions.apply(e,MobEffects.WEAKNESS,0,leases);}
                    } else if(e instanceof Mob) {state.putLong("fear_until",now+1200);if(!nullMagic)potion(e,MobEffects.MOVEMENT_SPEED,1200,1);}
                }
                case "misted_cologne" -> {
                    if(boss)break;
                    if(e instanceof Player){if(!nullMagic)potion(e,MobEffects.LUCK,3600,0);if(!maskProtects(e,c.gasContents()))indicator(e,"vulnerability");}
                    else if(e instanceof Mob mob&&(mob.getAttribute(Attributes.ATTACK_DAMAGE)!=null||mob instanceof net.minecraft.world.entity.monster.RangedAttackMob||mob instanceof net.minecraft.world.entity.monster.Enemy))cologne=true;
                }
                default -> {}
            }
        }
        var jump=e.getAttribute(Attributes.JUMP_STRENGTH);
        if(jump!=null) {jump.removeModifier(GRAVITY_JUMP);if(gravity&&e instanceof Player)jump.addTransientModifier(new AttributeModifier(GRAVITY_JUMP,-.5,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));}
        GasGravity.hold(e,"gravity",gravity&&e instanceof Mob&&!(e instanceof WitherBoss||e instanceof EnderDragon));
        GasFieldPotions.finish(e,leases);
        if(state.getBoolean("espresso_inside")&&!espresso) {
            if(e instanceof Player) {var crashes=state.getLongArray("espresso_crashes");var updated=Arrays.copyOf(crashes,crashes.length+1);updated[crashes.length]=now+2400;state.putLongArray("espresso_crashes",updated);}
            else if(!nullMagic)potion(e,MobEffects.WEAKNESS,600,0);
        }
        state.putBoolean("espresso_inside",espresso);
        var crashes=state.getLongArray("espresso_crashes");
        if(Arrays.stream(crashes).anyMatch(t->t<=now)) {
            if(!nullMagic)potion(e,MobEffects.WEAKNESS,600,0);
            state.putLongArray("espresso_crashes",Arrays.stream(crashes).filter(t->t>now).toArray());
        }
        var danceStates=state.getList("boogie",10);danceStates.removeIf(t->((CompoundTag)t).getLong("until")<=now);state.put("boogie",danceStates);
        if(e instanceof Mob mob) {
            if(blocksTarget(e)){mob.setTarget(null);mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);}
            if(cologne&&!blocksTarget(e)) {
                LivingEntity closest=cologneTarget(e);
                mob.setTarget(closest);
                if(closest!=null)mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET,closest);
            } else if(state.getBoolean("cologne_inside")&&!cologne) {mob.setTarget(null);mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);}
            state.putBoolean("cologne_inside",cologne);
            boolean control=blocksAttacks(e);
            if(control&&!GOALS.containsKey(mob)){var goal=new ControlGoal(mob);GOALS.put(mob,new java.lang.ref.WeakReference<>(goal));mob.goalSelector.addGoal(0,goal);}
            if(!control&&GOALS.containsKey(mob)){var goal=GOALS.remove(mob).get();if(goal!=null){mob.goalSelector.removeGoal(goal);goal.stop();}}
        }
    }
    public static void clearPotions(LivingEntity e) {
        GasFieldPotions.clear(e);
        for(var effect:List.copyOf(e.getActiveEffects()))if(!(effect.getEffect().value() instanceof GasFieldIndicator)) {
            if(!e.removeEffect(effect.getEffect())) {
                var removed=e.removeEffectNoUpdate(effect.getEffect());
                if(removed!=null)((GasPotionRemoval)e).gascraft$effectRemoved(removed);
            }
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public static void tick(EntityTickEvent.Pre event) { if(event.getEntity() instanceof LivingEntity e)refresh(e); }
    @SubscribeEvent public static void gravityMotion(EntityTickEvent.Post event) {
        if(!(event.getEntity() instanceof Mob mob)||mob.level().isClientSide||mob instanceof WitherBoss||mob instanceof EnderDragon||!mob.isAlive())return;
        var gravity=clouds(mob).stream().filter(c->c.gasContents().id().equals(GasCatalog.id("gravity_gas"))).findFirst().orElse(null);
        if(gravity==null)return;
        var toward=gravity.position().subtract(mob.position());
        if(toward.length()>1)mob.move(MoverType.SELF,toward.normalize().scale(Math.min(.25,(toward.length()-1)*.1)));
        mob.setDeltaMovement(Vec3.ZERO);mob.fallDistance=0;mob.hurtMarked=true;
    }
    private static void restoreGravity(LivingEntity e) {
        GasGravity.hold(e,"gravity",false);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void preventPotion(MobEffectEvent.Applicable event) {
        if(!event.getEntity().level().isClientSide&&!(event.getEffectInstance().getEffect().value() instanceof GasFieldIndicator)&&blocksMagic(event.getEntity()))event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }
    @SubscribeEvent public static void externalPotion(MobEffectEvent.Added event) {GasFieldPotions.external(event.getEntity(),event.getEffectInstance());}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void removedPotion(MobEffectEvent.Remove event) {GasFieldPotions.removed(event.getEntity(),event.getEffect());}
    @SubscribeEvent(priority=EventPriority.HIGH) public static void damage(LivingIncomingDamageEvent event) {
        var attacker=event.getSource().getEntity();var victim=event.getEntity();
        if(attacker instanceof LivingEntity living&&blocksAttacks(living)) {event.setCanceled(true);return;}
        if(victim instanceof Player&&attacker instanceof LivingEntity living&&undead(living)&&inField(victim,"soul_ward_gas")) {event.setCanceled(true);return;}
        if(event.getSource().is(SPECTRAL))return;
        boolean vulnerability=attacker instanceof LivingEntity&&(undead(victim)&&inField(victim,"soul_ward_gas")
            ||victim instanceof Player&&attacker instanceof Mob&&clouds(victim).stream().anyMatch(c->c.gasContents().id().equals(GasCatalog.id("misted_cologne"))&&!maskProtects(victim,c.gasContents())));
        if(vulnerability)event.setAmount(event.getAmount()*2);
    }
    @SubscribeEvent public static void teleport(EntityTeleportEvent event) { if(blocksTeleport(event.getEntity()))event.setCanceled(true); }
    @SubscribeEvent public static void castingItem(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if(blocksMagic(event.getEntity())&&gascraft.compat.GasMagicCompat.magicItem(event.getItemStack()))event.setCanceled(true);
    }
    @SubscribeEvent public static void startCastingItem(LivingEntityUseItemEvent.Start event) {
        if(blocksMagic(event.getEntity())&&gascraft.compat.GasMagicCompat.magicItem(event.getItem()))event.setCanceled(true);
    }
    @SubscribeEvent public static void spawnMagic(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide||!(event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile)||!(projectile.getOwner() instanceof LivingEntity owner))return;
        if(blocksAttacks(owner)||blocksMagic(owner)&&gascraft.compat.GasMagicCompat.magicProjectile(projectile))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event) {restoreGravity(event.getEntity());event.getEntity().getPersistentData().remove(DATA);GasFieldPotions.clear(event.getEntity());var jump=event.getEntity().getAttribute(Attributes.JUMP_STRENGTH);if(jump!=null)jump.removeModifier(GRAVITY_JUMP);if(event.getEntity() instanceof Mob mob)GOALS.remove(mob);}
    private static final class ControlGoal extends Goal {
        private final Mob mob;
        ControlGoal(Mob mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK,Flag.TARGET,Flag.JUMP));}
        @Override public boolean canUse(){return blocksAttacks(mob);}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){
            mob.setTarget(null);mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            if(data(mob).getLong("fear_until")>mob.level().getGameTime()) {
                var player=mob.level().getNearestPlayer(mob.getX(),mob.getY(),mob.getZ(),64,false);
                if(player!=null) {
                    Vec3 away=mob.position().subtract(player.position());if(away.lengthSqr()<.01)away=new Vec3(1,0,0);
                    Vec3 destination=mob.position().add(away.normalize().scale(16));mob.getNavigation().moveTo(destination.x,destination.y,destination.z,1.5);
                    mob.getMoveControl().setWantedPosition(destination.x,destination.y,destination.z,1.5);
                }
            } else if(dancing(mob)) {
                if(mob.onGround()&&mob.tickCount%10==0)mob.setDeltaMovement(Math.sin(mob.tickCount)*.12,.25,Math.cos(mob.tickCount)*.12);
                mob.getNavigation().stop();mob.setYRot(mob.getYRot()+12);
            }
        }
        @Override public void stop(){mob.getNavigation().stop();}
    }
    private GasFields() {}
}
