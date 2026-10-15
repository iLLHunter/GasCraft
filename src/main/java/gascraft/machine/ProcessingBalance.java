package gascraft.machine;

/** Approved upgrade arithmetic, independent of machine inventories and energy providers. */
public final class ProcessingBalance {
    public static final double UPGRADE_MULTIPLIER = 1.5;

    public static int processingTicks(int baseTicks, int speedUpgrades) {
        requirePositive(baseTicks);
        requireUpgradeCount(speedUpgrades);
        return Math.max(1, (int)Math.ceil(baseTicks / Math.pow(UPGRADE_MULTIPLIER, speedUpgrades)));
    }

    public static int energyPerTick(int baseTicks, int baseEnergyPerOperation, int speedUpgrades, int efficiencyUpgrades) {
        requirePositive(baseTicks);
        requirePositive(baseEnergyPerOperation);
        requireUpgradeCount(speedUpgrades);
        requireUpgradeCount(efficiencyUpgrades);
        // Subtracting exponents avoids rounding drift when equal speed/efficiency counts cancel.
        double rate = (double)baseEnergyPerOperation / baseTicks * Math.pow(UPGRADE_MULTIPLIER, speedUpgrades - efficiencyUpgrades);
        return Math.max(1, (int)Math.ceil(rate));
    }

    public static int generatorFuelTicks(int baseFuelTicks, int speedUpgrades) {
        requirePositive(baseFuelTicks);
        if (speedUpgrades < 0 || speedUpgrades > 1) throw new IllegalArgumentException("Generator accepts at most one Speed Upgrade");
        return speedUpgrades == 0 ? baseFuelTicks : Math.max(1, (int)Math.ceil(baseFuelTicks / 2.0));
    }

    private static void requirePositive(int value) { if (value < 1) throw new IllegalArgumentException("Processing values must be positive"); }
    private static void requireUpgradeCount(int count) { if (count < 0 || count > 9) throw new IllegalArgumentException("Upgrade count must be between zero and nine"); }
    private ProcessingBalance() {}
}
