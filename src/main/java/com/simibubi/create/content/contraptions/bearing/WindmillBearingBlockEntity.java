package com.simibubi.create.content.contraptions.bearing;

import java.util.List;

import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.Lang;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class WindmillBearingBlockEntity extends MechanicalBearingBlockEntity {

	protected ScrollOptionBehaviour<RotationDirection> movementDirection;
	protected float lastGeneratedSpeed;

	protected boolean queuedReassembly;

	public WindmillBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void onSpeedChanged(float prevSpeed) {
		boolean cancelAssembly = assembleNextTick;
		super.onSpeedChanged(prevSpeed);
		assembleNextTick = cancelAssembly;
	}

	@Override
	public void tick() {
		super.tick();
		if (level.isClientSide())
			return;
		if (!queuedReassembly)
			return;
		queuedReassembly = false;
		if (!running)
			assembleNextTick = true;
	}
	
	public void disassembleForMovement() {
		if (!running)
			return;
		disassemble();
		queuedReassembly = true;
	}

	public float getGeneratedSpeed() {
		if (!running)
			return 0;
		if (movedContraption == null)
			return lastGeneratedSpeed;
		int sails = ((BearingContraption) movedContraption.getContraption()).getSailBlocks()
			/ AllConfigs.server().kinetics.windmillSailsPerRPM.get();
		return Mth.clamp(sails, 1, 16) * getAngleSpeedDirection();
	}
	
	private static final float GENERATED_POWER = 100f; // EU/s (PU)
	private static final float MAX_REMOVED_POWER = -100f;
	private static final float POWER_LOSS_AT = 0.8f; // sail rpm / target rpm point where power loss starts. power generated will be 0 when that ratio is 1.
	
	@Override
	public boolean shouldCreateNetwork() {
		return true;
	}
	
	@Override
	public float getTorque(float speed) {
		int sails = ((BearingContraption) movedContraption.getContraption()).getSailBlocks();
		if(sails == 0) return super.getTorque(speed);
		float targetRPM = Math.min(sails, 64) * 2;
		float generatedPower = GENERATED_POWER * sails;
		float powerLossAt = targetRPM * Mth.clamp(POWER_LOSS_AT, 0, 0.9f);
		float power;
		speed = speed > 0 ? Math.max(speed, 1)
				  		  : Math.min(-speed, -1);
		if(speed * targetRPM < 0) {
			power = MAX_REMOVED_POWER;
		} else if(Math.abs(speed) < Math.abs(powerLossAt)) {
			power = generatedPower;
		} else {
			float slope = generatedPower / (targetRPM - powerLossAt);
			power = Math.max(generatedPower - slope * (speed - powerLossAt), MAX_REMOVED_POWER);
		}
		return power / speed + super.getTorque(speed);
	}

	@Override
	protected boolean isWindmill() {
		return true;
	}

	protected int getAngleSpeedDirection() {
		RotationDirection rotationDirection = RotationDirection.values()[movementDirection.getValue()];
		return (rotationDirection == RotationDirection.CLOCKWISE ? 1 : -1);
	}

	@Override
	public void write(CompoundTag compound, boolean clientPacket) {
		compound.putFloat("LastGenerated", lastGeneratedSpeed);
		compound.putBoolean("QueueAssembly", queuedReassembly);
		super.write(compound, clientPacket);
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		if (!wasMoved)
			lastGeneratedSpeed = compound.getFloat("LastGenerated");
		queuedReassembly = compound.getBoolean("QueueAssembly");
		super.read(compound, clientPacket);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		behaviours.remove(movementMode);
		movementDirection = new ScrollOptionBehaviour<>(RotationDirection.class,
			Lang.translateDirect("contraptions.windmill.rotation_direction"), this, getMovementModeSlot());
		behaviours.add(movementDirection);
		registerAwardables(behaviours, AllAdvancements.WINDMILL, AllAdvancements.WINDMILL_MAXED);
	}

	@Override
	public boolean isWoodenTop() {
		return true;
	}

	public static enum RotationDirection implements INamedIconOptions {

		CLOCKWISE(AllIcons.I_REFRESH), COUNTER_CLOCKWISE(AllIcons.I_ROTATE_CCW),

		;

		private String translationKey;
		private AllIcons icon;

		private RotationDirection(AllIcons icon) {
			this.icon = icon;
			translationKey = "generic." + Lang.asId(name());
		}

		@Override
		public AllIcons getIcon() {
			return icon;
		}

		@Override
		public String getTranslationKey() {
			return translationKey;
		}

	}

}
