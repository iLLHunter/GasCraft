package gascraft.client;
import com.mojang.blaze3d.vertex.PoseStack;
import gascraft.ModContent;
import gascraft.gas.GasCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.*;

public final class CuriosRenderers implements ICurioRenderer {
    private final boolean mask;
    private HumanoidModel<LivingEntity> model;
    private CuriosRenderers(boolean mask) { this.mask=mask; }
    public static void register() {
        CuriosRendererRegistry.register(ModContent.GAS_MASK.get(), () -> new CuriosRenderers(true));
        CuriosRendererRegistry.register(ModContent.GRENADE_HOLDER.get(), () -> new CuriosRenderers(false));
    }
    @Override public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext context, PoseStack pose, RenderLayerParent<T,M> parent, MultiBufferSource buffers, int light, float limbSwing, float limbAmount, float partialTick, float age, float headYaw, float headPitch) {
        if (Boolean.getBoolean("gascraft.clientGameplay")) context.entity().getPersistentData().putBoolean(mask?"gascraft:rendered_mask":"gascraft:rendered_grenade_holder",true);
        if (model == null) model = mask ? new GasMaskModel(Minecraft.getInstance().getEntityModels().bakeLayer(GascraftClient.MASK)) : new GrenadeHolderModel(Minecraft.getInstance().getEntityModels().bakeLayer(GascraftClient.HOLDER));
        model.setupAnim(context.entity(),limbSwing,limbAmount,age,headYaw,headPitch);
        if (parent.getModel() instanceof HumanoidModel<?> humanoid) { model.head.copyFrom(humanoid.head); model.body.copyFrom(humanoid.body); model.crouching=humanoid.crouching; }
        pose.pushPose(); ICurioRenderer.translateIfSneaking(pose,context.entity());
        model.renderToBuffer(pose,buffers.getBuffer(RenderType.armorCutoutNoCull(GasCatalog.id("textures/entity/"+(mask?"gas_mask":"grenade_holder")+".png"))),light,OverlayTexture.NO_OVERLAY,-1);
        pose.popPose();
    }
}
