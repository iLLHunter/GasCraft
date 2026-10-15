package gascraft.machine;

/** Approved machine types, working-slot counts and installed-upgrade limits. */
public enum MachineKind {
    GENERATOR(0, 1, 0, 0, 16_000, 0, 0, 0),
    POWDERIZER(1, 3, 3, 200, 100_000, 4_096, 0, 0),
    POWDER_MIXER(1, 3, 3, 200, 100_000, 4_096, 0, 0),
    MATERIALS_KILN(1, 7, 7, 200, 100_000, 4_096, 0, 0),
    STONE_SYNTHESIZER(3, 0, 0, 1_200, 50_000, 256, 4_000, 1_000),
    IRON_SYNTHESIZER(6, 3, 3, 600, 100_000, 1_024, 8_000, 2_000),
    DIAMOND_SYNTHESIZER(9, 5, 5, 300, 250_000, 4_096, 16_000, 4_000),
    NETHERITE_SYNTHESIZER(12, 9, 9, 150, 1_000_000, 16_384, 32_000, 8_000),
    FERMENTER(1, 0, 0, 24_000, 0, 0, 0, 0);

    public static final int GENERATOR_FE_PER_TICK = 32;
    public static final int BASE_PROCESSING_FE_PER_TICK = 32;

    private final int workingSlots;
    private final int maxSpeedUpgrades;
    private final int maxEfficiencyUpgrades;
    private final int baseProcessingTicks;
    private final int energyCapacity;
    private final int energyInputPerTick;
    private final int tankCapacity;
    private final int tankInputPerTick;

    MachineKind(int workingSlots, int maxSpeedUpgrades, int maxEfficiencyUpgrades, int baseProcessingTicks,
                int energyCapacity, int energyInputPerTick, int tankCapacity, int tankInputPerTick) {
        this.workingSlots = workingSlots;
        this.maxSpeedUpgrades = maxSpeedUpgrades;
        this.maxEfficiencyUpgrades = maxEfficiencyUpgrades;
        this.baseProcessingTicks = baseProcessingTicks;
        this.energyCapacity = energyCapacity;
        this.energyInputPerTick = energyInputPerTick;
        this.tankCapacity = tankCapacity;
        this.tankInputPerTick = tankInputPerTick;
    }

    public int workingSlots() { return workingSlots; }
    public int maxSpeedUpgrades() { return maxSpeedUpgrades; }
    public int maxEfficiencyUpgrades() { return maxEfficiencyUpgrades; }
    public int baseProcessingTicks() { return baseProcessingTicks; }
    public int energyCapacity() { return energyCapacity; }
    public int energyInputPerTick() { return energyInputPerTick; }
    public int tankCapacity() { return tankCapacity; }
    public int tankInputPerTick() { return tankInputPerTick; }
    public boolean isSynthesizer() { return ordinal() >= STONE_SYNTHESIZER.ordinal() && ordinal() <= NETHERITE_SYNTHESIZER.ordinal(); }
    public boolean hasReagentSlot() { return this == NETHERITE_SYNTHESIZER; }
}
