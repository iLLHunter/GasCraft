package gascraft.mixin;
import gascraft.gas.GasFields;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo @Mixin(targets="io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob",remap=false)
abstract class IronCasterMixin {
    @Inject(method="initiateCastSpell",at=@At("HEAD"),cancellable=true,remap=false)
    private void gascraft$cast(CallbackInfo ci){var entity=(LivingEntity)(Object)this;if(GasFields.blocksMagic(entity)||GasFields.blocksAttacks(entity))ci.cancel();}
}
