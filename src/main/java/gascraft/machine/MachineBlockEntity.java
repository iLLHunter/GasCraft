package gascraft.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent machine state shared by block recovery and higher-tier crafting. */
public final class MachineBlockEntity extends BlockEntity implements MenuProvider {
    private CompoundTag machineState = new CompoundTag();
    private final MachineEnergy energy = new MachineEnergy(this);
    private MachineSides sides = new MachineSides();
    private final MachineUpgrades upgrades;
    private final ProcessingMachine processor;
    private final SynthesizerMachine synthesizer;
    private final MachineTank tank;
    private HolderLookup.Provider serializationRegistries;
    private boolean pendingLoad;

    public MachineBlockEntity(BlockPos position, BlockState state) {
        super(MachineContent.MACHINE_BLOCK_ENTITY.get(), position, state);
        upgrades = new MachineUpgrades(this);
        processor = kind().isSynthesizer() ? null : new ProcessingMachine(this);
        synthesizer = kind().isSynthesizer() ? new SynthesizerMachine(this) : null;
        tank = kind().isSynthesizer() ? new MachineTank(this) : null;
    }
    public MachineKind kind() { return ((MachineBlock)getBlockState().getBlock()).kind(); }
    public MachineEnergy energy() { return energy; }
    public MachineSides sides() { return sides; }
    public MachineUpgrades upgrades() { return upgrades; }
    public ProcessingMachine processor() { return processor; }
    public SynthesizerMachine synthesizer() { return synthesizer; }
    public MachineTank tank() { return tank; }
    public boolean operationActive() { return processor != null ? processor.active() : synthesizer.active(); }
    public void syncFermenterContents() {
        if (level != null && !level.isClientSide && kind() == MachineKind.FERMENTER)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    public void serverTick() {
        upgrades.tick();
        if (synthesizer != null) { synthesizer.tick(); return; }
        processor.tick();
        boolean lit = processor.status() == ProcessingMachine.Status.RUNNING;
        if (getBlockState().getValue(MachineBlock.LIT) != lit)
            level.setBlock(worldPosition, getBlockState().setValue(MachineBlock.LIT, lit), 3);
    }
    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return processor == null ? new gascraft.menu.SynthesizerMenu(id, inventory, this) : new gascraft.menu.ProcessingMachineMenu(id, inventory, this);
    }
    @Override public void setLevel(Level level) {
        super.setLevel(level);
        serializationRegistries = level.registryAccess();
        if (pendingLoad) loadContents(serializationRegistries);
    }
    public boolean energySideEnabled(Direction side) { return sides.enabled(side, getBlockState().getValue(MachineBlock.FACING), MachineSides.Route.ENERGY); }
    public void toggleSide(MachineSides.Face face, MachineSides.Route route) {
        sides.toggle(face, route);
        setChanged();
        invalidateCapabilities();
    }
    public CompoundTag machineState() {
        CompoundTag result = machineState.copy();
        result.putInt("energy", energy.stored());
        sides.save(result);
        HolderLookup.Provider registries = level != null ? level.registryAccess() : serializationRegistries;
        if (registries != null && !pendingLoad) {
            result.put("installed_upgrades", upgrades.save(registries));
            if (processor != null) result.put("processor", processor.save(registries));
            if (synthesizer != null) { result.put("synthesizer", synthesizer.save(registries)); result.put("tank", tank.save(registries)); }
        }
        return result;
    }
    public void restoreMachineState(CompoundTag state) {
        machineState = state.copy();
        energy.restore(state.getInt("energy"));
        sides = new MachineSides();
        sides.load(state);
        pendingLoad = true;
        HolderLookup.Provider registries = level != null ? level.registryAccess() : serializationRegistries;
        if (registries != null) loadContents(registries);
        setChanged();
        invalidateCapabilities();
        syncFermenterContents();
    }
    private void loadContents(HolderLookup.Provider registries) {
        upgrades.load(registries, machineState.getCompound("installed_upgrades"));
        if (processor != null) processor.load(registries, machineState.getCompound("processor"));
        if (synthesizer != null) { synthesizer.load(registries, machineState.getCompound("synthesizer")); tank.load(registries, machineState.getCompound("tank")); }
        pendingLoad = false;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        serializationRegistries = registries;
        tag.put("machine_state", machineState());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        serializationRegistries = registries;
        restoreMachineState(tag.getCompound("machine_state"));
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
