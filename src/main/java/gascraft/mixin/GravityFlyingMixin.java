package gascraft.mixin;
import gascraft.gas.GasFields;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(FlyingMob.class)
abstract class GravityFlyingMixin {
    @ModifyVariable(method="travel",at=@At("HEAD"),argsOnly=true)
    private Vec3 gascraft$movement(Vec3 movement){return GasFields.gravityHeld((Entity)(Object)this)?Vec3.ZERO:movement;}
}
