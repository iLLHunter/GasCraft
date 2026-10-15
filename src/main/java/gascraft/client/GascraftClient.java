package gascraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import gascraft.*;
import gascraft.entity.GasCloudEntity;
import gascraft.gas.*;
import gascraft.item.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = Gascraft.MOD_ID, value = Dist.CLIENT)
public final class GascraftClient {
    public static final ModelLayerLocation MASK = new ModelLayerLocation(GasCatalog.id("gas_mask"), "main");
    public static final ModelLayerLocation HOLDER = new ModelLayerLocation(GasCatalog.id("grenade_holder"), "main");
    @SubscribeEvent public static void registerLauncherScreen(RegisterMenuScreensEvent event) {
        event.register(ModContent.GRENADE_LAUNCHER_MENU.get(), GrenadeLauncherScreen::new);
        event.register(gascraft.machine.MachineContent.PROCESSING_MENU.get(), ProcessingMachineScreen::new);
        event.register(gascraft.machine.MachineContent.SYNTHESIZER_MENU.get(), SynthesizerScreen::new);
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        net.neoforged.fml.ModList.get().getModContainerById(Gascraft.MOD_ID).orElseThrow().registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class, net.neoforged.neoforge.client.gui.ConfigurationScreen::new);
        if (net.neoforged.fml.ModList.get().isLoaded("curios")) CuriosRenderers.register();
        event.enqueueWork(() -> {
            GasCloudEntity.clientParticleSpawner = GasSmokeParticle::emit;
            GasFlash.clientHandler = ClientEffects::flash;
            ItemProperties.register(ModContent.CHEMICAL_GRENADE.get(), GasCatalog.id("gas_type"), (stack, level, entity, seed) -> GasContainerItem.containedGas(stack).smoke() ? 1 : 0);
            ItemProperties.register(ModContent.CHEMICAL_GRENADE.get(), GasCatalog.id("pin_removed"), (stack, level, entity, seed) -> stack.getOrDefault(ModContent.PIN_REMOVED, false) || entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1 : 0);
        });
    }
    @SubscribeEvent public static void registerGasContainerColors(RegisterColorHandlersEvent.Item event) {
        var containers = new java.util.ArrayList<net.minecraft.world.item.Item>();
        ModContent.CANISTERS.values().forEach(item -> containers.add(item.get()));
        containers.add(ModContent.CUSTOM_GAS_CANISTER.get());
        containers.add(ModContent.CHEMICAL_GRENADE.get());
        event.register((stack, layer) -> layer == 0 ? GasContainerItem.containedGas(stack).canisterStripeColor() | 0xff000000 : -1, containers.toArray(net.minecraft.world.item.Item[]::new));
    }
    @SubscribeEvent public static void registerGasParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModContent.GAS_FLASH_PARTICLE.get(), GasFlashParticle.Provider::new);
        event.registerSpriteSet(ModContent.GAS_MIST_PARTICLE.get(), GasSmokeParticle.Provider::new);
        event.registerSpriteSet(ModContent.SMOKE_PUFF_PARTICLE.get(), GasSmokeParticle.Provider::new);
        event.registerSpriteSet(ModContent.MISTED_MEAT_PARTICLE.get(), GasSmokeParticle.Provider::new);
    }
    @SubscribeEvent public static void registerEquipmentLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MASK, GasMaskModel::createMaskLayer);
        event.registerLayerDefinition(HOLDER, GrenadeHolderModel::createHolderLayer);
    }
    @SubscribeEvent public static void registerGasRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(gascraft.machine.MachineContent.MACHINE_BLOCK_ENTITY.get(), FermenterContentsRenderer::new);
        event.registerEntityRenderer(ModContent.GRENADE_ENTITY.get(), context -> new ThrownItemRenderer<>(context));
        event.registerEntityRenderer(ModContent.GAS_CLOUD.get(), context -> new EntityRenderer<GasCloudEntity>(context) {
            @Override public ResourceLocation getTextureLocation(GasCloudEntity entity) { return GasCatalog.id("textures/particle/gas_mist.png"); }
        });
    }
    @SubscribeEvent public static void registerClientItemExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private GasMaskModel model;
            @Override public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                if (model == null) model = new GasMaskModel(Minecraft.getInstance().getEntityModels().bakeLayer(MASK));
                return model;
            }
            @Override public void renderHelmetOverlay(ItemStack stack, Player player, GuiGraphics graphics, DeltaTracker delta) {
                if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;
                RenderSystem.enableBlend();
                graphics.blit(GasCatalog.id("textures/gui/gas_mask_overlay.png"), 0, 0, 0, 0, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(), graphics.guiHeight());
                RenderSystem.disableBlend();
            }
        }, ModContent.GAS_MASK);
    }
    @SubscribeEvent public static void registerGasGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CAMERA_OVERLAYS, GasCatalog.id("mask_overlay"), (graphics, delta) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.options.hideGui) return;
            if (gascraft.compat.AccessoryCompat.equipped(minecraft.player, ModContent.GAS_MASK.get()) && !minecraft.player.getItemBySlot(EquipmentSlot.HEAD).is(ModContent.GAS_MASK.get()))
                IClientItemExtensions.of(ModContent.GAS_MASK.get()).renderHelmetOverlay(new ItemStack(ModContent.GAS_MASK.get()), minecraft.player, graphics, delta);
        });
        event.registerAboveAll(GasCatalog.id("gas_breath_hud"), (graphics, delta) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.options.hideGui) return;
            GasExposure.BreathPayload state = GasExposure.clientState;
            if (state.air() < state.maximum() && !minecraft.player.isCreative() && !minecraft.player.isSpectator()) {
                int right = graphics.guiWidth() / 2 + 91, y = graphics.guiHeight() - minecraft.gui.rightHeight;
                int full = (int)Math.ceil((state.air() - 2) * 10 / state.maximum());
                int partial = (int)Math.ceil(state.air() * 10 / state.maximum()) - full;
                RenderSystem.enableBlend();
                for (int i = 0; i < full + partial; i++) graphics.blit(GasCatalog.id("textures/gui/gas_breath_icons.png"), right - i * 8 - 9, y, i < full ? 0 : 9, 0, 9, 9, 256, 256);
                minecraft.gui.rightHeight += 10;
                RenderSystem.disableBlend();
            }
        });
    }
    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        gascraft.gas.GasDefinitionLoader.restoreLocal();
        GasExposure.clientState = new GasExposure.BreathPayload(300, 300, 0, 0);
        gascraft.gas.GasDefinitionSync.clientSickness = new net.minecraft.nbt.CompoundTag();
    }
    private GascraftClient() {}
}
