package gascraft.client;
import gascraft.GascraftConfig;
import gascraft.gas.GasDefinitionSync;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid="gascraft",value=Dist.CLIENT)
public final class ManualConfigVisibility {
    private static Boolean previous;
    private static long previousRevision=-1;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null){previous=null;previousRevision=-1;GascraftConfig.CLIENT_USE_MANUAL=null;gascraft.gas.GasChainControls.clearClient();return;}
        boolean enabled=GascraftConfig.clientManualEnabled();long revision=gascraft.gas.GasChainControls.clientRevision();if(previous!=null&&previous==enabled&&previousRevision==revision)return;previous=enabled;previousRevision=revision;
        var parameters=new CreativeModeTab.ItemDisplayParameters(mc.level.enabledFeatures(),mc.player.canUseGameMasterBlocks(),mc.level.registryAccess());
        // Search contents are derived from categories, matching vanilla's rebuild order.
        var tabs=CreativeModeTabs.allTabs();
        for(var tab:tabs)if(tab.getType()==CreativeModeTab.Type.CATEGORY)tab.buildContents(parameters);
        for(var tab:tabs)if(tab.getType()!=CreativeModeTab.Type.CATEGORY)tab.buildContents(parameters);
        GasDefinitionSync.clientCatalogChanged.run();
        if(!enabled&&mc.screen instanceof ManualRecipeScreen)mc.setScreen(null);
    }
}
