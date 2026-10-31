package gascraft.mixin;
import gascraft.gas.GasTeleports;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerPlayer.class)
abstract class ServerPlayerTeleportMixin {
    @Inject(method="teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V",at=@At("HEAD"),cancellable=true)
    private void gascraft$directWorldTeleport(CallbackInfo ci){if(GasTeleports.blocked((ServerPlayer)(Object)this))ci.cancel();}
}
