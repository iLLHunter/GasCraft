package gascraft.client;

import gascraft.ModContent;
import gascraft.compat.MekanismChemicalCompat;
import gascraft.item.ProcessingUpgradeItem;
import gascraft.machine.*;
import gascraft.menu.SynthesizerMenu;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

public final class SynthesizerScreen extends AbstractContainerScreen<SynthesizerMenu> {
    private boolean upgradesOpen, sidesOpen;
    private Button speedButton, efficiencyButton, dumpButton, chargeDumpButton;
    private final List<Button> sideButtons = new ArrayList<>();
    public SynthesizerScreen(SynthesizerMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); imageWidth = 176; imageHeight = 222; inventoryLabelY = 132; }
    public boolean upgradesOpen() { return upgradesOpen; } public boolean sidesOpen() { return sidesOpen; }
    private static Component text(String key, Object... args) { return Component.translatable("gui.gascraft.machine." + key, args); }
    private void send(int button) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button); }
    @Override protected void init() {
        super.init();
        if (upgradesOpen || sidesOpen) leftPos = Math.max(4, Math.min(leftPos, width - (sidesOpen ? 422 : 338)));
        menu.setUpgradesOpen(upgradesOpen); sideButtons.clear();
        if (menu.kind().maxSpeedUpgrades() > 0) addRenderableWidget(Button.builder(Component.empty(), button -> { upgradesOpen = !upgradesOpen; rebuildWidgets(); })
            .bounds(leftPos + 132, topPos + titleLabelY, 18, 18).tooltip(Tooltip.create(text("upgrades"))).build());
        addRenderableWidget(Button.builder(Component.empty(), button -> { sidesOpen = !sidesOpen; rebuildWidgets(); })
            .bounds(leftPos + 153, topPos + titleLabelY, 18, 18).tooltip(Tooltip.create(text("sides"))).build());
        speedButton = addRenderableWidget(Button.builder(Component.literal("-1"), button -> send(0)).bounds(leftPos + 292, topPos + 58, 30, 18).tooltip(Tooltip.create(text("return_speed"))).build());
        efficiencyButton = addRenderableWidget(Button.builder(Component.literal("-1"), button -> send(1)).bounds(leftPos + 292, topPos + 80, 30, 18).tooltip(Tooltip.create(text("return_efficiency"))).build());
        dumpButton = addRenderableWidget(Button.builder(Component.literal("X").withStyle(net.minecraft.ChatFormatting.RED), button -> send(2)).bounds(leftPos + 152, topPos + 126, 16, 14).tooltip(Tooltip.create(text("dump_tank"))).build());
        if (menu.kind().hasReagentSlot()) chargeDumpButton = addRenderableWidget(Button.builder(Component.literal("X").withStyle(net.minecraft.ChatFormatting.RED), button -> send(3))
            .bounds(leftPos + 134, topPos + 40, 12, 12).tooltip(Tooltip.create(text("dump_reagent"))).build());
        for (var face : MachineSides.Face.values()) for (var route : MachineSides.Route.values()) {
            Button button = addRenderableWidget(Button.builder(Component.empty(), pressed -> send(SynthesizerMenu.sideButton(face, route)))
                .bounds(leftPos + 242 + route.ordinal() * 28, topPos + 134 + face.ordinal() * 13, 26, 12)
                .tooltip(Tooltip.create(text("route." + route.name().toLowerCase(Locale.ROOT)))).build()); sideButtons.add(button);
        }
    }
    private void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(leftPos + x, topPos + y, leftPos + x + width, topPos + y + height, 0xff373737);
        graphics.fill(leftPos + x + 2, topPos + y + 2, leftPos + x + width - 2, topPos + y + height - 2, 0xffc6c6c6);
    }
    private void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(leftPos + x - 1, topPos + y - 1, leftPos + x + 17, topPos + y + 17, 0xff373737);
        graphics.fill(leftPos + x, topPos + y, leftPos + x + 16, topPos + y + 16, 0xff8b8b8b);
        graphics.fill(leftPos + x, topPos + y + 16, leftPos + x + 17, topPos + y + 17, 0xffffffff);
        graphics.fill(leftPos + x + 16, topPos + y, leftPos + x + 17, topPos + y + 16, 0xffffffff);
    }
    private int tankColor() {
        var tank = menu.tank();
        if (tank.chemical() != null && ModList.get().isLoaded("mekanism")) return MekanismChemicalCompat.color(tank.chemical());
        return tank.fluid().isEmpty() ? 0xff436ba0 : IClientFluidTypeExtensions.of(tank.fluid().getFluid()).getTintColor(tank.fluid());
    }
    private Component tankName() {
        var tank = menu.tank();
        if (tank.chemical() != null) return ModList.get().isLoaded("mekanism") ? MekanismChemicalCompat.name(tank.chemical()) : Component.literal(tank.chemical().toString());
        return tank.fluid().isEmpty() ? text("empty_tank") : tank.fluid().getHoverName();
    }
    @Override protected void renderBg(GuiGraphics graphics, float tick, int mouseX, int mouseY) {
        panel(graphics, 0, 0, imageWidth, imageHeight);
        if (upgradesOpen) panel(graphics, 182, 0, 152, 104);
        if (sidesOpen) panel(graphics, 182, 106, 236, 116);
        for (var slot : menu.slots) if (slot.isActive()) slot(graphics, slot.x, slot.y);
        graphics.fill(leftPos + 7, topPos + 30, leftPos + 21, topPos + 122, 0xff373737);
        int energyHeight = (int)(90L * menu.energy() / menu.kind().energyCapacity());
        graphics.fill(leftPos + 8, topPos + 121 - energyHeight, leftPos + 20, topPos + 121, 0xffce5342);
        graphics.fill(leftPos + 151, topPos + 48, leftPos + 169, topPos + 122, 0xff373737);
        int tankHeight = 72 * menu.tank().amount() / menu.tank().capacity();
        graphics.fill(leftPos + 152, topPos + 121 - tankHeight, leftPos + 168, topPos + 121, tankColor());
        inputIcon(graphics, new net.minecraft.world.item.ItemStack(ModContent.POWDERED_SMOKE.get()), 32);
        inputIcon(graphics, new net.minecraft.world.item.ItemStack(ModContent.EMPTY_CANISTER.get()), 72);
        if (menu.kind().hasReagentSlot()) {
            inputIcon(graphics, new net.minecraft.world.item.ItemStack(menu.reagentType().equals("nether_star") ? net.minecraft.world.item.Items.NETHER_STAR : net.minecraft.world.item.Items.DIAMOND), 112);
            graphics.fill(leftPos + 130, topPos + 30, leftPos + 132, topPos + 46, 0xff373737);
            int chargeHeight = (int)Math.min(16L, 16L * menu.charges() / SynthesizerMachine.REAGENT_CHARGES);
            graphics.fill(leftPos + 130, topPos + 46 - chargeHeight, leftPos + 132, topPos + 46, 0xff68a04d);
        }
        for (int group = 0; group < menu.groups(); group++) {
            int y = 57 + group * 18; graphics.fill(leftPos + 112, topPos + y, leftPos + 144, topPos + y + 8, 0xff8b8b8b);
            int progress = menu.finished(group) ? 32 : menu.totalTicks(group) == 0 ? 0 : (int)(32L * menu.progress(group) / menu.totalTicks(group));
            graphics.fill(leftPos + 112, topPos + y, leftPos + 112 + progress, topPos + y + 8, 0xff68a04d);
        }
    }
    private void inputIcon(GuiGraphics graphics, net.minecraft.world.item.ItemStack stack, int x) {
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + x + 2, topPos + 17, 0);
        graphics.pose().scale(.75F, .75F, 1);
        graphics.renderFakeItem(stack, 0, 0);
        graphics.pose().popPose();
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(font, text("rate", menu.displayRate()), 7, 124, 0xff404040, false);
        if (upgradesOpen) {
            graphics.drawString(font, text("upgrades"), 190, 8, 0xff404040, false); graphics.drawString(font, text("insert"), 190, 21, 0xff404040, false); graphics.drawString(font, text("return"), 292, 21, 0xff404040, false);
            graphics.drawString(font, text("speed_count", menu.speed(), menu.kind().maxSpeedUpgrades()), 190, 63, 0xff404040, false);
            graphics.drawString(font, text("efficiency_count", menu.efficiency(), menu.kind().maxEfficiencyUpgrades()), 190, 85, 0xff404040, false);
        }
        if (sidesOpen) {
            graphics.drawString(font, text("sides"), 190, 110, 0xff404040, false);
            String[] headings = {"FE", "In", "Out", "Can", "Tank", "Reag"};
            for (int col = 0; col < headings.length; col++) if (col != 5 || menu.kind().hasReagentSlot()) graphics.drawString(font, headings[col], 242 + col * 28, 121, 0xff404040, false);
            for (var face : MachineSides.Face.values()) graphics.drawString(font, text("face." + face.name().toLowerCase(Locale.ROOT)), 190, 136 + face.ordinal() * 13, 0xff404040, false);
        }
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
        speedButton.visible = efficiencyButton.visible = upgradesOpen;
        speedButton.active = menu.canRemove(ProcessingUpgradeItem.Kind.SPEED); efficiencyButton.active = menu.canRemove(ProcessingUpgradeItem.Kind.EFFICIENCY); dumpButton.active = menu.canDump() && !menu.tank().empty();
        if (chargeDumpButton != null) chargeDumpButton.active = menu.canDumpCharges();
        for (var face : MachineSides.Face.values()) for (var route : MachineSides.Route.values()) {
            Button button = sideButtons.get(face.ordinal() * 6 + route.ordinal());
            button.visible = sidesOpen && (route != MachineSides.Route.REAGENT || menu.kind().hasReagentSlot()); button.setMessage(text(menu.enabled(face, route) ? "on" : "off"));
        }
        super.render(graphics, mouseX, mouseY, tick);
        if (menu.kind().maxSpeedUpgrades() > 0) MachineScreenIcons.upgrades(graphics, leftPos + 132, topPos + titleLabelY);
        MachineScreenIcons.sides(graphics, leftPos + 153, topPos + titleLabelY);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(7, 30, 14, 92, mouseX, mouseY)) graphics.renderTooltip(font, List.of(text("energy", menu.energy(), menu.kind().energyCapacity()), text("rate", menu.displayRate()), text("batch_rate", menu.configuredBatchRate())), Optional.empty(), mouseX, mouseY);
        if (menu.kind().hasReagentSlot() && isHovering(130, 30, 2, 16, mouseX, mouseY)) graphics.renderTooltip(font,
            List.of(new net.minecraft.world.item.ItemStack(menu.reagentType().equals("nether_star") ? net.minecraft.world.item.Items.NETHER_STAR : net.minecraft.world.item.Items.DIAMOND).getHoverName(),
                text("reagent_charges", menu.charges())), Optional.empty(), mouseX, mouseY);
        if (upgradesOpen && menu.active() && isHovering(193, 31, 18, 18, mouseX, mouseY)) graphics.renderTooltip(font, text("upgrade_lock"), mouseX, mouseY);
        if (isHovering(151, 48, 18, 74, mouseX, mouseY)) graphics.renderTooltip(font, List.of(tankName(), text("tank_amount", menu.tank().amount(), menu.tank().capacity())), Optional.empty(), mouseX, mouseY);
        String[] inputs = {"route.ingredient", "route.canister", "route.reagent"};
        for (int i = 0; i < (menu.kind().hasReagentSlot() ? 3 : 2); i++) if (isHovering(menu.slots.get(i).x, 30, 16, 16, mouseX, mouseY) && !menu.slots.get(i).hasItem()) graphics.renderTooltip(font, text(inputs[i]), mouseX, mouseY);
    }
    @Override protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        if (upgradesOpen && mouseX >= left + 182 && mouseX < left + 334 && mouseY >= top && mouseY < top + 104) return false;
        if (sidesOpen && mouseX >= left + 182 && mouseX < left + 418 && mouseY >= top + 106 && mouseY < top + 222) return false;
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }
}
