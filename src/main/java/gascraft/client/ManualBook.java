package gascraft.client;

import gascraft.manual.ManualContents;
import gascraft.manual.ManualRecipes.Card;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.*;

/** Uses vanilla book pages and links, paginated after resolving the active language. */
public final class ManualBook {
    public record Layout(List<Component> pages, Map<String,Integer> destinations, int contentsPages,Map<Integer,List<Card>> recipes,Map<Integer,Component> introductions) {
        public List<Card> recipeCards(){return recipes.values().stream().flatMap(List::stream).toList();}
        public int recipePage(String id){return recipes.entrySet().stream().filter(entry->entry.getValue().stream().anyMatch(card->card.id().equals(id))).mapToInt(Map.Entry::getKey).findFirst().orElseThrow(()->new IllegalArgumentException("Manual recipe missing: "+id));}
    }
    private static final int WIDTH=114, HEIGHT=128;
    private static MutableComponent text(String key) {return Component.translatable("manual.gascraft."+key);}
    public static void open() {
        var mc=Minecraft.getInstance();
        mc.setScreen(new ManualRecipeScreen(layout(mc.font)));
    }
    private static Component component(FormattedText text) {
        var result=Component.empty();
        text.visit((style,value)->{result.append(Component.literal(value).setStyle(style));return Optional.empty();},Style.EMPTY);
        return result;
    }
    private static List<Component> lines(Font font,Component text) {
        // Arabic shaping can change advances after the logical splitter measures a line.
        for(int width=WIDTH;width>0;width--) {
            var result=font.getSplitter().splitLines(text,width,Style.EMPTY).stream().map(ManualBook::component).toList();
            if(result.stream().allMatch(line->font.split(line,WIDTH).stream().allMatch(run->font.width(run)<=WIDTH)))return result;
        }
        throw new IllegalStateException("Manual glyph exceeds a native book line");
    }
    public static List<Component> introductionLines(Font font,Component text){return lines(font,text);}
    private static Component link(Component title,int page) {
        return title.copy().withStyle(style->style.withColor(ChatFormatting.BLUE).withUnderlined(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE,Integer.toString(page+1))));
    }
    private static List<Component> paginate(Font font,Component heading,Component body) {
        var header=heading.copy().withStyle(ChatFormatting.BOLD);
        int room=HEIGHT/font.lineHeight-lines(font,header).size()-3;
        if(room<1)throw new IllegalStateException("Manual heading exceeds a native book page");
        var bodyLines=lines(font,body);List<Component> pages=new ArrayList<>();
        for(int offset=0;offset<Math.max(1,bodyLines.size());offset+=room) {
            var page=Component.empty().append(header).append("\n\n");
            for(int i=offset;i<Math.min(offset+room,bodyLines.size());i++) {
                if(i>offset)page.append("\n");page.append(bodyLines.get(i));
            }
            page.append("\n\n").append(link(text("contents"),0));
            if(lines(font,page).size()*font.lineHeight>HEIGHT)throw new IllegalStateException("Manual page overflow");
            pages.add(page);
        }
        return pages;
    }
    private static Component body(ManualContents.Topic topic){
        var body=Component.empty();
        for(var paragraph:topic.paragraphs()){if(!body.getSiblings().isEmpty())body.append(Set.of("reagents","taco_chime_grande_box").contains(topic.id())?"\n":"\n\n");body.append(paragraph);}
        return body;
    }
    private static Component sharedGasPage(Font font,ManualContents.Topic first,ManualContents.Topic second){
        var content=Component.empty().append(first.title().copy().withStyle(ChatFormatting.BOLD)).append("\n").append(body(first)).append("\n\n")
            .append(second.title().copy().withStyle(ChatFormatting.BOLD)).append("\n").append(body(second));
        var rows=lines(font,content);if((rows.size()+2)*font.lineHeight>HEIGHT)return null;
        var page=Component.empty();for(int i=0;i<rows.size();i++){if(i>0)page.append("\n");page.append(rows.get(i));}
        return page.append("\n\n").append(link(text("contents"),0));
    }
    public static Layout layout(Font font) {
        var topics=ManualContents.topics(Locale.forLanguageTag(Minecraft.getInstance().options.languageCode.replace('_','-')));List<List<Component>> articles=new ArrayList<>();
        List<Map<Integer,List<Card>>> recipeArticles=new ArrayList<>();
        List<Map<Integer,Component>> introductions=new ArrayList<>();
        Map<String,String> sharedWith=new HashMap<>();
        var provisional=Component.empty().append(text("disclaimer")).append("\n\n");
        for(int topicIndex=0;topicIndex<topics.size();topicIndex++) {
            var topic=topics.get(topicIndex);var body=body(topic);Component sharedPage=null;
            String partner=Map.of("smoke","tear_gas","raid_gas","regenerating_mist").get(topic.id());
            if(partner!=null&&topicIndex+1<topics.size()&&topics.get(topicIndex+1).id().equals(partner)&&topic.recipes().isEmpty()&&topics.get(topicIndex+1).recipes().isEmpty()){
                sharedPage=sharedGasPage(font,topic,topics.get(topicIndex+1));if(sharedPage!=null)sharedWith.put(partner,topic.id());
            }
            int introductionLimit=Set.of("materials_kiln","fermenter").contains(topic.id())?5:4;
            boolean combine=(Set.of("powderizer","powder_mixer","materials_kiln","fermenter","stone_synthesizer","iron_synthesizer","diamond_synthesizer","netherite_synthesizer").contains(topic.id()))&&!topic.recipes().isEmpty()&&lines(font,body).size()<=introductionLimit;
            var article=new ArrayList<Component>();
            if(sharedPage!=null)article.add(sharedPage);
            else if(!sharedWith.containsKey(topic.id())&&!combine&&!topic.paragraphs().isEmpty())article.addAll(paginate(font,topic.title(),body));
            Map<Integer,List<Card>> recipePages=new LinkedHashMap<>();
            Map<Integer,Component> introPages=new LinkedHashMap<>();
            for(int i=0;i<topic.recipes().size();i++) {
                var recipe=topic.recipes().get(i);var cards=new ArrayList<Card>();cards.add(recipe);
                if(i+1<topic.recipes().size()&&canShare(recipe,topic.recipes().get(i+1)))cards.add(topic.recipes().get(++i));
                recipePages.put(article.size(),List.copyOf(cards));
                if(combine&&i==0)introPages.put(article.size(),body);
                article.add(Component.empty().append("\n".repeat(13)).append(link(text("contents"),0)));
            }
            articles.add(article);recipeArticles.add(recipePages);introductions.add(introPages);
            if(articles.size()>1)provisional.append("\n");provisional.append(link(topic.title(),0));
        }
        int tocCount=paginate(font,text("contents"),provisional).size(),page=tocCount;
        var contents=Component.empty().append(text("disclaimer")).append("\n\n");Map<String,Integer> destinations=new LinkedHashMap<>();
        for(int i=0;i<topics.size();i++) {
            var topic=topics.get(i);int destination=sharedWith.containsKey(topic.id())?destinations.get(sharedWith.get(topic.id())):page;destinations.put(topic.id(),destination);
            if(i>0)contents.append("\n");contents.append(link(topic.title(),destination));page+=articles.get(i).size();
        }
        List<Component> pages=new ArrayList<>(paginate(font,text("contents"),contents));
        if(pages.size()!=tocCount)throw new IllegalStateException("Manual contents pagination changed");
        Map<Integer,List<Card>> recipes=new LinkedHashMap<>();
        Map<Integer,Component> intros=new LinkedHashMap<>();
        for(int i=0;i<articles.size();i++){int offset=pages.size();recipeArticles.get(i).forEach((local,card)->recipes.put(offset+local,card));introductions.get(i).forEach((local,intro)->intros.put(offset+local,intro));pages.addAll(articles.get(i));}
        return new Layout(List.copyOf(pages),Collections.unmodifiableMap(destinations),tocCount,Collections.unmodifiableMap(recipes),Collections.unmodifiableMap(intros));
    }
    public static boolean canShare(Card first,Card second){
        if(!first.compact()||!second.compact()||!first.group().equals(second.group()))return false;
        if(first.launcherPart()||second.launcherPart())return first.launcherPart()&&second.launcherPart();
        if(first.upgrade()||second.upgrade())return first.upgrade()&&second.upgrade();
        if(first.group().equals("synthesizing")){if(first.coloredSmoke()||second.coloredSmoke())return first.coloredSmoke()&&second.coloredSmoke();if(first.voidPair()||second.voidPair())return first.voidPair()&&second.voidPair();}
        return true;
    }
    private ManualBook() {}
}
