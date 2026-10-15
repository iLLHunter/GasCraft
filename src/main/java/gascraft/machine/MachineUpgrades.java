package gascraft.machine;

import gascraft.ModContent;
import gascraft.item.ProcessingUpgradeItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** An insertion slot installs either mod's items into counts; removal always returns Gascraft items. */
public final class MachineUpgrades {
    private final MachineBlockEntity owner;
    private int speed;
    private int efficiency;
    private final ItemStackHandler slots = new ItemStackHandler(2) {
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            if (slot != 0 || owner.operationActive()) return false;
            return ProcessingUpgradeItem.kindOf(stack).map(kind -> count(kind) < maximum(kind)).orElse(false);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 0 && owner.operationActive() ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
        }
        @Override protected void onContentsChanged(int slot) { owner.setChanged(); }
        @Override public void setSize(int size) { super.setSize(2); }
    };

    public MachineUpgrades(MachineBlockEntity owner) { this.owner = owner; }
    public ItemStackHandler slots() { return slots; }
    public int speed() { return speed; }
    public int efficiency() { return efficiency; }
    public int count(ProcessingUpgradeItem.Kind kind) { return kind == ProcessingUpgradeItem.Kind.SPEED ? speed : efficiency; }
    public int maximum(ProcessingUpgradeItem.Kind kind) { return kind == ProcessingUpgradeItem.Kind.SPEED ? owner.kind().maxSpeedUpgrades() : owner.kind().maxEfficiencyUpgrades(); }
    public ItemStack returnedItem(ProcessingUpgradeItem.Kind kind) {
        return new ItemStack(kind == ProcessingUpgradeItem.Kind.SPEED ? ModContent.PROCESSING_SPEED_UPGRADE.get() : ModContent.PROCESSING_EFFICIENCY_UPGRADE.get());
    }
    public boolean canRemove(ProcessingUpgradeItem.Kind kind) {
        ItemStack output = slots.getStackInSlot(1), returned = returnedItem(kind);
        return !owner.operationActive() && count(kind) > 0 && (output.isEmpty()
            || ItemStack.isSameItemSameComponents(output, returned) && output.getCount() < output.getMaxStackSize());
    }
    public boolean removeOne(ProcessingUpgradeItem.Kind kind) {
        if (!canRemove(kind)) return false;
        ItemStack output = slots.getStackInSlot(1);
        slots.setStackInSlot(1, output.isEmpty() ? returnedItem(kind) : output.copyWithCount(output.getCount() + 1));
        if (kind == ProcessingUpgradeItem.Kind.SPEED) speed--; else efficiency--;
        owner.setChanged();
        return true;
    }
    public void tick() {
        if (owner.operationActive()) return;
        ItemStack input = slots.getStackInSlot(0);
        ProcessingUpgradeItem.kindOf(input).ifPresent(kind -> {
            int amount = Math.min(input.getCount(), maximum(kind) - count(kind));
            if (amount <= 0) return;
            if (kind == ProcessingUpgradeItem.Kind.SPEED) speed += amount; else efficiency += amount;
            slots.setStackInSlot(0, input.copyWithCount(input.getCount() - amount));
            owner.setChanged();
        });
    }
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("speed", speed); tag.putInt("efficiency", efficiency);
        tag.put("slots", slots.serializeNBT(registries));
        return tag;
    }
    public void load(HolderLookup.Provider registries, CompoundTag tag) {
        speed = Math.clamp(tag.getInt("speed"), 0, owner.kind().maxSpeedUpgrades());
        efficiency = Math.clamp(tag.getInt("efficiency"), 0, owner.kind().maxEfficiencyUpgrades());
        slots.deserializeNBT(registries, tag.getCompound("slots"));
    }
}
