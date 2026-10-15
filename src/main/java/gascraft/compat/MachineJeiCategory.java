package gascraft.compat;

import gascraft.ModContent;
import gascraft.gas.GasCatalog;
import gascraft.machine.*;
import gascraft.recipe.*;
import java.text.NumberFormat;
import java.util.*;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;

/** Native JEI item/fluid/chemical slots make every displayed requirement searchable. */
public final class MachineJeiCategory implements IRecipeCategory<MachineRecipeCatalog.Entry> {
    public static final RecipeType<MachineRecipeCatalog.Entry> GENERATING = type("generating");
    public static final RecipeType<MachineRecipeCatalog.Entry> POWDERIZING = type("powderizing");
    public static final RecipeType<MachineRecipeCatalog.Entry> MIXING = type("powder_mixing");
    public static final RecipeType<MachineRecipeCatalog.Entry> KILN = type("kiln_processing");
    public static final RecipeType<MachineRecipeCatalog.Entry> FERMENTING = type("fermenting");
    public static final RecipeType<MachineRecipeCatalog.Entry> SYNTHESIZING = type("synthesizing");
    public static final List<MachineKind> KINDS = List.of(MachineKind.GENERATOR, MachineKind.POWDERIZER, MachineKind.POWDER_MIXER,
        MachineKind.MATERIALS_KILN, MachineKind.FERMENTER, MachineKind.STONE_SYNTHESIZER);
    public static final List<MachineKind> TIERS = List.of(MachineKind.STONE_SYNTHESIZER, MachineKind.IRON_SYNTHESIZER,
        MachineKind.DIAMOND_SYNTHESIZER, MachineKind.NETHERITE_SYNTHESIZER);
    private final MachineKind kind;
    private final IDrawable background, icon, arrow;
    private IIngredientManager ingredients;
    public MachineJeiCategory(MachineKind kind, IGuiHelper gui) {
        this.kind = kind;
        background = gui.createBlankDrawable(kind.isSynthesizer() ? 238 : 186, kind.isSynthesizer() ? 144 : kind == MachineKind.FERMENTER ? 112 : kind == MachineKind.POWDER_MIXER ? 116 : 94);
        icon = gui.createDrawableItemLike(block(kind)); arrow = gui.getRecipeArrow();
    }
    public void ingredients(IIngredientManager ingredients) { this.ingredients = ingredients; }
    private static RecipeType<MachineRecipeCatalog.Entry> type(String id) { return new RecipeType<>(GasCatalog.id(id), MachineRecipeCatalog.Entry.class); }
    public static RecipeType<MachineRecipeCatalog.Entry> type(MachineKind kind) {
        if (kind.isSynthesizer()) return SYNTHESIZING;
        return switch (kind) { case GENERATOR -> GENERATING; case POWDERIZER -> POWDERIZING; case POWDER_MIXER -> MIXING;
            case MATERIALS_KILN -> KILN; case FERMENTER -> FERMENTING; default -> throw new IllegalArgumentException("Not a machine category"); };
    }
    public static MachineBlock block(MachineKind kind) { return (MachineBlock)BuiltInRegistries.BLOCK.stream().filter(block -> block instanceof MachineBlock machine && machine.kind() == kind).findFirst().orElseThrow(); }
    public static Component text(String key, Object... args) { return Component.translatable("jei.gascraft." + key, args); }
    private static String number(long value) { return NumberFormat.getIntegerInstance(Locale.US).format(value); }
    private static String seconds(int ticks) { return String.format(Locale.ROOT, "%.1f", ticks / 20.0); }
    private static boolean available(Ingredient ingredient) { return !ingredient.hasNoItems(); }
    public static List<Fluid> fluids(SynthesizingRecipe.TankIngredient tank) {
        if (!tank.id().isEmpty()) return BuiltInRegistries.FLUID.getOptional(ResourceLocation.parse(tank.id())).filter(fluid -> fluid != net.minecraft.world.level.material.Fluids.EMPTY).map(List::of).orElse(List.of());
        return BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, ResourceLocation.parse(tank.tag())))
            .map(tag -> tag.stream().map(holder -> holder.value()).filter(fluid -> fluid != net.minecraft.world.level.material.Fluids.EMPTY).toList()).orElse(List.of());
    }
    public boolean available(MachineRecipeCatalog.Entry entry) {
        if (entry.synthesizing() == null) return entry.inputs().stream().allMatch(input -> input == Ingredient.EMPTY || available(input));
        var recipe = entry.synthesizing();
        if (!GasCatalog.contains(recipe.gas()) || entry.canisters().isEmpty() || !available(recipe.ingredient())) return false;
        var tank = recipe.tank();
        return tank.amount() == 0 || tank.type().equals("fluid") && !fluids(tank).isEmpty()
            || tank.type().equals("chemical") && ModList.get().isLoaded("mekanism") && JeiChemicalIngredients.available(tank, ingredients);
    }
    @Override public RecipeType<MachineRecipeCatalog.Entry> getRecipeType() { return type(kind); }
    @Override public Component getTitle() { return kind.isSynthesizer() ? text("synthesizing") : block(kind).getName(); }
    @Override public IDrawable getBackground() { return background; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public ResourceLocation getRegistryName(MachineRecipeCatalog.Entry recipe) { return recipe.id(); }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, MachineRecipeCatalog.Entry entry, IFocusGroup focus) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (kind.isSynthesizer()) {
            var recipe = entry.synthesizing();
            var bases = new ArrayList<mezz.jei.api.gui.builder.IRecipeSlotBuilder>();
            for (int i = 0; i < 3; i++) {
                bases.add(builder.addInputSlot(4 + i * 20, 18).addItemStacks(entry.canisters()).setStandardSlotBackground().setSlotName("canister_" + i));
                builder.addOutputSlot(174 + i * 20, 18).addItemStack(entry.result(level)).setStandardSlotBackground().setSlotName("output_" + i);
            }
            builder.createFocusLink(bases.toArray(mezz.jei.api.gui.builder.IIngredientAcceptor<?>[]::new));
            builder.addInputSlot(4, 50).addIngredients(recipe.ingredient()).setStandardSlotBackground().setSlotName("ingredient");
            var tank = recipe.tank();
            if (tank.amount() > 0) {
                var slot = builder.addInputSlot(64, 50).setStandardSlotBackground().setSlotName("tank");
                if (tank.type().equals("fluid")) {
                    fluids(tank).forEach(fluid -> slot.addFluidStack(fluid, tank.amount()));
                    slot.setFluidRenderer(tank.amount(), false, 16, 16);
                } else JeiChemicalIngredients.add(slot, tank, ingredients);
            }
            if (recipe.reagentCharges() > 0) builder.addInputSlot(118, 50).addItemStacks(SynthesizerMachine.reagentItems(recipe.reagent()))
                .setStandardSlotBackground().setSlotName("reagent").addTooltipCallback((slot, tooltip) -> {
                    tooltip.add(text("reagent_value", SynthesizerMachine.REAGENT_CHARGES)); tooltip.add(text("reagent_cost", recipe.reagentCharges()));
                });
        } else {
            var inputs = entry.inputs();
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == Ingredient.EMPTY) continue;
                builder.addInputSlot(kind == MachineKind.POWDER_MIXER ? 6 + i % 3 * 18 : 40,
                    kind == MachineKind.POWDER_MIXER ? 6 + i / 3 * 18 : 18).addIngredients(inputs.get(i)).setStandardSlotBackground().setSlotName("input_" + i);
            }
            var output = entry.result(level);
            if (!output.isEmpty()) builder.addOutputSlot(kind == MachineKind.POWDER_MIXER ? 136 : 128,
                kind == MachineKind.POWDER_MIXER ? 24 : 18).addItemStack(output).setOutputSlotBackground().setSlotName("output");
        }
    }
    private void label(GuiGraphics graphics, Component label, int x, int y) { graphics.drawString(Minecraft.getInstance().font, label, x, y, 0xff404040, false); }
    @Override public void draw(MachineRecipeCatalog.Entry entry, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        if (kind.isSynthesizer()) {
            var recipe = entry.synthesizing();
            label(graphics, text("canisters"), 4, 5); label(graphics, text("output"), 174, 5); arrow.draw(graphics, 98, 19);
            label(graphics, text("ingredient"), 4, 39);
            if (recipe.tank().amount() > 0) { label(graphics, text("tank"), 64, 39); label(graphics, Component.literal(number(recipe.tank().amount()) + " mB"), 60, 69); }
            if (recipe.reagentCharges() > 0) { label(graphics, text("reagent"), 118, 39); label(graphics, text("charges", recipe.reagentCharges()), 112, 69); }
            label(graphics, text("minimum_tier", text("tier." + recipe.minimumTier())), 4, 82);
            int y = 96;
            for (var tier : TIERS) if (recipe.tierAllows(tier)) {
                label(graphics, text("tier_stats", text("tier." + tier.name().replace("_SYNTHESIZER", "").toLowerCase(Locale.ROOT)),
                    seconds(tier.baseProcessingTicks()), number(tier.baseProcessingTicks() * 32L)), 4, y); y += 12;
            }
        } else {
            arrow.draw(graphics, kind == MachineKind.POWDER_MIXER ? 90 : 76, kind == MachineKind.POWDER_MIXER ? 25 : 19);
            int y = kind == MachineKind.POWDER_MIXER ? 66 : 46;
            label(graphics, text("time", seconds(entry.ticks())), 4, y);
            if (kind == MachineKind.FERMENTER) {
                label(graphics, text("whole_stack"), 4, y + 14); label(graphics, text("sky_time", seconds((entry.ticks() + 1) / 2)), 4, y + 28);
            } else {
                int rate = kind == MachineKind.GENERATOR ? 32 : ProcessingBalance.energyPerTick(entry.ticks(), entry.energy(), 0, 0);
                label(graphics, text(kind == MachineKind.GENERATOR ? "generation" : "rate", rate), 4, y + 12);
                label(graphics, text(kind == MachineKind.GENERATOR ? "generated_total" : "total", number(kind == MachineKind.GENERATOR ? entry.fuelTicks() * 32L : rate * (long)entry.ticks())), 4, y + 24);
            }
        }
        label(graphics, Component.literal("?"), getWidth() - 8, getHeight() - 11);
    }
    @Override public List<Component> getTooltipStrings(MachineRecipeCatalog.Entry entry, IRecipeSlotsView slots, double x, double y) {
        if (x < getWidth() - 14 || y < getHeight() - 17) return List.of();
        if (kind == MachineKind.FERMENTER) return List.of(text("fermenter_help"), text("sky_help"));
        if (kind == MachineKind.GENERATOR) return List.of(text("generator_help"));
        return List.of(text("base_help"), text("upgrade_help"));
    }
}
