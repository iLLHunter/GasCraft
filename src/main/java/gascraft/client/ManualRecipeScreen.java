package gascraft.client;

import gascraft.manual.ManualRecipes;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Uses existing item models and vanilla GUI sprites; no recipe images or JEI runtime are bundled. */
public final class ManualRecipeScreen extends BookViewScreen {
    public record Cell(ItemStack stack,int x,int y,ManualRecipes.Input input){public Cell(ItemStack stack,int x,int y){this(stack,x,y,null);}}
    public record Block(ManualRecipes.Card card,int titleY,int gridY,int notesY){}
    private final ManualBook.Layout layout;
    private int page;
    public ManualRecipeScreen(ManualBook.Layout layout){super(new BookAccess(layout.pages()));this.layout=layout;}
    @Override protected void init(){super.init();if(minecraft.player!=null)setPage(ManualPageMemory.page(minecraft.player.getUUID(),layout.pages().size()));}
    public int currentPageIndex(){return page;}
    public Component pageIndicator(){
        var label=Component.translatable("book.pageIndicator",page+1,Math.max(1,layout.pages().size()));
        return minecraft.getLanguageManager().getSelected().equals("tr_tr")?Component.literal(label.getString().replaceAll(",(?=\\S)",", ")):label;
    }
    @Override public boolean setPage(int value){boolean changed=super.setPage(value);page=Math.clamp(value,0,layout.pages().size()-1);if(minecraft!=null&&minecraft.player!=null)ManualPageMemory.remember(minecraft.player.getUUID(),page);return changed;}
    @Override protected void pageBack(){setPage(page-1);}
    @Override protected void pageForward(){setPage(page+1);}
    @Override public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){
        super.renderBackground(graphics,mouseX,mouseY,partialTick);
        int left=(width-192)/2;
        graphics.fill(left+28,12,left+156,158,0xffc6c6c6);
    }
    public static List<Cell> cells(ManualRecipes.Card card,long time) {
        List<Cell> cells=new ArrayList<>();
        int pitch=cellPitch(card);
        if(card.width()>0){
            int size=Math.max(card.width(),card.height())>2?3:2;
            int padX=card.shapeless()?0:(size-card.width())/2,padY=card.shapeless()?0:(size-card.height())/2;
            for(int y=0;y<size;y++)for(int x=0;x<size;x++){
                int col=x-padX,row=y-padY,index=row*card.width()+col;
                var stack=col>=0&&col<card.width()&&row>=0&&row<card.height()&&index<card.inputs().size()?card.inputs().get(index).at(time):ItemStack.EMPTY;
                cells.add(new Cell(stack,x*pitch,y*pitch,col>=0&&col<card.width()&&row>=0&&row<card.height()&&index<card.inputs().size()?card.inputs().get(index):null));
            }
        }else for(int i=0;i<card.inputs().size();i++) {
            cells.add(new Cell(card.inputs().get(i).at(time),card.group().equals("synthesizing")?i*16:card.inputs().size()==1?18:i%3*18,card.group().equals("synthesizing")?0:i/3*20+9,card.inputs().get(i)));
        }
        cells.add(new Cell(card.output(),94,middle(card)));
        return List.copyOf(cells);
    }
    private static boolean compactMachine(ManualRecipes.Card card){return card.output().is(gascraft.machine.MachineContent.MATERIALS_KILN_ITEM.get())||card.output().is(gascraft.machine.MachineContent.FERMENTER_ITEM.get());}
    public static int cellPitch(ManualRecipes.Card card){return card.launcherPart()?14:card.upgrade()||compactMachine(card)||card.group().equals("synthesizing")?16:18;}
    public static int iconSize(ManualRecipes.Card card){return cellPitch(card)-2;}
    private static int middle(ManualRecipes.Card card){return card.group().equals("synthesizing")?0:card.width()>0?(Math.max(card.width(),card.height())>2?cellPitch(card):cellPitch(card)/2):9;}
    public static List<Block> blocks(List<ManualRecipes.Card> cards){
        return blocks(cards,0);
    }
    public static List<Block> blocks(List<ManualRecipes.Card> cards,int introHeight){
        if(cards.size()==2&&cards.getFirst().upgrade())return List.of(new Block(cards.get(0),30,40,90),new Block(cards.get(1),90,100,149));
        if(cards.size()==2&&cards.getFirst().launcherPart())return List.of(new Block(cards.get(0),41,51,94),new Block(cards.get(1),96,106,149));
        if(cards.size()==2&&cards.getFirst().group().equals("synthesizing"))return List.of(new Block(cards.get(0),44,55,74),new Block(cards.get(1),98,109,128));
        if(cards.size()==2)return List.of(new Block(cards.get(0),44,55,94),new Block(cards.get(1),99,110,149));
        var card=cards.getFirst();int y=introHeight>0?(compactMachine(card)?56:57)+introHeight:55;
        int height=card.group().equals("synthesizing")?16:card.width()>0?(Math.max(card.width(),card.height())>2?3:2)*cellPitch(card):46;
        return List.of(new Block(card,44,y,y+height+4));
    }
    public static int workstationY(List<ManualRecipes.Card> cards){return cards.size()==2&&cards.getFirst().launcherPart()?30:32;}
    private static void slot(GuiGraphics g,int x,int y,int size){
        g.fill(x-1,y-1,x+size+1,y+size+1,0xff373737);g.fill(x,y,x+size,y+size,0xff8b8b8b);
        g.fill(x,y+size,x+size+1,y+size+1,0xffffffff);g.fill(x+size,y,x+size+1,y+size+1,0xffffffff);
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){
        super.render(graphics,mouseX,mouseY,partialTick);
        if(minecraft.getLanguageManager().getSelected().equals("tr_tr")){
            String original=Component.translatable("book.pageIndicator",page+1,Math.max(1,layout.pages().size())).getString();
            String spaced=pageIndicator().getString();
            if(!spaced.equals(original)){int left=(width-192)/2;graphics.fill(left+28,16,left+156,28,0xffc6c6c6);graphics.drawString(font,Component.literal(spaced),left+148-font.width(spaced),18,0,false);}
        }
        var cards=layout.recipes().get(page);if(cards==null)return;
        int x=(width-192)/2+36;long time=minecraft.level==null?0:minecraft.level.getGameTime();
        boolean paired=cards.size()==2;var workstation=cards.getFirst().workstation();
        if(paired&&cards.getFirst().group().equals("crafting")&&cards.stream().allMatch(ManualRecipes.Card::shapeless))workstation=workstation.copy().append(" / ").append(Component.translatable("manual.gascraft.shapeless"));
        int stationY=workstationY(cards);if(!cards.getFirst().upgrade())ScreenLabels.drawBounded(graphics,font,workstation,x,stationY,114);
        var introduction=layout.introductions().get(page);var introLines=introduction==null?List.<Component>of():ManualBook.introductionLines(font,introduction);
        for(int i=0;i<introLines.size();i++)graphics.drawString(font,introLines.get(i),x,56+i*font.lineHeight,0xff404040,false);
        for(var block:blocks(cards,introLines.size()*font.lineHeight)){
            var card=block.card();int y=block.gridY();
            int icon=iconSize(card);
            ScreenLabels.drawBounded(graphics,font,card.output().getHoverName(),x,block.titleY(),114);
            graphics.blitSprite(ResourceLocation.withDefaultNamespace("container/furnace/burn_progress"),x+(card.group().equals("synthesizing")?69:62),y+middle(card),card.group().equals("synthesizing")?16:24,icon);
            for(var cell:cells(card,time)) {
                slot(graphics,x+cell.x(),y+cell.y(),icon);
                if(!cell.stack().isEmpty()){graphics.pose().pushPose();graphics.pose().translate(x+cell.x(),y+cell.y(),0);graphics.pose().scale(icon/16F,icon/16F,1);graphics.renderItem(cell.stack(),0,0);graphics.renderItemDecorations(font,cell.stack(),0,0);graphics.pose().popPose();}
            }
            int noteY=block.notesY();var notes=new ArrayList<>(card.notes());if(card.shapeless()&&!paired&&card.group().equals("crafting"))notes.add(Component.translatable("manual.gascraft.shapeless"));
            for(var note:notes){ScreenLabels.drawBounded(graphics,font,note,x,noteY,114);if(ScreenLabels.overflowsAt(font,note,x,noteY,114,mouseX,mouseY))graphics.renderTooltip(font,note,mouseX,mouseY);noteY+=10;}
            for(var cell:cells(card,time))if(!cell.stack().isEmpty()&&mouseX>=x+cell.x()&&mouseX<x+cell.x()+icon&&mouseY>=y+cell.y()&&mouseY<y+cell.y()+icon){var tooltip=new ArrayList<>(net.minecraft.client.gui.screens.Screen.getTooltipFromItem(minecraft,cell.stack()));if(cell.input()!=null)tooltip.addAll(cell.input().details());graphics.renderTooltip(font,tooltip,Optional.empty(),mouseX,mouseY);return;}
            if(ScreenLabels.overflowsAt(font,card.output().getHoverName(),x,block.titleY(),114,mouseX,mouseY))graphics.renderTooltip(font,card.output().getHoverName(),mouseX,mouseY);
        }
        if(!cards.getFirst().upgrade()&&ScreenLabels.overflowsAt(font,workstation,x,stationY,114,mouseX,mouseY))graphics.renderTooltip(font,workstation,mouseX,mouseY);
    }
}
