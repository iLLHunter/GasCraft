package gascraft.mixin;
import gascraft.gas.GasTeleports;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.*;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class PlayerConnectionTeleportMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="teleport(DDDFFLjava/util/Set;)V",at=@At("HEAD"),cancellable=true)
    private void gascraft$teleport(CallbackInfo ci){if(GasTeleports.blocked(player))ci.cancel();}
    @WrapOperation(method={"handleMovePlayer","updateAwaitingTeleport"},at=@At(value="INVOKE",target="Lnet/minecraft/server/network/ServerGamePacketListenerImpl;teleport(DDDFF)V"))
    private void gascraft$correction(ServerGamePacketListenerImpl connection,double x,double y,double z,float yaw,float pitch,Operation<Void> original){GasTeleports.correct(()->original.call(connection,x,y,z,yaw,pitch));}
}
