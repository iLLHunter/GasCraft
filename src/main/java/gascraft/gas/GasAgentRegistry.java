package gascraft.gas;
import gascraft.api.IGasExposureAgent;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
public final class GasAgentRegistry {
    private static final Map<ResourceLocation, IGasExposureAgent> AGENTS = new LinkedHashMap<>();
    public static void register(ResourceLocation id, IGasExposureAgent agent) { AGENTS.put(id, agent); }
    public static IGasExposureAgent getAgent(ResourceLocation id) { return AGENTS.get(id); }
    public static ResourceLocation getId(IGasExposureAgent agent) { return AGENTS.entrySet().stream().filter(e -> e.getValue() == agent).map(Map.Entry::getKey).findFirst().orElseThrow(); }
    public static GasCatalog.AgentDefinition carrying(IGasExposureAgent agent, float potency) {
        return new GasCatalog.AgentDefinition("custom", agent.isToxic(), false, potency, getId(agent).toString(), 0, 0, 0);
    }
    private GasAgentRegistry() {}
}
