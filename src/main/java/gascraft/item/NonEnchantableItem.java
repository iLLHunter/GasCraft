package gascraft.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/** Gas consumables reject enchanting tables and anvil books, including in Creative. */
public class NonEnchantableItem extends Item {
    public NonEnchantableItem(Properties properties){super(properties);}
    @Override public boolean isEnchantable(ItemStack stack){return false;}
    @Override public int getEnchantmentValue(){return 0;}
    @Override public int getEnchantmentValue(ItemStack stack){return 0;}
    @Override public boolean supportsEnchantment(ItemStack stack,Holder<Enchantment> enchantment){return false;}
    @Override public boolean isBookEnchantable(ItemStack stack,ItemStack book){return false;}
}
