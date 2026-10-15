package gascraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import gascraft.machine.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.*;

/** Stationary input/output items beneath the Fermenter's glass window. */
public final class FermenterContentsRenderer implements BlockEntityRenderer<MachineBlockEntity> {
    private final ItemRenderer items;
    public FermenterContentsRenderer(BlockEntityRendererProvider.Context context) { items = context.getItemRenderer(); }
    public static ItemStack displayedItem(MachineBlockEntity machine) {
        if (machine.kind() != MachineKind.FERMENTER) return ItemStack.EMPTY;
        var inventory = machine.processor().inventory();
        ItemStack output = inventory.getStackInSlot(1);
        return output.isEmpty() ? inventory.getStackInSlot(0) : output;
    }
    @Override public void render(MachineBlockEntity machine, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = displayedItem(machine);
        if (stack.isEmpty()) return;
        int windowLight = net.minecraft.client.renderer.LevelRenderer.getLightColor(machine.getLevel(), machine.getBlockPos().above());
        int count = Math.min(5, 1 + (stack.getCount() - 1) / 16);
        float[][] offsets = {{0,0},{-.12F,-.10F},{.12F,.10F},{-.10F,.12F},{.10F,-.12F}};
        for (int i = 0; i < count; i++) {
            pose.pushPose();
            pose.translate(.5 + offsets[i][0], .28 + i * .006, .5 + offsets[i][1]);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            pose.mulPose(Axis.ZP.rotationDegrees(i * 47));
            pose.scale(.32F, .32F, .32F);
            items.renderStatic(stack, ItemDisplayContext.FIXED, windowLight, overlay, pose, buffers, machine.getLevel(), (int)machine.getBlockPos().asLong() + i);
            pose.popPose();
        }
    }
}
