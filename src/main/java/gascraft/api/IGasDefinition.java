package gascraft.api;
import gascraft.gas.GasCatalog;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
public interface IGasDefinition {
    ResourceLocation id();
    boolean toxic();
    int color();
    int canisterStripeColor();
    String particleType();
    List<GasCatalog.AgentDefinition> agents();
    List<String> tooltipLines();
    default List<gascraft.gas.TimedCloudEffect> timedEffects() { return List.of(); }
    void applyEffect(LivingEntity entity, float concentration, boolean forced);
}
