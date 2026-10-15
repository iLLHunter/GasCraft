package gascraft.gas;

import gascraft.*;
import gascraft.entity.GasCloudEntity;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-authoritative exposure, with persistent sickness and a synchronized HUD. */
public final class GasExposure {
    private static final String STATE = "gascraft:gas_breath_state";
    private static final Map<LivingEntity, Map<GasCatalog.GasDefinition, Double>> PREVIOUS = new WeakHashMap<>();
    private static final Map<LivingEntity, Map<GasCatalog.GasDefinition, Double>> PENDING = new WeakHashMap<>();
    private static final Map<LivingEntity, Integer> LAST_POISON_DAMAGE = new WeakHashMap<>();
    public static gascraft.api.IGasBreathingState breathingHandler(LivingEntity entity) {
        return new gascraft.api.IGasBreathingState() {
            public float getAirSupply() { return breathingState(entity).getFloat("air"); }
            public void setAirSupply(float air) { breathingState(entity).putFloat("air", Math.max(0, Math.min(maximumGasBreath(entity), air))); }
            public float getMaxAirSupply() { return maximumGasBreath(entity); }
            public Map<GasCatalog.GasDefinition, Double> getGasConcentrations() { return Collections.unmodifiableMap(PREVIOUS.getOrDefault(entity, Map.of())); }
            public void setConcentration(gascraft.api.IGasDefinition gas,float concentration) {
                if (!(gas instanceof GasCatalog.GasDefinition definition)) throw new IllegalArgumentException("Use a registered gas definition");
                if (Float.isFinite(concentration) && concentration > 0) PENDING.computeIfAbsent(entity,e->new HashMap<>()).merge(definition,(double)Math.min(1,concentration),Math::max);
            }
            public boolean isImmune(GasCatalog.GasDefinition gas,float concentration) { return isImmuneToGas(entity,this,gas,concentration); }
            public void tick() { GasExposure.tickGasExposure(entity); }
        };
    }
    public static volatile BreathPayload clientState = new BreathPayload(300, 300, 0, 0);
    public record BreathPayload(float air, float maximum, float irritation, float concentration) implements CustomPacketPayload {
        public static final Type<BreathPayload> TYPE = new Type<>(GasCatalog.id("gas_breath_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BreathPayload> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, BreathPayload::air, ByteBufCodecs.FLOAT, BreathPayload::maximum, ByteBufCodecs.FLOAT, BreathPayload::irritation, ByteBufCodecs.FLOAT, BreathPayload::concentration, BreathPayload::new);
        @Override public Type<BreathPayload> type() { return TYPE; }
    }
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(BreathPayload.TYPE, BreathPayload.CODEC, (payload, context) -> context.enqueueWork(() -> clientState = payload));
    }
    private static float maximumGasBreath(LivingEntity entity) { return entity.getAttributes().hasAttribute(ModContent.GAS_BREATH_CAPACITY) ? (float)entity.getAttributeValue(ModContent.GAS_BREATH_CAPACITY) : entity instanceof Player ? 300 : entity instanceof AbstractVillager ? 200 : 40; }
    private static CompoundTag breathingState(LivingEntity entity) {
        CompoundTag root = entity.getPersistentData();
        if (!root.contains(STATE)) {
            CompoundTag state = new CompoundTag(); state.putFloat("air", maximumGasBreath(entity)); root.put(STATE, state);
        }
        return root.getCompound(STATE);
    }
    public static boolean protectedByMask(LivingEntity entity) {
        if (entity.getItemBySlot(EquipmentSlot.HEAD).is(ModContent.GAS_MASK.get()) || gascraft.compat.AccessoryCompat.equipped(entity, ModContent.GAS_MASK.get())) return true;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (String entry : GascraftConfig.PROTECTIVE_EQUIPMENT.get()) {
            String[] equipment = entry.split("&", -1);
            if (equipment.length < 1 || equipment.length > 4) continue;
            boolean matches = true;
            for (int index = 0; index < equipment.length; index++) {
                String key = equipment[index].trim();
                matches &= key.equals("*") || key.equals(BuiltInRegistries.ITEM.getKey(entity.getItemBySlot(slots[index]).getItem()).toString());
            }
            if (matches) return true;
        }
        return false;
    }
    private static boolean isImmuneToGas(LivingEntity entity,gascraft.api.IGasBreathingState handler,GasCatalog.GasDefinition gas,float concentration) {
        if (exemptPlayer(entity) || gas.id().equals(GasCatalog.id("sarin_gas")) && immuneToNerveAgent(entity)) return true;
        if (beneficialTo(entity, gas)) return false;
        boolean protection=protectedByMask(entity);
        return net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new gascraft.api.event.GasExposureEvent.GasExposureImmunityEvent(entity,handler,gas,concentration,protection)).isImmune();
    }
    /** Only wholly beneficial gases bypass gas protection; timed harmful clouds remain protected. */
    private static boolean beneficialTo(LivingEntity entity, GasCatalog.GasDefinition gas) {
        boolean hasBenefit = !gas.agents().isEmpty();
        for (var agent : gas.agents()) {
            boolean beneficial = switch (agent.factory()) {
                case "potion" -> beneficialPotion(entity, ResourceLocation.parse(agent.effect()));
                case "misted_meat" -> entity instanceof Player;
                case "custom" -> !agent.toxic();
                default -> false;
            };
            if (!beneficial) return false;
        }
        for (var effect : gas.timedEffects()) {
            if (effect.type().equals("instant_potion")) {
                if (!beneficialPotion(entity, ResourceLocation.parse(effect.potion()))) return false;
                hasBenefit = true;
            } else if (!List.of("color", "fade", "end").contains(effect.type())) return false;
        }
        return hasBenefit;
    }
    private static boolean beneficialPotion(LivingEntity entity, ResourceLocation id) {
        var potion = BuiltInRegistries.MOB_EFFECT.getOptional(id).orElse(null);
        if (potion == net.minecraft.world.effect.MobEffects.HEAL.value()) return !entity.isInvertedHealAndHarm();
        if (potion == net.minecraft.world.effect.MobEffects.HARM.value()) return entity.isInvertedHealAndHarm();
        return potion != null && potion.getCategory() == net.minecraft.world.effect.MobEffectCategory.BENEFICIAL;
    }
    public static boolean exemptPlayer(LivingEntity entity) {
        return entity instanceof Player player && (player.isCreative() || player.isSpectator());
    }
    public static boolean immuneToNerveAgent(LivingEntity entity) {
        return exemptPlayer(entity) || entity instanceof Axolotl;
    }
    public static void onLivingGasTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity entity && !(entity instanceof Player)) tickGasExposure(entity);
    }
    public static void onPlayerGasTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        updateGrenadeHolder(player);
        tickGasExposure(player);
    }
    public static void clearOnDeath(Player player) {
        SicknessStateStore.clear(player);
        player.getPersistentData().remove(STATE);
        PREVIOUS.remove(player);
        PENDING.remove(player);
        LAST_POISON_DAMAGE.remove(player);
        player.removeAllEffects();
        GasControl.clearGasControlEffects(player);
    }
    private static void syncClear(Player player) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            float max = maximumGasBreath(player);
            PacketDistributor.sendToPlayer(serverPlayer, new BreathPayload(max, max, 0, 0));
            PacketDistributor.sendToPlayer(serverPlayer, new GasDefinitionSync.SicknessPayload(new CompoundTag()));
        }
    }
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            detachClouds(player);
            clearOnDeath(player);
            syncClear(player);
        }
    }
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || event.getEntity().level().isClientSide) return;
        detachClouds(event.getOriginal());
        clearOnDeath(event.getOriginal());
        clearOnDeath(event.getEntity());
    }
    private static void detachClouds(Player player) {
        for (var level : player.level().getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof GasCloudEntity cloud) cloud.detachEmitter(player);
            }
        }
    }
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || event.getEntity().level().isClientSide) return;
        clearOnDeath(event.getEntity());
        syncClear(event.getEntity());
    }
    public static void clearClientState() {
        clientState = new BreathPayload(300, 300, 0, 0);
        GasDefinitionSync.clientSickness = new CompoundTag();
    }
    public static void updateGrenadeHolder(Player player) {
        int maximum = gascraft.compat.AccessoryCompat.equipped(player, ModContent.GRENADE_HOLDER.get()) ? 4 : 1;
        List<ItemStack> overflow = new ArrayList<>();
        for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
            ItemStack stack = player.getInventory().getItem(index);
            if (!stack.is(ModContent.CHEMICAL_GRENADE.get())) continue;
            stack.set(DataComponents.MAX_STACK_SIZE, maximum);
            while (stack.getCount() > maximum) overflow.add(stack.split(maximum));
        }
        for (ItemStack stack : overflow) if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
    public static void tickGasExposure(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        if (!entity.isAlive()) { GasControl.clearRaidOutline(entity); return; }
        CompoundTag state = breathingState(entity);
        float max = maximumGasBreath(entity), air = Math.max(0, Math.min(max, state.getFloat("air")));
        SicknessStateStore.tickSicknessEffects(entity);
        if (!entity.isAlive()) return;
        float eye, nerve;
        int radius = GascraftConfig.GAS_RANGE_BLOCKS.get();
        Map<GasCatalog.GasDefinition, Double> concentrations = new HashMap<>();
        concentrations.putAll(PENDING.getOrDefault(entity,Map.of())); PENDING.remove(entity);
        List<GasCloudEntity> clouds = entity.level().getEntitiesOfClass(GasCloudEntity.class, entity.getBoundingBox().inflate(radius));
        {
            for (GasCloudEntity cloud : clouds) {
                if (cloud.instantPulse()) continue; // The release pulse owns this visual cloud's only exposure.
                double concentration = cloud.gasConcentrationAt(entity.position());
                if (concentration <= 0) continue;
                concentrations.merge(cloud.gasContents(), concentration, Math::max);
            }
        }
        // Scale exposure through the entity's air-depletion hook,
        // rather than treating underwater breathing as gas immunity. Sarin has explicit exceptions.
        float respiratoryModifier = air - entity.decreaseAirSupply((int)air);
        concentrations.replaceAll((gas, concentration) -> gas.id().equals(GasCatalog.id("sarin_gas")) ? concentration : concentration * respiratoryModifier);
        var handler = breathingHandler(entity);
        var previous = PREVIOUS.getOrDefault(entity, Map.of());
        previous.forEach((gas, concentration) -> {
            if (!concentrations.containsKey(gas)) {
                var exit = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new gascraft.api.event.GasExposureEvent.GasExposureEndEvent(entity, handler, gas, concentration.floatValue()));
                if (exit.isCanceled()) concentrations.put(gas, concentration);
                else for (var agent : gas.agents()) if (agent.factory().equals("custom")) {
                    var extension = GasAgentRegistry.getAgent(ResourceLocation.parse(agent.effect())); if (extension != null) extension.onExposureEnded(entity, handler);
                }
            }
        });
        concentrations.entrySet().removeIf(entry -> {
            var gas = entry.getKey(); float concentration = entry.getValue().floatValue();
            if (handler.isImmune(gas,concentration)) return true;
            if (!previous.containsKey(gas)) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new gascraft.api.event.GasExposureEvent.GasExposureStartEvent(entity, handler, gas, concentration));
            return net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new gascraft.api.event.GasExposureEvent.GasExposureTickEvent(entity, handler, gas, concentration)).isCanceled();
        });
        boolean toxic = concentrations.keySet().stream().anyMatch(GasCatalog.GasDefinition::toxic);
        air = toxic ? Math.max(0, air - 2) : Math.min(max, air + 2);
        state.putFloat("air", air);
        // Apply the strongest Poison tier first so overlapping clouds share vanilla effect precedence.
        var exposures = new ArrayList<>(concentrations.entrySet());
        exposures.sort(Comparator.comparingInt((Map.Entry<GasCatalog.GasDefinition, Double> entry) -> poisonAmplifier(entry.getKey())).reversed());
        for (var entry : exposures) {
            apply(entity, state, entry.getKey(), entry.getValue(), air);
            if (!entity.isAlive()) return;
        }
        PREVIOUS.put(entity, new HashMap<>(concentrations));
        GasControl.tick(entity, air <= 0 && concentrations.keySet().stream().anyMatch(gas -> gas.id().equals(GasCatalog.id("mustard_gas"))));
        if (!entity.isAlive()) return;
        eye = SicknessStateStore.sicknessSeverity(entity, "chemical_eye_irritation"); nerve = SicknessStateStore.sicknessSeverity(entity, "nerve_agent_exposure");
        if (eye > 0) entity.addEffect(new MobEffectInstance(ModContent.CHEMICAL_EYE_IRRITATION, 40, 0, true, false));
        else entity.removeEffect(ModContent.CHEMICAL_EYE_IRRITATION);
        if (nerve > 0) {
            entity.addEffect(new MobEffectInstance(ModContent.NERVE_AGENT_EXPOSURE, 40, 0, true, false));
        } else entity.removeEffect(ModContent.NERVE_AGENT_EXPOSURE);
        float peak = (float) Math.min(1, concentrations.values().stream().mapToDouble(Double::doubleValue).max().orElse(0));
        if (entity instanceof ServerPlayer player && player.connection != null && entity.tickCount % 5 == 0) {
            PacketDistributor.sendToPlayer(player, new BreathPayload(air, max, eye, peak));
            PacketDistributor.sendToPlayer(player, new GasDefinitionSync.SicknessPayload(SicknessStateStore.exportSicknessState(entity)));
        }
    }
    private static void apply(LivingEntity entity, CompoundTag state, GasCatalog.GasDefinition gas, double concentration, float air) {
        applyGasAgents(entity,gas,(float)concentration,false);
    }
    public static void applyGasAgents(LivingEntity entity, GasCatalog.GasDefinition gas, float concentration, boolean forced) {
        if (entity.level().isClientSide || !entity.isAlive() || exemptPlayer(entity) || gas.id().equals(GasCatalog.id("sarin_gas")) && immuneToNerveAgent(entity)) return;
        float air=breathingHandler(entity).getAirSupply();
        if (!forced && gas.suspendable() && entity.isShiftKeyDown() && air > 0) return;
        for (GasCatalog.AgentDefinition agent : gas.agents()) {
            if (!entity.isAlive()) return;
            float dose = (float) concentration * agent.potency();
            switch (agent.factory()) {
                case "sickness" -> {
                    if (forced || agent.ignoreBreath() || air <= 0) {
                        SicknessStateStore.addFromGas(entity, ResourceLocation.parse(agent.effect()), dose / 20, gas.id());
                    }
                }
                case "damage" -> {
                    if (forced || air <= 0) {
                        entity.hurt(GasDamage.pulmonaryGasDamage(entity, gas), agent.damage() * agent.potency());
                        if (entity.isAlive() && gas.id().equals(GasCatalog.id("mustard_gas"))) SicknessStateStore.refreshMustardDamage(entity);
                    }
                }
                case "potion" -> {
                    boolean shortPoison = GasCatalog.isPoisonGas(gas.id()) && agent.effect().equals("minecraft:poison") && agent.duration() == 20;
                    if (shortPoison || forced || entity.level().getDayTime() % 20 == 0) BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(agent.effect())).ifPresent(effect -> {
                        entity.addEffect(new MobEffectInstance(effect, agent.duration(), agent.amplifier()));
                        // Refreshing 20 ticks every tick prevents vanilla's duration-based damage timer
                        // from reaching its intervals. Use the native Poison tick at its normal cadence
                        // while refreshing; after exit the finite vanilla effect expires on its own.
                        var active = entity.getEffect(effect);
                        if (shortPoison && active != null && active.getAmplifier() == agent.amplifier() && active.getDuration() == 20
                            && effect.value().shouldApplyEffectTickThisTick(entity.tickCount, agent.amplifier())
                            && LAST_POISON_DAMAGE.getOrDefault(entity, -1) != entity.tickCount) {
                            LAST_POISON_DAMAGE.put(entity, entity.tickCount);
                            effect.value().applyEffectTick(entity, agent.amplifier());
                        }
                    });
                }
                case "misted_meat" -> {
                    if (entity instanceof Player player && (forced || dose > 0 && entity.level().getDayTime() % Math.max(1, (int)(20 / dose)) == 0)) {
                        if (player.getRandom().nextBoolean()) player.getFoodData().setSaturation(player.getFoodData().getSaturationLevel() + 1);
                        else player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() + 1);
                    }
                }
                case "custom" -> {
                    var extension = GasAgentRegistry.getAgent(ResourceLocation.parse(agent.effect()));
                    if (extension != null) extension.applyAgentExposure(entity, breathingHandler(entity), (float)concentration, !PREVIOUS.getOrDefault(entity, Map.of()).containsKey(gas), agent.potency(), forced);
                }
                default -> throw new IllegalStateException("Unknown Gascraft agent: " + agent.factory());
            }
        }
    }
    public static void applyInstantPotionPulse(LivingEntity entity, GasCatalog.GasDefinition gas,
        net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> potion, int amplifier, Entity source, Entity owner) {
        if (entity.level().isClientSide || !entity.isAlive()) return;
        var handler = breathingHandler(entity);
        if (handler.isImmune(gas, 1)) return;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new gascraft.api.event.GasExposureEvent.GasExposureStartEvent(entity, handler, gas, 1));
        if (!net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new gascraft.api.event.GasExposureEvent.GasExposureTickEvent(entity, handler, gas, 1)).isCanceled())
            potion.value().applyInstantenousEffect(source, owner, entity, amplifier, 1);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new gascraft.api.event.GasExposureEvent.GasExposureEndEvent(entity, handler, gas, 1));
    }
    public static CompoundTag exportSicknessState(LivingEntity entity) {
        return SicknessStateStore.exportSicknessState(entity);
    }
    private static int poisonAmplifier(GasCatalog.GasDefinition gas) {
        return GasCatalog.isPoisonGas(gas.id()) ? gas.agents().stream()
            .filter(agent -> agent.factory().equals("potion") && agent.effect().equals("minecraft:poison"))
            .mapToInt(GasCatalog.AgentDefinition::amplifier).max().orElse(-1) : -1;
    }
    public static void mergeSicknessState(LivingEntity entity, CompoundTag sicknessSnapshot) {
        SicknessStateStore.mergeSicknessState(entity, sicknessSnapshot);
    }
    private GasExposure() {}
}

