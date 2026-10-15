package gascraft.api.event;
import gascraft.api.IGasExposureAgent;
import gascraft.gas.GasAgentRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
public final class GasAgentRegistrationEvent extends Event {
    public void register(ResourceLocation id, IGasExposureAgent agent) { GasAgentRegistry.register(id, agent); }
}
