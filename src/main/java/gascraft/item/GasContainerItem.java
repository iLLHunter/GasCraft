package gascraft.item;

import gascraft.ModContent;
import gascraft.gas.GasCatalog;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import java.util.List;

public class GasContainerItem extends NonEnchantableItem {
    public GasContainerItem(Properties properties) { super(properties.stacksTo(1)); }
    public static GasCatalog.GasDefinition containedGas(ItemStack stack) { return GasCatalog.gasDefinition(stack.getOrDefault(ModContent.GAS_CONTENTS, GasCatalog.id("empty_contents"))); }
    public static boolean hasContents(ItemStack stack) { return GasCatalog.contains(stack.get(ModContent.GAS_CONTENTS)); }
    public static ItemStack createFilledContainer(Item item, ResourceLocation gas) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModContent.GAS_CONTENTS, gas);
        return stack;
    }
    @Override public Component getName(ItemStack stack) {
        if (!hasContents(stack)) return Component.translatable(stack.getItem() instanceof GrenadeItem ? "item.gascraft.grenade_shell" : "item.gascraft.empty_canister");
        return Component.translatable(getDescriptionId(), Component.translatable("gas." + containedGas(stack).id().toString().replace(':','.')));
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (!hasContents(stack)) return;
        tooltip.add(Component.translatable("tooltip.gascraft.gas", Component.translatable("gas." + containedGas(stack).id().toString().replace(':','.'))));
        containedGas(stack).agents().stream().filter(agent -> !agent.translationKey().equals("agent.gascraft.misted_meat"))
            .forEach(agent -> tooltip.add(Component.translatable(agent.translationKey(), Component.translatable("effect."+agent.effect().replace(':','.')))));
        if (!containedGas(stack).id().equals(GasCatalog.id("stun_gas")) || this instanceof GrenadeItem)
            containedGas(stack).tooltipLines().forEach(line -> tooltip.add(line.equals("tooltip.gascraft.boogie_gas.4") ? Component.translatable(line).withStyle(net.minecraft.ChatFormatting.ITALIC) : Component.translatable(line)));
        if(this instanceof GrenadeItem&&containedGas(stack).id().equals(GasCatalog.id("misted_cologne")))tooltip.add(Component.translatable("tooltip.gascraft.misted_cologne.flavor").withStyle(net.minecraft.ChatFormatting.ITALIC));
        if(this instanceof GrenadeItem&&containedGas(stack).id().equals(GasCatalog.id("taco_chime_gas")))tooltip.add(Component.translatable("tooltip.gascraft.taco_chime_gas.flavor").withStyle(net.minecraft.ChatFormatting.ITALIC));
        if (flag.isAdvanced()) tooltip.add(Component.literal(containedGas(stack).id().toString()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
