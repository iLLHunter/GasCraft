package gascraft.api.event;
import gascraft.api.IGasBreathingState;
import gascraft.gas.GasCatalog;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.*;

/** Exposure lifecycle events are posted on NeoForge's game bus. */
public class GasExposureEvent extends Event {
    private final LivingEntity entity;
    private final IGasBreathingState handler;
    private final GasCatalog.GasDefinition gas;
    private final float concentration;
    public GasExposureEvent(LivingEntity entity, IGasBreathingState handler, GasCatalog.GasDefinition gas, float concentration) {
        this.entity=entity; this.handler=handler; this.gas=gas; this.concentration=concentration;
    }
    public LivingEntity getEntity() { return entity; }
    public IGasBreathingState getBreathHandler() { return handler; }
    public GasCatalog.GasDefinition getGas() { return gas; }
    public float getConcentration() { return concentration; }
    public static class GasExposureStartEvent extends GasExposureEvent {
        public GasExposureStartEvent(LivingEntity e, IGasBreathingState h, GasCatalog.GasDefinition g, float c) { super(e,h,g,c); }
    }
    public static class GasExposureTickEvent extends GasExposureEvent implements ICancellableEvent {
        public GasExposureTickEvent(LivingEntity e, IGasBreathingState h, GasCatalog.GasDefinition g, float c) { super(e,h,g,c); }
    }
    public static class GasExposureEndEvent extends GasExposureEvent implements ICancellableEvent {
        public GasExposureEndEvent(LivingEntity e, IGasBreathingState h, GasCatalog.GasDefinition g, float c) { super(e,h,g,c); }
    }
    public static class GasExposureImmunityEvent extends GasExposureEvent {
        private boolean immune;
        public GasExposureImmunityEvent(LivingEntity e, IGasBreathingState h, GasCatalog.GasDefinition g, float c, boolean immune) { super(e,h,g,c); this.immune=immune; }
        public boolean isImmune() { return immune; }
        public void setImmune(boolean immune) { this.immune=immune; }
    }
}
