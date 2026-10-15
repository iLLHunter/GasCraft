package gascraft.api;
import net.minecraft.world.entity.LivingEntity;
public interface IGasExposureAgent {
    boolean isToxic();
    void applyAgentExposure(LivingEntity entity, IGasBreathingState handler, float concentration, boolean firstTick, float potency, boolean forced);
    default void onExposureEnded(LivingEntity entity, IGasBreathingState handler) {}
}
