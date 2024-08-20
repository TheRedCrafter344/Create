package com.simibubi.create.content.contraptions.bearing;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class MechanicalBearingBlock extends BearingBlock implements IBE<MechanicalBearingBlockEntity> {

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	
	public MechanicalBearingBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(POWERED);
		super.createBlockStateDefinition(builder);
	}
	
	@Override
	public void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn, BlockPos fromPos,
		boolean isMoving) {
		if (worldIn.isClientSide)
			return;

		boolean previouslyPowered = state.getValue(POWERED);
		boolean hasNeighborSignal = worldIn.hasNeighborSignal(pos);
		if (previouslyPowered != hasNeighborSignal) {
			withBlockEntityDo(worldIn, pos, (kbe)-> kbe.neighbourChanged(hasNeighborSignal));
			worldIn.setBlock(pos, state.cycle(POWERED), 2);
		}
	}
	
	@Override
	public InteractionResult use(BlockState state, Level worldIn, BlockPos pos, Player player, InteractionHand handIn,
		BlockHitResult hit) {
		if (!player.mayBuild())
			return InteractionResult.FAIL;
		if (player.isShiftKeyDown())
			return InteractionResult.FAIL;
		if (player.getItemInHand(handIn)
			.isEmpty()) {
			if (worldIn.isClientSide)
				return InteractionResult.SUCCESS;
			withBlockEntityDo(worldIn, pos, be -> {
				if (be.running) {
					be.disassemble();
					return;
				}
				be.assembleNextTick = true;
			});
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	public Class<MechanicalBearingBlockEntity> getBlockEntityClass() {
		return MechanicalBearingBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends MechanicalBearingBlockEntity> getBlockEntityType() {
		return AllBlockEntityTypes.MECHANICAL_BEARING.get();
	}

}
