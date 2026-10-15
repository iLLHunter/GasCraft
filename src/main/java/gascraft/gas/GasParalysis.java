package gascraft.gas;

import gascraft.Gascraft;
import gascraft.GascraftConfig;
import gascraft.entity.GasCloudEntity;
import gascraft.api.event.GasExposureEvent;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Temporary cloud paralysis. Health, potion timers and other entity ticks keep running. */
@EventBusSubscriber(modid=Gascraft.MOD_ID)
public final class GasParalysis {
    private static final String PREVIOUS_AI = "gascraft:paralysis_previous_no_ai";
    private static final String PREVIOUS_GRAVITY = "gascraft:paralysis_previous_no_gravity";
    private static final Map<LivingEntity, Vec3> ANCHORS = new WeakHashMap<>();
    public static volatile boolean clientParalyzed;
    public record Payload(boolean paralyzed) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(GasCatalog.id("gas_paralysis"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Payload> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL,Payload::paralyzed,Payload::new);
        @Override public Type<Payload> type() { return TYPE; }
    }
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Payload.TYPE,Payload.CODEC,(payload,context)->context.enqueueWork(()->clientParalyzed=payload.paralyzed()));
    }
    public static boolean acceptsExposure(LivingEntity entity, GasCatalog.GasDefinition gas) {
        if (!entity.isAlive()) return false;
        var handler=GasExposure.breathingHandler(entity);
        return !handler.isImmune(gas,1) && !NeoForge.EVENT_BUS.post(new GasExposureEvent.GasExposureTickEvent(entity,handler,gas,1)).isCanceled();
    }
    private static boolean eligible(LivingEntity entity,GasCloudEntity cloud) {
        return cloud.paralysisActive() && cloud.gasAreaContains(entity.position()) && acceptsExposure(entity,cloud.gasContents());
    }
    public static boolean refresh(LivingEntity entity) { return refresh(entity,null); }
    private static boolean refresh(LivingEntity entity,GasCloudEntity released) {
        if (entity.level().isClientSide) return entity instanceof Player && clientParalyzed;
        hold(entity); // Restore packet-driven movement before testing whether the cloud still contains its anchor.
        boolean active=entity.isAlive() && (entity instanceof Mob || entity instanceof Player)
            && (released!=null && eligible(entity,released) || entity.level().getEntitiesOfClass(GasCloudEntity.class,
                entity.getBoundingBox().inflate(GascraftConfig.GAS_RANGE_BLOCKS.get())).stream().anyMatch(cloud->eligible(entity,cloud)));
        boolean previous=ANCHORS.containsKey(entity);
        if (active) {
            ANCHORS.putIfAbsent(entity,entity.position());
            if (!entity.getPersistentData().contains(PREVIOUS_GRAVITY)) entity.getPersistentData().putBoolean(PREVIOUS_GRAVITY,entity.isNoGravity());
            entity.setNoGravity(true);
            if (!previous) entity.hurtMarked=true; // Synchronize the initial zero velocity as well as frozen gravity.
            if (entity instanceof Mob mob) {
                if (!mob.getPersistentData().contains(PREVIOUS_AI)) mob.getPersistentData().putBoolean(PREVIOUS_AI,mob.isNoAi());
                mob.setNoAi(true);
                mob.getNavigation().stop();
            }
            entity.stopUsingItem();
            hold(entity);
        } else restore(entity);
        if (active!=previous && entity instanceof ServerPlayer player && player.connection!=null)
            PacketDistributor.sendToPlayer(player,new Payload(active));
        return active;
    }
    private static void hold(LivingEntity entity) {
        Vec3 anchor=ANCHORS.get(entity);
        if (anchor==null) return;
        if (entity.position().distanceToSqr(anchor)>0.000001) {
            if (entity instanceof ServerPlayer player && player.connection!=null)
                player.connection.teleport(anchor.x,anchor.y,anchor.z,player.getYRot(),player.getXRot());
            else entity.setPos(anchor);
        }
        entity.setDeltaMovement(Vec3.ZERO);
    }
    private static void restore(LivingEntity entity) {
        ANCHORS.remove(entity);
        if (entity.getPersistentData().contains(PREVIOUS_GRAVITY)) {
            entity.setNoGravity(entity.getPersistentData().getBoolean(PREVIOUS_GRAVITY));
            entity.getPersistentData().remove(PREVIOUS_GRAVITY);
        }
        if (entity instanceof Mob mob && mob.getPersistentData().contains(PREVIOUS_AI)) {
            mob.setNoAi(mob.getPersistentData().getBoolean(PREVIOUS_AI));
            mob.getPersistentData().remove(PREVIOUS_AI);
        }
    }
    public static void refreshCloud(GasCloudEntity cloud) {
        for (var entity:cloud.level().getEntitiesOfClass(LivingEntity.class,cloud.getBoundingBox().inflate(GascraftConfig.GAS_RANGE_BLOCKS.get()))) refresh(entity,cloud);
    }
    public static void refreshAffected(GasCloudEntity cloud) {
        if (!cloud.hasTimedEffects()) return;
        for (var entity:new ArrayList<>(ANCHORS.keySet())) if (entity.level()==cloud.level()) refresh(entity);
    }
    @SubscribeEvent public static void beforeTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof LivingEntity entity && !entity.level().isClientSide) refresh(entity);
    }
    @SubscribeEvent public static void afterTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity entity && !entity.level().isClientSide) hold(entity);
    }
    @SubscribeEvent public static void attack(AttackEntityEvent event) { if(refresh(event.getEntity())) event.setCanceled(true); }
    @SubscribeEvent public static void damage(LivingIncomingDamageEvent event) {
        if(event.getSource().getDirectEntity() instanceof LivingEntity attacker && refresh(attacker)) event.setCanceled(true);
    }
    @SubscribeEvent public static void use(LivingEntityUseItemEvent.Start event) { if(refresh(event.getEntity())) event.setCanceled(true); }
    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent event) { if(refresh(event.getEntity())) event.getEntity().setDeltaMovement(Vec3.ZERO); }
    private static void interact(PlayerInteractEvent event) {
        if (!refresh(event.getEntity())) return;
        if(event instanceof PlayerInteractEvent.RightClickBlock block) block.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
        else if(event instanceof ICancellableEvent cancel) cancel.setCanceled(true);
    }
    @SubscribeEvent public static void rightBlock(PlayerInteractEvent.RightClickBlock event) { interact(event); }
    @SubscribeEvent public static void rightItem(PlayerInteractEvent.RightClickItem event) { interact(event); }
    @SubscribeEvent public static void leftBlock(PlayerInteractEvent.LeftClickBlock event) { interact(event); }
    @SubscribeEvent public static void entityInteract(PlayerInteractEvent.EntityInteract event) { interact(event); }
    @SubscribeEvent public static void specificInteract(PlayerInteractEvent.EntityInteractSpecific event) { interact(event); }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) { if(refresh(event.getPlayer())) event.setCanceled(true); }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        restore(event.getEntity());
        if(event.getEntity() instanceof ServerPlayer player && player.connection!=null) PacketDistributor.sendToPlayer(player,new Payload(false));
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        restore(event.getEntity());
        if(event.getEntity() instanceof ServerPlayer player && player.connection!=null) PacketDistributor.sendToPlayer(player,new Payload(false));
    }
    private GasParalysis() {}
}
