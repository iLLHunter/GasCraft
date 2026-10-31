package gascraft.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.ColorParticleOption;

/** A textured cloud particle with gas-specific color and fading. */
public final class GasSmokeParticle extends TextureSheetParticle {
    private static gascraft.entity.GasCloudEntity emittingCloud;
    private final float maximumAlpha;
    private float normalAlpha;
    private gascraft.entity.GasCloudEntity sourceCloud;
    private net.minecraft.world.phys.Vec3 fieldOffset;
    private int fieldColor;
    public static void emit(gascraft.entity.GasCloudEntity cloud, ColorParticleOption options, double x, double y, double z) {
        var previous = emittingCloud;
        emittingCloud = cloud;
        try { cloud.level().addParticle(options, x, y, z, 0, 0, 0); }
        finally { emittingCloud = previous; }
    }
    private GasSmokeParticle(ClientLevel level, double x, double y, double z, ColorParticleOption options, SpriteSet sprites) {
        super(level, x, y, z, 0, 0.03, 0);
        setColor(options.getRed(), options.getGreen(), options.getBlue());
        maximumAlpha = options.getAlpha();
        alpha = 0;
        lifetime = 100; quadSize = Math.max(1, (float)(55 + 20 * random.nextGaussian())) * .1F;
        double mx = random.nextDouble() * 2 - 1, my = random.nextDouble() * 2 - 1, mz = random.nextDouble() * 2 - 1;
        double length = Math.sqrt(mx*mx + my*my + mz*mz);
        double speed = (random.nextDouble() + random.nextDouble() + 1) * .15 * .4;
        xd = mx / length * speed; yd = (my / length * speed + .1) * .5; zd = mz / length * speed;
        pickSprite(sprites);
    }
    @Override public void tick() {
        if (sourceCloud != null && (sourceCloud.isRemoved() || sourceCloud.fadeOpacity() <= 0)) { remove(); return; }
        xo = x; yo = y; zo = z;
        if (age++ >= lifetime) { normalAlpha *= .99F; if (normalAlpha <= .01F) remove(); }
        else normalAlpha = sourceCloud != null && sourceCloud.instantPulse() ? 1 : Math.min(age / 50F * (sourceCloud == null ? maximumAlpha : 1), 1);
        alpha = normalAlpha;
        if(sourceCloud!=null&&sourceCloud.visualProfile()!=null) {
            var profile=sourceCloud.visualProfile();
            if(fieldOffset==null) {
                fieldOffset=new net.minecraft.world.phys.Vec3(x-sourceCloud.getX(),y-sourceCloud.getY(),z-sourceCloud.getZ()).scale(1/Math.max(.02F,profile.radius(sourceCloud.cloudAge())));
                lifetime=25;quadSize=profile.gas().equals("misted_cologne")?2:4;
                if(profile.gas().equals("boogie_gas"))quadSize=random.nextBoolean()?.18F:2F;
                fieldColor=java.awt.Color.HSBtoRGB(random.nextFloat(),.8F,1F);
            }
            float radius=profile.radius(sourceCloud.cloudAge());
            double angle=profile.gas().equals("voidrot_gas")?(sourceCloud.cloudAge()+age)*.025:0;
            var offset=fieldOffset.yRot((float)angle).scale(radius);
            setPos(sourceCloud.getX()+offset.x,sourceCloud.getY()+offset.y,sourceCloud.getZ()+offset.z);
            int color=sourceCloud.displayColor();
            if(profile.gas().equals("boogie_gas"))color=(color&0xff000000)|(fieldColor&0xffffff);
            if(profile.gas().equals("voidrot_gas")&&fieldColor%5==0)color=(color&0xff000000)|0xb500da;
            if(profile.gas().equals("misted_moonlight"))color=(color&0xff000000)|(age%10<3?0xe8efff:0x9aaecb);
            setColor((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F);
            alpha=(color>>>24)/255F*sourceCloud.fadeOpacity()*Math.min(1,(lifetime-age)/5F);
            if(profile.gas().equals("boogie_gas"))alpha*=.35F+.65F*(float)Math.abs(Math.sin(age*.7+fieldColor));
            if(age>=lifetime)remove();
            return;
        }
        if (sourceCloud != null) {
            int color = sourceCloud.displayColor();
            setColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
            alpha *= (color >>> 24) / 255F * sourceCloud.fadeOpacity();
        }
        yd += .0005; move(xd, yd, zd);
        if (y == yo) { xd *= 1.1; zd *= 1.1; }
        xd *= .9; yd *= .96; zd *= .9;
        if (onGround) { xd *= .7; zd *= .7; }
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    @Override protected int getLightColor(float partialTick) { return sourceCloud!=null&&sourceCloud.fieldProfile()!=null&&sourceCloud.fieldProfile().gas().equals("boogie_gas")?0xf000f0:super.getLightColor(partialTick); }
    public record Provider(SpriteSet sprites) implements ParticleProvider<ColorParticleOption> {
        @Override public Particle createParticle(ColorParticleOption options, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            var particle = new GasSmokeParticle(level, x, y, z, options, sprites);
            if (emittingCloud != null && emittingCloud.hasTimedEffects()) particle.sourceCloud = emittingCloud;
            return particle;
        }
    }
}
