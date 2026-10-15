package gascraft.machine;

import gascraft.Gascraft;
import gascraft.ModContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class MachineContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Gascraft.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Gascraft.MOD_ID);

    public static final DeferredBlock<MachineBlock> GENERATOR = block("generator", MachineKind.GENERATOR);
    public static final DeferredBlock<MachineBlock> POWDERIZER = block("powderizer", MachineKind.POWDERIZER);
    public static final DeferredBlock<MachineBlock> POWDER_MIXER = block("powder_mixer", MachineKind.POWDER_MIXER);
    public static final DeferredBlock<MachineBlock> MATERIALS_KILN = block("materials_kiln", MachineKind.MATERIALS_KILN);
    public static final DeferredBlock<MachineBlock> STONE_SYNTHESIZER = block("stone_synthesizer", MachineKind.STONE_SYNTHESIZER);
    public static final DeferredBlock<MachineBlock> IRON_SYNTHESIZER = block("iron_synthesizer", MachineKind.IRON_SYNTHESIZER);
    public static final DeferredBlock<MachineBlock> DIAMOND_SYNTHESIZER = block("diamond_synthesizer", MachineKind.DIAMOND_SYNTHESIZER);
    public static final DeferredBlock<MachineBlock> NETHERITE_SYNTHESIZER = block("netherite_synthesizer", MachineKind.NETHERITE_SYNTHESIZER);
    public static final DeferredBlock<MachineBlock> FERMENTER = BLOCKS.register("fermenter", () -> new MachineBlock(MachineKind.FERMENTER, BlockBehaviour.Properties.ofFullCopy(Blocks.COMPOSTER)));

    public static final DeferredItem<BlockItem> GENERATOR_ITEM = ModContent.ITEMS.registerItem("generator", properties -> new BlockItem(GENERATOR.get(), properties));
    public static final DeferredItem<BlockItem> POWDERIZER_ITEM = ModContent.ITEMS.registerItem("powderizer", properties -> new BlockItem(POWDERIZER.get(), properties));
    public static final DeferredItem<BlockItem> POWDER_MIXER_ITEM = ModContent.ITEMS.registerItem("powder_mixer", properties -> new BlockItem(POWDER_MIXER.get(), properties));
    public static final DeferredItem<BlockItem> MATERIALS_KILN_ITEM = ModContent.ITEMS.registerItem("materials_kiln", properties -> new BlockItem(MATERIALS_KILN.get(), properties));
    public static final DeferredItem<BlockItem> STONE_SYNTHESIZER_ITEM = ModContent.ITEMS.registerItem("stone_synthesizer", properties -> new BlockItem(STONE_SYNTHESIZER.get(), properties));
    public static final DeferredItem<BlockItem> IRON_SYNTHESIZER_ITEM = ModContent.ITEMS.registerItem("iron_synthesizer", properties -> new BlockItem(IRON_SYNTHESIZER.get(), properties));
    public static final DeferredItem<BlockItem> DIAMOND_SYNTHESIZER_ITEM = ModContent.ITEMS.registerItem("diamond_synthesizer", properties -> new BlockItem(DIAMOND_SYNTHESIZER.get(), properties));
    public static final DeferredItem<BlockItem> NETHERITE_SYNTHESIZER_ITEM = ModContent.ITEMS.registerItem("netherite_synthesizer", properties -> new BlockItem(NETHERITE_SYNTHESIZER.get(), properties));
    public static final DeferredItem<BlockItem> FERMENTER_ITEM = ModContent.ITEMS.registerItem("fermenter", properties -> new BlockItem(FERMENTER.get(), properties));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlockEntity>> MACHINE_BLOCK_ENTITY = BLOCK_ENTITIES.register("machine", () -> BlockEntityType.Builder.of(MachineBlockEntity::new,
        GENERATOR.get(), POWDERIZER.get(), POWDER_MIXER.get(), MATERIALS_KILN.get(), STONE_SYNTHESIZER.get(), IRON_SYNTHESIZER.get(), DIAMOND_SYNTHESIZER.get(), NETHERITE_SYNTHESIZER.get(), FERMENTER.get()).build(null));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<gascraft.menu.ProcessingMachineMenu>> PROCESSING_MENU = ModContent.MENUS.register("processing_machine",
        () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(gascraft.menu.ProcessingMachineMenu::new));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<gascraft.menu.SynthesizerMenu>> SYNTHESIZER_MENU = ModContent.MENUS.register("synthesizer",
        () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(gascraft.menu.SynthesizerMenu::new));

    private static DeferredBlock<MachineBlock> block(String name, MachineKind kind) {
        return BLOCKS.register(name, () -> new MachineBlock(kind, BlockBehaviour.Properties.ofFullCopy(Blocks.FURNACE)));
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(MachineContent::registerCapabilities);
    }
    private static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
            MACHINE_BLOCK_ENTITY.get(), (machine, side) -> machine.kind() == MachineKind.FERMENTER ? null : machine.energy().view(side));
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
            MACHINE_BLOCK_ENTITY.get(), (machine, side) -> machine.processor() == null ? machine.synthesizer().sideView(side) : machine.processor().sideView(side));
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            MACHINE_BLOCK_ENTITY.get(), (machine, side) -> machine.tank() == null ? null : machine.tank().view(side));
    }
    private MachineContent() {}
}
