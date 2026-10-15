package gascraft.machine;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Machine placement, server processing, menus and state-preserving recovery. */
public final class MachineBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private final MachineKind kind;

    public MachineBlock(MachineKind kind, BlockBehaviour.Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    public MachineKind kind() { return kind; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, LIT); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) { return new MachineBlockEntity(position, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != MachineContent.MACHINE_BLOCK_ENTITY.get()) return null;
        return (world, position, blockState, entity) -> ((MachineBlockEntity)entity).serverTick();
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(position) instanceof MachineBlockEntity machine) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) serverPlayer.connection.send(machine.getUpdatePacket());
            player.openMenu(machine, position);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos position, Player player, InteractionHand hand, BlockHitResult hit) {
        if (kind.isSynthesizer() && level.getBlockEntity(position) instanceof MachineBlockEntity machine
            && net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(stack).isPresent()) {
            if (level.isClientSide || net.neoforged.neoforge.fluids.FluidUtil.interactWithFluidHandler(player, hand, machine.tank().view(null)))
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (kind == MachineKind.FERMENTER && level.getBlockEntity(position) instanceof MachineBlockEntity machine
            && machine.processor().inventory().isItemValid(0, stack)) {
            int requested = player.isShiftKeyDown() ? stack.getCount() : 1;
            ItemStack offered = stack.copyWithCount(requested);
            int inserted = requested - machine.processor().inventory().insertItem(0, offered, true).getCount();
            if (inserted > 0) {
                if (!level.isClientSide) {
                    machine.processor().inventory().insertItem(0, offered, false);
                    if (!player.getAbilities().instabuild) stack.shrink(inserted);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MachineBlockEntity machine) {
            for (ItemStack stack : drops) if (stack.is(asItem()))
                BlockItem.setBlockEntityData(stack, MachineContent.MACHINE_BLOCK_ENTITY.get(), machine.saveWithoutMetadata(params.getLevel().registryAccess()));
        }
        return drops;
    }
}
