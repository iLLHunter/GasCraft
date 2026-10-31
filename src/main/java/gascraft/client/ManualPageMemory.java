package gascraft.client;

import java.util.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Local per-player bookmarks last only for the current world connection. */
@EventBusSubscriber(modid="gascraft",value=Dist.CLIENT)
public final class ManualPageMemory {
    private static final Map<UUID,Integer> PAGES=new HashMap<>();
    public static int page(UUID player,int count){return Math.clamp(PAGES.getOrDefault(player,0),0,Math.max(0,count-1));}
    public static void remember(UUID player,int page){PAGES.put(player,Math.max(0,page));}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){PAGES.clear();}
    private ManualPageMemory(){}
}
