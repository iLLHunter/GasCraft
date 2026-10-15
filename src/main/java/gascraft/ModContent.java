package gascraft;

import com.mojang.serialization.Codec;
import gascraft.entity.*;
import gascraft.gas.GasCatalog;
import gascraft.item.*;
import gascraft.recipe.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.dispenser.ProjectileDispenseBehavior;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.registries.*;

public final class ModContent {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Gascraft.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Gascraft.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Gascraft.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Gascraft.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Gascraft.MOD_ID);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<gascraft.menu.GrenadeLauncherMenu>> GRENADE_LAUNCHER_MENU = MENUS.register("grenade_launcher", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(gascraft.menu.GrenadeLauncherMenu::new));
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Gascraft.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.entity.ai.attributes.Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, Gascraft.MOD_ID);
    public static final DeferredHolder<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.Attribute> GAS_BREATH_CAPACITY = ATTRIBUTES.register("gas_breath_capacity", () -> new net.minecraft.world.entity.ai.attributes.RangedAttribute("attribute.gascraft.gas_breath_capacity", 300, 0, 1024).setSyncable(true));
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, Gascraft.MOD_ID);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> GAS_MASK_MATERIAL = ARMOR_MATERIALS.register("gas_mask_material", () -> new ArmorMaterial(java.util.Map.of(ArmorItem.Type.HELMET, 1, ArmorItem.Type.CHESTPLATE, 0), 15, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.LEATHER), java.util.List.of(new ArmorMaterial.Layer(GasCatalog.id("gas_mask_material"))), 0, 0));
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Gascraft.MOD_ID);
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> GAS_MIST_PARTICLE = particle("gas_mist");
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> SMOKE_PUFF_PARTICLE = particle("smoke_puff");
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> MISTED_MEAT_PARTICLE = particle("misted_meat");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GAS_FLASH_PARTICLE = PARTICLES.register("gas_flash", () -> new SimpleParticleType(true));
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Gascraft.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> GAS_CONTENTS = COMPONENTS.registerComponentType("gas_contents", builder -> builder.persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> PIN_REMOVED = COMPONENTS.registerComponentType("pin_removed", builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LAUNCHER_ENERGY = COMPONENTS.registerComponentType("launcher_energy", builder -> builder.persistent(Codec.intRange(0, 1_000_000)).networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LAUNCHER_EFFICIENCY = COMPONENTS.registerComponentType("launcher_efficiency", builder -> builder.persistent(Codec.intRange(0, 9)).networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.world.item.component.ItemContainerContents>> LAUNCHER_UPGRADES = COMPONENTS.registerComponentType("launcher_upgrades", builder -> builder.persistent(net.minecraft.world.item.component.ItemContainerContents.CODEC).networkSynchronized(net.minecraft.world.item.component.ItemContainerContents.STREAM_CODEC));
    public static final DeferredItem<Item> FLESH_ASH = ITEMS.registerSimpleItem("flesh_ash");
    public static final DeferredItem<Item> GRENADE_DIFFUSER = ITEMS.registerSimpleItem("grenade_diffuser");
    public static final DeferredItem<Item> GRENADE_SHELL = ITEMS.registerSimpleItem("grenade_shell", new Item.Properties().craftRemainder(Items.GLASS_BOTTLE));
    public static final DeferredItem<GascraftArmorItem> GAS_MASK = ITEMS.registerItem("gas_mask", properties -> new GascraftArmorItem(ArmorItem.Type.HELMET, properties.durability(55), "gas_mask"));
    public static final java.util.Map<ResourceLocation, DeferredItem<GasCanisterItem>> CANISTERS = registerCanisters();
    public static final DeferredItem<GasCanisterItem> CUSTOM_GAS_CANISTER = ITEMS.registerItem("custom_gas_canister", GasCanisterItem::new);
    public static final DeferredItem<GrenadeItem> CHEMICAL_GRENADE = ITEMS.registerItem("chemical_grenade", GrenadeItem::new);
    public static final DeferredItem<GrenadeLauncherItem> GRENADE_LAUNCHER = ITEMS.registerItem("grenade_launcher", GrenadeLauncherItem::new);
    public static final DeferredItem<Item> GRENADE_LAUNCHER_HANDLE = ITEMS.registerSimpleItem("grenade_launcher_handle");
    public static final DeferredItem<Item> GRENADE_LAUNCHER_BARREL = ITEMS.registerSimpleItem("grenade_launcher_barrel");
    public static final DeferredItem<Item> GRENADE_LAUNCHER_TRIGGER = ITEMS.registerSimpleItem("grenade_launcher_trigger");
    public static final DeferredItem<Item> GRENADE_LAUNCHER_FIRING_MECHANISM = ITEMS.registerSimpleItem("grenade_launcher_firing_mechanism");
    public static final DeferredItem<Item> GRENADE_HOLDER = ITEMS.registerSimpleItem("grenade_holder", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> POWDERED_SMOKE = ITEMS.registerSimpleItem("powdered_smoke");
    public static final DeferredItem<Item> POWDERED_POISON = ITEMS.registerSimpleItem("powdered_poison");
    public static final DeferredItem<Item> POWDERED_POISON_CONCENTRATED = ITEMS.registerSimpleItem("powdered_poison_concentrated");
    public static final DeferredItem<Item> POISON_PELLET_COMPOSITE = ITEMS.registerSimpleItem("poison_pellet_composite");
    public static final DeferredItem<Item> POISON_PELLET = ITEMS.registerSimpleItem("poison_pellet");
    public static final DeferredItem<Item> POWDERED_GLISTERING_MELON = ITEMS.registerSimpleItem("powdered_glistering_melon");
    public static final DeferredItem<Item> POWDERED_GLISTERING_MELON_CONCENTRATED = ITEMS.registerSimpleItem("powdered_glistering_melon_concentrated");
    public static final DeferredItem<Item> HEALING_MIST_PELLET_COMPOSITE = ITEMS.registerSimpleItem("healing_mist_pellet_composite");
    public static final DeferredItem<Item> HEALING_PELLET = ITEMS.registerSimpleItem("healing_pellet");
    public static final DeferredItem<Item> TRACKING_POWDER = ITEMS.registerSimpleItem("tracking_powder");
    public static final DeferredItem<Item> POWDERED_TETRODOTOXIN = ITEMS.registerSimpleItem("powdered_tetrodotoxin");
    public static final DeferredItem<Item> POWDERED_TETRODOTOXIN_CONCENTRATED = ITEMS.registerSimpleItem("powdered_tetrodotoxin_concentrated");
    public static final DeferredItem<Item> TETRODOTOXIN_PELLET_COMPOSITE = ITEMS.registerSimpleItem("tetrodotoxin_pellet_composite");
    public static final DeferredItem<Item> TETRODOTOXIN_PELLET = ITEMS.registerSimpleItem("tetrodotoxin_pellet");
    public static final DeferredItem<Item> NAPALM = ITEMS.registerSimpleItem("napalm");
    public static final DeferredItem<Item> REGENERATION_AGENT = ITEMS.registerSimpleItem("regeneration_agent");
    public static final DeferredItem<Item> REGENERATION_AGENT_CONCENTRATED = ITEMS.registerSimpleItem("regeneration_agent_concentrated");
    public static final DeferredItem<Item> REGENERATION_AGENT_PELLET_COMPOSITE = ITEMS.registerSimpleItem("regeneration_agent_pellet_composite");
    public static final DeferredItem<Item> REGENERATION_AGENT_PELLET = ITEMS.registerSimpleItem("regeneration_agent_pellet");
    public static final DeferredItem<Item> LACHRYMATOR_AGENT = ITEMS.registerSimpleItem("lachrymator_agent");
    public static final DeferredItem<Item> POWDERED_PROTEIN = ITEMS.registerSimpleItem("powdered_protein");
    public static final DeferredItem<Item> TOXIC_BINDER = ITEMS.registerSimpleItem("toxic_binder");
    public static final DeferredItem<Item> COMPOSITE_BINDER = ITEMS.registerSimpleItem("composite_binder");
    public static final DeferredItem<Item> FERMENTED_VEGETATION = ITEMS.registerSimpleItem("fermented_vegetation");
    public static final DeferredItem<Item> EMPTY_CANISTER = ITEMS.registerSimpleItem("empty_canister", new Item.Properties().stacksTo(64));
    public static final DeferredItem<ProcessingUpgradeItem> PROCESSING_SPEED_UPGRADE = ITEMS.registerItem("processing_speed_upgrade", properties -> new ProcessingUpgradeItem(ProcessingUpgradeItem.Kind.SPEED, properties));
    public static final DeferredItem<ProcessingUpgradeItem> PROCESSING_EFFICIENCY_UPGRADE = ITEMS.registerItem("processing_efficiency_upgrade", properties -> new ProcessingUpgradeItem(ProcessingUpgradeItem.Kind.EFFICIENCY, properties));
    public static final DeferredItem<Item> SULFUR = ITEMS.registerSimpleItem("sulfur");
    public static final DeferredItem<Item> POWDERED_SULFUR = ITEMS.registerSimpleItem("powdered_sulfur");
    public static final DeferredItem<Item> POWDERED_SULFUR_POTENT = ITEMS.registerSimpleItem("powdered_sulfur_potent");
    public static final DeferredItem<Item> POWDERED_IRON = ITEMS.registerSimpleItem("powdered_iron");
    public static final DeferredItem<Item> FLASH_POWDER = ITEMS.registerSimpleItem("flash_powder");
    public static final DeferredItem<Item> SARIN_PELLET = ITEMS.registerSimpleItem("sarin_pellet");
    public static final DeferredItem<Item> SARIN_PELLET_COMPOSITE = ITEMS.registerSimpleItem("sarin_pellet_composite");
    public static final DeferredHolder<EntityType<?>, EntityType<GrenadeEntity>> GRENADE_ENTITY = ENTITIES.register("chemical_grenade_projectile", () -> EntityType.Builder.<GrenadeEntity>of(GrenadeEntity::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(1).build("gascraft:chemical_grenade_projectile"));
    public static final DeferredHolder<EntityType<?>, EntityType<GasCloudEntity>> GAS_CLOUD = ENTITIES.register("chemical_gas_cloud", () -> EntityType.Builder.<GasCloudEntity>of(GasCloudEntity::new, MobCategory.MISC).sized(0.1F, 0.1F).clientTrackingRange(16).updateInterval(10).build("gascraft:chemical_gas_cloud"));
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<GasContainerCraftingRecipe>> GAS_CONTAINER_CRAFTING = SERIALIZERS.register("gas_container_crafting", () -> new SimpleCraftingRecipeSerializer<>(GasContainerCraftingRecipe::new));
    public static final DeferredHolder<MobEffect, MobEffect> CHEMICAL_EYE_IRRITATION = EFFECTS.register("chemical_eye_irritation", () -> new GasSicknessEffect(0xaacccc));
    public static final DeferredHolder<MobEffect, MobEffect> NERVE_AGENT_EXPOSURE = EFFECTS.register("nerve_agent_exposure", () -> new GasSicknessEffect(0xeeeeee));
    static {
        TABS.register("gascraft", () -> CreativeModeTab.builder().title(Component.translatable("item_group.gascraft")).icon(() -> new ItemStack(CHEMICAL_GRENADE.get())).displayItems((parameters, output) -> {
            ITEMS.getEntries().forEach(item -> { if (!(item.get() instanceof GasCanisterItem) && item.get() != CHEMICAL_GRENADE.get() && (item.get()!=GRENADE_HOLDER.get() || net.neoforged.fml.ModList.get().isLoaded("curios"))) output.accept(item.get()); });
            for (GasCatalog.GasDefinition gas : GasCatalog.gasDefinitions()) {
                output.accept(GasCanisterItem.createFilledCanister(gas.id()));
                output.accept(GasContainerItem.createFilledContainer(CHEMICAL_GRENADE.get(), gas.id()));
            }
        }).build());
    }
    public static void register(IEventBus bus) {
        COMPONENTS.register(bus); ATTRIBUTES.register(bus); ARMOR_MATERIALS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); SERIALIZERS.register(bus); MENUS.register(bus); EFFECTS.register(bus); PARTICLES.register(bus); TABS.register(bus);
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> event.registerItem(
            net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM,
            (stack, context) -> new gascraft.item.GrenadeLauncherEnergy(stack), GRENADE_LAUNCHER.get()));
        bus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent event) -> {
            for (var type : event.getTypes()) event.add(type, GAS_BREATH_CAPACITY, type == EntityType.PLAYER ? 300 : type == EntityType.VILLAGER || type == EntityType.WANDERING_TRADER ? 200 : 40);
        });
    }
    public static void addSulfurLoot(LootTableLoadEvent event) {
        if (event.getName().equals(ResourceLocation.withDefaultNamespace("chests/nether_bridge")))
            event.getTable().addPool(LootPool.lootPool().name("gascraft_sulfur").setRolls(net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(1)).setBonusRolls(UniformGenerator.between(0, 1))
                .add(LootItem.lootTableItem(SULFUR.get()).setWeight(50).apply(net.minecraft.world.level.storage.loot.functions.SetItemCountFunction.setCount(UniformGenerator.between(1,6))))
                .add(net.minecraft.world.level.storage.loot.entries.EmptyLootItem.emptyItem().setWeight(50)).build());
    }
    public static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new gascraft.api.event.GasAgentRegistrationEvent()));
        event.enqueueWork(gascraft.gas.GasDefinitionLoader::initialize);
        event.enqueueWork(gascraft.compat.AccessoryCompat::initialize);
        event.enqueueWork(() -> net.minecraft.world.level.block.DispenserBlock.registerBehavior(CHEMICAL_GRENADE.get(), new ProjectileDispenseBehavior(CHEMICAL_GRENADE.get()) {
            @Override public ItemStack execute(net.minecraft.core.dispenser.BlockSource source, ItemStack stack) {
                return GasContainerItem.hasContents(stack) ? super.execute(source, stack) : stack;
            }
        }));
    }
    private static java.util.Map<ResourceLocation, DeferredItem<GasCanisterItem>> registerCanisters() {
        var items = new java.util.LinkedHashMap<ResourceLocation, DeferredItem<GasCanisterItem>>();
        for (String name : java.util.List.of("smoke", "tear_gas", "pulmonary_agent", "mustard_gas", "regenerating_mist", "healing_mist", "raid_gas", "misted_meat", "sarin_gas", "poison_i", "poison_ii", "poison_iii", "napalm", "stun_gas", "misted_tetrodotoxin_i", "misted_tetrodotoxin_ii", "misted_tetrodotoxin_iii")) registerCanister(items, name);
        for (DyeColor color : DyeColor.values()) registerCanister(items, "colored_smoke_" + color.getName());
        return java.util.Collections.unmodifiableMap(items);
    }
    private static void registerCanister(java.util.Map<ResourceLocation, DeferredItem<GasCanisterItem>> items, String contents) {
        ResourceLocation gas = GasCatalog.id(contents);
        items.put(gas, ITEMS.registerItem(contents + "_canister", p -> new GasCanisterItem(p.component(GAS_CONTENTS.get(), gas))));
    }
    private static DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> particle(String name) {
        return PARTICLES.register(name, () -> new ParticleType<ColorParticleOption>(false) {
            @Override public MapCodec<ColorParticleOption> codec() { return ColorParticleOption.codec(this); }
            @Override public StreamCodec<? super RegistryFriendlyByteBuf, ColorParticleOption> streamCodec() { return ColorParticleOption.streamCodec(this); }
        });
    }
    private static final class GasSicknessEffect extends MobEffect {
        GasSicknessEffect(int color) { super(MobEffectCategory.HARMFUL, color); }
    }
    private ModContent() {}
}
