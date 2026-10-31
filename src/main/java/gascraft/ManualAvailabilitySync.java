package gascraft;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Propagate midgame availability changes without mutating the server's config from a client. */
@EventBusSubscriber(modid="gascraft")
public final class ManualAvailabilitySync {
    private static final Map<MinecraftServer,Boolean> LAST=new WeakHashMap<>();
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        var server=event.getServer();boolean enabled=GascraftConfig.manualEnabled();
        var previous=LAST.put(server,enabled);if(previous!=null&&previous==enabled)return;
        for(var player:server.getPlayerList().getPlayers())gascraft.gas.GasDefinitionSync.sendCatalog(player);
    }
}
