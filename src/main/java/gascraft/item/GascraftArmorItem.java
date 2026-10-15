package gascraft.item;

import gascraft.gas.GasCatalog;
import gascraft.ModContent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;

public final class GascraftArmorItem extends ArmorItem {
    private final String texture;
    public GascraftArmorItem(Type type, Properties properties, String texture) {
        super(ModContent.GAS_MASK_MATERIAL, type, properties);
        this.texture = texture;
    }
    @Override public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        return GasCatalog.id("textures/entity/" + texture + ".png");
    }
}
