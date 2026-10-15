package gascraft.client;

import gascraft.gas.GasCatalog;
import gascraft.item.GrenadeLauncherItem;
import gascraft.menu.GrenadeLauncherMenu;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact energy display with the same upgrade input/return flyout as the machines. */
public final class GrenadeLauncherScreen extends AbstractContainerScreen<GrenadeLauncherMenu> {
    private boolean upgradesOpen;
    private Button returnButton;
    public boolean upgradesOpen() { return upgradesOpen; }
    public GrenadeLauncherScreen(GrenadeLauncherMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
    private static Component text(String key, Object... args) { return Component.translatable("gui.gascraft.machine." + key, args); }
    @Override protected void init() {
        super.init();
        if (upgradesOpen) leftPos = Math.max(4, Math.min(leftPos, width - 338));
        menu.setUpgradesOpen(upgradesOpen);
        addRenderableWidget(Button.builder(Component.empty(), button -> { upgradesOpen = !upgradesOpen; rebuildWidgets(); })
            .bounds(leftPos + 132, topPos + 18, 18, 18).tooltip(Tooltip.create(text("upgrades"))).build());
        returnButton = addRenderableWidget(Button.builder(Component.literal("-1"), button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1))
            .bounds(leftPos + 292, topPos + 62, 30, 18).tooltip(Tooltip.create(text("return_efficiency"))).build());
    }
    private void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(leftPos + x, topPos + y, leftPos + x + width, topPos + y + height, 0xff373737);
        graphics.fill(leftPos + x + 2, topPos + y + 2, leftPos + x + width - 2, topPos + y + height - 2, 0xffc6c6c6);
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        panel(graphics, 0, 0, imageWidth, imageHeight);
        if (upgradesOpen) panel(graphics, 182, 0, 152, 90);
        for (var slot : menu.slots) if (slot.isActive()) {
            graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xff373737);
            graphics.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xff8b8b8b);
            graphics.fill(leftPos + slot.x, topPos + slot.y + 16, leftPos + slot.x + 17, topPos + slot.y + 17, 0xffffffff);
            graphics.fill(leftPos + slot.x + 16, topPos + slot.y, leftPos + slot.x + 17, topPos + slot.y + 16, 0xffffffff);
        }
        graphics.fill(leftPos + 7, topPos + 27, leftPos + 21, topPos + 64, 0xff373737);
        int energyHeight = (int)(35L * menu.energy() / GrenadeLauncherItem.ENERGY_CAPACITY);
        graphics.fill(leftPos + 8, topPos + 63 - energyHeight, leftPos + 20, topPos + 63, 0xffce5342);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + 50, topPos + 33, 0);
        graphics.pose().scale(.5F, .5F, 1);
        graphics.blit(GasCatalog.id("textures/gui/grenade_launcher.png"), 0, 0, 0, 0, 160, 58, 160, 58);
        graphics.pose().popPose();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("gui.gascraft.launcher.shot_cost", menu.shotCost()), 7, 65, 0xff404040, false);
        if (upgradesOpen) {
            graphics.drawString(font, text("upgrades"), 190, 8, 0xff404040, false);
            graphics.drawString(font, text("insert"), 190, 21, 0xff404040, false);
            graphics.drawString(font, text("return"), 292, 21, 0xff404040, false);
            graphics.drawString(font, text("efficiency_count", menu.efficiency(), GrenadeLauncherItem.MAX_EFFICIENCY), 190, 67, 0xff404040, false);
        }
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        returnButton.visible = upgradesOpen; returnButton.active = menu.canRemoveEfficiency();
        super.render(graphics, mouseX, mouseY, partialTick);
        MachineScreenIcons.upgrades(graphics, leftPos + 132, topPos + 18);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(7, 27, 14, 37, mouseX, mouseY)) graphics.renderTooltip(font,
            List.of(text("energy", menu.energy(), GrenadeLauncherItem.ENERGY_CAPACITY),
                Component.translatable("gui.gascraft.launcher.shot_cost", menu.shotCost()),
                Component.translatable("gui.gascraft.launcher.charge_rate", GrenadeLauncherItem.CHARGE_RATE)), Optional.empty(), mouseX, mouseY);
    }
    @Override protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        if (upgradesOpen && mouseX >= left + 182 && mouseX < left + 334 && mouseY >= top && mouseY < top + 90) return false;
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }
}
