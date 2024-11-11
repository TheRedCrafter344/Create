package com.simibubi.create.content.contraptions.piston;

import java.util.List;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.contraptions.IProvideActorEnergy;
import com.simibubi.create.content.contraptions.IControlContraption.RotationMode;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.utility.Lang;
import com.simibubi.create.foundation.utility.ServerSpeedProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public abstract class LinearActuatorBlockEntity extends KineticBlockEntity
	implements IControlContraption, IDisplayAssemblyExceptions, IProvideActorEnergy {

	public float offset;
	public boolean running;
	public boolean assembleNextTick;
	public boolean needsContraption;
	public ControlledContraptionEntity movedContraption;
	protected boolean forceMove;
	protected AssemblyException lastException;
	protected double sequencedOffsetLimit;

	// Custom position sync
	protected float clientOffsetDiff;

	protected boolean wasStalled = false;
	protected boolean wasCollided = false;
	protected float storedEnergy;
	
	protected boolean wasMinimallyExtended = false;
	protected boolean wasMaximallyExtended = false;
	
	protected int ticksAtZeroSpeed = 0;
	
	public LinearActuatorBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
		setLazyTickRate(3);
		forceMove = true;
		needsContraption = true;
		sequencedOffsetLimit = -1;
	}
	
	@Override
	protected boolean syncSequenceContext() {
		return true;
	}

	@Override
	public void tick() {
		super.tick();

		if (running && hasNetwork() && !level.isClientSide && movedContraption != null) {
			float pistonSpeed = getMovementSpeed();
			if (offset >= getExtensionRange() && !wasMaximallyExtended && pistonSpeed > 0) {
				wasMaximallyExtended = true;
				getOrCreateNetwork().updateEffectiveInertia();
			}
			if (wasMaximallyExtended && (pistonSpeed < 0 || offset < getExtensionRange())) {
				wasMaximallyExtended = false;
				getOrCreateNetwork().stickEffectiveInertia(getContraptionInertia() * speedMultiplier * speedMultiplier);
				getOrCreateNetwork().updateEffectiveInertia();
			}
			if (offset <= 0 && !wasMinimallyExtended && pistonSpeed < 0) {
				wasMinimallyExtended = true;
				getOrCreateNetwork().updateEffectiveInertia();
			}
			if (wasMinimallyExtended && (pistonSpeed > 0 || offset > 0)) {
				wasMinimallyExtended = false;
				getOrCreateNetwork().stickEffectiveInertia(getContraptionInertia() * speedMultiplier * speedMultiplier);
				getOrCreateNetwork().updateEffectiveInertia();
			} 
		}
		
		if (movedContraption != null)
			if (!movedContraption.isAlive())
				movedContraption = null;

		if (isPassive())
			return;
		
		if (level.isClientSide)
			clientOffsetDiff *= .75f;

		if (!level.isClientSide && assembleNextTick) {
			assembleNextTick = false;
			if (running) {
				tryDisassemble();
				return;
			} else {
				try {
					if(assemble() && hasNetwork()) {
						getOrCreateNetwork().stickEffectiveInertia(getContraptionInertia() * speedMultiplier * speedMultiplier);
						getOrCreateNetwork().updateEffectiveInertia();
					}
					lastException = null;
				} catch (AssemblyException e) {
					lastException = e;
				}
				sendData();
				return;
			}
		}

		if (!running)
			return;

		boolean contraptionPresent = movedContraption != null;
		if (needsContraption && !contraptionPresent)
			return;

		float movementSpeed = getMovementSpeed();
		
		if((Math.abs(movementSpeed) < 0.01 || wasMaximallyExtended || wasMinimallyExtended) && !movedContraption.isStalled()) {
			if(getMovementMode() == MovementMode.MOVE_PLACE_STATIC
					|| getMovementMode() == MovementMode.MOVE_PLACE_STATIC_RETURNED && Math.abs(offset - getInitialOffset()) <= 0.5) {
				if(ticksAtZeroSpeed >= 5) { //TODO config
					if(movedContraption != null) movedContraption.getContraption().stop(level);
					tryDisassemble();
					return;
				}
				ticksAtZeroSpeed++;
			}
		} else {
			ticksAtZeroSpeed = 0;
		}
		
		boolean locked = false;
		if (sequencedOffsetLimit > 0) {
			sequencedOffsetLimit = Math.max(0, sequencedOffsetLimit - Math.abs(movementSpeed));
			locked = sequencedOffsetLimit == 0;
		}
		float newOffset = offset + movementSpeed;
		if ((int) newOffset != (int) offset)
			visitNewPosition();

		if (locked) {
			forceMove = true;
			resetContraptionToOffset();
			sendData();
		}
		
		if (contraptionPresent) {
			if (moveAndCollideContraption()) {
				movedContraption.setContraptionMotion(Vec3.ZERO);
				//offset = getGridOffset(offset);
				//resetContraptionToOffset();
				collided();
				return;
			} else if(!level.isClientSide) {
				if(wasCollided) {
					wasCollided = false;
					if(hasNetwork()) {
						KineticNetwork net = getOrCreateNetwork();
						net.stickEffectiveInertia(getContraptionInertia() * speedMultiplier * speedMultiplier);
						net.updateEffectiveInertia();
						net.setSpeed( (float) (Math.signum(net.getSpeed()) * Math.sqrt(net.getSpeed() * net.getSpeed() + 2 * storedEnergy / net.getEffectiveInertia())));
						storedEnergy = 0;
					}
				}
				if(!movedContraption.isStalled() && wasStalled) {
					wasStalled = false;
					if(hasNetwork()) {
						KineticNetwork net = getOrCreateNetwork();
						net.stickEffectiveInertia(getContraptionInertia() * speedMultiplier * speedMultiplier);
						net.updateEffectiveInertia();
						net.setSpeed( (float) (Math.signum(net.getSpeed()) * Math.sqrt(net.getSpeed() * net.getSpeed() + 2 * storedEnergy / net.getEffectiveInertia())));
						storedEnergy = 0;
					}
				}
			}
		}

		if (!contraptionPresent || !movedContraption.isStalled())
			offset = newOffset;

		int extensionRange = getExtensionRange();
		if (offset <= 0 || offset >= extensionRange) {
			offset = offset <= 0 ? 0 : extensionRange;
			if (!level.isClientSide) {
				moveAndCollideContraption();
				resetContraptionToOffset();
				if(getMovementMode() == MovementMode.MOVE_PLACE || (getMovementMode() == MovementMode.MOVE_PLACE_RETURNED && (int) (offset + .5f) == getInitialOffset()))
					tryDisassemble();
			}
			return;
		}
	}

	protected boolean isPassive() {
		return false;
	}
	
	@Override
	public void lazyTick() {
		super.lazyTick();
		if (movedContraption != null && !level.isClientSide)
			sendData();
	}

	protected int getGridOffset(float offset) {
		return Mth.clamp((int) (offset + .5f), 0, getExtensionRange());
	}

	public float getInterpolatedOffset(float partialTicks) {
		float interpolatedOffset =
			Mth.clamp(offset + (partialTicks - .5f) * getMovementSpeed(), 0, getExtensionRange());
		return interpolatedOffset;
	}

	@Override
	public void remove() {
		this.remove = true;
		if (!level.isClientSide)
			tryDisassemble();
		super.remove();
	}

	@Override
	protected void write(CompoundTag compound, boolean clientPacket) {
		compound.putBoolean("Running", running);
		compound.putFloat("Offset", offset);
		compound.putFloat("StoredEnergy", storedEnergy);
		if (sequencedOffsetLimit >= 0)
			compound.putDouble("SequencedOffsetLimit", sequencedOffsetLimit);
		AssemblyException.write(compound, lastException);
		super.write(compound, clientPacket);

		if (clientPacket && forceMove) {
			compound.putBoolean("ForceMovement", forceMove);
			forceMove = false;
		}
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		boolean forceMovement = compound.contains("ForceMovement");
		float offsetBefore = offset;

		running = compound.getBoolean("Running");
		offset = compound.getFloat("Offset");
		storedEnergy = compound.getFloat("StoredEnergy");
		sequencedOffsetLimit =
			compound.contains("SequencedOffsetLimit") ? compound.getDouble("SequencedOffsetLimit") : -1;
		lastException = AssemblyException.read(compound);
		super.read(compound, clientPacket);

		if (!clientPacket)
			return;
		if (forceMovement)
			resetContraptionToOffset();
		else if (running) {
			clientOffsetDiff = offset - offsetBefore;
			offset = offsetBefore;
		}
		if (!running)
			movedContraption = null;
	}

	@Override
	public AssemblyException getLastAssemblyException() {
		return lastException;
	}

	public abstract void disassemble();

	protected abstract boolean assemble() throws AssemblyException;

	protected abstract int getExtensionRange();

	protected abstract int getInitialOffset();
	
	protected abstract Vec3 toMotionVector(float speed);

	protected abstract Vec3 toPosition(float offset);

	protected void visitNewPosition() {}

	protected void tryDisassemble() {
		/*
		if (remove) {
			disassemble();
			if(hasNetwork()) getOrCreateNetwork().updateEffectiveInertia();
			return true;
		}
		
		if (getMovementMode() == MovementMode.MOVE_NEVER_PLACE) {
			waitingForSpeedChange = true;
			return false;
		}
		int initial = getInitialOffset();
		if ((int) (offset + .5f) != initial && getMovementMode() == MovementMode.MOVE_PLACE_RETURNED) {
			waitingForSpeedChange = true;
			return false;
		}
		*/
		disassemble();
		if(hasNetwork()) getOrCreateNetwork().updateEffectiveInertia();
	}

	protected boolean moveAndCollideContraption() {
		if (movedContraption == null)
			return false;
		if (movedContraption.isStalled()) {
			movedContraption.setContraptionMotion(Vec3.ZERO);
			return false;
		}
		Vec3 motion = getMotionVector();
		movedContraption.setContraptionMotion(getMotionVector());
		movedContraption.move(motion.x, motion.y, motion.z);
		return ContraptionCollider.collideBlocks(movedContraption);
	}

	protected void collided() {
		offset = getGridOffset(offset - getMovementSpeed());
		resetContraptionToOffset();
		if (level.isClientSide) {
			return;
		}
		if(getMovementMode() == MovementMode.MOVE_PLACE || (getMovementMode() == MovementMode.MOVE_PLACE_RETURNED && (int) (offset + .5f) == getInitialOffset()))
			tryDisassemble();
		else if(!wasCollided) {
			wasCollided = true;
			if(hasNetwork()) getOrCreateNetwork().updateEffectiveInertia();
			storedEnergy += 0.5f * getContraptionInertia() * getSpeed() * getSpeed();
		}
	}

	protected void resetContraptionToOffset() {
		if (movedContraption == null)
			return;
		if (!movedContraption.isAlive())
			return;
		Vec3 vec = toPosition(offset);
		movedContraption.setPos(vec.x, vec.y, vec.z);
		if (getSpeed() == 0)
			movedContraption.setContraptionMotion(Vec3.ZERO);
	}

	public float getMovementSpeed() {
		float movementSpeed = convertToLinear(getSpeed()) + clientOffsetDiff / 2f;
		if (level.isClientSide)
			movementSpeed *= ServerSpeedProvider.get();
		if (sequencedOffsetLimit >= 0)
			movementSpeed = (float) Mth.clamp(movementSpeed, -sequencedOffsetLimit, sequencedOffsetLimit);
		return movementSpeed;
	}
	
	public Vec3 getMotionVector() {
		return toMotionVector(getMovementSpeed());
	}

	@Override
	public void onStall() {
		if (!level.isClientSide) {
			forceMove = true;
			wasStalled = true;
			if(hasNetwork()) getOrCreateNetwork().updateEffectiveInertia();
			storedEnergy += 0.5f * getContraptionInertia() * getSpeed() * getSpeed();
			sendData();
		}
	}

	public void onLengthBroken() {
		offset = 0;
		sendData();
	}

	@Override
	public boolean isValid() {
		return !isRemoved();
	}

	@Override
	public void attach(ControlledContraptionEntity contraption) {
		this.movedContraption = contraption;
		if (!level.isClientSide) {
			this.running = true;
			if (hasNetwork()) {
				getOrCreateNetwork().stickEffectiveInertia(getContraptionInertia() * speedMultiplier * speedMultiplier);
				getOrCreateNetwork().updateEffectiveInertia();
			}
			sendData();
		}
	}

	@Override
	public boolean isAttachedTo(AbstractContraptionEntity contraption) {
		return movedContraption == contraption;
	}

	@Override
	public BlockPos getBlockPosition() {
		return worldPosition;
	}
	
	public abstract float getContraptionInertia();
	
	@Override
	public float getInertia() {
		return super.getInertia() + 
				(running && movedContraption != null && !wasStalled && !wasCollided && !wasMaximallyExtended && !wasMinimallyExtended ? getContraptionInertia() : 0);
	}
	
	@Override
	public float getStoredEnergy() {
		return storedEnergy;
	}
	
	@Override
	public void setStoredEnergy(float energy) {
		this.storedEnergy = energy;
	}
	
	@Override
	//returns how much energy was actually pulled
	public float pullStoredEnergy(float pullEnergy, boolean pullFromNetwork) {
		if(storedEnergy >= pullEnergy) {
			storedEnergy -= pullEnergy;
			return pullEnergy;
		} 
		if(!hasNetwork() || !pullFromNetwork || !pullsEnergyFromNetwork()) {
			float pulled = storedEnergy;
			storedEnergy = 0;
			return pulled;
		}
		KineticNetwork net = getOrCreateNetwork();
		float networkEnergy = 0.5f * net.getEffectiveInertia() * net.getSpeed() * net.getSpeed();
		if(storedEnergy + networkEnergy >= pullEnergy) {
			float pulledFromNetwork = pullEnergy - storedEnergy;
			storedEnergy = 0;
			net.setSpeed((float)(Math.signum(net.getSpeed()) * Math.sqrt(net.getSpeed() * net.getSpeed() - 2 * pulledFromNetwork / net.getEffectiveInertia())));
			return pullEnergy;
		} else {
			float pulled = storedEnergy + networkEnergy;
			storedEnergy = 0;
			net.setSpeed(0);
			return pulled;
		}
	}
	
	@Override
	public boolean shouldCreateNetwork() {
		return true;
	}
	
	public Vec3 smallG() {
		return new Vec3(0, -10, 0); //TODO rotate this if we are in a rotated grid, also per-dimension config and shit
	}
	
	protected abstract MovementMode getMovementMode();
	
	public boolean pullsEnergyFromNetwork() {
		return true;
	}
	
	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isSneaking) {
		if(network == null) {
			Lang.text("Not in network").forGoggles(tooltip);
		} else {
			Lang.text("Network ID: " + network).forGoggles(tooltip);
			Lang.text("Speed: " + getSpeed()).forGoggles(tooltip);
			Lang.text("Speed Multiplier: " + speedMultiplier).forGoggles(tooltip);
			Lang.text("Network Effective Inertia: " + getOrCreateClientNetwork().getEffectiveInertia()).forGoggles(tooltip);
			Lang.text("Stored Energy: " + storedEnergy).forGoggles(tooltip);
		}
		return true;
	}
}
