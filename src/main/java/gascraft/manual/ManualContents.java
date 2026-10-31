package gascraft.manual;

import com.google.gson.*;
import gascraft.gas.GasCatalog;
import gascraft.machine.MachineKind;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.network.chat.Component;

/** Localized prose is separate from topic ordering and live optional-content availability. */
public final class ManualContents {
    public record Topic(String id, Component title, List<Component> paragraphs,List<ManualRecipes.Card> recipes) {
        public Topic(String id,Component title,List<Component> paragraphs){this(id,title,paragraphs,List.of());}
    }
    private static final JsonArray DOCUMENT = load();
    private static JsonArray load() {
        try (var stream=Objects.requireNonNull(ManualContents.class.getResourceAsStream("/assets/gascraft/manual.json"));
             var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonArray();
        } catch(IOException error) { throw new UncheckedIOException(error); }
    }
    public static List<Topic> topics() {return topics(Locale.ROOT);}
    public static List<Topic> topics(Locale locale) {
        List<Topic> topics=new ArrayList<>();
        var recipes=ManualRecipes.cards();
        for(var element:DOCUMENT) {
            var entry=element.getAsJsonObject();
            var family=entry.has("gas")?family(entry.get("gas").getAsString()):List.<String>of();
            var active=family.stream().filter(g->GasCatalog.contains(GasCatalog.id(g))).toList();
            if(!family.isEmpty()&&active.isEmpty())continue;
            var title=Component.translatable(entry.get("title").getAsString());
            if(active.size()<family.size()&&family.size()==3){title=Component.empty();for(String gas:active){if(!title.getSiblings().isEmpty())title.append(" / ");title.append(Component.translatable("gas.gascraft."+gas));}}
            List<Component> paragraphs=new ArrayList<>();
            for(var key:entry.getAsJsonArray("paragraphs")) {
                if(key.getAsString().equals("manual.gascraft.meal_roll"))paragraphs.add(Component.translatable(key.getAsString(),Component.translatable("effect.farmersdelight.nourishment"),net.minecraft.world.effect.MobEffects.CONFUSION.value().getDisplayName(),Component.translatable("meal.gascraft.taco_chime_fart")));
                else if(key.getAsString().equals("manual.gascraft.reagent_types")) {
                    paragraphs.add(reagentList(recipes,locale));
                }
                else paragraphs.add(Component.translatable(key.getAsString()));
            }
            String id=entry.get("id").getAsString();
            var cards=recipes.stream().filter(card->card.section().equals(id)).sorted(Comparator.comparing((ManualRecipes.Card card)->!card.group().equals("crafting")).thenComparingInt(card->card.group().equals("synthesizing")?(card.coloredSmoke()?0:card.voidPair()?2:card.conditional()?3:1):card.group().equals("powder_mixing")?(card.compact()?0:1):0).thenComparing(ManualRecipes.Card::id)).toList();
            topics.add(new Topic(id,title,List.copyOf(paragraphs),cards));
        }
        var grenades=recipes.stream().filter(card->card.section().equals("grenade_crafting")).toList();
        if(!grenades.isEmpty())topics.add(new Topic("grenade_crafting",Component.translatable("manual.gascraft.grenade_crafting"),List.of(),grenades));
        if(topics.stream().mapToInt(topic->topic.recipes().size()).sum()!=recipes.size())throw new IllegalStateException("A manual recipe has no active topic");
        return List.copyOf(topics);
    }
    private static List<String> family(String gas){
        if(gas.equals("poison_i"))return List.of("poison_i","poison_ii","poison_iii");
        if(gas.equals("misted_tetrodotoxin_i"))return List.of("misted_tetrodotoxin_i","misted_tetrodotoxin_ii","misted_tetrodotoxin_iii");
        if(gas.equals("smoke")){var values=new ArrayList<String>();values.add(gas);for(String color:List.of("white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"))values.add("colored_smoke_"+color);return List.copyOf(values);}
        return List.of(gas);
    }
    public static Component reagentList(List<ManualRecipes.Card> cards,Locale locale){
        var uses=new HashMap<String,Set<String>>();
        for(var card:cards)if(card.group().equals("synthesizing"))for(var input:card.inputs())if(!input.reagent().isEmpty())uses.computeIfAbsent(input.reagent(),key->new HashSet<>()).add(card.output().get(gascraft.ModContent.GAS_CONTENTS).getPath());
        var collator=java.text.Collator.getInstance(locale);var result=Component.empty();
        var order=new ArrayList<>(uses.keySet());order.sort((a,b)->collator.compare(gascraft.machine.SynthesizerMachine.reagentItem(a).getDescription().getString(),gascraft.machine.SynthesizerMachine.reagentItem(b).getDescription().getString()));
        for(String reagent:order){
            if(!result.getSiblings().isEmpty())result.append("\n");result.append(gascraft.machine.SynthesizerMachine.reagentItem(reagent).getDescription()).append(": ");
            var gases=new ArrayList<>(uses.get(reagent));gases.sort((a,b)->collator.compare(Component.translatable("gas.gascraft."+a).getString(),Component.translatable("gas.gascraft."+b).getString()));
            for(int i=0;i<gases.size();i++){if(i>0)result.append(", ");result.append(Component.translatable("gas.gascraft."+gases.get(i)));}
        }return result;
    }
    private ManualContents() {}
}
