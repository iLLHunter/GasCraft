package gascraft.client;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import gascraft.*;
import gascraft.entity.GasCloudEntity;
import gascraft.gas.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.PostChain;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid=Gascraft.MOD_ID, value=Dist.CLIENT)
public final class ClientEffects {
    private static final String BLUR = "gascraft:shaders/post/eye_irritation_blur.json";
    private static PostChain overlay;
    private static int width, height;
    private static double flashStart;
    private static int flashDuration;
    public static void flash(GasFlash.Payload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || payload.durationTicks() <= 0) return;
        var particle = mc.particleEngine.createParticle(ModContent.GAS_FLASH_PARTICLE.get(), payload.x(), payload.y(), payload.z(), 0, 0, 0);
        if (particle != null) particle.setLifetime(payload.durationTicks());
        if (payload.screen() && mc.player != null && mc.player.isAlive()) {
            flashStart = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
            flashDuration = payload.durationTicks();
        }
    }
    public static float flashOpacity(double gameTime) {
        return flashDuration <= 0 ? 0 : (float)Math.clamp(1 - (gameTime - flashStart) / flashDuration, 0, 1);
    }
    @SubscribeEvent public static void flashOverlay(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) { flashDuration = 0; return; }
        int alpha = Math.round(255 * flashOpacity(mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false)));
        if (alpha > 0) {
            var graphics = event.getGuiGraphics();
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | 0xffffff);
        }
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) resources -> { if (overlay != null) overlay.close(); overlay=null; });
    }
    @SubscribeEvent public static void respawn(ClientPlayerNetworkEvent.Clone event) {
        if (!event.getOldPlayer().isAlive()) clearDeathState();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearDeathState();
    }
    private static void clearDeathState() {
        flashDuration = 0;
        GasExposure.clearClientState();
        Minecraft mc = Minecraft.getInstance();
        PostChain current = mc.gameRenderer.currentEffect();
        if (current != null && current.getName().equals(BLUR)) mc.gameRenderer.shutdownEffect();
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Pre event) {
        Minecraft mc=Minecraft.getInstance();
        float irritation=mc.player == null || !mc.player.isAlive() || GasExposure.exemptPlayer(mc.player) ? 0 : GasExposure.clientState.irritation();
        PostChain current=mc.gameRenderer.currentEffect();
        if (irritation > 0 && current == null) { mc.gameRenderer.loadEffect(net.minecraft.resources.ResourceLocation.parse(BLUR)); current=mc.gameRenderer.currentEffect(); }
        if (current != null && current.getName().equals(BLUR)) {
            if (irritation <= 0) mc.gameRenderer.shutdownEffect(); else current.setUniform("GascraftProgress", irritation * 2);
        }
        if (mc.player==null || mc.level==null || !GascraftConfig.RENDER_GAS_OVERLAYS.get() || !mc.options.getCameraType().isFirstPerson()) return;
        record Appearance(GasCatalog.GasDefinition gas, int color) {}
        java.util.Map<Appearance,Double> concentrations=new java.util.LinkedHashMap<>();
        for (GasCloudEntity cloud : mc.level.getEntitiesOfClass(GasCloudEntity.class,mc.player.getBoundingBox().inflate(GascraftConfig.GAS_RANGE_BLOCKS.get()))) {
            double concentration=cloud.gasConcentrationAt(mc.player.position()) * cloud.fadeOpacity();
            if (concentration>0) concentrations.merge(new Appearance(cloud.gasContents(),cloud.displayColor()),concentration,Math::max);
        }
        for (var entry:concentrations.entrySet()) {
            int color=entry.getKey().color(); float alpha=(float)(entry.getValue() * (color >>> 24) / 255F);
            if (alpha<=0) continue;
            if (GascraftConfig.USE_SHADERS.get()) {
                try {
                    if (overlay==null) overlay=new PostChain(mc.getTextureManager(),mc.getResourceManager(),mc.getMainRenderTarget(),GasCatalog.id("shaders/post/gas_overlay.json"));
                    int w=mc.getWindow().getWidth(), h=mc.getWindow().getHeight();
                    if (width!=w || height!=h) { overlay.resize(w,h); width=w; height=h; }
                    overlay.setUniform("ColorR",(color >> 16 & 255)/255F); overlay.setUniform("ColorG",(color >> 8 & 255)/255F); overlay.setUniform("ColorB",(color & 255)/255F);
                    overlay.setUniform("GasAlpha",alpha); overlay.setUniform("iTime",(mc.level.getGameTime()%100000)*50F);
                    overlay.process(event.getPartialTick().getGameTimeDeltaTicks()); mc.getMainRenderTarget().bindWrite(false);
                } catch (java.io.IOException error) { throw new IllegalStateException("Cannot load gas overlay shader",error); }
            } else {
                RenderSystem.enableBlend(); RenderSystem.setShaderColor((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,alpha);
                var graphics=event.getGuiGraphics(); graphics.blit(GasCatalog.id("textures/gui/gas_haze_overlay.png"),0,0,0,0,graphics.guiWidth(),graphics.guiHeight(),graphics.guiWidth(),graphics.guiHeight());
                RenderSystem.setShaderColor(1,1,1,1); RenderSystem.disableBlend();
            }
        }
    }
    private ClientEffects() {}
}
