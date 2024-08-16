package com.simibubi.create.content.kinetics.transmission;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class ClutchBlockEntity extends SplitShaftBlockEntity {

	public ClutchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public float getRotationSpeedModifier(Direction face) {
		float powered = getBlockState().getValue(BlockStateProperties.POWERED) ? 0 : 1;
		switch(getBlockState().getValue(BlockStateProperties.AXIS)) {
		case X:
			switch(face) {
			case EAST: return powered;
			case WEST: return powered;
			default: return 0;
			}
		case Y:
			switch(face) {
			case DOWN: return powered;
			case UP: return powered;
			default: return 0;
			}
		case Z:
			switch(face) {
			case NORTH: return powered;
			case SOUTH: return powered;
			default: return 0;
			}
		}
		return 0;
	}

}
