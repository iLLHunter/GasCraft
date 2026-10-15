package gascraft.api;
import gascraft.gas.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Standalone gas-definition builder for extensions. Registered definitions remain addressable by stable resource IDs. */
public final class GasDefinitionBuilder {
    public enum GasTypes { GAS, SMOKE }
    public enum GasParticleTypes { GAS, SMOKE, MISTED_MEAT }
    public static GasDefinitionBuilder builder() { return new GasDefinitionBuilder(); }
    private GasTypes type;
    private GasParticleTypes particle;
    private int color;
    private Integer canisterStripeColor;
    private boolean suspendable;
    private final List<GasCatalog.AgentDefinition> agents = new ArrayList<>();
    private final List<String> tooltip = new ArrayList<>();
    private final List<TimedCloudEffect> timedEffects = new ArrayList<>();
    public GasDefinitionBuilder setType(GasTypes type) { this.type=type; return this; }
    public GasDefinitionBuilder setParticleType(GasParticleTypes particle) { this.particle=particle; return this; }
    public GasDefinitionBuilder setColor(int color) { this.color=color; return this; }
    public GasDefinitionBuilder setCanisterStripeColor(int color) { canisterStripeColor=color; return this; }
    public GasDefinitionBuilder setSuspendable(boolean suspendable) { this.suspendable=suspendable; return this; }
    public GasDefinitionBuilder addAgent(IGasExposureAgent agent, float potency) { agents.add(GasAgentRegistry.carrying(agent,potency)); return this; }
    public GasDefinitionBuilder addAgent(GasCatalog.AgentDefinition agent) { agents.add(agent); return this; }
    public GasDefinitionBuilder addTooltipLine(String line) { tooltip.add(line); return this; }
    public GasDefinitionBuilder addTimedEffect(TimedCloudEffect effect) { timedEffects.add(Objects.requireNonNull(effect)); return this; }
    public GasCatalog.GasDefinition build(ResourceLocation id) {
        Objects.requireNonNull(type,"Gas type is required");
        return new GasCatalog.GasDefinition(id,type==GasTypes.SMOKE,color,canisterStripeColor==null?color:canisterStripeColor,List.copyOf(agents),particle==null?type.name():particle.name(),List.copyOf(tooltip),suspendable,List.copyOf(timedEffects));
    }
    public GasDefinitionBuilder() {}
}
