package gascraft.compat;

import gascraft.gas.GasCatalog;
import gascraft.gas.GasDefinitionLoader;
import gascraft.machine.MachineBlockEntity;
import gascraft.machine.MachineContent;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;

/** Loaded only when Mekanism is installed; delegates chemical identity and piping to its native API. */
public final class MekanismChemicalCompat {
    public static final BlockCapability<IChemicalHandler, Direction> CHEMICAL = BlockCapability.createSided(
        ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);
    public static void register(IEventBus bus) {
        // NeoForge 21.1 fires this after all RegisterEvents and before freezeData. The chemical
        // RegisterEvent precedes condition-codec registration, so conditional files need this phase.
        bus.addListener((BlockEntityTypeAddBlocksEvent event) -> GasDefinitionLoader.startupChemicalDefinitions().forEach((id, color) ->
            net.minecraft.core.Registry.register(MekanismAPI.CHEMICAL_REGISTRY, id, new Chemical(ChemicalBuilder.builder().tint(color & 0xffffff).gaseous()))));
        bus.addListener((RegisterCapabilitiesEvent event) -> event.registerBlockEntity(CHEMICAL, MachineContent.MACHINE_BLOCK_ENTITY.get(),
            (machine, side) -> machine.kind().isSynthesizer() ? view(machine, side) : null));
    }
    public static boolean inTag(ResourceLocation chemical, ResourceLocation tag) {
        return MekanismAPI.CHEMICAL_REGISTRY.getOptional(chemical).map(value -> value.getAsHolder().is(TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, tag))).orElse(false);
    }
    public static int color(ResourceLocation chemical) { return MekanismAPI.CHEMICAL_REGISTRY.get(chemical).getTint() | 0xff000000; }
    public static net.minecraft.network.chat.Component name(ResourceLocation chemical) { return MekanismAPI.CHEMICAL_REGISTRY.get(chemical).getTextComponent(); }
    public static IChemicalHandler view(MachineBlockEntity machine, Direction side) {
        return new IChemicalHandler() {
            @Override public int getChemicalTanks() { return 1; }
            @Override public ChemicalStack getChemicalInTank(int tank) {
                var id = machine.tank().chemical();
                return id == null ? ChemicalStack.EMPTY : MekanismAPI.CHEMICAL_REGISTRY.getOptional(id)
                    .map(value -> new ChemicalStack(value, machine.tank().amount())).orElse(ChemicalStack.EMPTY);
            }
            @Override public void setChemicalInTank(int tank, ChemicalStack stack) { throw new UnsupportedOperationException("Use chemical insertion"); }
            @Override public long getChemicalTankCapacity(int tank) { return machine.tank().capacity(); }
            @Override public boolean isValid(int tank, ChemicalStack stack) { return !stack.isEmpty(); }
            @Override public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
                if (tank != 0 || stack.isEmpty() || !machine.tank().enabled(side)) return stack;
                int accepted = machine.tank().fillChemical(MekanismAPI.CHEMICAL_REGISTRY.getKey(stack.getChemical()), stack.getAmount(), action.execute());
                return accepted == stack.getAmount() ? ChemicalStack.EMPTY : stack.copyWithAmount(stack.getAmount() - accepted);
            }
            @Override public ChemicalStack extractChemical(int tank, long amount, Action action) { return ChemicalStack.EMPTY; }
        };
    }
    private MekanismChemicalCompat() {}
}
