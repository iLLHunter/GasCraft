package gascraft;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class GascraftConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.BooleanValue USE_SHADERS;
    public static final ModConfigSpec.BooleanValue RENDER_GAS_OVERLAYS;
    public static final ModConfigSpec.BooleanValue STRAIGHT_LINE_PROPAGATION;
    public static final ModConfigSpec.IntValue CLOUD_LIFETIME_TICKS;
    public static final ModConfigSpec.IntValue GAS_RANGE_BLOCKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROTECTIVE_EQUIPMENT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> POWDERIZER_PREFERRED_OUTPUTS;
    public static final ModConfigSpec.DoubleValue ZOMBIE_GAS_MASK_SPAWN_CHANCE;
    public static final ModConfigSpec.DoubleValue ZOMBIE_GAS_MASK_DROP_CHANCE;
    public static final ModConfigSpec.BooleanValue ZOMBIE_GAS_MASK_SPAWNING_ENABLED;
    public static final ModConfigSpec.BooleanValue USE_MANUAL;
    public static volatile Boolean CLIENT_USE_MANUAL;
    public static final java.util.Map<String,ModConfigSpec.BooleanValue> DISABLE_GASES;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        STRAIGHT_LINE_PROPAGATION = builder.comment("Use straight distance instead of checking paths around solid blocks.").define("straight_line_propagation", false);
        CLOUD_LIFETIME_TICKS = builder.comment("Gas cloud lifetime in ticks.").defineInRange("cloud_lifetime_ticks", 600, 20, 72000);
        GAS_RANGE_BLOCKS = builder.comment("Maximum gas propagation distance in blocks.").defineInRange("gas_range_blocks", 10, 1, 32);
        PROTECTIVE_EQUIPMENT = builder.comment("Protective equipment IDs; separate head, chest, legs, feet with &, using * for any item.")
            .defineListAllowEmpty("protective_equipment", List.of(), () -> "minecraft:diamond_helmet", value -> value instanceof String);
        POWDERIZER_PREFERRED_OUTPUTS = builder.comment("Preferred Powderizer dust outputs: material=item_id, for example iron=mekanism:dust_iron. The item must belong to that material's c:dusts or c:powders tag. Without a matching preference, outputs are ordered by registry ID; Gascraft Powdered Iron and Powdered Sulfur are fallbacks after other dusts of their material.")
            .defineListAllowEmpty("powderizer_preferred_outputs", List.of(), () -> "iron=mekanism:dust_iron", gascraft.recipe.PowderizerRecipeDiscovery::isPreference);
        ZOMBIE_GAS_MASK_SPAWNING_ENABLED = builder.comment("Allow newly spawned Zombies and Zombie subclasses to receive Gascraft Gas Masks. Set false to disable mask spawning without changing the spawn/drop percentages below. Existing zombies and their masks are unchanged.")
            .define("zombie_gas_mask_spawning_enabled",true);
        ZOMBIE_GAS_MASK_SPAWN_CHANCE = builder.comment("Percent chance (0-100) for a newly spawned Zombie or Zombie subclass, including husks, drowned, zombie villagers and modded variants, to wear a Gascraft Gas Mask. Applies to all new spawn reasons, preserves existing headgear, and does not retrofit entities loaded from saves. Set to 0 to disable this spawning feature.")
            .defineInRange("zombie_gas_mask_spawn_chance",2.0,0.0,100.0);
        ZOMBIE_GAS_MASK_DROP_CHANCE = builder.comment("Base equipment drop chance in percent (0-100) for a gas mask equipped by this feature. Vanilla equipment-drop rules and Looting bonuses apply; normal mob-loot rules are respected. Set to 0 to prevent this mask from dropping on death, including with Looting. Changing this value also applies when an existing equipped zombie dies.")
            .defineInRange("zombie_gas_mask_drop_chance",20.0,0.0,100.0);
        USE_MANUAL = builder.comment("Enable the Gascraft Manual. When false, the manual is hidden from Creative/JEI, cannot be crafted, and existing copies cannot be opened. This can be changed for a running world. Its registry ID remains stable; no item migration is performed.")
            .define("useManual",true);
        var disables=new java.util.LinkedHashMap<String,ModConfigSpec.BooleanValue>();
        for(var control:gascraft.gas.GasChainControls.CONTROLS)disables.put(control.gas(),builder
            .comment("Disable "+control.name()+" and its production chain. Requires a world/server restart. Shared materials needed by enabled gases remain. Existing containers become inert; old lingering effects finish normally.")
            .worldRestart().define(control.key(),false));
        DISABLE_GASES=java.util.Collections.unmodifiableMap(disables);
        SPEC = builder.build();
        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        USE_SHADERS = client.comment("Animate gas overlays with the cloud shader.").define("use_shaders", true);
        RENDER_GAS_OVERLAYS = client.comment("Show an overlay while inside gas clouds.").define("render_gas_overlays", false);
        CLIENT_SPEC = client.build();
    }
    public static boolean manualEnabled(){return !SPEC.isLoaded()||USE_MANUAL.get();}
    public static boolean clientManualEnabled(){return CLIENT_USE_MANUAL==null?manualEnabled():CLIENT_USE_MANUAL;}
    private GascraftConfig() {}
}
