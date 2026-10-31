package gascraft.mixin;
import gascraft.gas.GasTeleports;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin({Entity.class,ServerPlayer.class})
abstract class EntityTeleportMixin {
    @Inject(method="teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z",at=@At("HEAD"),cancellable=true)
    private void gascraft$worldTeleport(CallbackInfoReturnable<Boolean> ci){if(GasTeleports.blocked((Entity)(Object)this))ci.setReturnValue(false);}
    @Inject(method={"teleportTo(DDD)V","teleportRelative(DDD)V"},at=@At("HEAD"),cancellable=true)
    private void gascraft$teleport(CallbackInfo ci){if(GasTeleports.blocked((Entity)(Object)this))ci.cancel();}
    @Inject(method="changeDimension",at=@At("HEAD"),cancellable=true)
    private void gascraft$dimension(CallbackInfoReturnable<Entity> ci){if(GasTeleports.blocked((Entity)(Object)this))ci.setReturnValue(null);}
}
