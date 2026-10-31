package gascraft.mixin;
import gascraft.gas.GasFields;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo @Mixin(targets={"elucent.rootsclassic.item.StaffItem","elucent.rootsclassic.item.CrystalStaffItem","com.sammy.malum.common.item.curiosities.weapons.staff.AbstractStaffItem","com.stal111.forbidden_arcanus.common.item.MagicWandItem"},remap=false)
abstract class MagicItemMixin {
    @Inject(method="use",at=@At("HEAD"),cancellable=true,remap=false)
    private void gascraft$use(Level level,Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResultHolder<ItemStack>> ci){if(GasFields.blocksMagic(player))ci.setReturnValue(InteractionResultHolder.fail(player.getItemInHand(hand)));}
    @Inject(method="releaseUsing",at=@At("HEAD"),cancellable=true,remap=false)
    private void gascraft$release(ItemStack item,Level level,LivingEntity entity,int time,CallbackInfo ci){if(GasFields.blocksMagic(entity))ci.cancel();}
}
