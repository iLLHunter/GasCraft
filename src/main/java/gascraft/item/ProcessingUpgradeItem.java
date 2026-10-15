package gascraft.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.Optional;

/** Upgrade identity and item stack size; each machine enforces its own installed limits. */
public final class ProcessingUpgradeItem extends Item {
    public enum Kind { SPEED, EFFICIENCY }

    private final Kind kind;

    public ProcessingUpgradeItem(Kind kind, Properties properties) {
        super(properties.stacksTo(64));
        this.kind = java.util.Objects.requireNonNull(kind);
    }

    public Kind kind() { return kind; }

    /** Recognize optional Mekanism upgrade items without linking any Mekanism classes. */
    public static Optional<Kind> kindOf(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        if (stack.getItem() instanceof ProcessingUpgradeItem upgrade) return Optional.of(upgrade.kind());
        return switch (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()) {
            case "mekanism:upgrade_speed" -> Optional.of(Kind.SPEED);
            case "mekanism:upgrade_energy" -> Optional.of(Kind.EFFICIENCY);
            default -> Optional.empty();
        };
    }
}
