package gascraft.gas;
import net.minecraft.world.entity.Entity;
/** Internal movement correction and respawn are not gameplay teleport requests. */
public final class GasTeleports {
    private static final ThreadLocal<Integer> INTERNAL=ThreadLocal.withInitial(()->0);
    public static void beginInternal(){INTERNAL.set(INTERNAL.get()+1);}
    public static void endInternal(){INTERNAL.set(Math.max(0,INTERNAL.get()-1));}
    public static boolean blocked(Entity entity){return INTERNAL.get()==0&&GasFields.blocksTeleport(entity);}
    public static void correct(Runnable action){beginInternal();try{action.run();}finally{endInternal();}}
    private GasTeleports(){}
}
