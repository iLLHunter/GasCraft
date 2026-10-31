package gascraft.manual;

import com.google.gson.*;
import gascraft.ModContent;
import gascraft.gas.*;
import gascraft.item.*;
import gascraft.machine.SynthesizerMachine;
import gascraft.recipe.SynthesizingRecipe;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;

/** Built-in defaults read directly from the same packaged resources that define the recipes. */
public final class ManualRecipes {
    public record Input(List<ItemStack> alternatives,boolean blank,List<Component> details,String reagent) {
        public Input(List<ItemStack> alternatives,boolean blank){this(alternatives,blank,List.of(),"");}
        public ItemStack at(long time){return alternatives.isEmpty()?ItemStack.EMPTY:alternatives.get(Math.floorMod(time/40,alternatives.size()));}
    }
    public record Card(String id,String group,int width,int height,boolean shapeless,List<Input> inputs,ItemStack output,List<Component> notes,String section,boolean conditional) {
        public Card(String id,String group,int width,int height,boolean shapeless,List<Input> inputs,ItemStack output,List<Component> notes,String section){this(id,group,width,height,shapeless,inputs,output,notes,section,false);}
        public Card(String id,String group,int width,int height,boolean shapeless,List<Input> inputs,ItemStack output,List<Component> notes){this(id,group,width,height,shapeless,inputs,output,notes,ManualRecipes.section(group,output));}
        public Component workstation(){return switch(group){
            case "crafting"->Component.translatable("block.minecraft.crafting_table");
            case "synthesizing"->Component.translatable("jei.gascraft.synthesizing");
            default->Component.translatable("block.gascraft."+switch(group){case "powderizing"->"powderizer";case "powder_mixing"->"powder_mixer";case "kiln_processing"->"materials_kiln";default->"fermenter";});};}
        public boolean available(){return !output.isEmpty()&&!GasFieldsContent.hidden(output)&&inputs.stream().allMatch(i->i.blank()||i.alternatives().stream().anyMatch(stack->!GasFieldsContent.hidden(stack)));}
        public boolean upgrade(){return output.is(ModContent.PROCESSING_SPEED_UPGRADE.get())||output.is(ModContent.PROCESSING_EFFICIENCY_UPGRADE.get());}
        public boolean launcherPart(){return BuiltInRegistries.ITEM.getKey(output.getItem()).getPath().startsWith("grenade_launcher_");}
        public boolean coloredSmoke(){var gas=output.get(ModContent.GAS_CONTENTS);return gas!=null&&gas.getPath().startsWith("colored_smoke_");}
        public boolean voidPair(){var gas=output.get(ModContent.GAS_CONTENTS);return gas!=null&&List.of("void_gas","voidrot_gas").contains(gas.getPath());}
        public boolean compact(){
            if(group.equals("synthesizing"))return !conditional&&inputs.size()<=4&&notes.size()==1;
            if(upgrade()||launcherPart())return true;
            return notes.isEmpty()&&width<=2&&height<=2&&inputs.size()<=4&&switch(group){case "powderizing","kiln_processing","powder_mixing","fermenting"->true;case "crafting"->shapeless;default->false;};
        }
    }
    private static String section(String group,ItemStack output){
        if(!group.equals("crafting"))return switch(group){case "powderizing"->"powderizer";case "powder_mixing"->"powder_mixer";case "kiln_processing"->"materials_kiln";case "fermenting"->"fermenter";default->throw new IllegalArgumentException("Unassigned manual group: "+group);};
        if(output.is(ModContent.CHEMICAL_GRENADE.get()))return "grenade_crafting";
        String id=BuiltInRegistries.ITEM.getKey(output.getItem()).getPath();
        if(Arrays.stream(gascraft.machine.MachineKind.values()).anyMatch(kind->kind.name().toLowerCase(Locale.ROOT).equals(id)))return id;
        if(id.equals("processing_speed_upgrade")||id.equals("processing_efficiency_upgrade"))return "automation";
        return id.equals("taco_chime_grande_box")&&GasCatalog.contains(GasCatalog.id("taco_chime_gas"))?id:"start";
    }
    public static final List<String> GROUPS=List.of("crafting","powderizing","powder_mixing","kiln_processing","fermenting","synthesizing");
    private static final JsonArray INDEX=read("/assets/gascraft/manual_recipe_index.json").getAsJsonArray();
    private static JsonElement read(String name){try(var in=Objects.requireNonNull(ManualRecipes.class.getResourceAsStream(name));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){return JsonParser.parseReader(reader);}catch(IOException error){throw new UncheckedIOException(error);}}
    private static Input blank(){return new Input(List.of(),true);}
    private static Input of(ItemStack... stacks){return new Input(Arrays.stream(stacks).filter(s->!s.isEmpty()).toList(),false);}
    private static Input ingredient(JsonElement element) {
        if(element==null||element.isJsonNull())return blank();
        if(element.isJsonArray()){var all=new ArrayList<ItemStack>();for(var value:element.getAsJsonArray())all.addAll(ingredient(value).alternatives());return new Input(List.copyOf(all),false);}
        var value=element.getAsJsonObject();
        if(value.has("item"))return of(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(value.get("item").getAsString()))));
        if(value.has("tag"))return new Input(BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM,ResourceLocation.parse(value.get("tag").getAsString())))
            .map(tag->tag.stream().map(h->new ItemStack(h.value())).sorted(Comparator.comparing(s->s.is(ModContent.POWDERED_IRON.get())?1:0)).toList()).orElse(List.of()),false);
        throw new IllegalArgumentException("Unsupported built-in manual ingredient: "+element);
    }
    private static ItemStack result(JsonObject json){var value=json.getAsJsonObject("result");return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(value.get("id").getAsString())),value.has("count")?value.get("count").getAsInt():1);}
    private static Input counted(Input input,int count){return new Input(input.alternatives().stream().map(s->s.copyWithCount(count)).toList(),input.blank());}
    private static Card synth(String id,SynthesizingRecipe recipe) {return synth(id,recipe,false);}
    private static Card synth(String id,SynthesizingRecipe recipe,boolean conditional) {
        var inputs=new ArrayList<Input>();var notes=new ArrayList<Component>();
        if(!GasCatalog.contains(recipe.gas()))return null;
        if(!recipe.inputGas().isEmpty()){if(!GasCatalog.contains(ResourceLocation.parse(recipe.inputGas())))return null;inputs.add(of(GasCanisterItem.createFilledCanister(ResourceLocation.parse(recipe.inputGas())).copyWithCount(3)));}
        else inputs.add(new Input(Arrays.stream(recipe.input().getItems()).map(s->s.copyWithCount(3)).toList(),false));
        if(recipe.requiresIngredient())inputs.add(new Input(Arrays.stream(recipe.ingredient().getItems()).map(ItemStack::copy).toList(),false));
        else notes.add(Component.translatable("manual.gascraft.empty_slot"));
        if(recipe.reagentCharges()>0)inputs.add(new Input(SynthesizerMachine.reagentItems(recipe.reagent()),false,List.of(Component.translatable("manual.gascraft.batch_charges",recipe.reagentCharges())),recipe.reagent()));
        if(recipe.tank().amount()>0){var fluid=BuiltInRegistries.FLUID.get(ResourceLocation.parse(recipe.tank().id()));inputs.add(new Input(List.of(new ItemStack(fluid.getBucket())),false,List.of(Component.literal(recipe.tank().amount()+" mB").append(" ").append(fluid.getFluidType().getDescription())),""));}
        notes.add(Component.translatable("manual.gascraft.minimum_tier",Component.translatable("jei.gascraft.tier."+recipe.minimumTier())));
        return new Card(id,"synthesizing",0,0,false,List.copyOf(inputs),GasCanisterItem.createFilledCanister(recipe.gas()).copyWithCount(3),List.copyOf(notes),recipe.minimumTier()+"_synthesizer",conditional);
    }
    public static List<Card> cards() {
        var cards=new ArrayList<Card>();
        for(var entry:INDEX) {
            String path=entry.getAsString(),id="gascraft:"+path.substring(0,path.length()-5);
            var json=read("/data/gascraft/recipe/"+path).getAsJsonObject();String type=json.get("type").getAsString();
            if(json.has("neoforge:conditions")&&json.getAsJsonArray("neoforge:conditions").asList().stream().anyMatch(condition->{var value=condition.getAsJsonObject();return value.get("type").getAsString().equals("neoforge:mod_loaded")&&!net.neoforged.fml.ModList.get().isLoaded(value.get("modid").getAsString());}))continue;
            if(type.equals("gascraft:gas_container_crafting"))continue;
            if(type.equals("gascraft:synthesizing")) {
                var base=json.has("input_gas")?Ingredient.EMPTY:toIngredient(ingredient(json.get("input")));
                var tank=json.has("tank")?SynthesizingRecipe.TankIngredient.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json.get("tank")).getOrThrow():SynthesizingRecipe.TankIngredient.EMPTY;
                var recipe=new SynthesizingRecipe(ResourceLocation.parse(json.get("gas").getAsString()),toIngredient(ingredient(json.get("ingredient"))),base,
                    json.has("input_gas")?json.get("input_gas").getAsString():"",json.has("input_potion")?json.get("input_potion").getAsString():"",tank,
                    json.has("minimum_tier")?json.get("minimum_tier").getAsString():"stone",json.has("reagent_charges")?json.get("reagent_charges").getAsInt():0,json.has("reagent")?json.get("reagent").getAsString():"diamond");
                var card=synth(id,recipe,json.has("neoforge:conditions"));if(card!=null)cards.add(card);continue;
            }
            String group=type.startsWith("minecraft:crafting_")||type.equals("gascraft:machine_tier_crafting")||type.equals("gascraft:manual_crafting")?"crafting":type.substring(type.indexOf(':')+1);
            var inputs=new ArrayList<Input>();int width=0,height=0;boolean shapeless=false;
            if(json.has("pattern")) {
                var pattern=json.getAsJsonArray("pattern");width=pattern.get(0).getAsString().length();height=pattern.size();var keys=json.getAsJsonObject("key");
                for(var row:pattern)for(char c:row.getAsString().toCharArray())inputs.add(c==' '?blank():ingredient(keys.get(String.valueOf(c))));
                shapeless=json.has("unordered_square")&&json.get("unordered_square").getAsBoolean();
            }else if(json.has("ingredients")){
                for(var value:json.getAsJsonArray("ingredients"))inputs.add(ingredient(value));width=inputs.size()<=4?2:3;height=(inputs.size()+width-1)/width;shapeless=true;
            }else inputs.add(ingredient(json.get("ingredient")));
            cards.add(new Card(id,group,width,height,shapeless,List.copyOf(inputs),result(json),List.of()));
        }
        int legacy=0;
        for(var element:read("/assets/gascraft/gas_recipes.json").getAsJsonArray()) {
            var json=element.getAsJsonObject();var input=json.getAsJsonObject("input");var ingredient=json.getAsJsonObject("ingredient");
            var mix=new GasCatalog.BrewingMix(ResourceLocation.parse(json.get("gas").getAsString()),string(input,"gas"),string(input,"item"),string(ingredient,"item"),string(ingredient,"tag"),string(input,"potion"));
            try{var card=synth("gascraft:synthesizing/default_legacy/"+legacy,SynthesizingRecipe.fromLegacy(mix));if(card!=null)cards.add(card);}catch(IllegalArgumentException ignored){}legacy++;
        }
        // Dynamic built-in material processing and disc grinding are defaults, not datapack recipes.
        cards.add(new Card("gascraft:manual/iron","powderizing",0,0,false,List.of(of(new ItemStack(Items.IRON_INGOT))),new ItemStack(ModContent.POWDERED_IRON.get()),List.of()));
        cards.add(new Card("gascraft:manual/sulfur","powderizing",0,0,false,List.of(ingredient(tag("c:ores/sulfur"))),new ItemStack(ModContent.POWDERED_SULFUR.get()),List.of()));
        var discs=BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(GasFieldsContent::musicDisc).toList();
        cards.add(new Card("gascraft:manual/discs","powderizing",0,0,false,List.of(new Input(discs,false)),new ItemStack(GasFieldsContent.ITEMS.get("powdered_funk").get()),List.of()));
        var canisters=new ArrayList<ItemStack>();
        for(var gas:ModContent.CANISTERS.keySet())if(GasCatalog.contains(gas)) {
            var canister=GasCanisterItem.createFilledCanister(gas);canisters.add(canister);
            cards.add(new Card("gascraft:manual/grenade/"+gas.getPath(),"crafting",2,1,true,List.of(of(canister),of(new ItemStack(ModContent.GRENADE_DIFFUSER.get()),new ItemStack(ModContent.GRENADE_SHELL.get()))),GasContainerItem.createFilledContainer(ModContent.CHEMICAL_GRENADE.get(),gas),List.of()));
        }
        cards.add(new Card("gascraft:manual/empty_canister","crafting",1,1,true,List.of(new Input(List.copyOf(canisters),false)),new ItemStack(ModContent.EMPTY_CANISTER.get()),List.of()));
        cards.add(new Card("gascraft:manual/recycle_shell","crafting",1,1,true,List.of(of(new ItemStack(ModContent.GRENADE_SHELL.get()))),new ItemStack(ModContent.GRENADE_DIFFUSER.get()),List.of()));
        return cards.stream().filter(Card::available).sorted(Comparator.comparing(Card::id)).toList();
    }
    private static Ingredient toIngredient(Input input){return Ingredient.of(input.alternatives().stream());}
    private static JsonObject tag(String name){var json=new JsonObject();json.addProperty("tag",name);return json;}
    private static String string(JsonObject json,String key){return json.has(key)?json.get(key).getAsString():"";}
    private ManualRecipes(){}
}
