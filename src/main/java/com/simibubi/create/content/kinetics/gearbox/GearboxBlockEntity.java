package com.simibubi.create.content.kinetics.gearbox;

import com.simibubi.create.content.kinetics.base.DirectionalShaftHalvesBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class GearboxBlockEntity extends DirectionalShaftHalvesBlockEntity {

	public GearboxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}
	
	@Override
	protected boolean isNoisy() {
		return false;
	}

	@Override
	public float getRotationSpeedModifier(Direction face) {
		switch(getBlockState().getValue(BlockStateProperties.AXIS)) {
		case X:
			switch(face) {
			case DOWN: return -1;
			case EAST: return 0;
			case NORTH: return -1;
			case SOUTH: return 1;
			case UP: return 1;
			case WEST: return 0;
			}
		case Y:
			switch(face) {
			case DOWN: return 0;
			case EAST: return -1;
			case NORTH: return -1;
			case SOUTH: return 1;
			case UP: return 0;
			case WEST: return 1;
			}
		case Z:
			switch(face) {
			case DOWN: return -1;
			case EAST: return -1;
			case NORTH: return 0;
			case SOUTH: return 0;
			case UP: return 1;
			case WEST: return 1;
			}
		}
		return 0;
	}
	
}
