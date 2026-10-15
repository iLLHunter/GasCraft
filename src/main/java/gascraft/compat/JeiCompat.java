package gascraft.compat;
import gascraft.ModContent;
import gascraft.gas.GasCatalog;
import gascraft.item.GasContainerItem;
import gascraft.machine.*;
import gascraft.menu.*;
import gascraft.client.*;
import gascraft.recipe.MachineRecipeCatalog;
import java.util.*;
import mezz.jei.api.*;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.ingredients.subtypes.*;
import mezz.jei.api.registration.*;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import mezz.jei.api.gui.handlers.*;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;

@JeiPlugin
public final class JeiCompat implements IModPlugin {
    private final Map<MachineKind, MachineJeiCategory> categories = new LinkedHashMap<>();
    private final Map<MachineKind, List<MachineRecipeCatalog.Entry>> displayed = new LinkedHashMap<>();
    private IJeiRuntime runtime;
    public JeiCompat() {
        gascraft.gas.GasDefinitionSync.clientCatalogChanged = () -> Minecraft.getInstance().execute(this::refreshMachines);
    }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        categories.clear();
        for (var kind : MachineJeiCategory.KINDS) {
            var category = new MachineJeiCategory(kind, registration.getJeiHelpers().getGuiHelper());
            categories.put(kind, category); registration.addRecipeCategories(category);
        }
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (var kind : MachineKind.values()) registration.addRecipeCatalyst(new ItemStack(MachineJeiCategory.block(kind)), MachineJeiCategory.type(kind));
    }
    @Override public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        for (var kind : MachineJeiCategory.KINDS) {
            var type = MachineJeiCategory.type(kind);
            if (kind.isSynthesizer()) registration.addRecipeTransferHandler(new MachineJeiTransferHandler<>(SynthesizerMenu.class, MachineContent.SYNTHESIZER_MENU.get(), type, registration.getTransferHelper()), type);
            else registration.addRecipeTransferHandler(new MachineJeiTransferHandler<>(ProcessingMachineMenu.class, MachineContent.PROCESSING_MENU.get(), type, registration.getTransferHelper()), type);
        }
    }
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(GrenadeLauncherScreen.class, new IGuiContainerHandler<>() {
            @Override public List<Rect2i> getGuiExtraAreas(GrenadeLauncherScreen screen) {
                return screen.upgradesOpen() ? List.of(new Rect2i(screen.getGuiLeft() + 182, screen.getGuiTop(), 152, 90)) : List.of();
            }
        });
        registration.addGuiContainerHandler(ProcessingMachineScreen.class, new IGuiContainerHandler<>() {
            @Override public List<Rect2i> getGuiExtraAreas(ProcessingMachineScreen screen) { return extraAreas(screen.getGuiLeft(), screen.getGuiTop(), screen.upgradesOpen(), screen.sidesOpen(), 152); }
            @Override public Collection<IGuiClickableArea> getGuiClickableAreas(ProcessingMachineScreen screen, double x, double y) {
                var kind = screen.getMenu().kind();
                int arrow = kind == MachineKind.POWDER_MIXER ? 94 : kind == MachineKind.GENERATOR ? 109 : 80;
                return List.of(IGuiClickableArea.createBasic(arrow, 53, 32, 8, MachineJeiCategory.type(kind)));
            }
        });
        registration.addGuiContainerHandler(SynthesizerScreen.class, new IGuiContainerHandler<>() {
            @Override public List<Rect2i> getGuiExtraAreas(SynthesizerScreen screen) { return extraAreas(screen.getGuiLeft(), screen.getGuiTop(), screen.upgradesOpen(), screen.sidesOpen(), 236); }
            @Override public Collection<IGuiClickableArea> getGuiClickableAreas(SynthesizerScreen screen, double x, double y) {
                List<IGuiClickableArea> areas = new ArrayList<>();
                for (int i = 0; i < screen.getMenu().groups(); i++) areas.add(IGuiClickableArea.createBasic(112, 57 + i * 18, 32, 8, MachineJeiCategory.SYNTHESIZING));
                return areas;
            }
        });
    }
    private static List<Rect2i> extraAreas(int left, int top, boolean upgrades, boolean sides, int sideWidth) {
        List<Rect2i> areas = new ArrayList<>();
        if (upgrades) areas.add(new Rect2i(left + 182, top, 152, 104));
        if (sides) areas.add(new Rect2i(left + 182, top + 106, sideWidth, 116));
        return areas;
    }
    @Override public void onRuntimeAvailable(IJeiRuntime runtime) { this.runtime = runtime; }
    @Override public void onRuntimeUnavailable() { runtime = null; }
    private void refreshMachines() {
        var level = Minecraft.getInstance().level;
        if (runtime == null || level == null) return;
        var manager = runtime.getRecipeManager();
        for (var kind : MachineJeiCategory.KINDS) {
            var type = MachineJeiCategory.type(kind);
            manager.hideRecipes(type, displayed.getOrDefault(kind, List.of()));
            var category = categories.get(kind); category.ingredients(runtime.getIngredientManager());
            var entries = MachineRecipeCatalog.recipes(level, kind).stream().filter(category::available).toList();
            displayed.put(kind, entries); manager.addRecipes(type, entries); manager.unhideRecipes(type, entries);
        }
    }
    public ResourceLocation getPluginUid() { return GasCatalog.id("gascraft_jei"); }
    @Override public void registerItemSubtypes(ISubtypeRegistration registration) {
        ISubtypeInterpreter<ItemStack> interpreter=new ISubtypeInterpreter<>() {
            public Object getSubtypeData(ItemStack stack, UidContext context) { return GasContainerItem.containedGas(stack).id(); }
            public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) { return getSubtypeData(stack,context).toString(); }
        };
        ModContent.CANISTERS.values().forEach(item -> registration.registerSubtypeInterpreter(item.get(), interpreter));
        registration.registerSubtypeInterpreter(ModContent.CUSTOM_GAS_CANISTER.get(),interpreter);
        registration.registerSubtypeInterpreter(ModContent.CHEMICAL_GRENADE.get(),interpreter);
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level != null) for (var kind : MachineJeiCategory.KINDS) {
            var category = categories.get(kind); category.ingredients(registration.getIngredientManager());
            var entries = MachineRecipeCatalog.recipes(level, kind).stream().filter(category::available).toList();
            displayed.put(kind, entries); registration.addRecipes(MachineJeiCategory.type(kind), entries);
        }
        List<RecipeHolder<CraftingRecipe>> crafting=new ArrayList<>();
        for (var gas:GasCatalog.gasDefinitions()) {
            ItemStack canister=gascraft.item.GasCanisterItem.createFilledCanister(gas.id()), output=GasContainerItem.createFilledContainer(ModContent.CHEMICAL_GRENADE.get(),gas.id());
            crafting.add(createCraftingPreview("canister_empty/" + gas.id().getNamespace() + "/" + gas.id().getPath(), new ItemStack(ModContent.EMPTY_CANISTER.get()), DataComponentIngredient.of(false,canister)));
            for (Item container:List.of(ModContent.GRENADE_DIFFUSER.get(),ModContent.GRENADE_SHELL.get())) {
                ItemStack shell=new ItemStack(container); crafting.add(createCraftingPreview("grenade_fill/"+gas.id().getNamespace()+"/"+gas.id().getPath()+"/"+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(container).getPath(),output,DataComponentIngredient.of(false,shell),DataComponentIngredient.of(false,canister)));
            }
        }
        ItemStack shell=new ItemStack(ModContent.GRENADE_SHELL.get()); ItemStack grenade_diffuser=new ItemStack(ModContent.GRENADE_DIFFUSER.get()); crafting.add(createCraftingPreview("grenade_recycle",grenade_diffuser,DataComponentIngredient.of(false,shell)));
        registration.addRecipes(RecipeTypes.CRAFTING,crafting);
    }
    private static RecipeHolder<CraftingRecipe> createCraftingPreview(String id,ItemStack output,Ingredient... inputs) {
        return new RecipeHolder<>(GasCatalog.id(id),new ShapelessRecipe("gascraft",CraftingBookCategory.MISC,output,NonNullList.of(Ingredient.EMPTY,inputs)) {
            @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
                if (output.is(ModContent.CHEMICAL_GRENADE.get())) return NonNullList.withSize(input.size(), ItemStack.EMPTY);
                return super.getRemainingItems(input);
            }
        });
    }
}
