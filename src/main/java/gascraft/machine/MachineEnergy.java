package gascraft.machine;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** All face views share one buffer and one per-world-tick transfer budget. */
public final class MachineEnergy {
    private final MachineBlockEntity owner;
    private int stored;
    private long transferTick = Long.MIN_VALUE;
    private int received;
    private int extracted;
    private int charged;
    private boolean chargingFinished;

    public MachineEnergy(MachineBlockEntity owner) { this.owner = owner; }
    public int stored() { return stored; }
    public void restore(int amount) { stored = Math.clamp(amount, 0, owner.kind().energyCapacity()); }
    public boolean spend(int amount) {
        if (amount < 0 || amount > stored) return false;
        stored -= amount;
        owner.setChanged();
        return true;
    }
    public boolean generate(int amount) {
        if (amount < 0 || amount > owner.kind().energyCapacity() - stored) return false;
        stored += amount;
        owner.setChanged();
        return true;
    }

    private void refreshBudget() {
        long tick = owner.getLevel() == null ? 0 : owner.getLevel().getGameTime();
        if (tick != transferTick) { transferTick = tick; received = 0; extracted = 0; charged = 0; chargingFinished = false; }
    }
    int availableOutput() { refreshBudget(); return Math.min(stored, MachineKind.GENERATOR_FE_PER_TICK - extracted); }
    int sidedOutputThisTick() { refreshBudget(); return extracted - charged; }
    void extractForCharging(int amount) {
        refreshBudget();
        if (amount < 0 || amount > availableOutput()) throw new IllegalArgumentException("Charging exceeded shared Generator output budget");
        stored -= amount; extracted += amount; charged += amount; owner.setChanged();
    }
    void finishCharging() { refreshBudget(); chargingFinished = true; }

    public IEnergyStorage view(Direction side) {
        return new IEnergyStorage() {
            private boolean enabled() { return owner.energySideEnabled(side) && !owner.isRemoved(); }
            private boolean server() { return owner.getLevel() != null && !owner.getLevel().isClientSide; }
            @Override public int receiveEnergy(int maximum, boolean simulate) {
                if (maximum <= 0 || !canReceive() || !server()) return 0;
                refreshBudget();
                int amount = Math.min(maximum, Math.min(owner.kind().energyCapacity() - stored, owner.kind().energyInputPerTick() - received));
                if (!simulate && amount > 0) { stored += amount; received += amount; owner.setChanged(); }
                return amount;
            }
            @Override public int extractEnergy(int maximum, boolean simulate) {
                if (maximum <= 0 || !canExtract() || !server()) return 0;
                refreshBudget();
                // Reserve a half-share until the charging step, even if a pipe pulls before our tick.
                int reserved = chargingFinished || owner.processor() == null ? 0
                    : Math.min(availableOutput(), owner.processor().chargeAcceptance(Math.max(0, MachineKind.GENERATOR_FE_PER_TICK / 2 - charged)));
                int amount = Math.min(maximum, Math.min(stored - reserved, MachineKind.GENERATOR_FE_PER_TICK - extracted - reserved));
                if (!simulate && amount > 0) { stored -= amount; extracted += amount; owner.setChanged(); }
                return amount;
            }
            @Override public int getEnergyStored() { return stored; }
            @Override public int getMaxEnergyStored() { return owner.kind().energyCapacity(); }
            @Override public boolean canExtract() { return enabled() && owner.kind() == MachineKind.GENERATOR; }
            @Override public boolean canReceive() { return enabled() && owner.kind() != MachineKind.GENERATOR; }
        };
    }
}
