package gascraft.client;

import net.minecraft.client.gui.GuiGraphics;

/** Small pixel icons drawn over ordinary buttons so their native interaction stays intact. */
final class MachineScreenIcons {
    static void upgrades(GuiGraphics graphics, int x, int y) {
        for (int row = 0; row < 5; row++) graphics.fill(x + 8 - row, y + 4 + row, x + 10 + row, y + 5 + row, 0xffeeeeee);
        graphics.fill(x + 7, y + 9, x + 11, y + 14, 0xffeeeeee);
    }
    static void sides(GuiGraphics graphics, int x, int y) {
        outline(graphics, x + 7, y + 4, 8, 8, 0xff858585);
        outline(graphics, x + 4, y + 7, 8, 8, 0xffeeeeee);
    }
    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
    private MachineScreenIcons() {}
}
