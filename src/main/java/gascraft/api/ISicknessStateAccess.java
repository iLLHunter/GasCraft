package gascraft.api;
import java.util.Collection;
import java.util.function.BinaryOperator;
public interface ISicknessStateAccess {
    boolean isSicknessActive(ISicknessDefinition sickness);
    SicknessState getActiveEffect(ISicknessDefinition sickness);
    Collection<SicknessState> getActiveSicknesses();
    void addSickness(SicknessState effect, BinaryOperator<SicknessState> merge);
    default void addSickness(SicknessState effect) { addSickness(effect,SicknessState::mergeSicknessState); }
    void tick();
}
