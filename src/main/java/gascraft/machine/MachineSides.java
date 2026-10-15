package gascraft.machine;

import java.util.Arrays;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/** Independent resource switches, stored relative to the front of the machine. */
public final class MachineSides {
    public enum Face { FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM }
    public enum Route { ENERGY, INGREDIENT, OUTPUT, CANISTER, TANK, REAGENT }
    private final int[] masks = new int[Face.values().length];

    public MachineSides() {
        Arrays.fill(masks, bit(Route.ENERGY));
        masks[Face.TOP.ordinal()] |= bit(Route.INGREDIENT);
        masks[Face.FRONT.ordinal()] |= bit(Route.OUTPUT);
        masks[Face.BOTTOM.ordinal()] |= bit(Route.CANISTER) | bit(Route.OUTPUT);
        masks[Face.BACK.ordinal()] |= bit(Route.TANK);
    }

    private static int bit(Route route) { return 1 << route.ordinal(); }
    public int mask(Face face) { return masks[face.ordinal()]; }
    public void setMask(Face face, int mask) { masks[face.ordinal()] = mask & 63; }
    public boolean enabled(Face face, Route route) { return (mask(face) & bit(route)) != 0; }
    public void toggle(Face face, Route route) { masks[face.ordinal()] ^= bit(route); }

    public static Face face(Direction direction, Direction front) {
        if (direction == Direction.UP) return Face.TOP;
        if (direction == Direction.DOWN) return Face.BOTTOM;
        if (direction == front) return Face.FRONT;
        if (direction == front.getOpposite()) return Face.BACK;
        return direction == front.getClockWise() ? Face.LEFT : Face.RIGHT;
    }

    public boolean enabled(Direction direction, Direction front, Route route) {
        return direction == null || enabled(face(direction, front), route);
    }

    public void save(CompoundTag tag) { tag.putIntArray("sides", masks); }
    public void load(CompoundTag tag) {
        int[] saved = tag.getIntArray("sides");
        if (saved.length == masks.length) for (Face face : Face.values()) setMask(face, saved[face.ordinal()]);
    }
}
