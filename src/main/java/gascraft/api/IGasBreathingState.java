package gascraft.api;
import gascraft.gas.GasCatalog;
import java.util.Map;

public interface IGasBreathingState {
    float getAirSupply();
    void setAirSupply(float air);
    float getMaxAirSupply();
    Map<GasCatalog.GasDefinition, Double> getGasConcentrations();
    void setConcentration(IGasDefinition gas, float concentration);
    boolean isImmune(GasCatalog.GasDefinition gas, float concentration);
    void tick();
}
