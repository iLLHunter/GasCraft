package gascraft.mixin;
import gascraft.gas.GasFields;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(targets="net.minecraft.world.entity.monster.SpellcasterIllager$SpellcasterUseSpellGoal")
abstract class IllagerSpellMixin {
    @Shadow @Final private SpellcasterIllager this$0;
    @Shadow protected int attackWarmupDelay;
    @Inject(method={"canUse","canContinueToUse"},at=@At("HEAD"),cancellable=true)
    private void gascraft$blocked(CallbackInfoReturnable<Boolean> ci){if(GasFields.blocksMagic(this$0)||GasFields.blocksAttacks(this$0))ci.setReturnValue(false);}
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void gascraft$interrupt(CallbackInfo ci){if(GasFields.blocksMagic(this$0)||GasFields.blocksAttacks(this$0)){attackWarmupDelay=0;ci.cancel();}}
}
