package gascraft.item;

import gascraft.ModContent;
import java.util.WeakHashMap;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Receive-only standard item FE capability, with one shared charging budget per stack/server tick. */
public final class GrenadeLauncherEnergy implements IEnergyStorage {
    private static final WeakHashMap<ItemStack, Budget> BUDGETS = new WeakHashMap<>();
    private final ItemStack stack;
    public GrenadeLauncherEnergy(ItemStack stack) { this.stack = stack; }
    @Override public int receiveEnergy(int requested, boolean simulate) {
        var server = ServerLifecycleHooks.getCurrentServer();
        int tick = server == null ? 0 : server.getTickCount();
        Budget budget = BUDGETS.computeIfAbsent(stack, ignored -> new Budget());
        if (budget.tick != tick) { budget.tick = tick; budget.received = 0; }
        int accepted = Math.max(0, Math.min(requested, Math.min(GrenadeLauncherItem.CHARGE_RATE - budget.received, getMaxEnergyStored() - getEnergyStored())));
        if (!simulate && accepted > 0) {
            stack.set(ModContent.LAUNCHER_ENERGY, getEnergyStored() + accepted);
            budget.received += accepted;
        }
        return accepted;
    }
    @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
    @Override public int getEnergyStored() { return GrenadeLauncherItem.storedEnergy(stack); }
    @Override public int getMaxEnergyStored() { return GrenadeLauncherItem.ENERGY_CAPACITY; }
    @Override public boolean canExtract() { return false; }
    @Override public boolean canReceive() { return true; }
    private static final class Budget { int tick = -1, received; }
}
