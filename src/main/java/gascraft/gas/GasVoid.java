package gascraft.gas;

import gascraft.GascraftConfig;
import gascraft.entity.GasCloudEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A single safe, same-dimension teleport pulse; the remaining cloud is cosmetic. */
public final class GasVoid {
    public static final int MAX_DISTANCE=64;
    public static void release(ServerLevel level,GasCloudEntity cloud) {
        var targets=level.getEntitiesOfClass(LivingEntity.class,cloud.getBoundingBox().inflate(GascraftConfig.GAS_RANGE_BLOCKS.get()),
            e->e.isAlive()&&!(e instanceof ArmorStand)&&!GasExposure.exemptPlayer(e)&&cloud.gasAreaContains(e.position()));
        for(var target:targets)teleport(target);
    }
    private static boolean hazardous(BlockState state) {
        return state.is(BlockTags.FIRE)||state.is(Blocks.LAVA)||state.is(Blocks.CACTUS)||state.is(Blocks.MAGMA_BLOCK)
            ||state.is(Blocks.CAMPFIRE)||state.is(Blocks.SOUL_CAMPFIRE)||state.is(Blocks.SWEET_BERRY_BUSH)
            ||state.is(Blocks.WITHER_ROSE)||state.is(Blocks.POWDER_SNOW)||state.is(Blocks.POINTED_DRIPSTONE)
            ||state.is(Blocks.NETHER_PORTAL)||state.is(Blocks.END_PORTAL)||state.is(Blocks.END_GATEWAY);
    }
    public static boolean safeDestination(LivingEntity entity,Vec3 position) {
        if(!(entity.level() instanceof ServerLevel level)||position.distanceToSqr(entity.position())>MAX_DISTANCE*MAX_DISTANCE)return false;
        var feet=BlockPos.containing(position);
        if(!level.hasChunkAt(feet)||position.y<level.getMinBuildHeight()||position.y+entity.getBbHeight()>level.getMaxBuildHeight())return false;
        var box=entity.getBoundingBox().move(position.subtract(entity.position()));
        if(!level.getWorldBorder().isWithinBounds(box)||!level.noCollision(entity,box)||level.getBlockStates(box.inflate(.01)).anyMatch(GasVoid::hazardous))return false;
        boolean aquatic=entity instanceof WaterAnimal||entity instanceof Axolotl;
        if(aquatic)return level.getFluidState(feet).is(FluidTags.WATER)&&level.getFluidState(BlockPos.containing(position.add(0,entity.getBbHeight()*.8,0))).is(FluidTags.WATER);
        var below=level.getBlockState(feet.below());
        return !level.containsAnyLiquid(box)&&!hazardous(below)&&below.isFaceSturdy(level,feet.below(),Direction.UP);
    }
    public static boolean teleport(LivingEntity entity) {
        if(!(entity.level() instanceof ServerLevel level)||!entity.isAlive()||GasExposure.exemptPlayer(entity)||GasTeleports.blocked(entity))return false;
        var origin=entity.position();var random=entity.getRandom();
        for(int attempt=0;attempt<128;attempt++) {
            double x=origin.x+(random.nextDouble()*2-1)*MAX_DISTANCE,z=origin.z+(random.nextDouble()*2-1)*MAX_DISTANCE;
            if((x-origin.x)*(x-origin.x)+(z-origin.z)*(z-origin.z)>MAX_DISTANCE*MAX_DISTANCE)continue;
            int top=Math.min(level.getMaxBuildHeight()-1,(int)Math.floor(origin.y)+random.nextInt(129)-64);
            int bottom=Math.max(level.getMinBuildHeight()+1,(int)Math.ceil(origin.y-MAX_DISTANCE));
            for(int y=top;y>=bottom;y--) {
                var destination=new Vec3(x,y,z);
                if(!safeDestination(entity,destination))continue;
                entity.stopRiding();entity.teleportTo(x,y,z);
                if(entity.position().distanceToSqr(destination)>.01) return false;
                entity.fallDistance=0;
                if(entity.getDeltaMovement().y<0)entity.setDeltaMovement(entity.getDeltaMovement().multiply(1,0,1));
                if(entity instanceof net.minecraft.world.entity.PathfinderMob mob)mob.getNavigation().stop();
                return true;
            }
        }
        return false;
    }
    private GasVoid(){}
}
