package gascraft.menu;

import gascraft.ModContent;
import gascraft.item.GrenadeLauncherItem;
import gascraft.item.ProcessingUpgradeItem;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;

/** Server-owned launcher upgrades and full-width FE synchronization, bound to the opened weapon. */
public final class GrenadeLauncherMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final int launcherSlot;
    private final ItemStack launcher;
    private final SimpleContainer upgrades = new SimpleContainer(2);
    private boolean upgradesOpen, updating;
    private int clientEnergy, clientEfficiency;

    public GrenadeLauncherMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) { this(id, inventory, data.readVarInt()); }
    public GrenadeLauncherMenu(int id, Inventory inventory, int launcherSlot) {
        super(ModContent.GRENADE_LAUNCHER_MENU.get(), id);
        this.inventory = inventory; this.launcherSlot = launcherSlot; launcher = inventory.getItem(launcherSlot);
        var saved = launcher.getOrDefault(ModContent.LAUNCHER_UPGRADES, ItemContainerContents.EMPTY).stream().toList();
        for (int i = 0; i < Math.min(saved.size(), 2); i++) upgrades.setItem(i, saved.get(i).copy());
        upgrades.addListener(container -> installAndSave());
        addSlot(new Slot(upgrades, 0, 194, 32) {
            @Override public int getMaxStackSize() { return 64; }
            @Override public boolean isActive() { return !inventory.player.level().isClientSide || upgradesOpen; }
            @Override public boolean mayPlace(ItemStack stack) { return efficiency() < GrenadeLauncherItem.MAX_EFFICIENCY && isEfficiency(stack); }
        });
        addSlot(new Slot(upgrades, 1, 304, 32) {
            @Override public int getMaxStackSize() { return 64; }
            @Override public boolean isActive() { return !inventory.player.level().isClientSide || upgradesOpen; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) addInventorySlot(column + row * 9 + 9, 8 + column * 18, 84 + row * 18);
        for (int column = 0; column < 9; column++) addInventorySlot(column, 8 + column * 18, 142);
        for (int part = 0; part < 2; part++) {
            final int shift = part * 16;
            addDataSlot(new DataSlot() {
                @Override public int get() { return GrenadeLauncherItem.storedEnergy(launcher) >>> shift & 0xffff; }
                @Override public void set(int value) { clientEnergy = clientEnergy & ~(0xffff << shift) | (value & 0xffff) << shift; }
            });
        }
        addDataSlot(new DataSlot() {
            @Override public int get() { return GrenadeLauncherItem.efficiency(launcher); }
            @Override public void set(int value) { clientEfficiency = value; }
        });
    }
    private static boolean isEfficiency(ItemStack stack) { return ProcessingUpgradeItem.kindOf(stack).orElse(null) == ProcessingUpgradeItem.Kind.EFFICIENCY; }
    private void addInventorySlot(int inventoryIndex, int x, int y) {
        addSlot(new Slot(inventory, inventoryIndex, x, y) {
            @Override public boolean mayPickup(Player player) { return inventoryIndex != launcherSlot; }
            @Override public boolean mayPlace(ItemStack stack) { return inventoryIndex != launcherSlot; }
        });
    }
    public void setUpgradesOpen(boolean open) { upgradesOpen = open; }
    public int energy() { return inventory.player.level().isClientSide ? clientEnergy : GrenadeLauncherItem.storedEnergy(launcher); }
    public int efficiency() { return inventory.player.level().isClientSide ? clientEfficiency : GrenadeLauncherItem.efficiency(launcher); }
    public int shotCost() { return Math.max(1, (int)Math.ceil(GrenadeLauncherItem.BASE_SHOT_COST / Math.pow(gascraft.machine.ProcessingBalance.UPGRADE_MULTIPLIER, efficiency()))); }
    private void installAndSave() {
        if (updating || inventory.player.level().isClientSide || !stillValid(inventory.player)) return;
        updating = true;
        try {
            ItemStack input = upgrades.getItem(0);
            if (isEfficiency(input)) {
                int count = Math.min(input.getCount(), GrenadeLauncherItem.MAX_EFFICIENCY - efficiency());
                if (count > 0) { input.shrink(count); launcher.set(ModContent.LAUNCHER_EFFICIENCY, efficiency() + count); }
            }
            launcher.set(ModContent.LAUNCHER_UPGRADES, ItemContainerContents.fromItems(List.of(upgrades.getItem(0), upgrades.getItem(1))));
            inventory.setChanged();
        } finally { updating = false; }
    }
    public boolean canRemoveEfficiency() {
        var returned = upgrades.getItem(1);
        return efficiency() > 0 && (returned.isEmpty() || returned.is(ModContent.PROCESSING_EFFICIENCY_UPGRADE.get()) && returned.getCount() < returned.getMaxStackSize());
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (button != 1 || player.level().isClientSide || !stillValid(player) || !canRemoveEfficiency()) return false;
        updating = true;
        try {
            launcher.set(ModContent.LAUNCHER_EFFICIENCY, efficiency() - 1);
            ItemStack returned = upgrades.getItem(1);
            if (returned.isEmpty()) {
                returned = new ItemStack(ModContent.PROCESSING_EFFICIENCY_UPGRADE.get());
                upgrades.setItem(1, returned);
            } else returned.grow(1);
            launcher.set(ModContent.LAUNCHER_UPGRADES, ItemContainerContents.fromItems(List.of(upgrades.getItem(0), returned)));
            inventory.setChanged();
        } finally { updating = false; }
        return true;
    }
    @Override public boolean stillValid(Player player) {
        ItemStack held = inventory.getItem(launcherSlot);
        return held.getItem() instanceof GrenadeLauncherItem && (player.level().isClientSide || held == launcher);
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player) || type == ClickType.SWAP && button == launcherSlot) return;
        super.clicked(slot, button, type, player);
    }
    @Override public void broadcastChanges() { installAndSave(); super.broadcastChanges(); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < 2) { if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY; }
        else if (!isEfficiency(stack) || !moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return original;
    }
}
