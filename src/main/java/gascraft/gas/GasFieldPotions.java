package gascraft.gas;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;

/** Persisted field-owned potion leases retain external effects and native hidden-effect stacks. */
final class GasFieldPotions {
    private static final String KEY="gascraft:field_potion_leases";
    private static final ThreadLocal<Boolean> OWNED=ThreadLocal.withInitial(()->false);
    private static CompoundTag leases(LivingEntity entity) {return entity.getPersistentData().getCompound(KEY);}
    private static MobEffectInstance remaining(CompoundTag lease,long now) {
        if(!lease.contains("baseline"))return null;
        var saved=lease.getCompound("baseline").copy();
        advance(saved,(int)Math.min(Integer.MAX_VALUE,Math.max(0,now-lease.getLong("started"))));
        String id=saved.getString("id");
        while(saved.getInt("duration")==0&&saved.contains("hidden_effect")){saved=saved.getCompound("hidden_effect").copy();saved.putString("id",id);}
        var effect=MobEffectInstance.load(saved);
        return effect!=null&&(effect.isInfiniteDuration()||effect.getDuration()>0)?effect:null;
    }
    private static void advance(CompoundTag tag,int elapsed) {
        if(tag.contains("duration")&&tag.getInt("duration")!=-1)tag.putInt("duration",Math.max(0,tag.getInt("duration")-elapsed));
        if(tag.contains("hidden_effect"))advance(tag.getCompound("hidden_effect"),elapsed);
    }
    private static void owned(Runnable action) {boolean previous=OWNED.get();OWNED.set(true);try{action.run();}finally{OWNED.set(previous);}}
    static void apply(LivingEntity entity,Holder<MobEffect> effect,int amplifier,Set<Holder<MobEffect>> active) {
        active.add(effect);var root=entity.getPersistentData();if(!root.contains(KEY))root.put(KEY,new CompoundTag());
        var leases=leases(entity);String id=BuiltInRegistries.MOB_EFFECT.getKey(effect.value()).toString();
        if(!leases.contains(id)) {
            var lease=new CompoundTag();var baseline=entity.getEffect(effect);if(baseline!=null)lease.put("baseline",baseline.save());
            lease.putLong("started",entity.level().getGameTime());leases.put(id,lease);
        }
        owned(()->entity.addEffect(new MobEffectInstance(effect,2,amplifier)));
    }
    static void external(LivingEntity entity,MobEffectInstance incoming) {
        if(OWNED.get())return;String id=BuiltInRegistries.MOB_EFFECT.getKey(incoming.getEffect().value()).toString();var leases=leases(entity);
        if(!leases.contains(id))return;
        long now=entity.level().getGameTime();var lease=leases.getCompound(id);var baseline=remaining(lease,now);
        var copy=MobEffectInstance.load((CompoundTag)incoming.save());if(baseline==null)baseline=copy;else baseline.update(copy);
        lease.put("baseline",baseline.save());lease.putLong("started",now);
    }
    static void removed(LivingEntity entity,Holder<MobEffect> effect) {
        if(OWNED.get())return;String id=BuiltInRegistries.MOB_EFFECT.getKey(effect.value()).toString();var leases=leases(entity);
        if(leases.contains(id))leases.getCompound(id).remove("baseline");
    }
    static void finish(LivingEntity entity,Set<Holder<MobEffect>> active) {
        var leases=leases(entity);
        for(String id:Set.copyOf(leases.getAllKeys())) {
            var effect=BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(id)).orElse(null);
            if(effect!=null&&active.contains(effect))continue;
            var current=effect==null?null:entity.getEffect(effect);
            if(current==null||current.getDuration()<=2) {
                if(current!=null)owned(()->entity.removeEffect(effect));
                var baseline=remaining(leases.getCompound(id),entity.level().getGameTime());
                if(baseline!=null)owned(()->entity.addEffect(baseline));
            }
            leases.remove(id);
        }
        if(leases.isEmpty())entity.getPersistentData().remove(KEY);
    }
    static void clear(LivingEntity entity) {entity.getPersistentData().remove(KEY);}
}
