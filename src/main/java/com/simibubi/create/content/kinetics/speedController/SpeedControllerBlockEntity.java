package com.simibubi.create.content.kinetics.speedController;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.compat.computercraft.AbstractComputerBehaviour;
import com.simibubi.create.compat.computercraft.ComputerCraftProxy;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.KineticScrollValueBehaviour;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.utility.Lang;
import com.simibubi.create.foundation.utility.VecHelper;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

public class SpeedControllerBlockEntity extends KineticBlockEntity {

	public static final int DEFAULT_SPEED = 16;
	public ScrollValueBehaviour targetSpeed;
	public AbstractComputerBehaviour computerBehaviour;

	boolean hasBracket;

	public static final float MIN_MODIFIER = 0.1f;
	public static final float MAX_MODIFIER = 10f;
	private float modifier = 1;
	
	public SpeedControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		hasBracket = false;
	}

	@Override
	public void tick() {
		if (hasNetwork() && !level.isClientSide) {
			float targetSpeed = this.targetSpeed.getValue();
			float prevModifier = modifier;
			float newModifier = targetSpeed / getSpeed();
			if (newModifier > 0) {
				newModifier = Mth.clamp(newModifier, MIN_MODIFIER, MAX_MODIFIER);
			} else {
				newModifier = Mth.clamp(newModifier, -MAX_MODIFIER, -MIN_MODIFIER);
			}
			Create.LOGGER.info("prev: " + prevModifier);
			Create.LOGGER.info("new: " + newModifier);
			if (Math.abs(prevModifier - newModifier) > 0.01f) {
				modifier = newModifier;
				detachKinetics();
				attachKinetics();
				
				Create.LOGGER.info("nigga");
			} 
		}
		super.tick();
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		updateBracket();
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		Integer max = AllConfigs.server().kinetics.maxRotationSpeed.get();

		targetSpeed = new KineticScrollValueBehaviour(Lang.translateDirect("kinetics.speed_controller.rotation_speed"),
			this, new ControllerValueBoxTransform());
		targetSpeed.between(-max, max);
		targetSpeed.value = DEFAULT_SPEED;
		behaviours.add(targetSpeed);
		behaviours.add(computerBehaviour = ComputerCraftProxy.behaviour(this));

		registerAwardables(behaviours, AllAdvancements.SPEED_CONTROLLER);
	}

	public void updateBracket() {
		if (level != null && level.isClientSide)
			hasBracket = isCogwheelPresent();
	}

	private boolean isCogwheelPresent() {
		BlockState stateAbove = level.getBlockState(worldPosition.above());
		return ICogWheel.isDedicatedCogWheel(stateAbove.getBlock()) && ICogWheel.isLargeCog(stateAbove)
			&& stateAbove.getValue(CogWheelBlock.AXIS)
				.isHorizontal();
	}

	@NotNull
	@Override
	public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
		if (computerBehaviour.isPeripheralCap(cap))
			return computerBehaviour.getPeripheralCapability();
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		computerBehaviour.removePeripheral();
	}

	@Override
	public boolean isCustomConnection(KineticBlockEntity other, BlockState from, BlockState to) {
		if (!ICogWheel.isLargeCog(to) || !AllBlocks.ROTATION_SPEED_CONTROLLER.has(from))
			return false;
		if (!other.getBlockPos().equals(getBlockPos().above()))
			return false;
		Axis axis = to.getValue(CogWheelBlock.AXIS);
		if (axis.isVertical())
			return false;
		if (from.getValue(SpeedControllerBlock.HORIZONTAL_AXIS) == axis)
			return false;
		return true;
	}
	
	@Override
	public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs) {
		if(isCustomConnection(target, stateFrom, stateTo)) return modifier;
		return 0;
	}
	
	private class ControllerValueBoxTransform extends ValueBoxTransform.Sided {

		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, 11f, 15.5f);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			if (direction.getAxis()
				.isVertical())
				return false;
			return state.getValue(SpeedControllerBlock.HORIZONTAL_AXIS) != direction.getAxis();
		}

		@Override
		public float getScale() {
			return 0.5f;
		}

	}

}
