package galena.oreganized.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

import static net.minecraft.world.level.block.CandleBlock.LIT;
import static net.minecraft.world.level.block.CandleBlock.MAX_CANDLES;
import static net.minecraft.world.level.block.CandleBlock.MIN_CANDLES;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.CANDLES;

public class VigilCandleBlock extends LanternBlock {

    private static VoxelShape shape(double x, double y, double z) {
        return Block.box(x, y, z, 6 + x, 10 + y, 6 + z);
    }

    private static VoxelShape[] createShapes(boolean hanging) {
        return new VoxelShape[]{
                shape(5, hanging ? 2 : 0, 5),
                Shapes.or(
                        shape(6 + (hanging ? 0 : 1), hanging ? 2 : 0, 1),
                        shape(3, 0, 9)
                ),
                Shapes.or(
                        shape(1, 0, 2 + (hanging ? 2 : 0)),
                        shape(9, hanging ? 2 : 0, 1),
                        shape(7 + (hanging ? 2 : 0), hanging ? 4 : 0, 9)
                ),
                Shapes.or(
                        shape(1, hanging ? 4 : 0, 1),
                        shape(9, 0, 1),
                        shape(1, 0, 9),
                        shape(9, hanging ? 2 : 0, 9)
                )
        };
    }

    private static final VoxelShape[] SHAPES = createShapes(false);
    private static final VoxelShape[] HANGING_SHAPES = createShapes(true);

    public VigilCandleBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(HANGING, false)
                .setValue(LIT, false)
                .setValue(WATERLOGGED, false)
                .setValue(CANDLES, MIN_CANDLES));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CANDLES, LIT);
    }

    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        if (super.canBeReplaced(state, context)) return true;
        return !context.isSecondaryUseActive()
                && context.getItemInHand().is(asItem())
                && state.getValue(CANDLES) < MAX_CANDLES;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var candles = state.getValue(CANDLES);
        var handing = state.getValue(HANGING);
        var index = candles - 1;
        return (handing ? HANGING_SHAPES : SHAPES)[index];
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState previous = context.getLevel().getBlockState(context.getClickedPos());
        var candles = previous.is(this) ? Math.min(previous.getValue(CANDLES) + 1, MAX_CANDLES) : MIN_CANDLES;
        return Optional.ofNullable(super.getStateForPlacement(context))
                .map(it -> it.setValue(CANDLES, candles))
                .orElse(null);
    }

    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction support = getConnectedDirection(state).getOpposite();
        var candles = state.getValue(CANDLES);
        var supporting = pos.relative(support);
        if (candles > 1) {
            return Block.isFaceFull(level.getBlockState(supporting).getCollisionShape(level, supporting), support.getOpposite());
        } else {
            return Block.canSupportCenter(level, pos.relative(support), support.getOpposite());
        }
    }

    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getAbilities().mayBuild && player.getItemInHand(hand).isEmpty() && state.getValue(LIT)) {
            AbstractCandleBlock.extinguish(player, state, level, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        if (!state.getValue(WATERLOGGED) && fluid.getType() == Fluids.WATER) {
            BlockState waterlogged = state.setValue(WATERLOGGED, true);
            if (state.getValue(LIT)) {
                AbstractCandleBlock.extinguish(null, waterlogged, level, pos);
            } else {
                level.setBlock(pos, waterlogged, 3);
            }

            level.scheduleTick(pos, fluid.getType(), fluid.getType().getTickDelay(level));
            return true;
        } else {
            return false;
        }
    }

}
