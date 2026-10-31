package gascraft.gas;

import com.google.gson.Gson;
import gascraft.*;
import gascraft.item.*;
import gascraft.recipe.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.event.server.*;

/** Restart-scoped gas controls. Shared stages follow the world's actual recipe dependencies. */
@EventBusSubscriber(modid=Gascraft.MOD_ID)
public final class GasChainControls {
    public record Control(String gas,String key,String name) {}
    public record State(Set<ResourceLocation> gases,Set<ResourceLocation> materials) {
        public State {gases=Set.copyOf(gases);materials=Set.copyOf(materials);}
        private static final State EMPTY=new State(Set.of(),Set.of());
    }
    public static final List<Control> CONTROLS=load();
    private static final Set<String> EQUIPMENT=Set.of("gas_mask","grenade_holder","grenade_launcher","grenade_diffuser","grenade_shell","empty_canister","gascraft_manual","processing_speed_upgrade","processing_efficiency_upgrade");
    private static volatile State serverState=State.EMPTY,clientState;
    private static volatile long clientRevision;
    private static MinecraftServer currentServer;
    private static List<Control> load(){
        try(var in=Objects.requireNonNull(Gascraft.class.getResourceAsStream("/assets/gascraft/gas_controls.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            return List.of(new Gson().fromJson(reader,Control[].class));
        }catch(IOException error){throw new UncheckedIOException(error);}
    }
    private static State state(){return EffectiveSide.get().isClient()&&clientState!=null?clientState:serverState;}
    public static State serverState(){return serverState;}
    public static long clientRevision(){return clientRevision;}
    public static boolean enabled(ResourceLocation gas){return gas==null||!state().gases().contains(gas);}
    public static boolean anyDisabled(){return !state().gases().isEmpty();}
    public static boolean itemAllowed(ItemStack stack){
        if(stack.isEmpty())return true;
        var snapshot=state();if(snapshot.gases().isEmpty()&&snapshot.materials().isEmpty())return true;
        var gas=stack.get(ModContent.GAS_CONTENTS);if(gas!=null&&snapshot.gases().contains(gas))return false;
        var id=BuiltInRegistries.ITEM.getKey(stack.getItem());return id==null||!snapshot.materials().contains(id);
    }
    public static void acceptClient(Collection<ResourceLocation> gases,Collection<ResourceLocation> materials){clientState=new State(new HashSet<>(gases),new HashSet<>(materials));clientRevision++;}
    public static void clearClient(){if(clientState!=null){clientState=null;clientRevision++;}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void starting(ServerAboutToStartEvent event){capture(event.getServer());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){if(currentServer==event.getServer()){currentServer=null;serverState=State.EMPTY;}}
    public static void capture(MinecraftServer server){
        currentServer=server;var disabled=new HashSet<ResourceLocation>();
        GascraftConfig.DISABLE_GASES.forEach((gas,value)->{if(value.get())disabled.add(GasCatalog.id(gas));});
        serverState=calculate(server,disabled);
    }
    /** Replacing datapack recipes updates shared ownership, while the disable choices remain frozen. */
    public static void refreshRecipes(MinecraftServer server){if(currentServer==server)serverState=calculate(server,serverState.gases());}
    private static Set<ResourceLocation> ingredients(Collection<Ingredient> ingredients){
        var result=new HashSet<ResourceLocation>();for(var ingredient:ingredients)for(var item:ingredient.getItems())if(!item.isEmpty())result.add(BuiltInRegistries.ITEM.getKey(item.getItem()));return result;
    }
    private static ResourceLocation canister(ResourceLocation gas){var named=ModContent.CANISTERS.get(gas);return named==null?ModContent.CUSTOM_GAS_CANISTER.getId():named.getId();}
    private static void synth(Map<ResourceLocation,Set<ResourceLocation>> graph,SynthesizingRecipe recipe){
        var inputs=ingredients(List.of(recipe.ingredient(),recipe.input()));
        if(recipe.reagentCharges()>0)for(var reagent:gascraft.machine.SynthesizerMachine.reagentItems(recipe.reagent()))inputs.add(BuiltInRegistries.ITEM.getKey(reagent.getItem()));
        if(!recipe.inputGas().isEmpty())inputs.add(canister(ResourceLocation.parse(recipe.inputGas())));
        graph.computeIfAbsent(canister(recipe.gas()),key->new HashSet<>()).addAll(inputs);
    }
    private static Set<ResourceLocation> reachable(Collection<ResourceLocation> roots,Map<ResourceLocation,Set<ResourceLocation>> graph,Set<ResourceLocation> blocked){
        var found=new HashSet<ResourceLocation>();var queue=new ArrayDeque<ResourceLocation>(roots);
        while(!queue.isEmpty()){var item=queue.removeFirst();if(blocked.contains(item)||!found.add(item))continue;queue.addAll(graph.getOrDefault(item,Set.of()));}return found;
    }
    private static State calculate(MinecraftServer server,Set<ResourceLocation> disabled){
        if(disabled.isEmpty())return State.EMPTY;
        var graph=new HashMap<ResourceLocation,Set<ResourceLocation>>();
        for(var holder:server.getRecipeManager().getRecipes()){
            var recipe=holder.value();if(recipe instanceof SynthesizingRecipe synth){synth(graph,synth);continue;}
            var output=recipe.getResultItem(server.registryAccess());if(output.isEmpty())continue;var id=BuiltInRegistries.ITEM.getKey(output.getItem());if(!id.getNamespace().equals("gascraft")&&!holder.id().getNamespace().equals("gascraft"))continue;
            graph.computeIfAbsent(id,key->new HashSet<>()).addAll(ingredients(recipe.getIngredients()));
        }
        for(var mix:GasCatalog.brewingMixes())try{synth(graph,SynthesizingRecipe.fromLegacy(mix));}catch(IllegalArgumentException ignored){}
        var common=new HashSet<ResourceLocation>();var candidates=new HashSet<ResourceLocation>();
        for(var registered:ModContent.ITEMS.getEntries()){
            var item=registered.get();var id=registered.getId();
            if(item instanceof BlockItem||EQUIPMENT.contains(id.getPath()))common.add(id);
            else if(!(item instanceof GasContainerItem))candidates.add(id);
            PowderizerRecipeDiscovery.findRecipe(new ItemStack(item)).ifPresent(recipe->{var output=recipe.getResultItem(server.registryAccess());if(!output.isEmpty())graph.computeIfAbsent(BuiltInRegistries.ITEM.getKey(output.getItem()),key->new HashSet<>()).add(id);});
        }
        var allRoots=new HashSet<>(common);var activeRoots=new HashSet<>(common);var blocked=new HashSet<ResourceLocation>();
        for(var gas:GasCatalog.gasDefinitions())if(GasFieldsContent.ingredientsAvailable(gas.id())){
            var root=canister(gas.id());allRoots.add(root);if(disabled.contains(gas.id()))blocked.add(root);else activeRoots.add(root);
        }
        var owned=reachable(allRoots,graph,Set.of());var needed=reachable(activeRoots,graph,blocked);
        owned.retainAll(candidates);owned.removeAll(needed);return new State(disabled,owned);
    }
    private GasChainControls(){}
}
