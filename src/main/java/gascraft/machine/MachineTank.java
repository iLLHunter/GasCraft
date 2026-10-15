package gascraft.machine;

import gascraft.compat.MekanismChemicalCompat;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** One shared space for a fluid or a native chemical, with a shared per-tick insertion budget. */
public final class MachineTank {
    private final MachineBlockEntity owner;
    private FluidStack fluid = FluidStack.EMPTY;
    private ResourceLocation chemical;
    private int chemicalAmount;
    private long transferTick = Long.MIN_VALUE;
    private int inserted;
    public MachineTank(MachineBlockEntity owner) { this.owner = owner; }
    public FluidStack fluid() { return fluid.copy(); }
    public ResourceLocation chemical() { return chemicalAmount > 0 ? chemical : null; }
    public int amount() { return chemical() != null ? chemicalAmount : fluid.getAmount(); }
    public int capacity() { return owner.kind().tankCapacity(); }
    public boolean empty() { return amount() == 0; }
    private boolean server() { return owner.getLevel() != null && !owner.getLevel().isClientSide && !owner.isRemoved(); }
    public boolean enabled(Direction side) { return !owner.isRemoved() && owner.sides().enabled(side, owner.getBlockState().getValue(MachineBlock.FACING), MachineSides.Route.TANK); }
    private int insertionLimit() {
        long now = owner.getLevel() == null ? 0 : owner.getLevel().getGameTime();
        if (now != transferTick) { transferTick = now; inserted = 0; }
        return Math.min(capacity() - amount(), owner.kind().tankInputPerTick() - inserted);
    }
    private void changed() {
        owner.setChanged();
        if (server()) owner.getLevel().sendBlockUpdated(owner.getBlockPos(), owner.getBlockState(), owner.getBlockState(), 2);
    }
    public int fillFluid(FluidStack offered, boolean execute) {
        if (!server() || offered.isEmpty() || chemical() != null || !fluid.isEmpty() && !FluidStack.isSameFluidSameComponents(fluid, offered)) return 0;
        int accepted = Math.min(offered.getAmount(), insertionLimit());
        if (execute && accepted > 0) {
            fluid = offered.copyWithAmount(fluid.getAmount() + accepted); inserted += accepted; changed();
        }
        return accepted;
    }
    public int fillChemical(ResourceLocation id, long offered, boolean execute) {
        if (!server() || id == null || offered <= 0 || !fluid.isEmpty() || chemical() != null && !chemical.equals(id)) return 0;
        int accepted = (int)Math.min(offered, insertionLimit());
        if (execute && accepted > 0) { chemical = id; chemicalAmount += accepted; inserted += accepted; changed(); }
        return accepted;
    }
    public boolean consume(int amount) {
        if (amount < 0 || amount > amount()) return false;
        if (chemical() != null) { chemicalAmount -= amount; if (chemicalAmount == 0) chemical = null; }
        else { fluid.shrink(amount); if (fluid.isEmpty()) fluid = FluidStack.EMPTY; }
        changed(); return true;
    }
    public boolean canDump() { return owner.synthesizer() == null || owner.synthesizer().reservedTank() == 0; }
    public boolean dump() {
        if (!server() || !canDump()) return false;
        fluid = FluidStack.EMPTY; chemical = null; chemicalAmount = 0; changed(); return true;
    }
    public IFluidHandler view(Direction side) {
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int tank) { return fluid(); }
            @Override public int getTankCapacity(int tank) { return capacity(); }
            @Override public boolean isFluidValid(int tank, FluidStack stack) { return !stack.isEmpty(); }
            @Override public int fill(FluidStack stack, FluidAction action) { return enabled(side) ? fillFluid(stack, action.execute()) : 0; }
            @Override public FluidStack drain(FluidStack stack, FluidAction action) { return FluidStack.EMPTY; }
            @Override public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }
        };
    }
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag(); tag.put("fluid", fluid.saveOptional(registries));
        if (chemical() != null) { tag.putString("chemical", chemical.toString()); tag.putInt("chemical_amount", chemicalAmount); }
        return tag;
    }
    public void load(HolderLookup.Provider registries, CompoundTag tag) {
        fluid = FluidStack.parseOptional(registries, tag.getCompound("fluid"));
        if (fluid.getAmount() > capacity()) fluid.setAmount(capacity());
        chemical = fluid.isEmpty() && tag.contains("chemical", net.minecraft.nbt.Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString("chemical")) : null;
        chemicalAmount = chemical == null ? 0 : Math.clamp(tag.getInt("chemical_amount"), 0, capacity());
    }
    public boolean chemicalTag(ResourceLocation tag) {
        return chemical() != null && ModList.get().isLoaded("mekanism") && MekanismChemicalCompat.inTag(chemical, tag);
    }
}
