package gascraft.gas;

import gascraft.*;
import gascraft.api.event.GasExposureEvent;
import gascraft.entity.GasCloudEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Persisted, non-stacking size packages. Only the shrinking package is dispellable. */
@EventBusSubscriber(modid=Gascraft.MOD_ID)
public final class GasSizing {
    private static final String KEY="gascraft:size_state";
    private static final ResourceLocation SCALE=GasCatalog.id("size_scale"),HEALTH=GasCatalog.id("size_health"),ABSORPTION=GasCatalog.id("size_absorption");
    private static final ThreadLocal<Boolean> CHANGING_ABSORPTION=ThreadLocal.withInitial(()->false);
    private static CompoundTag state(LivingEntity entity){return entity.getPersistentData().getCompound(KEY);}
    public static boolean shrinking(LivingEntity entity){var s=state(entity);return s.getString("kind").equals("shrinking")&&s.getInt("remaining")>0&&entity.hasEffect(ModContent.SHRINKING);}
    public static boolean enlarging(LivingEntity entity){var s=state(entity);return s.getString("kind").equals("enlarging")&&s.getInt("remaining")>0;}
    public static boolean awaitingRestoration(LivingEntity entity){return state(entity).getBoolean("pending_restore");}
    public static int remainingTicks(LivingEntity entity){return state(entity).getInt("remaining");}
    public static void release(ServerLevel level,GasCloudEntity cloud,boolean large,int ticks){
        for(var entity:level.getEntitiesOfClass(LivingEntity.class,cloud.getBoundingBox().inflate(GascraftConfig.GAS_RANGE_BLOCKS.get()),e->e.isAlive()&&(e instanceof Mob||e instanceof Player))){
            if(!cloud.gasAreaContains(entity.position())||GasOlfactory.isBoss(entity)||GasExposure.exemptPlayer(entity))continue;
            var handler=GasExposure.breathingHandler(entity);var gas=cloud.gasContents();
            if(handler.isImmune(gas,1))continue;
            NeoForge.EVENT_BUS.post(new GasExposureEvent.GasExposureStartEvent(entity,handler,gas,1));
            if(!NeoForge.EVENT_BUS.post(new GasExposureEvent.GasExposureTickEvent(entity,handler,gas,1)).isCanceled())apply(entity,large,ticks);
            NeoForge.EVENT_BUS.post(new GasExposureEvent.GasExposureEndEvent(entity,handler,gas,1));
        }
    }
    private static void modifier(LivingEntity entity,net.minecraft.core.Holder<Attribute> type,ResourceLocation id,double amount,AttributeModifier.Operation operation){
        var attribute=entity.getAttribute(type);if(attribute==null)return;var old=attribute.getModifier(id);
        if(old!=null&&old.amount()==amount&&old.operation()==operation)return;
        attribute.removeModifier(id);if(amount!=0)attribute.addPermanentModifier(new AttributeModifier(id,amount,operation));
    }
    private static void remove(LivingEntity entity,net.minecraft.core.Holder<Attribute> type,ResourceLocation id){var attribute=entity.getAttribute(type);if(attribute!=null)attribute.removeModifier(id);}
    private static double scaleWithoutGas(LivingEntity entity){var attribute=entity.getAttribute(Attributes.SCALE);var own=attribute.getModifier(SCALE);return attribute.getValue()/(own==null?1:1+own.amount());}
    private static boolean fits(LivingEntity entity,double width,double height){var pos=entity.position();var box=new AABB(pos.x-width/2,pos.y,pos.z-width/2,pos.x+width/2,pos.y+height,pos.z+width/2);return entity.level().getWorldBorder().isWithinBounds(box)&&entity.level().noCollision(entity,box.deflate(1e-5))&&box.maxY<=entity.level().getMaxBuildHeight();}
    private static boolean normalFits(LivingEntity entity,CompoundTag s){double scale=scaleWithoutGas(entity);return fits(entity,s.getDouble("normal_width")*scale,s.getDouble("normal_height")*scale);}
    /** Changes only baby state; native changes to attack/health bases must not alter the approved baseline. */
    private static void baby(LivingEntity entity,boolean value,int age){
        if(!(entity instanceof Mob mob))return;
        float health=entity.getHealth();var damage=entity.getAttribute(Attributes.ATTACK_DAMAGE);var maximum=entity.getAttribute(Attributes.MAX_HEALTH);
        double damageBase=damage==null?0:damage.getBaseValue(),healthBase=maximum.getBaseValue();
        if(entity instanceof AgeableMob ageable)ageable.setAge(value?(age<0?age:-24000):age);else mob.setBaby(value);
        if(damage!=null)damage.setBaseValue(damageBase);maximum.setBaseValue(healthBase);entity.setHealth(Math.min(health,entity.getMaxHealth()));entity.refreshDimensions();
    }
    public static boolean apply(LivingEntity entity,boolean large,int ticks){
        if(entity.level().isClientSide||!entity.isAlive()||GasOlfactory.isBoss(entity)||GasExposure.exemptPlayer(entity)||!(entity instanceof Mob||entity instanceof Player)||ticks<=0)return false;
        if(!large&&GasFields.blocksMagic(entity))return false;
        var s=state(entity);String kind=large?"enlarging":"shrinking";
        if(!large&&s.getString("kind").equals(kind)&&s.getBoolean("pending_restore")){
            s.putInt("remaining",ticks);s.remove("pending_restore");s.remove("bonuses_finished");entity.addEffect(new MobEffectInstance(ModContent.SHRINKING,ticks,0,false,false,true));GasSizePotions.grant(entity,MobEffects.MOVEMENT_SPEED,ticks,0);return true;
        }
        if(s.getString("kind").equals(kind)&&s.getInt("remaining")>0&&!s.getBoolean("pending_restore")){
            s.putInt("remaining",ticks);entity.addEffect(new MobEffectInstance(large?ModContent.ENLARGING:ModContent.SHRINKING,ticks,0,false,false,true));
            if(!large||entity instanceof Player)GasSizePotions.grant(entity,large?MobEffects.DAMAGE_BOOST:MobEffects.MOVEMENT_SPEED,ticks,large?9:0);return true;
        }
        double normalWidth=s.isEmpty()?entity.getDimensions(Pose.STANDING).width()/entity.getScale():s.getDouble("normal_width");
        double normalHeight=s.isEmpty()?entity.getDimensions(Pose.STANDING).height()/entity.getScale():s.getDouble("normal_height");
        double baselineScale=scaleWithoutGas(entity);
        // Check the replacement's final body before discarding any part of an existing package.
        if(large&&!fits(entity,normalWidth*baselineScale*3,normalHeight*baselineScale*3))return false;
        if(!s.isEmpty()&&!restoreBody(entity,s))return false;
        finishBonuses(entity,s);entity.getPersistentData().remove(KEY);
        s=new CompoundTag();s.putString("kind",kind);s.putInt("remaining",ticks);s.putDouble("normal_width",normalWidth);s.putDouble("normal_height",normalHeight);s.putBoolean("original_baby",entity.isBaby());
        if(entity instanceof AgeableMob ageable)s.putInt("original_age",ageable.getAge());
        entity.getPersistentData().put(KEY,s);
        if(large){
            modifier(entity,Attributes.SCALE,SCALE,2,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            if(entity instanceof Player){
                float previous=entity.getAbsorptionAmount();modifier(entity,Attributes.MAX_ABSORPTION,ABSORPTION,20,AttributeModifier.Operation.ADD_VALUE);
                s.putFloat("own_absorption",20);ownedAbsorption(()->entity.setAbsorptionAmount(previous+20));
            }else{float ratio=entity.getHealth()/entity.getMaxHealth();modifier(entity,Attributes.MAX_HEALTH,HEALTH,1,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);entity.setHealth(entity.getMaxHealth()*ratio);}
        }else{
            if(entity instanceof Mob){baby(entity,true,s.getInt("original_age"));s.putBoolean("native_baby",entity.isBaby());}
            if(!s.getBoolean("native_baby"))modifier(entity,Attributes.SCALE,SCALE,-.5,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        entity.refreshDimensions();
        entity.addEffect(new MobEffectInstance(large?ModContent.ENLARGING:ModContent.SHRINKING,ticks,0,false,false,true));
        if(!large||entity instanceof Player)GasSizePotions.grant(entity,large?MobEffects.DAMAGE_BOOST:MobEffects.MOVEMENT_SPEED,ticks,large?9:0);
        return true;
    }
    private static void ownedAbsorption(Runnable action){boolean before=CHANGING_ABSORPTION.get();CHANGING_ABSORPTION.set(true);try{action.run();}finally{CHANGING_ABSORPTION.set(before);}}
    /** The added layer absorbs damage first; later absorption grants do not become gas-owned. */
    public static void absorptionChanging(LivingEntity entity,float amount){
        if(CHANGING_ABSORPTION.get()||entity.level().isClientSide)return;var s=state(entity);if(!s.contains("own_absorption")||amount>=entity.getAbsorptionAmount())return;
        if(entity.getAbsorptionAmount()>entity.getMaxAbsorption()&&amount>=entity.getMaxAbsorption())s.putFloat("own_absorption",Math.min(s.getFloat("own_absorption"),entity.getMaxAbsorption()));
        else s.putFloat("own_absorption",Math.max(0,s.getFloat("own_absorption")-(entity.getAbsorptionAmount()-Math.max(0,amount))));
    }
    private static void finishBonuses(LivingEntity entity,CompoundTag s){
        if(s.isEmpty()||s.getBoolean("bonuses_finished"))return;
        s.putBoolean("bonuses_finished",true);
        if(s.getString("kind").equals("enlarging")){
            if(entity instanceof Player){float own=Math.min(entity.getAbsorptionAmount(),s.getFloat("own_absorption"));ownedAbsorption(()->{entity.setAbsorptionAmount(entity.getAbsorptionAmount()-own);remove(entity,Attributes.MAX_ABSORPTION,ABSORPTION);});}
            else{float ratio=entity.getHealth()/entity.getMaxHealth();remove(entity,Attributes.MAX_HEALTH,HEALTH);entity.setHealth(Math.max(entity.isAlive()?.001F:0,entity.getMaxHealth()*ratio));}
        }
        GasSizePotions.finish(entity);entity.removeEffect(ModContent.SHRINKING);entity.removeEffect(ModContent.ENLARGING);
    }
    private static boolean restoreBody(LivingEntity entity,CompoundTag s){
        if(s.getString("kind").equals("shrinking")&&!normalFits(entity,s))return false;
        remove(entity,Attributes.SCALE,SCALE);
        if(s.getBoolean("native_baby")||entity instanceof AgeableMob)baby(entity,s.getBoolean("original_baby"),s.getInt("original_age"));
        entity.refreshDimensions();return true;
    }
    public static void tick(LivingEntity entity){
        if(entity.level().isClientSide||!entity.getPersistentData().contains(KEY))return;var s=state(entity);
        if(!entity.isAlive()){clearOnDeath(entity);return;}
        boolean active=s.getInt("remaining")>0&&!s.getBoolean("bonuses_finished")&&(s.getString("kind").equals("enlarging")||entity.hasEffect(ModContent.SHRINKING));
        if(active){
            if(entity instanceof AgeableMob ageable){int age=s.getString("kind").equals("shrinking")?-24000:s.getInt("original_age");if(ageable.getAge()!=age)baby(entity,age<0,age);}
            s.putInt("remaining",s.getInt("remaining")-1);
            if(s.getString("kind").equals("enlarging"))entity.addEffect(new MobEffectInstance(ModContent.ENLARGING,Math.max(1,s.getInt("remaining")),0,false,false,true));
            if(s.getInt("remaining")>0)return;
        }
        s.putInt("remaining",0);finishBonuses(entity,s);
        if(restoreBody(entity,s))entity.getPersistentData().remove(KEY);else s.putBoolean("pending_restore",true);
    }
    public static void clearOnDeath(LivingEntity entity){
        var s=state(entity);if(s.isEmpty())return;finishBonuses(entity,s);remove(entity,Attributes.SCALE,SCALE);if(s.getBoolean("native_baby")||entity instanceof AgeableMob)baby(entity,s.getBoolean("original_baby"),s.getInt("original_age"));entity.getPersistentData().remove(KEY);GasSizePotions.discard(entity);entity.refreshDimensions();
    }
    @SubscribeEvent public static void afterTick(EntityTickEvent.Post event){if(event.getEntity() instanceof LivingEntity entity&&!(entity instanceof Player))tick(entity);}
    @SubscribeEvent public static void afterPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event){tick(event.getEntity());}
    @SubscribeEvent public static void damage(LivingIncomingDamageEvent event){if(event.getSource().getEntity() instanceof LivingEntity attacker){if(shrinking(attacker))event.setAmount(event.getAmount()*.75F);else if(enlarging(attacker)&&attacker instanceof Mob)event.setAmount(event.getAmount()*2);}}
    @SubscribeEvent public static void added(MobEffectEvent.Added event){GasSizePotions.external(event.getEntity(),event.getEffectInstance());}
    @SubscribeEvent public static void removed(MobEffectEvent.Remove event){
        if(event.getEffect().equals(ModContent.ENLARGING)&&enlarging(event.getEntity())&&!state(event.getEntity()).getBoolean("bonuses_finished")){event.setCanceled(true);return;}
        GasSizePotions.removed(event.getEntity(),event.getEffect());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void death(LivingDeathEvent event){clearOnDeath(event.getEntity());}
    private GasSizing(){}
}
