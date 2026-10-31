package gascraft.gas;
/** Implemented by the living-entity mixin so forced cleansing retains native attribute and packet cleanup. */
public interface GasPotionRemoval {
    void gascraft$effectRemoved(net.minecraft.world.effect.MobEffectInstance effect);
}
