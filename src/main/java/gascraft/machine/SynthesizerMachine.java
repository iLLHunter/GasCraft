package gascraft.machine;

import gascraft.ModContent;
import gascraft.gas.GasCatalog;
import gascraft.item.GasCanisterItem;
import gascraft.recipe.*;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.*;

/** Groups of three staged canisters share inputs while reserving resources for every paid operation. */
public final class SynthesizerMachine {
    public static final int REAGENT_CHARGES = 12;
    public static final int INGREDIENT = 0, CANISTER = 1, REAGENT = 2, WORKING = 3;
    private final MachineBlockEntity owner;
    private final ItemStackHandler inventory;
    private final Work[] batches;
    private final boolean[] finished;
    private int charges;
    private String reagentType = "diamond";
    private boolean internalChange;

    public SynthesizerMachine(MachineBlockEntity owner) {
        this.owner = owner;
        batches = new Work[owner.kind().workingSlots() / 3]; finished = new boolean[batches.length];
        inventory = new ItemStackHandler(WORKING + owner.kind().workingSlots()) {
            @Override public void setSize(int size) { super.setSize(WORKING + owner.kind().workingSlots()); }
            @Override public int getSlotLimit(int slot) { return slot >= WORKING ? 1 : 64; }
            @Override protected int getStackLimit(int slot, ItemStack stack) { return slot == INGREDIENT && stack.hasCraftingRemainingItem() ? 1 : super.getStackLimit(slot, stack); }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                if (stack.isEmpty()) return false;
                if (slot == INGREDIENT) return active() ? sameIngredient(stack) : recipes().stream().anyMatch(recipe -> recipe.tierAllows(owner.kind()) && recipe.ingredient().test(stack));
                if (slot == REAGENT) return owner.kind().hasReagentSlot() && (diamond(stack) || stack.is(Items.NETHER_STAR))
                    && (charges == 0 || reagentMatches(reagentType, stack));
                if (!canister(stack)) return false;
                if (slot == CANISTER) return true;
                int group = (slot - WORKING) / 3;
                return batches[group] == null && !finished[group] && compatibleGroup(group, stack);
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return !internalChange && locked(slot) ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
            }
            @Override protected void onContentsChanged(int slot) { owner.setChanged(); }
        };
    }
    public ItemStackHandler inventory() { return inventory; }
    public int groups() { return batches.length; }
    public int charges() { return charges; }
    public boolean canUseReagent(String type) { return charges == 0 || reagentType.equals(type); }
    public String reagentType() {
        if (charges > 0) return reagentType;
        ItemStack stack = inventory.getStackInSlot(REAGENT);
        if (!stack.isEmpty()) return stack.is(Items.NETHER_STAR) ? "nether_star" : "diamond";
        return recipes().stream().filter(recipe -> recipe.reagentCharges() > 0 && recipe.ingredient().test(inventory.getStackInSlot(INGREDIENT)))
            .map(SynthesizingRecipe::reagent).findFirst().orElse("diamond");
    }
    public boolean canDumpCharges() { return charges > 0 && reservedCharges() == 0; }
    public boolean dumpCharges() {
        if (!canDumpCharges()) return false;
        charges = 0; reagentType = "diamond"; owner.setChanged(); return true;
    }
    public boolean active() { return Arrays.stream(batches).anyMatch(Objects::nonNull); }
    public int progress(int group) { return batches[group] == null ? 0 : batches[group].progress; }
    public int totalTicks(int group) { return batches[group] == null ? 0 : batches[group].ticks; }
    public boolean finished(int group) { return finished[group]; }
    public int activeMask() { int mask = 0; for (int i = 0; i < groups(); i++) if (batches[i] != null) mask |= 1 << i; return mask; }
    public int finishedMask() { int mask = 0; for (int i = 0; i < groups(); i++) if (finished[i]) mask |= 1 << i; return mask; }
    public int energyPerTick() { return Arrays.stream(batches).filter(Objects::nonNull).mapToInt(work -> work.rate).sum(); }
    public int reservedIngredients() { return (int)Arrays.stream(batches).filter(Objects::nonNull).count(); }
    public int reservedTank() { return Arrays.stream(batches).filter(Objects::nonNull).mapToInt(work -> work.tankAmount).sum(); }
    public int reservedCharges() { return Arrays.stream(batches).filter(Objects::nonNull).mapToInt(work -> work.charges).sum(); }
    public boolean locked(int slot) {
        if (slot == INGREDIENT) return active();
        if (slot == REAGENT) return reservedCharges() > 0;
        return slot >= WORKING && batches[(slot - WORKING) / 3] != null;
    }
    public static boolean diamond(ItemStack stack) { return stack.is(Items.DIAMOND)
        || stack.is(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "dusts/diamond"))); }
    public static boolean reagentMatches(String type, ItemStack stack) { return type.equals("nether_star") ? stack.is(Items.NETHER_STAR) : diamond(stack); }
    public static List<ItemStack> reagentItems(String type) {
        if (type.equals("nether_star")) return List.of(new ItemStack(Items.NETHER_STAR));
        var items = new ArrayList<ItemStack>(); items.add(new ItemStack(Items.DIAMOND));
        Arrays.stream(net.minecraft.world.item.crafting.Ingredient.of(TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "dusts/diamond"))).getItems()).filter(SynthesizerMachine::diamond).forEach(items::add);
        return items;
    }
    public List<SynthesizingRecipe> recipes() {
        if (owner.getLevel() == null) return List.of();
        List<SynthesizingRecipe> recipes = new ArrayList<>();
        owner.getLevel().getRecipeManager().getAllRecipesFor(MachineRecipes.SYNTHESIZING.get()).forEach(holder -> recipes.add(holder.value()));
        for (var mix : GasCatalog.brewingMixes()) {
            try { recipes.add(SynthesizingRecipe.fromLegacy(mix)); } catch (IllegalArgumentException ignored) { /* Invalid legacy definitions cannot match. */ }
        }
        return recipes;
    }
    private boolean sameIngredient(ItemStack stack) {
        return Arrays.stream(batches).filter(Objects::nonNull).allMatch(work -> ItemStack.isSameItemSameComponents(work.ingredient, stack));
    }
    private boolean canister(ItemStack stack) {
        return stack.is(ModContent.EMPTY_CANISTER.get()) || GasCanisterItem.isCanister(stack)
            || recipes().stream().anyMatch(recipe -> recipe.tierAllows(owner.kind()) && recipe.baseMatches(stack));
    }
    private boolean compatibleGroup(int group, ItemStack stack) {
        for (int i = 0; i < 3; i++) {
            ItemStack existing = inventory.getStackInSlot(WORKING + group * 3 + i);
            if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) return false;
        }
        return true;
    }
    private List<ItemStack> groupItems(int group) {
        return List.of(inventory.getStackInSlot(WORKING + group * 3), inventory.getStackInSlot(WORKING + group * 3 + 1), inventory.getStackInSlot(WORKING + group * 3 + 2));
    }
    private void stage() {
        internalChange = true;
        try {
            for (int group = 0; group < groups(); group++) {
                if (finished[group] && groupItems(group).stream().allMatch(ItemStack::isEmpty)) finished[group] = false;
                if (finished[group] || batches[group] != null) continue;
                for (int i = 0; i < 3; i++) {
                    ItemStack input = inventory.getStackInSlot(CANISTER);
                    int slot = WORKING + group * 3 + i;
                    if (!input.isEmpty() && inventory.getStackInSlot(slot).isEmpty() && compatibleGroup(group, input)) {
                        inventory.setStackInSlot(slot, input.copyWithCount(1));
                        inventory.setStackInSlot(CANISTER, input.copyWithCount(input.getCount() - 1));
                    }
                }
            }
        } finally { internalChange = false; }
    }
    private Work find(int group, List<SynthesizingRecipe> recipes) {
        var input = new SynthesizingRecipe.Input(groupItems(group), inventory.getStackInSlot(INGREDIENT));
        for (var recipe : recipes) {
            if (!recipe.tierAllows(owner.kind()) || !recipe.matches(input, owner.getLevel()) || !recipe.tank().matches(owner.tank())) continue;
            if (input.ingredient().getCount() <= reservedIngredients() || owner.tank().amount() - reservedTank() < recipe.tank().amount()) continue;
            if (recipe.reagentCharges() > 0 && !canUseReagent(recipe.reagent())) continue;
            int needed = Math.max(0, recipe.reagentCharges() - (charges - reservedCharges()));
            int reagents = (int)((needed + (long)REAGENT_CHARGES - 1) / REAGENT_CHARGES);
            if (reagents > 0 && (!reagentMatches(recipe.reagent(), inventory.getStackInSlot(REAGENT)) || inventory.getStackInSlot(REAGENT).getCount() < reagents)) continue;
            return new Work(groupItems(group).stream().map(stack -> stack.copyWithCount(1)).toList(), input.ingredient().copyWithCount(1),
                recipe.getResultItem(owner.getLevel().registryAccess()), recipe.tank().amount(), owner.tank().fluid(), owner.tank().chemical(), recipe.reagentCharges(), recipe.reagent(),
                ProcessingBalance.processingTicks(owner.kind().baseProcessingTicks(), owner.upgrades().speed()),
                ProcessingBalance.energyPerTick(owner.kind().baseProcessingTicks(), owner.kind().baseProcessingTicks() * 32, owner.upgrades().speed(), owner.upgrades().efficiency()));
        }
        return null;
    }
    private boolean valid(int group, Work work) {
        for (int i = 0; i < 3; i++) if (!ItemStack.isSameItemSameComponents(work.canisters.get(i), groupItems(group).get(i))) return false;
        ItemStack ingredient = inventory.getStackInSlot(INGREDIENT);
        if (!ItemStack.isSameItemSameComponents(ingredient, work.ingredient) || ingredient.getCount() < reservedIngredients()) return false;
        if (work.tankAmount > 0 && (owner.tank().amount() < reservedTank() || (work.chemical == null
            ? !FluidStack.isSameFluidSameComponents(owner.tank().fluid(), work.fluid) : !work.chemical.equals(owner.tank().chemical())))) return false;
        return charges >= reservedCharges() && (work.charges == 0 || reagentType.equals(work.reagent));
    }
    public void tick() {
        stage();
        List<SynthesizingRecipe> recipes = recipes();
        boolean running = false;
        for (int group = 0; group < groups(); group++) {
            if (finished[group]) continue;
            Work work = batches[group];
            boolean fresh = work == null;
            if (fresh) work = find(group, recipes);
            if (work == null || !fresh && !valid(group, work) || !owner.energy().spend(work.rate)) continue;
            if (fresh) {
                int needed = Math.max(0, work.charges - (charges - reservedCharges()));
                int reagents = (int)((needed + (long)REAGENT_CHARGES - 1) / REAGENT_CHARGES);
                if (reagents > 0) {
                    ItemStack reagent = inventory.getStackInSlot(REAGENT);
                    inventory.setStackInSlot(REAGENT, reagent.copyWithCount(reagent.getCount() - reagents));
                    reagentType = work.reagent; charges += reagents * REAGENT_CHARGES;
                }
                batches[group] = work;
            }
            work.progress++; running = true; owner.setChanged();
            if (work.progress >= work.ticks) complete(group, work);
        }
        if (owner.getBlockState().getValue(MachineBlock.LIT) != running)
            owner.getLevel().setBlock(owner.getBlockPos(), owner.getBlockState().setValue(MachineBlock.LIT, running), 3);
    }
    private void complete(int group, Work work) {
        internalChange = true;
        try {
            ItemStack input = inventory.getStackInSlot(INGREDIENT).copy(); input.shrink(1);
            ItemStack remainder = work.ingredient.getCraftingRemainingItem();
            if (!remainder.isEmpty()) input = input.isEmpty() ? remainder : input.copyWithCount(input.getCount() + remainder.getCount());
            inventory.setStackInSlot(INGREDIENT, input);
            charges -= work.charges;
            batches[group] = null; finished[group] = true;
            for (int i = 0; i < 3; i++) inventory.setStackInSlot(WORKING + group * 3 + i, work.result.copy());
            if (work.tankAmount > 0) owner.tank().consume(work.tankAmount);
        } finally { internalChange = false; }
    }
    public IItemHandler sideView(Direction side) {
        return new IItemHandler() {
            private boolean enabled(MachineSides.Route route) { return !owner.isRemoved() && owner.sides().enabled(side, owner.getBlockState().getValue(MachineBlock.FACING), route); }
            @Override public int getSlots() { return inventory.getSlots(); }
            @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
            @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                return slot < WORKING && enabled(slot == INGREDIENT ? MachineSides.Route.INGREDIENT : slot == CANISTER ? MachineSides.Route.CANISTER : MachineSides.Route.REAGENT)
                    && inventory.isItemValid(slot, stack);
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return isItemValid(slot, stack) ? inventory.insertItem(slot, stack, simulate) : stack; }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return slot >= WORKING && finished[(slot - WORKING) / 3] && enabled(MachineSides.Route.OUTPUT) ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }
        };
    }
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag(); tag.put("inventory", inventory.serializeNBT(registries)); tag.putInt("charges", charges);
        tag.putString("reagent_type", reagentType);
        ListTag work = new ListTag(); byte[] outputs = new byte[groups()];
        for (int group = 0; group < groups(); group++) { work.add(batches[group] == null ? new CompoundTag() : batches[group].save(registries)); outputs[group] = (byte)(finished[group] ? 1 : 0); }
        tag.put("batches", work); tag.putByteArray("finished", outputs); return tag;
    }
    public void load(HolderLookup.Provider registries, CompoundTag tag) {
        inventory.deserializeNBT(registries, tag.getCompound("inventory")); charges = Math.max(0, tag.getInt("charges"));
        reagentType = tag.getString("reagent_type").equals("nether_star") ? "nether_star" : "diamond";
        ListTag work = tag.getList("batches", Tag.TAG_COMPOUND); byte[] outputs = tag.getByteArray("finished");
        for (int group = 0; group < groups(); group++) {
            batches[group] = group < work.size() ? Work.load(registries, work.getCompound(group)) : null;
            finished[group] = group < outputs.length && outputs[group] != 0 && batches[group] == null;
        }
    }
    private static final class Work {
        private final List<ItemStack> canisters;
        private final ItemStack ingredient, result;
        private final int tankAmount, charges, ticks, rate;
        private final String reagent;
        private final FluidStack fluid;
        private final ResourceLocation chemical;
        private int progress;
        private Work(List<ItemStack> canisters, ItemStack ingredient, ItemStack result, int tankAmount, FluidStack fluid, ResourceLocation chemical, int charges, String reagent, int ticks, int rate) {
            this.canisters = canisters; this.ingredient = ingredient; this.result = result; this.tankAmount = tankAmount;
            this.fluid = fluid.copy(); this.chemical = chemical; this.charges = charges; this.ticks = ticks; this.rate = rate;
            this.reagent = reagent;
        }
        private CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag(); ListTag bases = new ListTag(); canisters.forEach(stack -> bases.add(stack.saveOptional(registries))); tag.put("canisters", bases);
            tag.put("ingredient", ingredient.saveOptional(registries)); tag.put("result", result.saveOptional(registries));
            tag.putInt("tank_amount", tankAmount); tag.put("fluid", fluid.saveOptional(registries)); if (chemical != null) tag.putString("chemical", chemical.toString());
            tag.putString("reagent", reagent);
            tag.putInt("charges", charges); tag.putInt("ticks", ticks); tag.putInt("rate", rate); tag.putInt("progress", progress); return tag;
        }
        private static Work load(HolderLookup.Provider registries, CompoundTag tag) {
            ListTag bases = tag.getList("canisters", Tag.TAG_COMPOUND);
            ItemStack input = ItemStack.parseOptional(registries, tag.getCompound("ingredient")), output = ItemStack.parseOptional(registries, tag.getCompound("result"));
            if (bases.size() != 3 || input.isEmpty() || output.isEmpty() || tag.getInt("ticks") < 1 || tag.getInt("rate") < 1) return null;
            List<ItemStack> canisters = new ArrayList<>(); for (int i = 0; i < 3; i++) canisters.add(ItemStack.parseOptional(registries, bases.getCompound(i)));
            Work work = new Work(canisters, input, output, Math.max(0, tag.getInt("tank_amount")), FluidStack.parseOptional(registries, tag.getCompound("fluid")),
                tag.contains("chemical", Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString("chemical")) : null, Math.max(0, tag.getInt("charges")),
                tag.getString("reagent").equals("nether_star") ? "nether_star" : "diamond", tag.getInt("ticks"), tag.getInt("rate"));
            work.progress = Math.clamp(tag.getInt("progress"), 0, work.ticks - 1); return work;
        }
    }
}
