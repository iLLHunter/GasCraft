package gascraft.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;

/** Left-aligned labels with horizontal scrolling and a full-text hover tooltip. */
public final class ScreenLabels {
    private double mouseX, mouseY;
    private Component hovered;

    public void begin(double x, double y) { mouseX = x; mouseY = y; hovered = null; }

    public void draw(GuiGraphics graphics, Font font, Component text, int x, int y, int width) {
        drawBounded(graphics, font, text, x, y, width);
        if (overflowsAt(font, text, x, y, width, mouseX, mouseY)) hovered = text;
    }

    public Component hoveredText() { return hovered; }

    public void renderTooltip(GuiGraphics graphics, Font font, int x, int y) {
        if (hovered != null) graphics.renderTooltip(font, hovered, x, y);
    }

    public static boolean overflowsAt(Font font, Component text, int x, int y, int width, double mouseX, double mouseY) {
        return font.width(text) > width && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + font.lineHeight;
    }

    public static void drawBounded(GuiGraphics graphics, Font font, Component text, int x, int y, int width) {
        int excess = font.width(text) - width;
        if (excess <= 0) { graphics.drawString(font, text, x, y, 0xff404040, false); return; }
        // Use Minecraft's smooth back-and-forth timing without changing the label's alignment or shadow.
        double seconds = Util.getMillis() / 1000.0;
        double period = Math.max(excess * 0.5, 3.0);
        double phase = Math.sin((Math.PI / 2) * Math.cos(2 * Math.PI * seconds / period)) / 2 + 0.5;
        int offset = (int)(phase * excess);
        // GuiGraphics scissors use absolute GUI coordinates, unlike the translated label draw calls.
        var matrix = graphics.pose().last().pose();
        var start = matrix.transformPosition(new Vector3f(x, y, 0));
        var end = matrix.transformPosition(new Vector3f(x + width, y, 0));
        graphics.flush();
        // Only clip horizontally so combining marks above/below the line remain visible.
        graphics.enableScissor((int)Math.floor(start.x()), 0, (int)Math.ceil(end.x()), Minecraft.getInstance().getWindow().getGuiScaledHeight());
        graphics.drawString(font, text, x - offset, y, 0xff404040, false);
        graphics.flush();
        graphics.disableScissor();
    }
}
