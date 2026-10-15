package gascraft;

import gascraft.gas.GasCatalog;
import gascraft.gas.GasExposure;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Gascraft.MOD_ID)
public final class Gascraft {
    public static final String MOD_ID = "gascraft";

    public Gascraft(IEventBus bus, ModContainer container) {
        GasCatalog.initialize();
        ModContent.register(bus);
        gascraft.machine.MachineContent.register(bus);
        if (net.neoforged.fml.ModList.get().isLoaded("mekanism")) gascraft.compat.MekanismChemicalCompat.register(bus);
        gascraft.recipe.MachineRecipes.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, GascraftConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, GascraftConfig.CLIENT_SPEC);
        bus.addListener(GasExposure::registerPayloads);
        bus.addListener(gascraft.gas.GasParalysis::registerPayloads);
        bus.addListener(gascraft.gas.GasFlash::registerPayloads);
        bus.addListener(gascraft.gas.GasDefinitionSync::register);
        bus.addListener(gascraft.machine.MachineRecipeTransfer::register);
        bus.addListener(ModContent::commonSetup);
        NeoForge.EVENT_BUS.addListener(GasExposure::onLivingGasTick);
        NeoForge.EVENT_BUS.addListener(GasExposure::onPlayerGasTick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, GasExposure::onPlayerDeath);
        NeoForge.EVENT_BUS.addListener(GasExposure::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(GasExposure::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(gascraft.gas.GasControl::onGasTargetChange);
        NeoForge.EVENT_BUS.addListener(ModContent::addSulfurLoot);
        NeoForge.EVENT_BUS.addListener(gascraft.gas.GasDefinitionLoader::addListeners);
        NeoForge.EVENT_BUS.addListener(gascraft.gas.GasDefinitionSync::sync);
    }
}
