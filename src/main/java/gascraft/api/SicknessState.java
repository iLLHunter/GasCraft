package gascraft.api;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

/** Severity and both timers survive entity saves and serialized sickness copies. */
public final class SicknessState {
    private final ISicknessDefinition sickness;
    private float severity;
    private int elapsed, lastPerform;
    private final java.util.Set<String> gasOrigins = new java.util.LinkedHashSet<>();
    private Runnable changed = () -> {};
    public SicknessState(ISicknessDefinition sickness, float severity) { this.sickness = sickness; setSeverity(severity); }
    public SicknessState(ISicknessDefinition sickness, CompoundTag nbt) {
        this(sickness, nbt.getFloat("severity")); elapsed = nbt.getInt("elapsed_ticks"); lastPerform = nbt.getInt("ticks_since_last_application");
        for (var origin : nbt.getList("gasOrigins", net.minecraft.nbt.Tag.TAG_STRING)) gasOrigins.add(origin.getAsString());
    }
    public ISicknessDefinition getSickness() { return sickness; }
    public float getSeverity() { return severity; }
    public void setSeverity(float value) { severity = Float.isFinite(value) ? Math.max(0, value) : 0; changed.run(); }
    public SicknessState onChange(Runnable listener) { changed=listener; return this; }
    public int elapsedTicks() { return elapsed; }
    public int ticksSinceLastApplication() { return lastPerform; }
    public SicknessState fromGas(String gas) { gasOrigins.add(gas); return this; }
    public boolean comesFromGas(String gas) { return gasOrigins.contains(gas); }
    public SicknessState mergeSicknessState(SicknessState other) { gasOrigins.addAll(other.gasOrigins); setSeverity(severity + other.severity); return this; }
    public void tickSicknessEffect(LivingEntity carrier) { if (sickness.applySicknessTick(carrier, this)) lastPerform = 0; lastPerform++; elapsed++; }
    public CompoundTag exportState() {
        CompoundTag nbt = new CompoundTag(); nbt.putString("effect", sickness.id().toString()); nbt.putFloat("severity", severity);
        nbt.putInt("elapsed_ticks", elapsed); nbt.putInt("ticks_since_last_application", lastPerform);
        if (!gasOrigins.isEmpty()) {
            var origins = new net.minecraft.nbt.ListTag(); gasOrigins.forEach(gas -> origins.add(net.minecraft.nbt.StringTag.valueOf(gas))); nbt.put("gasOrigins", origins);
        }
        return nbt;
    }
}
