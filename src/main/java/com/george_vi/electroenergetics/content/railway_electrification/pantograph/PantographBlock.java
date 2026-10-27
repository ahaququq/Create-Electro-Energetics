package com.george_vi.electroenergetics.content.railway_electrification.pantograph;

import com.george_vi.electroenergetics.*;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.CEELang;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class PantographBlock extends SimpleElectricalDeviceBlock<PantographDevice> implements IBE<PantographBlockEntity>, IPantographBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty DOUBLE = BooleanProperty.create("double");
    public static final EnumProperty<PantographControlMode> CONTROL_MODE = EnumProperty.create("mode", PantographControlMode.class);

    public PantographBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
			.setValue(DOUBLE, false)
			.setValue(CONTROL_MODE, PantographControlMode.MANUAL)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DOUBLE, CONTROL_MODE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getPlayer().isShiftKeyDown() ? context.getHorizontalDirection().getOpposite() : context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CEEShapes.PANTOGRAPH.get(state.getValue(FACING));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        BlockState facingState = level.getBlockState(pos.relative(facing.getOpposite()));
        if (CEEBlocks.PANTOGRAPH.has(facingState) && facingState.getValue(FACING).equals(facing.getOpposite())) {
            if (!state.getValue(DOUBLE)) {
                level.setBlock(pos.relative(facing.getOpposite()), facingState
						.setValue(DOUBLE, true)
						.setValue(CONTROL_MODE, PantographControlMode.NONE), 2);

				return state.setValue(DOUBLE, true).setValue(CONTROL_MODE, PantographControlMode.NONE);
            }
        } else if (state.getValue(DOUBLE)) {
			if (
				level.getBlockEntity(pos) instanceof PantographBlockEntity be &&
				be.controlModeFromItems() != state.getValue(CONTROL_MODE)
			) { // Pantograph was double and recessive, lost its companion, which held its used control item
				return state.setValue(DOUBLE, false).setValue(CONTROL_MODE, be.controlModeFromItems());
			}
			return state.setValue(DOUBLE, false);
        }
        return state;
    }

	private static void becomeDouble(BlockPos pos, BlockEntity current, BlockEntity other, LevelAccessor level) {
		CreateElectroEnergetics.LOGGER.info("becomeDouble @ {}", pos);
		CreateElectroEnergetics.LOGGER.info("current = {}", current instanceof PantographBlockEntity);
		CreateElectroEnergetics.LOGGER.info("other   = {}", other instanceof PantographBlockEntity);
		if (
			current instanceof PantographBlockEntity be0 && be0.wouldBeRecessive() &&
			other instanceof PantographBlockEntity be1
		) {
			CreateElectroEnergetics.LOGGER.info("becomeDouble from {} for {}", current.getBlockPos(), other.getBlockPos());
			be0.color = be1.color;
			for (int i = 0; i < be0.getContainerSize(); i++) {
				ItemStack item = be0.removeItemNoUpdate(0);
				if (be1.canPlaceItem(be1.lastSlot(), item)) {
					be1.setItem(be1.lastSlot(), item);
				} else if (level instanceof Level l) {
					Containers.dropItemStack(l, be0.getBlockPos().getX(), be0.getBlockPos().getY(), be0.getBlockPos().getZ(), item);
				}
			}
			be0.clearContent();
		} else if (
			other instanceof PantographBlockEntity be0 && be0.wouldBeRecessive() &&
			current instanceof PantographBlockEntity be1
		) {
			CreateElectroEnergetics.LOGGER.info("becomeDouble from {} for {}", current.getBlockPos(), other.getBlockPos());
			be0.color = be1.color;
			for (int i = 0; i < be0.getContainerSize(); i++) {
				ItemStack item = be0.removeItemNoUpdate(0);
				if (be1.canPlaceItem(be1.lastSlot(), item)) {
					be1.setItem(be1.lastSlot(), item);
				} else if (level instanceof Level l) {
					Containers.dropItemStack(l, be0.getBlockPos().getX(), be0.getBlockPos().getY(), be0.getBlockPos().getZ(), item);
				}
			}
			be0.clearContent();
		}
	}

	@Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
		if (state.getValue(CONTROL_MODE).isRedstone() && level.getBlockEntity(pos) instanceof PantographBlockEntity be) {
			updateRedstone(state, level, pos, be, false);
		}

//		if (true) return; // Doesn't work anyway
//		Direction facing = state.getValue(FACING);
//	    BlockState oppositeState = level.getBlockState(pos.relative(facing));
//        if (CEEBlocks.PANTOGRAPH.has(oppositeState) && oppositeState.getValue(FACING).equals(facing.getOpposite())) {
//            if (!state.getValue(DOUBLE)) {
//				becomeDouble(pos, level.getBlockEntity(pos), level.getBlockEntity(pos.relative(facing.getOpposite())), level);
//
//				level.setBlockAndUpdate(pos.relative(facing), oppositeState.setValue(DOUBLE, true));
//                level.setBlockAndUpdate(pos, state.setValue(DOUBLE, true));
//            }
//        } else if (!CEEBlocks.PANTOGRAPH.has(oppositeState) || !oppositeState.getValue(FACING).equals(facing.getOpposite())) {
//			if (state.getValue(DOUBLE)) {
//				if (
//					level.getBlockEntity(pos) instanceof PantographBlockEntity be &&
//					be.controlModeFromItems() != state.getValue(CONTROL_MODE)
//				) { // Pantograph was double and recessive, lost its companion, which held its used control item
//					level.setBlockAndUpdate(pos, state.setValue(DOUBLE, false).setValue(CONTROL_MODE, be.controlModeFromItems()));
//				}
//				level.setBlockAndUpdate(pos, state.setValue(DOUBLE, false));
//			}
//		}
    }

	private static void updateRedstone(BlockState state, Level level, BlockPos pos, PantographBlockEntity be, boolean chain) {
		BlockPos relative = pos.relative(state.getValue(FACING).getOpposite());
		// If recessive, pass on to the dominant
		if (be.isRecessive() && level.getBlockEntity(relative) instanceof PantographBlockEntity be1) {
			if (!chain) updateRedstone(level.getBlockState(relative), level, relative, be1, true);
			return;
		}

		int redstone = level.getBestNeighborSignal(pos);
		int other = level.getBestNeighborSignal(relative);
		if (be.isDominant() && other > redstone) redstone = other; // Take the max signal if it's double

		be.physicsController.springPosition = // Map redstone strength into spring pos
			be.physicsController.getSpringLowerLimit() +
			(
				be.physicsController.getSpringUpperLimit() -
				be.physicsController.getSpringLowerLimit()
			) * (redstone / 15.0f);
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
//		Direction facing = state.getValue(FACING);
//		BlockState oppositeState = level.getBlockState(pos.relative(facing));
//		if (CEEBlocks.PANTOGRAPH.has(oppositeState) && oppositeState.getValue(FACING).equals(facing.getOpposite())) {
//			if (!state.getValue(DOUBLE)) {
//				becomeDouble(level.getBlockEntity(pos), level.getBlockEntity(pos.relative(facing.getOpposite())), level);
//
//				level.setBlockAndUpdate(pos.relative(facing), oppositeState.setValue(DOUBLE, true));
//				level.setBlockAndUpdate(pos, state.setValue(DOUBLE, true));
//			}
//		} else if (state.getValue(DOUBLE)) {
//			if (
//					level.getBlockEntity(pos) instanceof PantographBlockEntity be &&
//							be.controlModeFromItems() != state.getValue(CONTROL_MODE)
//			) { // Pantograph was double and recessive, lost its companion, which held its used control item
//				level.setBlockAndUpdate(pos, state.setValue(DOUBLE, false).setValue(CONTROL_MODE, be.controlModeFromItems()));
//			}
//			level.setBlockAndUpdate(pos, state.setValue(DOUBLE, false));
//		}
		super.onPlace(state, level, pos, oldState, movedByPiston);
	}

	@Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		Direction facing = state.getValue(FACING);
		BlockPos oppositePosition = pos.relative(facing.getOpposite());
	    if (stack.isEmpty() && !player.isShiftKeyDown()) {
			if (state.getValue(CONTROL_MODE).isManual()) {
				AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos);

				if (
					state.getValue(DOUBLE) &&
					level.getBlockEntity(pos) instanceof PantographBlockEntity be &&
					level.getBlockEntity(oppositePosition) instanceof PantographBlockEntity opposite &&
					be.isRecessive()
				) { // Recessive pantograph toggles the other block entity
					opposite.physicsController.toggle();
					return ItemInteractionResult.SUCCESS;
				}

				if (level.getBlockEntity(pos) instanceof PantographBlockEntity be) { // Dominant or single controls only itself
					be.physicsController.toggle();
				}

				return ItemInteractionResult.SUCCESS;
			}
			AllSoundEvents.DENY.playOnServer(level, pos, 0.2f, 1.0f);

			return ItemInteractionResult.FAIL;
        }

		if (stack.getItem() instanceof DyeItem di) { // Dying
			if (level.getBlockEntity(pos) instanceof PantographBlockEntity be) {
				be.color = di.getDyeColor();

				if (!level.isClientSide)
					be.sendData();
			}

			if (state.getValue(DOUBLE)) {
				if (level.getBlockEntity(oppositePosition) instanceof PantographBlockEntity be) {
					be.color = di.getDyeColor();

					if (!level.isClientSide)
						be.sendData();
				}
			}

			return ItemInteractionResult.SUCCESS;
		}

		if (level.getBlockEntity(pos) instanceof PantographBlockEntity be) {
			PantographBlockEntity targetInventory =
					be.isRecessive() && level.getBlockEntity(oppositePosition) instanceof PantographBlockEntity be1
					? be1 : be; // If recessive, insert into the other one

			// Put control item
			if (targetInventory.canPlaceItem(targetInventory.lastSlot(), stack)) {
				addControlItem(stack, level, pos, player, targetInventory);

				be.updateControlMode();
				if (state.getValue(DOUBLE) && level.getBlockEntity(oppositePosition) instanceof PantographBlockEntity be1)
					be1.updateControlMode();

				return ItemInteractionResult.SUCCESS;
			}

			// Replace control item - clear contents and try adding like before
			// Disabled because it was causing desyncs for some reason
			if (stack.is(CEETags.PANTOGRAPH_ANY) && false) {
				if (player.isCreative()) targetInventory.clearContent();
				else targetInventory.giveBackItems(player);

				if (targetInventory.canPlaceItem(targetInventory.lastSlot(), stack)) {
					addControlItem(stack, level, pos, player, targetInventory);

					be.updateControlMode();
					if (state.getValue(DOUBLE) && level.getBlockEntity(oppositePosition) instanceof PantographBlockEntity be1)
						be1.updateControlMode();

					return ItemInteractionResult.SUCCESS;
				}
				CreateElectroEnergetics.LOGGER.warn("Player attempted to insert a #pantograph_controlers item into a pantograph, but the pantograph didn't accept it");
				return ItemInteractionResult.FAIL; // If logic breaks
			}

			// Remove control items
			if (stack.isEmpty() && player.isShiftKeyDown()) {
				if (player.isCreative()) targetInventory.clearContent();
				else targetInventory.giveBackItems(player);

				AllSoundEvents.CRAFTER_CLICK.playOnServer(level, pos);
				if (!level.isClientSide) targetInventory.notifyUpdate();

				be.updateControlMode();
				if (state.getValue(DOUBLE) && level.getBlockEntity(oppositePosition) instanceof PantographBlockEntity be1)
					be1.updateControlMode();

				return ItemInteractionResult.SUCCESS;
			}
		}

		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	// Add the item on click, take it from the player if not in creative
	private static void addControlItem(ItemStack stack, Level level, BlockPos pos, Player player, PantographBlockEntity be) {
		ItemStack toPlace;
		if (player.isCreative()) toPlace = stack.copyWithCount(1);
		else toPlace = stack.consumeAndReturn(1, player);
		be.setItem(be.lastSlot(), toPlace);
		if (!level.isClientSide) be.notifyUpdate();

		AllSoundEvents.CRAFTER_CLICK.playOnServer(level, pos);
	}

	// Handle breaking the block, by moving items over to the other one if it's double, and dropping them if not
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		CreateElectroEnergetics.LOGGER.info("Remove pantograph at {}", pos.toShortString());
		// Why is onRemove called for stuff other than removing the block
		if ( // If the block entity still exist, and we were double recessive, try passing the items to the other one, dropping if it can't take them
			!level.isClientSide() && !newState.is(CEEBlocks.PANTOGRAPH) &&
			level.getBlockEntity(pos) instanceof PantographBlockEntity be && be.isDominant() &&
			level.getBlockEntity(pos.relative(state.getValue(FACING).getOpposite())) instanceof PantographBlockEntity be1
		) {
			CreateElectroEnergetics.LOGGER.info("Move items for pantograph at {}", pos.toShortString());
			for (int i = 0; i < be.getContainerSize(); i++) {
				ItemStack item = be.removeItemNoUpdate(0);
				if (be1.canPlaceItem(be1.lastSlot(), item)) {
					be1.setItem(be1.lastSlot(), item);
				} else if (level instanceof Level l) {
					Containers.dropItemStack(l, be.getBlockPos().getX(), be.getBlockPos().getY(), be.getBlockPos().getZ(), item);
				}
			}
//			be.clearContent();
		}
		Containers.dropContentsOnDestroy(state, newState, level, pos);
		IBE.onRemove(state, level, pos, newState);
	}

    @Override
    public Class<PantographBlockEntity> getBlockEntityClass() {
        return PantographBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PantographBlockEntity> getBlockEntityType() {
        return CEEBlockEntityTypes.PANTOGRAPH.get();
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public PantographType getPantographType(BlockState state) {
        return state.getValue(DOUBLE) ? CEEPantographTypes.DOUBLE.get() : CEEPantographTypes.STANDARD.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return state.getValue(CONTROL_MODE).getNodes().getNodes(state.getValue(FACING).getOpposite());
    }

    @Override
    public @Nullable Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return state.getValue(CONTROL_MODE).getNodes().getNodePos(state.getValue(FACING).getOpposite(), id);
    }

	@Override
	public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
		if (id == 1 || id == 2)
			return CEELang.nodeLabel("control");
		return CEELang.nodeLabel("contact");
	}

    @Override
    public SimulatedDeviceType<PantographDevice> getDevice() {
        return CEESimulatedDevices.PANTOGRAPH.get();
    }
}
