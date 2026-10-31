package gascraft.gas;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;

/** Finite size-gas bonuses retain unrelated effects, including effects applied during the lease. */
final class GasSizePotions {
    private static final String KEY="gascraft:size_potions";
    private static final ThreadLocal<Boolean> OWNED=ThreadLocal.withInitial(()->false);
    private static void owned(Runnable action){boolean old=OWNED.get();OWNED.set(true);try{action.run();}finally{OWNED.set(old);}}
    private static String id(Holder<MobEffect> effect){return BuiltInRegistries.MOB_EFFECT.getKey(effect.value()).toString();}
    private static MobEffectInstance baseline(CompoundTag lease,long now){
        if(!lease.contains("baseline"))return null;
        var tag=lease.getCompound("baseline").copy();advance(tag,(int)Math.min(Integer.MAX_VALUE,Math.max(0,now-lease.getLong("started"))));
        String id=tag.getString("id");
        while(tag.getInt("duration")==0&&tag.contains("hidden_effect")){tag=tag.getCompound("hidden_effect").copy();tag.putString("id",id);}
        var effect=MobEffectInstance.load(tag);return effect!=null&&(effect.isInfiniteDuration()||effect.getDuration()>0)?effect:null;
    }
    private static void advance(CompoundTag tag,int ticks){if(tag.contains("duration")&&tag.getInt("duration")!=-1)tag.putInt("duration",Math.max(0,tag.getInt("duration")-ticks));if(tag.contains("hidden_effect"))advance(tag.getCompound("hidden_effect"),ticks);}
    static void grant(LivingEntity entity,Holder<MobEffect> effect,int ticks,int amplifier){
        var root=entity.getPersistentData();if(!root.contains(KEY))root.put(KEY,new CompoundTag());var leases=root.getCompound(KEY);String id=id(effect);
        if(!leases.contains(id)){var lease=new CompoundTag();var previous=entity.getEffect(effect);if(previous!=null)lease.put("baseline",previous.save());lease.putLong("started",entity.level().getGameTime());leases.put(id,lease);}
        owned(()->entity.addEffect(new MobEffectInstance(effect,ticks,amplifier)));
    }
    static void external(LivingEntity entity,MobEffectInstance effect){
        if(OWNED.get())return;var leases=entity.getPersistentData().getCompound(KEY);String id=id(effect.getEffect());if(!leases.contains(id))return;
        var lease=leases.getCompound(id);long now=entity.level().getGameTime();var saved=baseline(lease,now);var copy=MobEffectInstance.load((CompoundTag)effect.save());if(saved==null)saved=copy;else saved.update(copy);lease.put("baseline",saved.save());lease.putLong("started",now);
    }
    static void removed(LivingEntity entity,Holder<MobEffect> effect){if(!OWNED.get()){var leases=entity.getPersistentData().getCompound(KEY);if(leases.contains(id(effect)))leases.getCompound(id(effect)).remove("baseline");}}
    static void finish(LivingEntity entity){
        var leases=entity.getPersistentData().getCompound(KEY).copy();entity.getPersistentData().remove(KEY);
        for(String id:leases.getAllKeys())BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(id)).ifPresent(effect->{
            var saved=baseline(leases.getCompound(id),entity.level().getGameTime());
            owned(()->{entity.removeEffect(effect);if(saved!=null)entity.addEffect(saved);});
        });
    }
    static void discard(LivingEntity entity){entity.getPersistentData().remove(KEY);}
    private GasSizePotions(){}
}
