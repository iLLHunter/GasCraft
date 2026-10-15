package gascraft.machine;

import gascraft.recipe.*;
import java.util.ArrayList;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.*;

/** Server processing for the Generator, Powderizer, Mixer and Kiln. */
public final class ProcessingMachine {
    public enum Status { IDLE, RUNNING, NO_POWER, OUTPUT_BLOCKED, BUFFER_FULL }
    private final MachineBlockEntity owner;
    private final int inputCount;
    private final int outputSlot;
    private final ItemStackHandler inventory;
    private final NonNullList<ItemStack> filters;
    private Work work;
    private int fuelRemaining;
    private int fuelTotal;
    private long fuelEnergyRemaining;
    private int fuelGenerationRate = MachineKind.GENERATOR_FE_PER_TICK;
    private double experienceStored;
    private Status status = Status.IDLE;
    private boolean internalChange;

    public ProcessingMachine(MachineBlockEntity owner) {
        this.owner = owner;
        inputCount = owner.kind() == MachineKind.POWDER_MIXER ? 9 : 1;
        outputSlot = owner.kind() == MachineKind.GENERATOR ? -1 : inputCount;
        filters = NonNullList.withSize(inputCount, ItemStack.EMPTY);
        inventory = new ItemStackHandler(inputCount + (owner.kind() == MachineKind.GENERATOR || outputSlot >= 0 ? 1 : 0)) {
            @Override public void setSize(int size) { super.setSize(inputCount + (owner.kind() == MachineKind.GENERATOR || outputSlot >= 0 ? 1 : 0)); }
            @Override public int getSlotLimit(int slot) { return 64; }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                if (slot == chargingSlot()) {
                    var capability = stack.getCapability(Capabilities.EnergyStorage.ITEM);
                    return capability != null && capability.canReceive();
                }
                if (slot >= inputCount || stack.isEmpty()) return false;
                if (required(slot)) return ItemStack.isSameItemSameComponents(work.inputs.get(slot), stack);
                if (owner.kind() == MachineKind.GENERATOR) return stack.getBurnTime(RecipeType.SMELTING) > 0;
                if (owner.kind() == MachineKind.POWDER_MIXER) return true;
                return singleRecipe(stack) != null;
            }
            @Override protected int getStackLimit(int slot, ItemStack stack) {
                // Container ingredients need space for their remaining item in the same input slot.
                return slot < inputCount && stack.hasCraftingRemainingItem() ? 1 : super.getStackLimit(slot, stack);
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return !internalChange && required(slot) ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
            }
            @Override public void setStackInSlot(int slot, ItemStack stack) {
                super.setStackInSlot(slot, stack);
                if (!internalChange && slot < inputCount && owner.kind() == MachineKind.POWDER_MIXER && !stack.isEmpty())
                    filters.set(slot, stack.copyWithCount(1));
            }
            @Override protected void onContentsChanged(int slot) { owner.setChanged(); owner.syncFermenterContents(); }
        };
    }

    public ItemStackHandler inventory() { return inventory; }
    public int inputCount() { return inputCount; }
    public int outputSlot() { return outputSlot; }
    public int chargingSlot() { return owner.kind() == MachineKind.GENERATOR ? 1 : -1; }
    private net.neoforged.neoforge.energy.IEnergyStorage chargingCapability() {
        return chargingSlot() < 0 ? null : inventory.getStackInSlot(chargingSlot()).getCapability(Capabilities.EnergyStorage.ITEM);
    }
    int chargeAcceptance(int maximum) {
        var receiver = chargingCapability();
        return receiver != null && receiver.canReceive() ? receiver.receiveEnergy(maximum, true) : 0;
    }
    public ItemStack filter(int slot) { return filters.get(slot).copy(); }
    public Status status() { return status; }
    public boolean active() { return work != null || fuelRemaining > 0; }
    public int progress() { return work != null ? work.progress : fuelTotal - fuelRemaining; }
    public int totalTicks() { return work != null ? work.ticks : fuelTotal; }
    public int displayedProgress() { return Math.ceilDiv(progress(), displayTimeDivisor()); }
    public int displayedTotalTicks() { return Math.ceilDiv(totalTicks(), displayTimeDivisor()); }
    private int displayTimeDivisor() { return owner.kind() == MachineKind.FERMENTER && exposed() ? 2 : 1; }
    public int energyPerTick() { return work != null ? work.rate : fuelRemaining > 0 ? (int)Math.min(fuelGenerationRate, fuelEnergyRemaining) : 0; }
    public int displayedEnergyPerTick() {
        if (active()) return energyPerTick();
        if (owner.kind() == MachineKind.FERMENTER) return 0;
        if (owner.kind() == MachineKind.GENERATOR) {
            int rate = MachineKind.GENERATOR_FE_PER_TICK * (owner.upgrades().speed() > 0 ? 2 : 1);
            int ticks = inventory.getStackInSlot(0).getBurnTime(RecipeType.SMELTING);
            return ticks > 0 ? (int)Math.min(rate, (long)ticks * MachineKind.GENERATOR_FE_PER_TICK) : rate;
        }
        Work next = owner.getLevel() == null ? null : findWork();
        return next != null ? next.rate : ProcessingBalance.energyPerTick(1, MachineKind.BASE_PROCESSING_FE_PER_TICK, owner.upgrades().speed(), owner.upgrades().efficiency());
    }
    public int fuelRemaining() { return fuelRemaining; }
    private boolean required(int slot) { return work != null && slot < inputCount && !work.inputs.get(slot).isEmpty(); }
    public int lockedInputs() {
        int mask = 0;
        for (int slot = 0; slot < inputCount; slot++) if (required(slot)) mask |= 1 << slot;
        return mask;
    }
    public boolean clearFilter(int slot) {
        if (active() || owner.kind() != MachineKind.POWDER_MIXER || slot < 0 || slot >= inputCount) return false;
        filters.set(slot, ItemStack.EMPTY); owner.setChanged(); return true;
    }
    public boolean clearFilters() {
        if (active() || owner.kind() != MachineKind.POWDER_MIXER) return false;
        for (int slot = 0; slot < inputCount; slot++) filters.set(slot, ItemStack.EMPTY);
        owner.setChanged(); return true;
    }

    private SingleInputProcessingRecipe singleRecipe(ItemStack input) {
        if (owner.getLevel() == null) return null;
        var type = owner.kind() == MachineKind.POWDERIZER ? MachineRecipes.POWDERIZING.get()
            : owner.kind() == MachineKind.FERMENTER ? MachineRecipes.FERMENTING.get() : MachineRecipes.KILN_PROCESSING.get();
        var explicit = owner.getLevel().getRecipeManager().getRecipeFor(type, new SingleRecipeInput(input), owner.getLevel());
        if (explicit.isPresent()) return explicit.get().value();
        if (owner.kind() == MachineKind.MATERIALS_KILN) return owner.getLevel().getRecipeManager()
            .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), owner.getLevel())
            .map(holder -> KilnRecipeDiscovery.adapt(holder.value(), holder.value().assemble(new SingleRecipeInput(input), owner.getLevel().registryAccess())))
            .orElse(null);
        return owner.kind() == MachineKind.POWDERIZER ? PowderizerRecipeDiscovery.findRecipe(input).orElse(null) : null;
    }

    private Work findWork() {
        ItemStack result;
        int baseTicks, baseEnergy;
        float experience = 0;
        if (owner.kind() == MachineKind.POWDER_MIXER) {
            var stacks = new ArrayList<ItemStack>();
            for (int slot = 0; slot < inputCount; slot++) stacks.add(inventory.getStackInSlot(slot));
            var input = CraftingInput.ofPositioned(3, 3, stacks).input();
            var found = owner.getLevel().getRecipeManager().getRecipeFor(MachineRecipes.POWDER_MIXING.get(), input, owner.getLevel());
            if (found.isEmpty()) return null;
            var recipe = found.get().value();
            result = recipe.assemble(input, owner.getLevel().registryAccess());
            baseTicks = recipe.processingTicks(); baseEnergy = recipe.energyPerOperation();
        } else {
            var recipe = singleRecipe(inventory.getStackInSlot(0));
            if (recipe == null) return null;
            result = recipe.getResultItem(owner.getLevel().registryAccess());
            baseTicks = recipe.processingTicks(); baseEnergy = recipe.energyPerOperation(); experience = recipe.experience();
        }
        NonNullList<ItemStack> inputs = NonNullList.withSize(inputCount, ItemStack.EMPTY);
        for (int slot = 0; slot < inputCount; slot++) inputs.set(slot, inventory.getStackInSlot(slot).copyWithCount(1));
        if (owner.kind() == MachineKind.FERMENTER) {
            int count = inventory.getStackInSlot(0).getCount();
            count = Math.min(count, result.getMaxStackSize() / result.getCount());
            inputs.set(0, inventory.getStackInSlot(0).copyWithCount(count));
            result = result.copyWithCount(result.getCount() * count);
        }
        return new Work(inputs, result, ProcessingBalance.processingTicks(baseTicks, owner.upgrades().speed()),
            owner.kind() == MachineKind.FERMENTER ? 0 : ProcessingBalance.energyPerTick(baseTicks, baseEnergy, owner.upgrades().speed(), owner.upgrades().efficiency()), experience);
    }

    private boolean fits(Work operation) {
        ItemStack output = inventory.getStackInSlot(outputSlot);
        if (!output.isEmpty() && !ItemStack.isSameItemSameComponents(output, operation.result)) return false;
        if (output.getCount() + operation.result.getCount() > Math.min(64, operation.result.getMaxStackSize())) return false;
        for (int slot = 0; slot < inputCount; slot++) {
            ItemStack required = operation.inputs.get(slot);
            if (required.isEmpty()) continue;
            ItemStack input = inventory.getStackInSlot(slot);
            if (input.getCount() < required.getCount() || !ItemStack.isSameItemSameComponents(input, required)) return false;
            ItemStack remainder = required.getCraftingRemainingItem();
            if (!remainder.isEmpty() && input.getCount() > 1 && (!ItemStack.isSameItemSameComponents(input, remainder)
                || input.getCount() - 1 + remainder.getCount() > input.getMaxStackSize())) return false;
        }
        return true;
    }

    public void tick() {
        if (owner.kind() == MachineKind.GENERATOR) { tickGenerator(); return; }
        if (work == null) work = findWork();
        if (work == null) { status = Status.IDLE; return; }
        if (!fits(work)) {
            status = Status.OUTPUT_BLOCKED;
            if (work.progress == 0) work = null;
            return;
        }
        if (!owner.energy().spend(work.rate)) {
            status = Status.NO_POWER;
            if (work.progress == 0) work = null;
            return;
        }
        status = Status.RUNNING;
        work.progress += owner.kind() == MachineKind.FERMENTER && exposed() ? 2 : 1;
        owner.setChanged();
        if (work.progress < work.ticks) return;
        internalChange = true;
        try {
            for (int slot = 0; slot < inputCount; slot++) {
                if (work.inputs.get(slot).isEmpty()) continue;
                ItemStack input = inventory.getStackInSlot(slot).copy();
                input.shrink(work.inputs.get(slot).getCount());
                ItemStack remainder = work.inputs.get(slot).getCraftingRemainingItem();
                if (!remainder.isEmpty()) {
                    if (input.isEmpty()) input = remainder;
                    else input.grow(remainder.getCount());
                }
                inventory.setStackInSlot(slot, input);
            }
            ItemStack output = inventory.getStackInSlot(outputSlot);
            inventory.setStackInSlot(outputSlot, output.isEmpty() ? work.result.copy() : output.copyWithCount(output.getCount() + work.result.getCount()));
            experienceStored += work.experience;
            work = null;
        } finally { internalChange = false; }
    }

    public boolean exposed() {
        return owner.getLevel() != null && owner.getLevel().canSeeSky(owner.getBlockPos().above());
    }

    private void tickGenerator() {
        status = Status.IDLE;
        int generation = fuelRemaining > 0 ? energyPerTick() : MachineKind.GENERATOR_FE_PER_TICK * (owner.upgrades().speed() > 0 ? 2 : 1);
        ItemStack fuel = inventory.getStackInSlot(0);
        int ticks = fuelRemaining == 0 ? fuel.getBurnTime(RecipeType.SMELTING) : 0;
        // Very short modded fuels can contain less energy than one upgraded generation tick.
        if (ticks > 0) generation = (int)Math.min(generation, (long)ticks * MachineKind.GENERATOR_FE_PER_TICK);
        if (owner.energy().stored() > owner.kind().energyCapacity() - generation) status = Status.BUFFER_FULL;
        else {
            if (fuelRemaining == 0) {
                if (ticks > 0) {
                    fuelRemaining = fuelTotal = ProcessingBalance.generatorFuelTicks(ticks, owner.upgrades().speed());
                    fuelGenerationRate = MachineKind.GENERATOR_FE_PER_TICK * (owner.upgrades().speed() > 0 ? 2 : 1);
                    fuelEnergyRemaining = (long)ticks * MachineKind.GENERATOR_FE_PER_TICK;
                    ItemStack remainder = fuel.getCraftingRemainingItem();
                    inventory.setStackInSlot(0, remainder.isEmpty() ? fuel.copyWithCount(fuel.getCount() - 1) : remainder);
                }
            }
            if (fuelRemaining > 0 && owner.energy().generate(energyPerTick())) {
                fuelEnergyRemaining -= energyPerTick();
                fuelRemaining--; status = Status.RUNNING; owner.setChanged();
            }
        }
        // Charging and sided push/pull share the same 32 FE/t extraction budget.
        var charger = chargingCapability();
        int available = owner.energy().availableOutput();
        if (charger != null && charger.canReceive() && available > 0) {
            boolean sideDemand = owner.energy().sidedOutputThisTick() > 0;
            for (Direction side : Direction.values()) {
                if (!owner.energySideEnabled(side)) continue;
                var neighbor = owner.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, owner.getBlockPos().relative(side), side.getOpposite());
                if (neighbor != null && neighbor.canReceive() && neighbor.receiveEnergy(available, true) > 0) sideDemand = true;
            }
            int share = sideDemand ? Math.min(available, (available + owner.energy().sidedOutputThisTick() + 1) / 2) : available;
            int accepted = charger.receiveEnergy(share, false);
            if (accepted > 0) owner.energy().extractForCharging(accepted);
        }
        owner.energy().finishCharging();
        for (Direction side : Direction.values()) {
            var source = owner.energy().view(side);
            int sideAvailable = source.extractEnergy(MachineKind.GENERATOR_FE_PER_TICK, true);
            if (sideAvailable == 0) continue;
            var destination = owner.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, owner.getBlockPos().relative(side), side.getOpposite());
            if (destination == null || !destination.canReceive()) continue;
            int accepted = destination.receiveEnergy(sideAvailable, false);
            if (accepted > 0) source.extractEnergy(accepted, false);
        }
        // A blocked or partly full neighbor leaves its unused share available to the charging item.
        available = owner.energy().availableOutput();
        if (charger != null && charger.canReceive() && available > 0) {
            int accepted = charger.receiveEnergy(available, false);
            if (accepted > 0) owner.energy().extractForCharging(accepted);
        }
    }

    public IItemHandler sideView(Direction side) {
        return new IItemHandler() {
            private boolean enabled(MachineSides.Route route) {
                return !owner.isRemoved() && owner.sides().enabled(side, owner.getBlockState().getValue(MachineBlock.FACING), route);
            }
            @Override public int getSlots() { return inventory.getSlots(); }
            @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
            @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                if (slot >= inputCount || !enabled(MachineSides.Route.INGREDIENT) || !inventory.isItemValid(slot, stack)) return false;
                return owner.kind() != MachineKind.POWDER_MIXER || !filters.get(slot).isEmpty() && filters.get(slot).is(stack.getItem());
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return isItemValid(slot, stack) ? inventory.insertItem(slot, stack, simulate) : stack;
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                boolean container = owner.kind() == MachineKind.GENERATOR && slot == 0 && !inventory.getStackInSlot(slot).isEmpty()
                    && inventory.getStackInSlot(slot).getBurnTime(RecipeType.SMELTING) == 0;
                return enabled(MachineSides.Route.OUTPUT) && (slot == outputSlot || container)
                    ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }
        };
    }

    public void awardExperience(Player player) {
        if (!(player.level() instanceof ServerLevel level) || experienceStored <= 0) return;
        int whole = (int)Math.floor(experienceStored);
        if (level.random.nextDouble() < experienceStored - whole) whole++;
        ExperienceOrb.award(level, player.position(), whole);
        experienceStored = 0; owner.setChanged();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("inventory", inventory.serializeNBT(registries));
        ListTag savedFilters = new ListTag();
        for (ItemStack filter : filters) savedFilters.add(filter.saveOptional(registries));
        tag.put("filters", savedFilters);
        tag.putInt("fuel_remaining", fuelRemaining); tag.putInt("fuel_total", fuelTotal);
        tag.putLong("fuel_energy_remaining", fuelEnergyRemaining); tag.putInt("fuel_generation_rate", fuelGenerationRate);
        tag.putDouble("experience", experienceStored);
        if (work != null) tag.put("work", work.save(registries));
        return tag;
    }
    public void load(HolderLookup.Provider registries, CompoundTag tag) {
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        ListTag savedFilters = tag.getList("filters", Tag.TAG_COMPOUND);
        for (int slot = 0; slot < inputCount; slot++) filters.set(slot, slot < savedFilters.size() ? ItemStack.parseOptional(registries, savedFilters.getCompound(slot)) : ItemStack.EMPTY);
        fuelTotal = Math.max(0, tag.getInt("fuel_total")); fuelRemaining = Math.clamp(tag.getInt("fuel_remaining"), 0, fuelTotal);
        fuelGenerationRate = tag.contains("fuel_generation_rate", Tag.TAG_INT) ? Math.clamp(tag.getInt("fuel_generation_rate"), 32, 64)
            : MachineKind.GENERATOR_FE_PER_TICK * (owner.upgrades().speed() > 0 ? 2 : 1);
        fuelEnergyRemaining = tag.contains("fuel_energy_remaining", Tag.TAG_LONG) ? Math.clamp(tag.getLong("fuel_energy_remaining"), 0L, (long)fuelRemaining * fuelGenerationRate)
            : (long)fuelRemaining * fuelGenerationRate;
        experienceStored = tag.getDouble("experience");
        if (!Double.isFinite(experienceStored) || experienceStored < 0) experienceStored = 0;
        work = tag.contains("work", Tag.TAG_COMPOUND) ? Work.load(registries, tag.getCompound("work"), inputCount) : null;
        status = Status.IDLE;
    }

    private static final class Work {
        private final NonNullList<ItemStack> inputs;
        private final ItemStack result;
        private final int ticks, rate;
        private final float experience;
        private int progress;
        private Work(NonNullList<ItemStack> inputs, ItemStack result, int ticks, int rate, float experience) {
            this.inputs = inputs; this.result = result.copy(); this.ticks = ticks; this.rate = rate; this.experience = experience;
        }
        private CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag(); ListTag saved = new ListTag();
            for (ItemStack input : inputs) saved.add(input.saveOptional(registries));
            tag.put("inputs", saved); tag.put("result", result.save(registries));
            tag.putInt("ticks", ticks); tag.putInt("rate", rate); tag.putInt("progress", progress); tag.putFloat("experience", experience);
            return tag;
        }
        private static Work load(HolderLookup.Provider registries, CompoundTag tag, int count) {
            ListTag saved = tag.getList("inputs", Tag.TAG_COMPOUND);
            ItemStack result = ItemStack.parseOptional(registries, tag.getCompound("result"));
            if (saved.size() != count || result.isEmpty() || tag.getInt("ticks") < 1 || tag.getInt("rate") < 0) return null;
            NonNullList<ItemStack> inputs = NonNullList.withSize(count, ItemStack.EMPTY);
            for (int slot = 0; slot < count; slot++) inputs.set(slot, ItemStack.parseOptional(registries, saved.getCompound(slot)));
            float experience = tag.getFloat("experience");
            Work work = new Work(inputs, result, tag.getInt("ticks"), tag.getInt("rate"), Float.isFinite(experience) && experience >= 0 ? experience : 0);
            work.progress = Math.clamp(tag.getInt("progress"), 0, work.ticks - 1);
            return work;
        }
    }
}
