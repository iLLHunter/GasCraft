package gascraft.client;

import gascraft.Gascraft;
import gascraft.gas.GasParalysis;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid=Gascraft.MOD_ID,value=Dist.CLIENT)
public final class ClientParalysis {
    @SubscribeEvent public static void movement(MovementInputUpdateEvent event) {
        if (!GasParalysis.clientParalyzed || event.getEntity()!=Minecraft.getInstance().player) return;
        var input=event.getInput();
        input.forwardImpulse=0; input.leftImpulse=0;
        input.up=false; input.down=false; input.left=false; input.right=false; input.jumping=false;
    }
    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        if (GasParalysis.clientParalyzed && Minecraft.getInstance().screen==null) {
            event.setCanceled(true); event.setSwingHand(false);
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { GasParalysis.clientParalyzed=false; }
    @SubscribeEvent public static void clonePlayer(ClientPlayerNetworkEvent.Clone event) { GasParalysis.clientParalyzed=false; }
    private ClientParalysis() {}
}
