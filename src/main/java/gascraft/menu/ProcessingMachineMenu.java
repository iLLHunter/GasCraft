package gascraft.menu;

import gascraft.item.ProcessingUpgradeItem;
import gascraft.machine.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Ordinary container synchronization carries inventories, settings, counts and full-width FE values. */
public final class ProcessingMachineMenu extends AbstractContainerMenu {
    private static final int SIDES = 8, FILTERS = 14;
    private final MachineBlockEntity machine;
    private final Inventory playerInventory;
    private final int machineSlots;
    private final int upgradeInputIndex;
    private final int[] values = new int[24];
    private boolean upgradePanelOpen;

    public void setUpgradePanelOpen(boolean open) { upgradePanelOpen = open; }
    private boolean upgradeSlotsVisible() { return !playerInventory.player.level().isClientSide || upgradePanelOpen; }

    public ProcessingMachineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, resolve(inventory, data.readBlockPos()));
    }
    private static MachineBlockEntity resolve(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof MachineBlockEntity machine && machine.processor() != null) return machine;
        throw new IllegalStateException("Processing machine is unavailable at " + pos);
    }
    public ProcessingMachineMenu(int id, Inventory inventory, MachineBlockEntity machine) {
        super(MachineContent.PROCESSING_MENU.get(), id);
        this.machine = machine;
        this.playerInventory = inventory;
        var processor = machine.processor();
        for (int slot = 0; slot < processor.inputCount(); slot++) {
            final int inputSlot = slot;
            int x = kind() == MachineKind.POWDER_MIXER ? 28 + slot % 3 * 18 : kind() == MachineKind.GENERATOR ? 80 : 52;
            int y = kind() == MachineKind.POWDER_MIXER ? 30 + slot / 3 * 18 : 48;
            addSlot(new SlotItemHandler(processor.inventory(), slot, x, y) {
                @Override public boolean mayPickup(Player player) { return !inputLocked(inputSlot) && super.mayPickup(player); }
                @Override public boolean mayPlace(ItemStack stack) {
                    return (!inputLocked(inputSlot) || ItemStack.isSameItemSameComponents(getItem(), stack)) && super.mayPlace(stack);
                }
                @Override public int getMaxStackSize(ItemStack stack) { return stack.hasCraftingRemainingItem() ? 1 : super.getMaxStackSize(stack); }
                @Override public void setChanged() { machine.setChanged(); machine.syncFermenterContents(); }
            });
        }
        if (processor.outputSlot() >= 0) addSlot(new SlotItemHandler(processor.inventory(), processor.outputSlot(), 128, 48) {
            @Override public void onTake(Player player, ItemStack stack) { processor.awardExperience(player); super.onTake(player, stack); }
            @Override public void setChanged() { machine.setChanged(); machine.syncFermenterContents(); }
        });
        if (processor.chargingSlot() >= 0) addSlot(new SlotItemHandler(processor.inventory(), processor.chargingSlot(), 128, 84) {
            @Override public void setChanged() { machine.setChanged(); }
        });
        upgradeInputIndex = slots.size();
        if (kind().maxSpeedUpgrades() > 0 || kind().maxEfficiencyUpgrades() > 0) {
        addSlot(new SlotItemHandler(machine.upgrades().slots(), 0, 194, 32) {
            @Override public boolean isActive() { return upgradeSlotsVisible(); }
            @Override public boolean mayPickup(Player player) { return !active() && super.mayPickup(player); }
            @Override public boolean mayPlace(ItemStack stack) {
                return !active() && ProcessingUpgradeItem.kindOf(stack).map(kind -> kind == ProcessingUpgradeItem.Kind.SPEED
                    ? speed() < machine.kind().maxSpeedUpgrades() : efficiency() < machine.kind().maxEfficiencyUpgrades()).orElse(false);
            }
            @Override public void setChanged() { machine.setChanged(); }
        });
        addSlot(new SlotItemHandler(machine.upgrades().slots(), 1, 304, 32) {
            @Override public boolean isActive() { return upgradeSlotsVisible(); }
            @Override public void setChanged() { machine.setChanged(); }
        });
        }
        machineSlots = slots.size();
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 134 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 192));
        for (int logical = 0; logical < values.length; logical++) {
            final int index = logical;
            // Vanilla container data packets carry signed shorts; split large values to preserve every bit.
            int parts = index < 4 ? 2 : 1;
            for (int part = 0; part < parts; part++) {
                final int shift = part * 16;
                addDataSlot(new DataSlot() {
                    @Override public int get() { return read(index) >>> shift & 0xffff; }
                    @Override public void set(int value) { values[index] = values[index] & ~(0xffff << shift) | (value & 0xffff) << shift; }
                });
            }
        }
    }

    private int read(int index) {
        var processor = machine.processor();
        return switch (index) {
            case 0 -> machine.energy().stored();
            case 1 -> processor.displayedProgress();
            case 2 -> processor.displayedTotalTicks();
            case 3 -> processor.displayedEnergyPerTick();
            case 4 -> machine.upgrades().speed();
            case 5 -> machine.upgrades().efficiency();
            case 6 -> machine.operationActive() ? 1 : 0;
            case 7 -> processor.status().ordinal();
            case 23 -> processor.lockedInputs();
            default -> index < FILTERS ? machine.sides().mask(MachineSides.Face.values()[index - SIDES])
                : kind() == MachineKind.POWDER_MIXER ? BuiltInRegistries.ITEM.getId(processor.filter(index - FILTERS).getItem()) : 0;
        };
    }
    private int value(int index) { return playerInventory.player.level().isClientSide ? values[index] : read(index); }
    public MachineKind kind() { return machine.kind(); }
    public MachineBlockEntity machine() { return machine; }
    public int energy() { return value(0); }
    public int progress() { return value(1); }
    public int totalTicks() { return value(2); }
    public int rate() { return value(3); }
    public int displayRate() { return rate(); }
    public int speed() { return value(4); }
    public int efficiency() { return value(5); }
    public boolean active() { return value(6) != 0; }
    private boolean inputLocked(int slot) { return (value(23) & 1 << slot) != 0; }
    public ProcessingMachine.Status status() { return ProcessingMachine.Status.values()[Math.clamp(value(7), 0, ProcessingMachine.Status.values().length - 1)]; }
    public boolean enabled(MachineSides.Face face, MachineSides.Route route) { return (value(SIDES + face.ordinal()) & 1 << route.ordinal()) != 0; }
    public ItemStack filter(int slot) { return new ItemStack(BuiltInRegistries.ITEM.byId(value(FILTERS + slot))); }
    public boolean canRemove(ProcessingUpgradeItem.Kind kind) {
        if (upgradeInputIndex == machineSlots) return false;
        int count = kind == ProcessingUpgradeItem.Kind.SPEED ? speed() : efficiency();
        ItemStack output = slots.get(upgradeInputIndex + 1).getItem();
        ItemStack returned = machine.upgrades().returnedItem(kind);
        return !active() && count > 0 && (output.isEmpty() || ItemStack.isSameItemSameComponents(output, returned) && output.getCount() < output.getMaxStackSize());
    }
    public static int sideButton(MachineSides.Face face, MachineSides.Route route) { return 20 + face.ordinal() * 3 + route.ordinal(); }

    @Override public boolean stillValid(Player player) {
        return !machine.isRemoved() && player.level().getBlockEntity(machine.getBlockPos()) == machine
            && player.distanceToSqr(machine.getBlockPos().getX() + .5, machine.getBlockPos().getY() + .5, machine.getBlockPos().getZ() + .5) <= 64;
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (player.level().isClientSide || !stillValid(player)) return false;
        if (button == 0 || button == 1) return machine.upgrades().removeOne(ProcessingUpgradeItem.Kind.values()[button]);
        if (button == 2) return machine.processor().clearFilters();
        if (button >= 100 && button < 109) return machine.processor().clearFilter(button - 100);
        if (button >= 20 && button < 38) {
            int choice = button - 20;
            if (kind() == MachineKind.FERMENTER && choice % 3 == 0) return false;
            machine.toggleSide(MachineSides.Face.values()[choice / 3], MachineSides.Route.values()[choice % 3]);
            return true;
        }
        return false;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (ProcessingUpgradeItem.kindOf(stack).isPresent()) {
            if (upgradeInputIndex == machineSlots) return ItemStack.EMPTY;
            if (!moveItemStackTo(stack, upgradeInputIndex, upgradeInputIndex + 1, false)) return ItemStack.EMPTY;
        } else if (kind() == MachineKind.GENERATOR && machine.processor().inventory().isItemValid(machine.processor().chargingSlot(), stack)) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, machine.processor().inputCount(), false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
