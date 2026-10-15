package gascraft.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import gascraft.ModContent;
import gascraft.gas.GasCatalog;
import gascraft.item.GasCanisterItem;
import gascraft.item.GasContainerItem;
import gascraft.machine.*;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Batch recipes are independent of gas effects; the existing reloadable catalog supplies legacy conversions. */
public record SynthesizingRecipe(ResourceLocation gas, Ingredient ingredient, Ingredient input, String inputGas, String inputPotion,
                                 TankIngredient tank, String minimumTier, int reagentCharges, String reagent) implements Recipe<SynthesizingRecipe.Input> {
    public SynthesizingRecipe(ResourceLocation gas, Ingredient ingredient, Ingredient input, String inputGas, String inputPotion,
                              TankIngredient tank, String minimumTier, int reagentCharges) {
        this(gas, ingredient, input, inputGas, inputPotion, tank, minimumTier, reagentCharges, "diamond");
    }
    public record Input(List<ItemStack> canisters, ItemStack ingredient) implements RecipeInput {
        @Override public ItemStack getItem(int slot) { return slot == 3 ? ingredient : canisters.get(slot); }
        @Override public int size() { return 4; }
    }
    public record TankIngredient(String type, String id, String tag, int amount) {
        public static final TankIngredient EMPTY = new TankIngredient("", "", "", 0);
        public static final Codec<TankIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(TankIngredient::type),
            Codec.STRING.optionalFieldOf("id", "").forGetter(TankIngredient::id),
            Codec.STRING.optionalFieldOf("tag", "").forGetter(TankIngredient::tag),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("amount").forGetter(TankIngredient::amount)
        ).apply(instance, TankIngredient::new));
        public TankIngredient {
            if (amount != 0 && (!type.equals("fluid") && !type.equals("chemical") || id.isEmpty() == tag.isEmpty()))
                throw new IllegalArgumentException("Tank ingredient needs fluid/chemical type and exactly one ID or tag");
            if (!id.isEmpty()) ResourceLocation.parse(id);
            if (!tag.isEmpty()) ResourceLocation.parse(tag);
        }
        public boolean matches(MachineTank stored) {
            if (amount == 0) return true;
            if (type.equals("chemical")) return stored.chemical() != null && (id.isEmpty() ? stored.chemicalTag(ResourceLocation.parse(tag)) : stored.chemical().equals(ResourceLocation.parse(id)));
            var fluid = stored.fluid();
            return !fluid.isEmpty() && (id.isEmpty() ? fluid.is(TagKey.create(Registries.FLUID, ResourceLocation.parse(tag)))
                : BuiltInRegistries.FLUID.getKey(fluid.getFluid()).equals(ResourceLocation.parse(id)));
        }
    }
    public SynthesizingRecipe {
        if (input.isEmpty() == inputGas.isEmpty()) throw new IllegalArgumentException("Specify exactly one input ingredient or input_gas");
        if (!inputGas.isEmpty()) ResourceLocation.parse(inputGas);
        if (!inputPotion.isEmpty()) ResourceLocation.parse(inputPotion);
        if (!List.of("stone", "iron", "diamond", "netherite").contains(minimumTier) || reagentCharges < 0)
            throw new IllegalArgumentException("Invalid Synthesizer tier or reagent charges");
        if (!List.of("diamond", "nether_star").contains(reagent)) throw new IllegalArgumentException("Unknown Synthesizer reagent");
    }
    public int tier() { return List.of("stone", "iron", "diamond", "netherite").indexOf(minimumTier); }
    public boolean tierAllows(MachineKind kind) {
        return kind.isSynthesizer() && kind.ordinal() - MachineKind.STONE_SYNTHESIZER.ordinal() >= tier() && (reagentCharges == 0 || kind.hasReagentSlot());
    }
    public boolean baseMatches(ItemStack stack) {
        if (!inputGas.isEmpty()) return GasCanisterItem.isCanister(stack) && GasContainerItem.hasContents(stack)
            && GasContainerItem.containedGas(stack).id().equals(ResourceLocation.parse(inputGas));
        return input.test(stack) && (inputPotion.isEmpty() || BuiltInRegistries.POTION.getHolder(ResourceLocation.parse(inputPotion))
            .map(potion -> stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(potion)).orElse(false));
    }
    @Override public boolean matches(Input inputs, Level level) {
        return GasCatalog.contains(gas) && ingredient.test(inputs.ingredient()) && inputs.canisters().size() == 3 && inputs.canisters().stream().allMatch(this::baseMatches);
    }
    @Override public ItemStack assemble(Input inputs, HolderLookup.Provider registries) { return getResultItem(registries); }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return GasCanisterItem.createFilledCanister(gas); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 4; }
    @Override public boolean isSpecial() { return true; }
    @Override public NonNullList<Ingredient> getIngredients() { return NonNullList.of(Ingredient.EMPTY, ingredient); }
    @Override public RecipeType<?> getType() { return MachineRecipes.SYNTHESIZING.get(); }
    @Override public RecipeSerializer<?> getSerializer() { return MachineRecipes.SYNTHESIZING_SERIALIZER.get(); }
    public static SynthesizingRecipe fromLegacy(GasCatalog.BrewingMix mix) {
        Ingredient ingredient = !mix.ingredientItem().isEmpty() ? Ingredient.of(BuiltInRegistries.ITEM.get(ResourceLocation.parse(mix.ingredientItem())))
            : Ingredient.of(TagKey.create(Registries.ITEM, ResourceLocation.parse(mix.ingredientTag())));
        boolean mundane = mix.inputPotion().equals("minecraft:mundane") && mix.inputItem().equals("minecraft:potion");
        boolean sarin = mundane && mix.result().equals(GasCatalog.id("sarin_gas"))
            && mix.ingredientItem().equals("gascraft:sarin_pellet");
        Ingredient input = mundane ? Ingredient.of(ModContent.EMPTY_CANISTER.get()) : mix.inputGas().isEmpty()
            ? Ingredient.of(BuiltInRegistries.ITEM.get(ResourceLocation.parse(mix.inputItem()))) : Ingredient.EMPTY;
        return new SynthesizingRecipe(mix.result(), ingredient, input, mix.inputGas(), mundane ? "" : mix.inputPotion(),
            mundane ? new TankIngredient("fluid", "minecraft:water", "", sarin ? 3_000 : 600) : TankIngredient.EMPTY,
            sarin ? "netherite" : "stone", sarin ? 3 : 0);
    }
    public static final MapCodec<SynthesizingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        ResourceLocation.CODEC.fieldOf("gas").forGetter(SynthesizingRecipe::gas),
        Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(SynthesizingRecipe::ingredient),
        Ingredient.CODEC.optionalFieldOf("input", Ingredient.EMPTY).forGetter(SynthesizingRecipe::input),
        Codec.STRING.optionalFieldOf("input_gas", "").forGetter(SynthesizingRecipe::inputGas),
        Codec.STRING.optionalFieldOf("input_potion", "").forGetter(SynthesizingRecipe::inputPotion),
        TankIngredient.CODEC.optionalFieldOf("tank", TankIngredient.EMPTY).forGetter(SynthesizingRecipe::tank),
        Codec.STRING.optionalFieldOf("minimum_tier", "stone").forGetter(SynthesizingRecipe::minimumTier),
        Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("reagent_charges", 0).forGetter(SynthesizingRecipe::reagentCharges),
        Codec.STRING.optionalFieldOf("reagent", "diamond").forGetter(SynthesizingRecipe::reagent)
    ).apply(instance, SynthesizingRecipe::new));
    public static final class Serializer implements RecipeSerializer<SynthesizingRecipe> {
        @Override public MapCodec<SynthesizingRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, SynthesizingRecipe> streamCodec() { return ByteBufCodecs.fromCodecWithRegistries(CODEC.codec()); }
    }
}
