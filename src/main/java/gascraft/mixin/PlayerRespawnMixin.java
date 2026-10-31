package gascraft.mixin;
import gascraft.gas.GasTeleports;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
@Mixin(PlayerList.class)
abstract class PlayerRespawnMixin {
    @WrapMethod(method="respawn") private ServerPlayer gascraft$respawn(ServerPlayer player,boolean alive,Entity.RemovalReason reason,Operation<ServerPlayer> original){GasTeleports.beginInternal();try{return original.call(player,alive,reason);}finally{GasTeleports.endInternal();}}
}
