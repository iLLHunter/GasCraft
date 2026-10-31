package gascraft.gas;
import net.minecraft.world.effect.*;
/** HUD marker for a physical field control; potion cleansing does not remove that control. */
public final class GasFieldIndicator extends MobEffect {
    public GasFieldIndicator(boolean beneficial,int color) { super(beneficial?MobEffectCategory.BENEFICIAL:MobEffectCategory.HARMFUL,color); }
}
