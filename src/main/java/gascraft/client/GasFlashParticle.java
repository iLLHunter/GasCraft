package gascraft.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/** A stationary, full-bright flash using Minecraft's firework-flash sprite. */
public final class GasFlashParticle extends TextureSheetParticle {
    private GasFlashParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        lifetime = 10; quadSize = 7.1F; gravity = 0;
        pickSprite(sprites);
    }
    @Override public void tick() {
        if (++age >= lifetime) remove();
    }
    @Override public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        setAlpha(Math.max(0, 1 - (age + partialTick) / lifetime));
        super.render(buffer, camera, partialTick);
    }
    @Override protected int getLightColor(float partialTick) { return 0xf000f0; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new GasFlashParticle(level, x, y, z, sprites);
        }
    }
}
