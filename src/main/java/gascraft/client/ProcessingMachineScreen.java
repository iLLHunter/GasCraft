package gascraft.client;

import gascraft.ModContent;
import gascraft.item.ProcessingUpgradeItem;
import gascraft.machine.*;
import gascraft.menu.ProcessingMachineMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact processing screen with independently opened upgrade and side configuration flyouts. */
public final class ProcessingMachineScreen extends AbstractContainerScreen<ProcessingMachineMenu> {
    private Button speedButton, efficiencyButton, clearButton;
    private final List<Button> sideButtons = new ArrayList<>();
    private boolean upgradesOpen, sidesOpen;
    public boolean upgradesOpen() { return upgradesOpen; }
    public boolean sidesOpen() { return sidesOpen; }
    public ProcessingMachineScreen(ProcessingMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176; imageHeight = 222; inventoryLabelY = 122;
    }
    private static Component text(String key, Object... args) { return Component.translatable("gui.gascraft.machine." + key, args); }
    private void send(int button) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button); }
    @Override protected void init() {
        super.init();
        if (upgradesOpen || sidesOpen) leftPos = Math.max(4, Math.min(leftPos, width - 338));
        sideButtons.clear();
        if (menu.kind().maxSpeedUpgrades() > 0 || menu.kind().maxEfficiencyUpgrades() > 0)
        addRenderableWidget(Button.builder(Component.empty(), button -> {
            upgradesOpen = !upgradesOpen;
            menu.setUpgradePanelOpen(upgradesOpen);
            rebuildWidgets();
        }).bounds(leftPos + 132, topPos + titleLabelY, 18, 18).tooltip(Tooltip.create(text("upgrades"))).build());
        addRenderableWidget(Button.builder(Component.empty(), button -> { sidesOpen = !sidesOpen; rebuildWidgets(); })
            .bounds(leftPos + 153, topPos + titleLabelY, 18, 18).tooltip(Tooltip.create(text("sides"))).build());
        menu.setUpgradePanelOpen(upgradesOpen);
        speedButton = addRenderableWidget(Button.builder(Component.literal("-1"), button -> send(0)).bounds(leftPos + 292, topPos + 58, 30, 18).build());
        efficiencyButton = addRenderableWidget(Button.builder(Component.literal("-1"), button -> send(1)).bounds(leftPos + 292, topPos + 80, 30, 18).build());
        speedButton.setTooltip(Tooltip.create(text("return_speed")));
        efficiencyButton.setTooltip(Tooltip.create(text("return_efficiency")));
        if (menu.kind() == MachineKind.POWDER_MIXER)
            clearButton = addRenderableWidget(Button.builder(text("clear_filters"), button -> send(2)).bounds(leftPos + 58, topPos + 94, 86, 17).build());
        for (var face : MachineSides.Face.values()) for (int col = 0; col < 3; col++) {
            var route = MachineSides.Route.values()[col];
            Button button = addRenderableWidget(Button.builder(Component.empty(), pressed -> send(ProcessingMachineMenu.sideButton(face, route)))
                .bounds(leftPos + 242 + col * 28, topPos + 134 + face.ordinal() * 13, 26, 12).build());
            button.setTooltip(Tooltip.create(text(menu.kind() == MachineKind.GENERATOR && route == MachineSides.Route.OUTPUT ? "fuel_container_output" : "route." + route.name().toLowerCase(java.util.Locale.ROOT))));
            sideButtons.add(button);
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
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        panel(graphics, 0, 0, imageWidth, imageHeight);
        if (upgradesOpen) panel(graphics, 182, 0, 152, 104);
        if (sidesOpen) panel(graphics, 182, 106, 152, 116);
        for (var slot : menu.slots) if (slot.isActive()) slot(graphics, slot.x, slot.y);
        if (menu.kind() == MachineKind.GENERATOR) graphics.renderFakeItem(new net.minecraft.world.item.ItemStack(ModContent.GRENADE_LAUNCHER.get()), leftPos + 128, topPos + 66);
        if (menu.kind() == MachineKind.POWDER_MIXER) for (int i = 0; i < 9; i++) {
            var slot = menu.slots.get(i);
            if (!slot.hasItem() && !menu.filter(i).isEmpty()) {
                graphics.renderFakeItem(menu.filter(i), leftPos + slot.x, topPos + slot.y);
                graphics.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0x998b8b8b);
            }
        }
        if (menu.kind() != MachineKind.FERMENTER) {
        graphics.fill(leftPos + 7, topPos + 27, leftPos + 21, topPos + 89, 0xff373737);
        int height = (int)(60L * menu.energy() / menu.kind().energyCapacity());
        graphics.fill(leftPos + 8, topPos + 88 - height, leftPos + 20, topPos + 88, 0xffce5342);
        }
        int x = menu.kind() == MachineKind.POWDER_MIXER ? 94 : menu.kind() == MachineKind.GENERATOR ? 109 : 80;
        graphics.fill(leftPos + x, topPos + 53, leftPos + x + 32, topPos + 61, 0xff8b8b8b);
        int width = menu.totalTicks() == 0 ? 0 : (int)(32L * menu.progress() / menu.totalTicks());
        graphics.fill(leftPos + x, topPos + 53, leftPos + x + width, topPos + 61, 0xff68a04d);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (upgradesOpen) {
        graphics.drawString(font, text("upgrades"), 190, 8, 0xff404040, false);
        graphics.drawString(font, text("insert"), 190, 21, 0xff404040, false);
        graphics.drawString(font, text("return"), 292, 21, 0xff404040, false);
        graphics.drawString(font, text("speed_count", menu.speed(), menu.kind().maxSpeedUpgrades()), 190, 63, 0xff404040, false);
        graphics.drawString(font, text("efficiency_count", menu.efficiency(), menu.kind().maxEfficiencyUpgrades()), 190, 85, 0xff404040, false);
        }
        if (sidesOpen) {
        graphics.drawString(font, text("sides"), 190, 110, 0xff404040, false);
        if (menu.kind() != MachineKind.FERMENTER) graphics.drawString(font, "FE", 246, 121, 0xff404040, false);
        graphics.drawString(font, text("in"), 274, 121, 0xff404040, false);
        graphics.drawString(font, text("out"), 300, 121, 0xff404040, false);
        for (var face : MachineSides.Face.values()) graphics.drawString(font, text("face." + face.name().toLowerCase(java.util.Locale.ROOT)), 190, 136 + face.ordinal() * 13, 0xff404040, false);
        }
        if (menu.kind() != MachineKind.FERMENTER) {
            graphics.drawString(font, text("rate", menu.displayRate()), 7, 93, 0xff404040, false);
        } else graphics.drawString(font, text("fermentation_progress", menu.progress(), menu.totalTicks()), 28, 98, 0xff404040, false);
        if (menu.kind() != MachineKind.POWDER_MIXER && menu.status() != ProcessingMachine.Status.RUNNING)
            graphics.drawString(font, text("status." + menu.status().name().toLowerCase(java.util.Locale.ROOT)), 28, 111, 0xff404040, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        speedButton.active = menu.canRemove(ProcessingUpgradeItem.Kind.SPEED);
        efficiencyButton.active = menu.canRemove(ProcessingUpgradeItem.Kind.EFFICIENCY);
        speedButton.visible = efficiencyButton.visible = upgradesOpen;
        if (clearButton != null) clearButton.active = !menu.active();
        for (var face : MachineSides.Face.values()) for (int col = 0; col < 3; col++) {
            boolean enabled = menu.enabled(face, MachineSides.Route.values()[col]);
            sideButtons.get(face.ordinal() * 3 + col).setMessage(text(enabled ? "on" : "off"));
            sideButtons.get(face.ordinal() * 3 + col).visible = sidesOpen && (menu.kind() != MachineKind.FERMENTER || col != 0);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (menu.kind().maxSpeedUpgrades() > 0 || menu.kind().maxEfficiencyUpgrades() > 0) MachineScreenIcons.upgrades(graphics, leftPos + 132, topPos + titleLabelY);
        MachineScreenIcons.sides(graphics, leftPos + 153, topPos + titleLabelY);
        renderTooltip(graphics, mouseX, mouseY);
        if (menu.kind() != MachineKind.FERMENTER && isHovering(7, 27, 14, 62, mouseX, mouseY))
            graphics.renderTooltip(font, List.of(text("energy", menu.energy(), menu.kind().energyCapacity()), text("rate", menu.displayRate())), java.util.Optional.empty(), mouseX, mouseY);
        if (upgradesOpen && menu.active() && isHovering(193, 31, 18, 18, mouseX, mouseY))
            graphics.renderTooltip(font, text(menu.kind() == MachineKind.GENERATOR ? "fuel_upgrade_lock" : "upgrade_lock"), mouseX, mouseY);
        if (menu.kind() == MachineKind.GENERATOR && !menu.slots.get(1).hasItem() && isHovering(128, 84, 16, 16, mouseX, mouseY))
            graphics.renderTooltip(font, text("charging_slot"), mouseX, mouseY);
        if (menu.kind() == MachineKind.POWDER_MIXER) for (int i = 0; i < 9; i++) {
            var slot = menu.slots.get(i);
            if (!slot.hasItem() && !menu.filter(i).isEmpty() && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY))
                graphics.renderTooltip(font, List.of(text("filter", menu.filter(i).getHoverName()), text("clear_filter_hint")), java.util.Optional.empty(), mouseX, mouseY);
        }
    }
    @Override protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        if (upgradesOpen && mouseX >= left + 182 && mouseX < left + 334 && mouseY >= top && mouseY < top + 104) return false;
        if (sidesOpen && mouseX >= left + 182 && mouseX < left + 334 && mouseY >= top + 106 && mouseY < top + 222) return false;
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu.kind() == MachineKind.POWDER_MIXER && button == 1 && !menu.active() && menu.getCarried().isEmpty())
            for (int i = 0; i < 9; i++) {
                var slot = menu.slots.get(i);
                if (!slot.hasItem() && !menu.filter(i).isEmpty() && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) { send(100 + i); return true; }
            }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
