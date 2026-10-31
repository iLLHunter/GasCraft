package gascraft.gas;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
/** Shared ownership keeps overlapping physical controls from capturing one another as the native baseline. */
public final class GasGravity {
    private static final String KEY="gascraft:temporary_no_gravity";
    private static final String LEGACY="gascraft:paralysis_previous_no_gravity";
    public static void hold(LivingEntity entity,String owner,boolean active) {
        var root=entity.getPersistentData();
        if(!root.contains(KEY)) {
            if(!active&&!root.contains(LEGACY))return;
            var state=new CompoundTag();state.putBoolean("baseline",root.contains(LEGACY)?root.getBoolean(LEGACY):entity.isNoGravity());
            if(root.contains(LEGACY)){state.putBoolean("paralysis",true);root.remove(LEGACY);}
            root.put(KEY,state);
        }
        var state=root.getCompound(KEY);state.putBoolean(owner,active);
        if(state.getBoolean("gravity")||state.getBoolean("paralysis"))entity.setNoGravity(true);
        else {entity.setNoGravity(state.getBoolean("baseline"));root.remove(KEY);}
    }
    private GasGravity(){}
}
