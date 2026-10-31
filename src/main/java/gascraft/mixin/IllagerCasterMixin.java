package gascraft.mixin;
import gascraft.gas.GasCastInterrupt;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import org.spongepowered.asm.mixin.*;
@Mixin(SpellcasterIllager.class)
abstract class IllagerCasterMixin implements GasCastInterrupt {
    @Shadow protected int spellCastingTickCount;
    @Override public void gascraft$interruptCast(){spellCastingTickCount=0;((SpellcasterIllager)(Object)this).setIsCastingSpell(SpellcasterIllager.IllagerSpell.NONE);}
}
