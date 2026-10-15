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
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        STRAIGHT_LINE_PROPAGATION = builder.comment("Use straight distance instead of checking paths around solid blocks.").define("straight_line_propagation", false);
        CLOUD_LIFETIME_TICKS = builder.comment("Gas cloud lifetime in ticks.").defineInRange("cloud_lifetime_ticks", 600, 20, 72000);
        GAS_RANGE_BLOCKS = builder.comment("Maximum gas propagation distance in blocks.").defineInRange("gas_range_blocks", 10, 1, 32);
        PROTECTIVE_EQUIPMENT = builder.comment("Protective equipment IDs; separate head, chest, legs, feet with &, using * for any item.")
            .defineListAllowEmpty("protective_equipment", List.of(), () -> "minecraft:diamond_helmet", value -> value instanceof String);
        POWDERIZER_PREFERRED_OUTPUTS = builder.comment("Preferred Powderizer dust outputs: material=item_id, for example iron=mekanism:dust_iron. The item must belong to that material's c:dusts or c:powders tag. Without a matching preference, outputs are ordered by registry ID; Gascraft Powdered Iron and Powdered Sulfur are fallbacks after other dusts of their material.")
            .defineListAllowEmpty("powderizer_preferred_outputs", List.of(), () -> "iron=mekanism:dust_iron", gascraft.recipe.PowderizerRecipeDiscovery::isPreference);
        SPEC = builder.build();
        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        USE_SHADERS = client.comment("Animate gas overlays with the cloud shader.").define("use_shaders", true);
        RENDER_GAS_OVERLAYS = client.comment("Show an overlay while inside gas clouds.").define("render_gas_overlays", false);
        CLIENT_SPEC = client.build();
    }
    private GascraftConfig() {}
}
