package gascraft.machine;

import gascraft.gas.GasCatalog;
import gascraft.ModContent;
import gascraft.menu.*;
import gascraft.recipe.MachineRecipeCatalog;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Atomic, server-validated transfers; requests identify recipes, never supply trusted item stacks. */
public final class MachineRecipeTransfer {
    public record Request(int containerId, ResourceLocation recipe) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(GasCatalog.id("machine_recipe_transfer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Request::containerId, ResourceLocation.STREAM_CODEC, Request::recipe, Request::new);
        @Override public Type<Request> type() { return TYPE; }
    }
    public record Plan(String error, Map<Integer, ItemStack> targets, List<ItemStack> inventory) {
        public boolean valid() { return error.isEmpty(); }
        public void apply(AbstractContainerMenu menu, Player player) {
            if (!valid()) return;
            for (int i = 0; i < inventory.size(); i++) player.getInventory().setItem(i, inventory.get(i));
            targets.forEach((index, stack) -> menu.slots.get(index).setByPlayer(stack));
            player.getInventory().setChanged(); menu.broadcastChanges();
        }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(Request.TYPE, Request.CODEC, (request, context) -> context.enqueueWork(() -> {
            Player player = context.player(); var menu = player.containerMenu;
            if (menu.containerId != request.containerId() || !menu.stillValid(player)) return;
            MachineKind kind = menu instanceof ProcessingMachineMenu processing ? processing.kind()
                : menu instanceof SynthesizerMenu synth ? synth.kind() : null;
            if (kind == null || !menu.getCarried().isEmpty()) return;
            MachineRecipeCatalog.recipes(player.level(), kind).stream().filter(entry -> entry.id().equals(request.recipe())).findFirst()
                .ifPresent(entry -> plan(menu, player, entry).apply(menu, player));
        }));
    }
    public static Plan plan(AbstractContainerMenu menu, Player player, MachineRecipeCatalog.Entry recipe) {
        if (!menu.getCarried().isEmpty()) return failure("cursor");
        var planner = new Planner(menu, player);
        if (menu instanceof ProcessingMachineMenu processing) {
            if (recipe.kind() != processing.kind()) return failure("wrong_machine");
            if (processing.active() && processing.kind() != MachineKind.GENERATOR) return failure("locked");
            var ingredients = recipe.inputs();
            for (int i = 0; i < ingredients.size(); i++) {
                var ingredient = ingredients.get(i); var slot = menu.slots.get(i);
                if (ingredient.isEmpty()) { if (slot.hasItem()) return failure("occupied"); continue; }
                var filter = processing.kind() == MachineKind.POWDER_MIXER ? processing.filter(i) : ItemStack.EMPTY;
                Predicate<ItemStack> matches = stack -> ingredient.test(stack)
                    && (filter.isEmpty() || ItemStack.isSameItemSameComponents(filter, stack));
                boolean preferIronDust = processing.kind() == MachineKind.POWDER_MIXER
                    && recipe.result(player.level()).is(ModContent.FLASH_POWDER.get()) && (i == 1 || i == 7);
                if (!planner.fill(i, 1, matches, stack -> !preferIronDust || !stack.is(ModContent.POWDERED_IRON.get())))
                    return failure(filter.isEmpty() ? "items" : "filters");
            }
        } else if (menu instanceof SynthesizerMenu synth) {
            var operation = recipe.synthesizing();
            if (operation == null || !operation.tierAllows(synth.kind())) return failure("tier");
            int start = synth.kind().hasReagentSlot() ? 3 : 2, group = -1;
            for (int candidate = 0; candidate < synth.groups(); candidate++) {
                if (synth.finished(candidate) || synth.totalTicks(candidate) > 0) continue;
                boolean suitable = true;
                for (int i = 0; i < 3; i++) {
                    var slot = menu.slots.get(start + candidate * 3 + i);
                    if (slot.hasItem() && !operation.baseMatches(slot.getItem())) suitable = false;
                }
                if (suitable) { group = candidate; break; }
            }
            if (group < 0) return failure("working_slots");
            ItemStack existingBase = ItemStack.EMPTY;
            for (int i = 0; i < 3; i++) if (menu.slots.get(start + group * 3 + i).hasItem()) existingBase = menu.slots.get(start + group * 3 + i).getItem();
            for (int i = 0; i < 3; i++) {
                final var selectedBase = existingBase;
                Predicate<ItemStack> matches = stack -> operation.baseMatches(stack)
                    && (selectedBase.isEmpty() || ItemStack.isSameItemSameComponents(selectedBase, stack));
                int slot = start + group * 3 + i;
                if (!planner.fill(slot, 1, matches)) return failure("items");
                existingBase = planner.targets.getOrDefault(slot, menu.slots.get(slot).getItem());
            }
            if (!planner.fill(0, synth.reservedIngredients() + 1, operation.ingredient()::test)) return failure("items");
            if (operation.reagentCharges() > 0) {
                if (!synth.canUseReagent(operation.reagent())) return failure("reagent");
                int needed = Math.max(0, operation.reagentCharges() - (synth.charges() - synth.reservedCharges()));
                int reagents = (int)((needed + (long)SynthesizerMachine.REAGENT_CHARGES - 1) / SynthesizerMachine.REAGENT_CHARGES);
                if (reagents > 0 && !planner.fill(2, reagents, stack -> SynthesizerMachine.reagentMatches(operation.reagent(), stack))) return failure("reagent");
            }
        } else return failure("wrong_machine");
        return new Plan("", planner.targets, planner.inventory);
    }
    private static Plan failure(String error) { return new Plan(error, Map.of(), List.of()); }
    private static final class Planner {
        private final AbstractContainerMenu menu;
        private final List<ItemStack> inventory = new ArrayList<>();
        private final Map<Integer, ItemStack> targets = new LinkedHashMap<>();
        private Planner(AbstractContainerMenu menu, Player player) {
            this.menu = menu;
            for (int i = 0; i < 36; i++) inventory.add(player.getInventory().getItem(i).copy());
        }
        private boolean fill(int index, int count, Predicate<ItemStack> matches) {
            return fill(index, count, matches, stack -> true);
        }
        private boolean fill(int index, int count, Predicate<ItemStack> matches, Predicate<ItemStack> preferred) {
            Slot slot = menu.slots.get(index); ItemStack target = slot.getItem().copy();
            if (!target.isEmpty() && !matches.test(target)) return false;
            if (target.getCount() >= count) return true;
            for (boolean preferredPass : new boolean[]{true,false}) {
              for (ItemStack source : inventory) {
                if (preferred.test(source) != preferredPass) continue;
                if (source.isEmpty() || !matches.test(source) || !slot.mayPlace(source)
                    || !target.isEmpty() && !ItemStack.isSameItemSameComponents(target, source)) continue;
                int room = slot.getMaxStackSize(source) - target.getCount();
                int move = Math.min(source.getCount(), Math.min(count - target.getCount(), room));
                if (move <= 0) continue;
                target = source.copyWithCount(target.getCount() + move); source.shrink(move);
                if (target.getCount() == count) break;
              }
              if (target.getCount() == count) break;
            }
            if (target.getCount() < count) return false;
            targets.put(index, target); return true;
        }
    }
    private MachineRecipeTransfer() {}
}
