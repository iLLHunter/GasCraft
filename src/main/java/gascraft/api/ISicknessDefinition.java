package gascraft.api;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public interface ISicknessDefinition {
    ResourceLocation id();
    boolean applySicknessTick(LivingEntity carrier, SicknessState effect);
    default void onSicknessCleared(SicknessState effect, LivingEntity carrier) {}
    default boolean synchronizesToClient() { return false; }
}
