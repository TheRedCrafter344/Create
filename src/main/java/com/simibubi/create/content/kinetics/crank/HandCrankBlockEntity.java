package com.simibubi.create.content.kinetics.crank;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.Create;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.render.CachedBufferer;
import com.simibubi.create.foundation.render.SuperByteBuffer;
import com.simibubi.create.foundation.utility.AnimationTickHolder;
import com.simibubi.create.infrastructure.config.AllConfigs;

import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class HandCrankBlockEntity extends KineticBlockEntity {

	public int inUse;
	public boolean backwards;
	public float independentAngle;
	public float chasingVelocity;

	public HandCrankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public void turn(boolean back) {
		inUse = 10;
		this.backwards = back;
	}

	public float getIndependentAngle(float partialTicks) {
		return (independentAngle + partialTicks * chasingVelocity) / 360;
	}

	/*
	@Override
	public float getGeneratedSpeed() {
		Block block = getBlockState().getBlock();
		if (!(block instanceof HandCrankBlock))
			return 0;
		HandCrankBlock crank = (HandCrankBlock) block;
		int speed = (inUse == 0 ? 0 : clockwise() ? -1 : 1) * crank.getRotationSpeed();
		return convertToDirection(speed, getBlockState().getValue(HandCrankBlock.FACING));
	}
	*/

	@Override
	public float getTorque(float speed) {
		float clampedSpeed = speed > 0 ? Math.max(speed, 1) : Math.min(speed, -1);
		float torque = (inUse == 0 ? 0 : clockwise() ? -1 : 1) * AllConfigs.server().kinetics.handcrankPower.getF() / Math.abs(clampedSpeed);
		return super.getTorque(speed) + convertToDirection(torque, getBlockState().getValue(HandCrankBlock.FACING));
	}
	
	@Override
	public boolean shouldCreateNetwork() {
		return true;
	}
	
	protected boolean clockwise() {
		return backwards;
	}

	@Override
	public void write(CompoundTag compound, boolean clientPacket) {
		compound.putInt("InUse", inUse);
		compound.putBoolean("Backwards", backwards);
		super.write(compound, clientPacket);
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		inUse = compound.getInt("InUse");
		backwards = compound.getBoolean("Backwards");
		super.read(compound, clientPacket);
	}

	@Override
	public void tick() {
		super.tick();
		
		chasingVelocity += ((getSpeed() * 10 / 3f) - chasingVelocity) * .25f;
		independentAngle += chasingVelocity;

		if (inUse > 0) {
			inUse--;	
		}
	}

	@OnlyIn(Dist.CLIENT)
	public SuperByteBuffer getRenderedHandle() {
		BlockState blockState = getBlockState();
		Direction facing = blockState.getOptionalValue(HandCrankBlock.FACING)
			.orElse(Direction.UP);
		return CachedBufferer.partialFacing(AllPartialModels.HAND_CRANK_HANDLE, blockState, facing.getOpposite());
	}

	@OnlyIn(Dist.CLIENT)
	public Model getRenderedHandleInstance() {
		BlockState blockState = getBlockState();
		Direction facing = blockState.getOptionalValue(HandCrankBlock.FACING)
			.orElse(Direction.UP);
		return Models.partial(AllPartialModels.HAND_CRANK_HANDLE, facing.getOpposite());
	}

	@OnlyIn(Dist.CLIENT)
	public boolean shouldRenderShaft() {
		return true;
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void tickAudio() {
		super.tickAudio();
		if (inUse > 0 && AnimationTickHolder.getTicks() % 10 == 0) {
			if (!AllBlocks.HAND_CRANK.has(getBlockState()))
				return;
			AllSoundEvents.CRANKING.playAt(level, worldPosition, (inUse) / 2.5f, .65f + (10 - inUse) / 10f, true);
		}
	}

}
