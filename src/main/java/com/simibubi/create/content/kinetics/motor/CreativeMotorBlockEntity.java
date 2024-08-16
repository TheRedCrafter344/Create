package com.simibubi.create.content.kinetics.motor;

import java.util.List;

import com.jozufozu.flywheel.util.transform.TransformStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.utility.AngleHelper;
import com.simibubi.create.foundation.utility.Lang;
import com.simibubi.create.foundation.utility.VecHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class CreativeMotorBlockEntity extends KineticBlockEntity {

	protected CreativeMotorValueBehaviour generatorSettings;

	public CreativeMotorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		generatorSettings = new CreativeMotorValueBehaviour(Lang.translateDirect("kinetics.creative_motor.generator_settings"), this, new MotorValueBox());
		behaviours.add(generatorSettings);
	}

	@Override
	public float getTorque(float speed) {
		if(!AllBlocks.CREATIVE_MOTOR.has(getBlockState())) return 0;
		int row = generatorSettings.getValueSettings().row();
		int value = generatorSettings.getValueSettings().value() - 128;
		switch(row) {
		case 0:
			float clampedSpeed = speed >= 0 ? Math.max(speed, 10f) : Math.min(speed, -10f);
			float power = 200*value;
			return power / clampedSpeed;
		case 1:
			float torque = 10*value;
			return torque;
		case 2:
			float targetSpeed = 2*value;
			float stiffness = 0.5f;
			return Mth.clamp(stiffness * (targetSpeed - speed) * getNetworkInertia() / speedMultiplier, -1500f, 1500f);
		}
		return 0f;
	}
	
	@Override
	public boolean shouldCreateNetwork() {
		return true;
	}
	
	class MotorValueBox extends ValueBoxTransform.Sided {

		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, 8, 12.5);
		}

		@Override
		public Vec3 getLocalOffset(BlockState state) {
			Direction facing = state.getValue(CreativeMotorBlock.FACING);
			return super.getLocalOffset(state).add(Vec3.atLowerCornerOf(facing.getNormal())
				.scale(-1 / 16f));
		}

		@Override
		public void rotate(BlockState state, PoseStack ms) {
			super.rotate(state, ms);
			Direction facing = state.getValue(CreativeMotorBlock.FACING);
			if (facing.getAxis() == Axis.Y)
				return;
			if (getSide() != Direction.UP)
				return;
			TransformStack.cast(ms)
				.rotateZ(-AngleHelper.horizontalAngle(facing) + 180);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			Direction facing = state.getValue(CreativeMotorBlock.FACING);
			if (facing.getAxis() != Axis.Y && direction == Direction.DOWN)
				return false;
			return direction.getAxis() != facing.getAxis();
		}

	}

}
