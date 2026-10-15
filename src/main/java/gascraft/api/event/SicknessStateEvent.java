package gascraft.api.event;
import gascraft.api.SicknessState;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.*;
import java.util.function.BinaryOperator;

public class SicknessStateEvent extends Event {
    private final LivingEntity entity;
    private final SicknessState effect;
    public SicknessStateEvent(LivingEntity entity, SicknessState effect) { this.entity = entity; this.effect = effect; }
    public LivingEntity getEntity() { return entity; }
    public SicknessState getEffect() { return effect; }
    public static class SicknessAppliedEvent extends SicknessStateEvent implements ICancellableEvent {
        private BinaryOperator<SicknessState> merge = SicknessState::mergeSicknessState;
        public SicknessAppliedEvent(LivingEntity entity, SicknessState effect) { super(entity, effect); }
        public BinaryOperator<SicknessState> getMergeFunction() { return merge; }
        public void setMergeFunction(BinaryOperator<SicknessState> merge) { this.merge = merge; }
    }
    public static class SicknessUpdateEvent extends SicknessStateEvent implements ICancellableEvent {
        public SicknessUpdateEvent(LivingEntity entity, SicknessState effect) { super(entity, effect); }
    }
    public static class SicknessClearedEvent extends SicknessStateEvent implements ICancellableEvent {
        public SicknessClearedEvent(LivingEntity entity, SicknessState effect) { super(entity, effect); }
    }
}
