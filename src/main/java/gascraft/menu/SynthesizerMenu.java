package gascraft.menu;

import gascraft.item.ProcessingUpgradeItem;
import gascraft.machine.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Native container synchronization for tier slots, staged batches, resource locks and shared flyouts. */
public final class SynthesizerMenu extends AbstractContainerMenu {
    private final MachineBlockEntity machine;
    private final Inventory playerInventory;
    private final int[] values = new int[25];
    private final int machineSlots, upgradeInputIndex;
    private boolean upgradesOpen;
    public SynthesizerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) { this(id, inventory, resolve(inventory, data.readBlockPos())); }
    private static MachineBlockEntity resolve(Inventory inventory, BlockPos position) {
        if (inventory.player.level().getBlockEntity(position) instanceof MachineBlockEntity machine && machine.synthesizer() != null) return machine;
        throw new IllegalStateException("Synthesizer unavailable at " + position);
    }
    public SynthesizerMenu(int id, Inventory inventory, MachineBlockEntity machine) {
        super(MachineContent.SYNTHESIZER_MENU.get(), id); this.machine = machine; playerInventory = inventory;
        addInput(SynthesizerMachine.INGREDIENT, 32, 30); addInput(SynthesizerMachine.CANISTER, 72, 30);
        if (kind().hasReagentSlot()) addInput(SynthesizerMachine.REAGENT, 112, 30);
        for (int group = 0; group < groups(); group++) for (int col = 0; col < 3; col++) addInput(SynthesizerMachine.WORKING + group * 3 + col, 48 + col * 18, 52 + group * 18);
        upgradeInputIndex = slots.size();
        if (kind().maxSpeedUpgrades() > 0) {
            addSlot(new SlotItemHandler(machine.upgrades().slots(), 0, 194, 32) {
                @Override public boolean isActive() { return !playerInventory.player.level().isClientSide || upgradesOpen; }
                @Override public boolean mayPickup(Player player) { return !active() && super.mayPickup(player); }
                @Override public boolean mayPlace(ItemStack stack) { return !active() && ProcessingUpgradeItem.kindOf(stack).map(kind -> kind == ProcessingUpgradeItem.Kind.SPEED
                    ? speed() < machine.kind().maxSpeedUpgrades() : efficiency() < machine.kind().maxEfficiencyUpgrades()).orElse(false); }
                @Override public void setChanged() { machine.setChanged(); }
            });
            addSlot(new SlotItemHandler(machine.upgrades().slots(), 1, 304, 32) {
                @Override public boolean isActive() { return !playerInventory.player.level().isClientSide || upgradesOpen; }
                @Override public void setChanged() { machine.setChanged(); }
            });
        }
        machineSlots = slots.size();
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 144 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 202));
        for (int i = 0; i < values.length; i++) {
            final int index = i;
            for (int part = 0; part < (i < 4 ? 2 : 1); part++) {
                final int shift = part * 16;
                addDataSlot(new DataSlot() {
                    @Override public int get() { return read(index) >>> shift & 0xffff; }
                    @Override public void set(int value) { values[index] = values[index] & ~(0xffff << shift) | (value & 0xffff) << shift; }
                });
            }
        }
    }
    private void addInput(int slot, int x, int y) {
        addSlot(new SlotItemHandler(machine.synthesizer().inventory(), slot, x, y) {
            @Override public boolean mayPickup(Player player) { return !locked(slot) && super.mayPickup(player); }
            @Override public boolean mayPlace(ItemStack stack) {
                if (slot >= SynthesizerMachine.WORKING && finished((slot - 3) / 3)) return false;
                return !locked(slot) && super.mayPlace(stack)
                    || slot < SynthesizerMachine.WORKING && locked(slot)
                    && (slot == SynthesizerMachine.REAGENT && !hasItem() || ItemStack.isSameItemSameComponents(getItem(), stack))
                    && super.mayPlace(stack);
            }
            @Override public int getMaxStackSize(ItemStack stack) { return slot == 0 && stack.hasCraftingRemainingItem() ? 1 : super.getMaxStackSize(stack); }
            @Override public void setChanged() { machine.setChanged(); }
        });
    }
    private int read(int index) {
        var synth = machine.synthesizer();
        return switch (index) {
            case 0 -> machine.energy().stored(); case 1 -> synth.energyPerTick(); case 2 -> synth.charges(); case 3 -> synth.reservedTank();
            case 4 -> machine.upgrades().speed(); case 5 -> machine.upgrades().efficiency(); case 6 -> synth.activeMask(); case 7 -> synth.finishedMask();
            case 22 -> synth.reservedIngredients(); case 23 -> synth.reservedCharges();
            case 24 -> synth.reagentType().equals("nether_star") ? 1 : 0;
            default -> index < 14 ? machine.sides().mask(MachineSides.Face.values()[index - 8])
                : (index - 14) / 2 >= groups() ? 0 : (index - 14) % 2 == 0 ? synth.progress((index - 14) / 2) : synth.totalTicks((index - 14) / 2);
        };
    }
    private int value(int index) { return playerInventory.player.level().isClientSide ? values[index] : read(index); }
    public MachineKind kind() { return machine.kind(); }
    public MachineBlockEntity machine() { return machine; }
    public int reservedIngredients() { return value(22); }
    public int reservedCharges() { return value(23); }
    public String reagentType() { return value(24) == 1 ? "nether_star" : "diamond"; }
    public boolean canUseReagent(String type) { return charges() == 0 || reagentType().equals(type); }
    public boolean canDumpCharges() { return charges() > 0 && reservedCharges() == 0; }
    public MachineTank tank() { return machine.tank(); }
    public int groups() { return machine.kind().workingSlots() / 3; }
    public int energy() { return value(0); } public int rate() { return value(1); } public int charges() { return value(2); }
    public int configuredBatchRate() { return ProcessingBalance.energyPerTick(1, MachineKind.BASE_PROCESSING_FE_PER_TICK, speed(), efficiency()); }
    public int displayRate() { return active() ? rate() : configuredBatchRate(); }
    public int speed() { return value(4); } public int efficiency() { return value(5); }
    public boolean active() { return value(6) != 0; }
    public boolean finished(int group) { return (value(7) & 1 << group) != 0; }
    public int progress(int group) { return value(14 + group * 2); } public int totalTicks(int group) { return value(15 + group * 2); }
    public boolean canDump() { return value(3) == 0; }
    public void setUpgradesOpen(boolean open) { upgradesOpen = open; }
    private boolean locked(int slot) { return slot == 0 ? active() : slot == 2 ? value(23) > 0 : slot >= 3 && (value(6) & 1 << (slot - 3) / 3) != 0; }
    public boolean enabled(MachineSides.Face face, MachineSides.Route route) { return (value(8 + face.ordinal()) & 1 << route.ordinal()) != 0; }
    public boolean canRemove(ProcessingUpgradeItem.Kind kind) {
        if (upgradeInputIndex == machineSlots) return false;
        var output = slots.get(upgradeInputIndex + 1).getItem(); var returned = machine.upgrades().returnedItem(kind);
        return !active() && (kind == ProcessingUpgradeItem.Kind.SPEED ? speed() : efficiency()) > 0
            && (output.isEmpty() || ItemStack.isSameItemSameComponents(output, returned) && output.getCount() < output.getMaxStackSize());
    }
    public static int sideButton(MachineSides.Face face, MachineSides.Route route) { return 20 + face.ordinal() * 6 + route.ordinal(); }
    @Override public boolean stillValid(Player player) { return !machine.isRemoved() && player.level().getBlockEntity(machine.getBlockPos()) == machine
        && player.distanceToSqr(machine.getBlockPos().getX() + .5, machine.getBlockPos().getY() + .5, machine.getBlockPos().getZ() + .5) <= 64; }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (player.level().isClientSide || !stillValid(player)) return false;
        if (button == 0 || button == 1) return machine.upgrades().removeOne(ProcessingUpgradeItem.Kind.values()[button]);
        if (button == 2) return machine.tank().dump();
        if (button == 3 && kind().hasReagentSlot()) return machine.synthesizer().dumpCharges();
        if (button >= 20 && button < 56) {
            int choice = button - 20; var route = MachineSides.Route.values()[choice % 6];
            if (route == MachineSides.Route.REAGENT && !kind().hasReagentSlot()) return false;
            machine.toggleSide(MachineSides.Face.values()[choice / 6], route); return true;
        }
        return false;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index); if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < machineSlots) { if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY; }
        else if (ProcessingUpgradeItem.kindOf(stack).isPresent()) {
            if (upgradeInputIndex == machineSlots || !moveItemStackTo(stack, upgradeInputIndex, upgradeInputIndex + 1, false)) return ItemStack.EMPTY;
        } else {
            // Only the explicit input slots receive shift-clicks; working slots are filled by staging.
            int inputs = kind().hasReagentSlot() ? 3 : 2;
            if (!moveItemStackTo(stack, 0, inputs, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged(); slot.onTake(player, stack); return original;
    }
}
