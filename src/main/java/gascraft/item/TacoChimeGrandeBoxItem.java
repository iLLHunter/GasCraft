package gascraft.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class TacoChimeGrandeBoxItem extends Item {
    public TacoChimeGrandeBoxItem(Properties properties) {
        super(properties.food(new FoodProperties.Builder().nutrition(44).saturationModifier(68F / 88F).build()));
    }

    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity eater) {
        if(!gascraft.gas.GasChainControls.enabled(gascraft.gas.GasCatalog.id("taco_chime_gas")))return stack;
        int roll = level.isClientSide ? -1 : eater.getRandom().nextInt(100);
        ItemStack remaining = super.finishUsingItem(stack, level, eater);
        if (!level.isClientSide) {
            // One uniform roll: 5% Nausea, 20% Nourishment, 1% fart, 74% neither.
            if (roll < 5) eater.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 400, 0));
            else if (roll < 25) BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.fromNamespaceAndPath("farmersdelight", "nourishment"))
                .ifPresent(effect -> eater.addEffect(new MobEffectInstance(effect, 6000, 0)));
            else if (roll == 25 && level instanceof net.minecraft.server.level.ServerLevel server
                && gascraft.gas.GasCatalog.contains(gascraft.gas.GasCatalog.id("taco_chime_gas")))
                GrenadeItem.spawnGasCloud(server, eater.position(), GasCanisterItem.createFilledCanister(gascraft.gas.GasCatalog.id("taco_chime_gas")), null);
        }
        return remaining;
    }

    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand){
        return gascraft.gas.GasChainControls.enabled(gascraft.gas.GasCatalog.id("taco_chime_gas"))?super.use(level,player,hand):net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand));
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gascraft.meal_effect", Component.translatable("effect.farmersdelight.nourishment"), "05:00", "20%")
            .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.gascraft.meal_effect", Component.translatable(MobEffects.CONFUSION.value().getDescriptionId()), "0:20", "5%")
            .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.gascraft.meal_fart",Component.translatable("meal.gascraft.taco_chime_fart"),"1%"));
    }
}
