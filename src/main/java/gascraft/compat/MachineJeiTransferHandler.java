package gascraft.compat;

import gascraft.machine.MachineRecipeTransfer;
import gascraft.menu.*;
import gascraft.recipe.MachineRecipeCatalog;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** One click prepares one operation; fluid/chemical filling remains a separate action. */
public final class MachineJeiTransferHandler<C extends AbstractContainerMenu> implements IRecipeTransferHandler<C, MachineRecipeCatalog.Entry> {
    private final Class<C> container;
    private final MenuType<C> menu;
    private final RecipeType<MachineRecipeCatalog.Entry> type;
    private final IRecipeTransferHandlerHelper helper;
    public MachineJeiTransferHandler(Class<C> container, MenuType<C> menu, RecipeType<MachineRecipeCatalog.Entry> type, IRecipeTransferHandlerHelper helper) {
        this.container = container; this.menu = menu; this.type = type; this.helper = helper;
    }
    @Override public Class<C> getContainerClass() { return container; }
    @Override public Optional<MenuType<C>> getMenuType() { return Optional.of(menu); }
    @Override public RecipeType<MachineRecipeCatalog.Entry> getRecipeType() { return type; }
    @Override public IRecipeTransferError transferRecipe(C menu, MachineRecipeCatalog.Entry recipe, IRecipeSlotsView slots, Player player, boolean maximum, boolean transfer) {
        if (menu instanceof ProcessingMachineMenu processing && recipe.kind() != processing.kind()) return helper.createInternalError();
        var plan = MachineRecipeTransfer.plan(menu, player, recipe);
        if (!plan.valid()) return helper.createUserErrorWithTooltip(MachineJeiCategory.text("transfer." + plan.error()));
        if (transfer) PacketDistributor.sendToServer(new MachineRecipeTransfer.Request(menu.containerId, recipe.id()));
        return null;
    }
}
