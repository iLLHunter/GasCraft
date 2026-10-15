package gascraft.api.event;
import gascraft.gas.GasCatalog;
import net.neoforged.bus.api.Event;
public final class GasDefinitionRegistrationEvent extends Event {
    public void register(GasCatalog.GasDefinition gas) { GasCatalog.register(gas); }
}
