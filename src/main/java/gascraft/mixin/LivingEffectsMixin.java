package gascraft.mixin;
import gascraft.gas.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(LivingEntity.class)
public abstract class LivingEffectsMixin implements GasPotionRemoval {
    @Inject(method="addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",at=@At("HEAD"),cancellable=true)
    private void gascraft$preventPotion(MobEffectInstance effect,Entity source,CallbackInfoReturnable<Boolean> ci){var entity=(LivingEntity)(Object)this;if(!entity.level().isClientSide&&!(effect.getEffect().value() instanceof GasFieldIndicator)&&GasFields.blocksMagic(entity))ci.setReturnValue(false);}
    @Inject(method="forceAddEffect",at=@At("HEAD"),cancellable=true)
    private void gascraft$preventForcedPotion(MobEffectInstance effect,Entity source,CallbackInfo ci){var entity=(LivingEntity)(Object)this;if(!entity.level().isClientSide&&!(effect.getEffect().value() instanceof GasFieldIndicator)&&GasFields.blocksMagic(entity))ci.cancel();}
    @Shadow protected abstract void onEffectRemoved(MobEffectInstance effect);
    @Inject(method="setAbsorptionAmount",at=@At("HEAD"))
    private void gascraft$absorption(float amount,CallbackInfo ci){GasSizing.absorptionChanging((LivingEntity)(Object)this,amount);}
    @Override public void gascraft$effectRemoved(MobEffectInstance effect){onEffectRemoved(effect);}
    @ModifyVariable(method="travel",at=@At("HEAD"),argsOnly=true)
    private net.minecraft.world.phys.Vec3 gascraft$gravityMovement(net.minecraft.world.phys.Vec3 movement){return GasFields.gravityHeld((Entity)(Object)this)?net.minecraft.world.phys.Vec3.ZERO:movement;}
    @Inject(method="randomTeleport",at=@At("HEAD"),cancellable=true)
    private void gascraft$randomTeleport(CallbackInfoReturnable<Boolean> ci){if(GasTeleports.blocked((Entity)(Object)this))ci.setReturnValue(false);}
}
