package gascraft.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.LivingEntity;

/** grenade holder geometry for the equipment renderer. */
public final class GrenadeHolderModel extends HumanoidModel<LivingEntity> {
    public GrenadeHolderModel(ModelPart root) { super(root); }
    @Override public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer vertices, int light, int overlay, int color) {
        pose.pushPose();
        pose.translate(2.3 / 16, 3.9 / 16, -2.5 / 16);
        pose.scale(0.5F, 0.5F, 0.5F);
        pose.translate(-2.3 / 16, -3.9 / 16, 2.5 / 16);
        super.renderToBuffer(pose, vertices, light, overlay, color);
        pose.popPose();
    }
    public static LayerDefinition createHolderLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
        for (String part : new String[] {"head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"})
            mesh.getRoot().addOrReplaceChild(part, CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition Bandoulier = body.addOrReplaceChild("Bandoulier", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -2.0F, 0.0F, 12.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(2.3F, 3.9F, -2.5F, 0.0F, 0.0F, -1.1812388377497625F));
        PartDefinition shape48 = Bandoulier.addOrReplaceChild("shape48", CubeListBuilder.create().texOffs(2, 5).addBox(0.0F, 0.0F, 0.0F, 1.0F, 4.0F, 8.0F), PartPose.offsetAndRotation(8.0F, -2.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition EmptySlot4 = Bandoulier.addOrReplaceChild("EmptySlot4", CubeListBuilder.create().texOffs(0, 5).addBox(-1.5F, 0.0F, -1.0F, 3.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(5.5F, -0.5F, -0.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition shape15 = Bandoulier.addOrReplaceChild("shape15", CubeListBuilder.create().texOffs(26, 0).addBox(-12.0F, -4.0F, 0.0F, 12.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(-3.0F, 2.0F, 0.0F, 0.0F, 0.0F, 0.5592034923389831F));
        PartDefinition shape15_1 = Bandoulier.addOrReplaceChild("shape15_1", CubeListBuilder.create().texOffs(12, 5).addBox(-3.0F, -2.0F, -1.0F, 12.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 10.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition EmptySlot3 = Bandoulier.addOrReplaceChild("EmptySlot3", CubeListBuilder.create().texOffs(52, 0).addBox(-1.5F, 0.0F, -1.0F, 3.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(1.5F, -0.5F, -0.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition shape27 = shape15.addOrReplaceChild("shape27", CubeListBuilder.create().texOffs(48, 5).addBox(-1.5F, 0.0F, -1.0F, 3.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(-8.5F, -2.5F, -0.5F, 0.0F, 0.0F, 0.06283185307179587F));
        PartDefinition Bag = shape15.addOrReplaceChild("Bag", CubeListBuilder.create().texOffs(20, 10).addBox(-5.5F, -1.5F, -3.0F, 10.0F, 6.0F, 4.0F), PartPose.offsetAndRotation(-4.3F, -3.5F, 11.0F, -0.12566370614359174F, 0.0F, -0.06283185307179587F));
        PartDefinition shape15_2 = shape15.addOrReplaceChild("shape15_2", CubeListBuilder.create().texOffs(38, 5).addBox(-4.0F, -4.0F, 0.0F, 4.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(-12.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.6283185307179586F));
        PartDefinition EmptySlot2 = shape15.addOrReplaceChild("EmptySlot2", CubeListBuilder.create().texOffs(48, 15).addBox(-1.5F, 0.0F, -1.0F, 3.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(-0.5F, -2.8F, -0.5F, 0.0F, 0.0F, -0.25132741228718347F));
        PartDefinition shape27_1 = shape15.addOrReplaceChild("shape27_1", CubeListBuilder.create().texOffs(48, 10).addBox(-1.5F, 0.0F, -1.0F, 3.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(-4.5F, -2.5F, -0.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition shape53 = shape15.addOrReplaceChild("shape53", CubeListBuilder.create().texOffs(0, 20).addBox(0.0F, 0.0F, -1.0F, 12.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(-12.0F, -4.0F, 10.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition shape15_3 = shape53.addOrReplaceChild("shape15_3", CubeListBuilder.create().texOffs(23, 32).addBox(-4.0F, -4.0F, -1.0F, 4.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 0.0F, 0.6283185307179586F));
        PartDefinition shape22 = Bag.addOrReplaceChild("shape22", CubeListBuilder.create().texOffs(36, 20).addBox(-4.5F, 0.0F, -4.0F, 8.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 4.0F, 1.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition EmptySlot1 = shape15_2.addOrReplaceChild("EmptySlot1", CubeListBuilder.create().texOffs(26, 20).addBox(-1.5F, 0.0F, -1.0F, 3.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(-1.5F, -2.5F, -0.5F, 0.0F, 0.0F, -0.12566370614359174F));
        PartDefinition shape23 = Bag.addOrReplaceChild("shape23", CubeListBuilder.create().texOffs(0, 25).addBox(-4.5F, 0.0F, 0.0F, 9.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-0.5F, -2.0F, -3.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Nade2 = shape27_1.addOrReplaceChild("Nade2", CubeListBuilder.create().texOffs(46, 28).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(0.3F, -1.7F, 0.2F, 0.0F, 0.0F, 0.12566370614359174F));
        PartDefinition Nade1 = shape27.addOrReplaceChild("Nade1", CubeListBuilder.create().texOffs(46, 28).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(-0.1F, -1.7F, 0.2F, 0.0F, 0.0F, -0.06283185307179587F));
        PartDefinition shape15_4 = shape15_3.addOrReplaceChild("shape15_4", CubeListBuilder.create().texOffs(32, 31).addBox(-1.0F, -4.0F, 0.0F, 1.0F, 4.0F, 8.0F), PartPose.offsetAndRotation(-3.0F, 0.0F, -9.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition shape27_2 = Nade1.addOrReplaceChild("shape27_2", CubeListBuilder.create().texOffs(0, 10).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.2F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition shape23_1 = shape23.addOrReplaceChild("shape23_1", CubeListBuilder.create().texOffs(0, 17).addBox(-4.5F, 0.0F, 0.0F, 9.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.5F, 3.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition shape27_7 = Nade2.addOrReplaceChild("shape27_7", CubeListBuilder.create().texOffs(54, 28).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.2F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition shape23_2 = shape23_1.addOrReplaceChild("shape23_2", CubeListBuilder.create().texOffs(22, 25).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition shape27_5 = shape27_2.addOrReplaceChild("shape27_5", CubeListBuilder.create().texOffs(54, 25).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-0.2F, 0.0F, 0.2F, 0.0F, 0.0F, 0.0F));
        PartDefinition shape27_4 = shape27_2.addOrReplaceChild("shape27_4", CubeListBuilder.create().texOffs(46, 25).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-0.2F, 0.0F, -0.2F, 0.0F, 0.0F, 0.0F));
        PartDefinition shape27_11 = shape27_7.addOrReplaceChild("shape27_11", CubeListBuilder.create().texOffs(16, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-0.2F, -0.7F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition shape27_8 = shape27_7.addOrReplaceChild("shape27_8", CubeListBuilder.create().texOffs(32, 29).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-0.4F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition shape27_10 = shape27_7.addOrReplaceChild("shape27_10", CubeListBuilder.create().texOffs(8, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-0.2F, 0.0F, 0.2F, 0.0F, 0.0F, 0.0F));
        PartDefinition shape27_3 = shape27_2.addOrReplaceChild("shape27_3", CubeListBuilder.create().texOffs(12, 10).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-0.4F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition shape27_6 = shape27_2.addOrReplaceChild("shape27_6", CubeListBuilder.create().texOffs(26, 27).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-0.2F, -0.7F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition shape27_9 = shape27_7.addOrReplaceChild("shape27_9", CubeListBuilder.create().texOffs(0, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-0.2F, 0.0F, -0.2F, 0.0F, 0.0F, 0.0F));
        PartDefinition shape50_1 = shape27_11.addOrReplaceChild("shape50_1", CubeListBuilder.create().texOffs(0, 5).addBox(0.0F, -1.0F, 0.0F, 1.0F, 1.0F, 0.0F), PartPose.offsetAndRotation(-0.5F, 0.3F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition shape26 = shape23_2.addOrReplaceChild("shape26", CubeListBuilder.create().texOffs(58, 5).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 1.2F, -0.12566370614359174F, 0.0F, 0.0F));
        PartDefinition shape50 = shape27_6.addOrReplaceChild("shape50", CubeListBuilder.create().texOffs(0, 5).addBox(0.0F, -1.0F, 0.0F, 1.0F, 1.0F, 0.0F), PartPose.offsetAndRotation(-0.5F, 0.3F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
}
