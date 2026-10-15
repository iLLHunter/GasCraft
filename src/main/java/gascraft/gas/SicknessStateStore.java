package gascraft.gas;
import gascraft.api.*;
import gascraft.api.event.SicknessStateEvent.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

/** Extensible sickness registry and per-entity state. */
public final class SicknessStateStore {
    private static final Map<ResourceLocation, ISicknessDefinition> REGISTRY = new LinkedHashMap<>();
    private static final String KEY = "gascraft:sickness_state";
    static {
        register(new ISicknessDefinition() {
            public ResourceLocation id() { return GasCatalog.id("chemical_eye_irritation"); }
            public boolean synchronizesToClient() { return true; }
            public boolean applySicknessTick(LivingEntity entity, SicknessState effect) { effect.setSeverity(effect.getSeverity() - .001F); return false; }
        });
        register(new ISicknessDefinition() {
            public ResourceLocation id() { return GasCatalog.id("mustard_lung_damage"); }
            public boolean applySicknessTick(LivingEntity entity, SicknessState effect) { effect.setSeverity(effect.getSeverity() - 1); return false; }
        });
        register(new ISicknessDefinition() {
            public ResourceLocation id() { return GasCatalog.id("nerve_agent_exposure"); }
            public boolean applySicknessTick(LivingEntity entity, SicknessState effect) {
                effect.setSeverity(effect.getSeverity() - .004F);
                if (!entity.level().isClientSide && effect.ticksSinceLastApplication() % 20 == 0) {
                    entity.hurt(GasDamage.nerveAgentDamage(entity), (float)Math.ceil(effect.getSeverity() * 10)); return true;
                }
                return false;
            }
        });
    }
    public static void register(ISicknessDefinition sickness) { REGISTRY.put(sickness.id(), sickness); }
    public static Collection<ISicknessDefinition> registered() { return Collections.unmodifiableCollection(REGISTRY.values()); }
    public static ISicknessStateAccess sicknessHandler(LivingEntity entity) {
        return new ISicknessStateAccess() {
            public boolean isSicknessActive(ISicknessDefinition sickness) { return readSicknessEffects(entity).containsKey(sickness.id()); }
            public SicknessState getActiveEffect(ISicknessDefinition sickness) { return mutableSicknessEffects(entity).get(sickness.id()); }
            public Collection<SicknessState> getActiveSicknesses() { return mutableSicknessEffects(entity).values(); }
            public void addSickness(SicknessState effect,java.util.function.BinaryOperator<SicknessState> merge) { addSicknessEffect(entity,effect,merge); }
            public void tick() { SicknessStateStore.tickSicknessEffects(entity); }
        };
    }
    public static Map<ResourceLocation, SicknessState> readSicknessEffects(LivingEntity entity) {
        Map<ResourceLocation, SicknessState> result = new LinkedHashMap<>();
        for (Tag entry : entity.getPersistentData().getList(KEY, Tag.TAG_COMPOUND)) {
            CompoundTag nbt = (CompoundTag)entry;
            ResourceLocation id = ResourceLocation.tryParse(nbt.getString("effect"));
            if (REGISTRY.containsKey(id)) result.put(id, new SicknessState(REGISTRY.get(id), nbt));
        }
        return result;
    }
    private static Map<ResourceLocation,SicknessState> mutableSicknessEffects(LivingEntity entity) {
        Map<ResourceLocation,SicknessState> result=readSicknessEffects(entity);
        result.values().forEach(effect->effect.onChange(()->{
            Map<ResourceLocation,SicknessState> current=readSicknessEffects(entity); current.put(effect.getSickness().id(),effect); saveSicknessEffects(entity,current.values());
        }));
        return result;
    }
    private static void saveSicknessEffects(LivingEntity entity, Collection<SicknessState> effects) {
        ListTag list = new ListTag(); effects.forEach(e -> list.add(e.exportState())); entity.getPersistentData().put(KEY, list);
    }
    public static void addSicknessEffect(LivingEntity entity, ResourceLocation id, float severity) {
        addFromGas(entity, id, severity, null);
    }
    public static void addFromGas(LivingEntity entity, ResourceLocation id, float severity, ResourceLocation gas) {
        ISicknessDefinition sickness = REGISTRY.get(id); if (sickness == null || severity <= 0) return;
        SicknessState effect = new SicknessState(sickness, severity);
        if (gas != null) effect.fromGas(gas.toString());
        addSicknessEffect(entity, effect);
    }
    static void refreshMustardDamage(LivingEntity entity) {
        // The next tick decays before applying effects, leaving exactly 100 damaging ticks after exposure.
        addSicknessEffect(entity, new SicknessState(REGISTRY.get(GasCatalog.id("mustard_lung_damage")), 101), (current, incoming) -> {
            current.setSeverity(incoming.getSeverity()); return current;
        });
    }
    public static void addSicknessEffect(LivingEntity entity, SicknessState effect) {
        addSicknessEffect(entity,effect,SicknessState::mergeSicknessState);
    }
    public static void addSicknessEffect(LivingEntity entity, SicknessState effect,java.util.function.BinaryOperator<SicknessState> merge) {
        if (!entity.isAlive() || GasExposure.exemptPlayer(entity) || effect.getSickness().id().equals(GasCatalog.id("nerve_agent_exposure")) && GasExposure.immuneToNerveAgent(entity)) return;
        SicknessAppliedEvent proposed=new SicknessAppliedEvent(entity,effect); proposed.setMergeFunction(merge);
        SicknessAppliedEvent event = NeoForge.EVENT_BUS.post(proposed);
        if (event.isCanceled()) return;
        Map<ResourceLocation, SicknessState> effects = readSicknessEffects(entity);
        effects.merge(effect.getSickness().id(), effect, event.getMergeFunction()); saveSicknessEffects(entity, effects.values());
    }
    public static float sicknessSeverity(LivingEntity entity, String id) {
        SicknessState effect = readSicknessEffects(entity).get(GasCatalog.id(id)); return effect == null ? 0 : effect.getSeverity();
    }
    public static void clear(LivingEntity entity) {
        var active = readSicknessEffects(entity);
        entity.getPersistentData().remove(KEY);
        active.values().forEach(effect -> effect.getSickness().onSicknessCleared(effect, entity));
        entity.getPersistentData().remove(KEY);
    }
    public static void tickSicknessEffects(LivingEntity entity) {
        if (!entity.isAlive()) return;
        Map<ResourceLocation, SicknessState> effects = readSicknessEffects(entity);
        effects.values().removeIf(effect -> {
            if (!entity.isAlive()) return true;
            if (GasExposure.exemptPlayer(entity) || effect.getSickness().id().equals(GasCatalog.id("nerve_agent_exposure")) && GasExposure.immuneToNerveAgent(entity)) {
                effect.getSickness().onSicknessCleared(effect, entity);
                return true;
            }
            if (!NeoForge.EVENT_BUS.post(new SicknessUpdateEvent(entity, effect)).isCanceled()) effect.tickSicknessEffect(entity);
            if (effect.getSeverity() > 0 || NeoForge.EVENT_BUS.post(new SicknessClearedEvent(entity, effect)).isCanceled()) return false;
            effect.getSickness().onSicknessCleared(effect, entity); return true;
        });
        // A lethal sickness tick can run the death cleanup before this snapshot is saved.
        if (!entity.isAlive()) { clear(entity); return; }
        saveSicknessEffects(entity, effects.values());
    }
    public static CompoundTag exportSicknessState(LivingEntity entity) {
        CompoundTag result = new CompoundTag(); result.put("effects", entity.getPersistentData().getList(KEY, Tag.TAG_COMPOUND).copy()); return result;
    }
    public static void mergeSicknessState(LivingEntity entity, CompoundTag sicknessSnapshot) {
        for (Tag entry : sicknessSnapshot.getList("effects", Tag.TAG_COMPOUND)) {
            CompoundTag nbt = (CompoundTag)entry; ISicknessDefinition sickness = REGISTRY.get(ResourceLocation.tryParse(nbt.getString("effect")));
            if (sickness != null) addSicknessEffect(entity, new SicknessState(sickness, nbt));
        }
    }
    private SicknessStateStore() {}
}
