package com.simibubi.create.content.contraptions.piston;

import java.util.List;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.contraptions.IProvideActorEnergy;
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
	protected ScrollOptionBehaviour<MovementMode> movementMode;
	protected boolean waitingForSpeedChange;
	protected AssemblyException lastException;
	protected double sequencedOffsetLimit;

	// Custom position sync
	protected float clientOffsetDiff;

	protected boolean wasStalled = false;
	protected float storedEnergy;
	
	protected boolean wasMinimallyExtended = false;
	protected boolean wasMaximallyExtended = false;
	
	public LinearActuatorBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
		setLazyTickRate(3);
		forceMove = true;
		needsContraption = true;
		sequencedOffsetLimit = -1;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		movementMode = new ScrollOptionBehaviour<>(MovementMode.class, Lang.translateDirect("contraptions.movement_mode"),
			this, getMovementModeSlot());
		movementMode.withCallback(t -> waitingForSpeedChange = false);
		behaviours.add(movementMode);
		registerAwardables(behaviours, AllAdvancements.CONTRAPTION_ACTORS);
	}
	
	@Override
	protected boolean syncSequenceContext() {
		return true;
	}

	@Override
	public void tick() {
		super.tick();

		if (running && hasNetwork() && !level.isClientSide && movedContraption != null) {
			Direction pistonDirection = getBlockState().getValue(BlockStateProperties.FACING);
			int movementModifier = pistonDirection.getAxisDirection().getStep()
					* (pistonDirection.getAxis() == Axis.Z ? -1 : 1);
			float pistonSpeed = getSpeed() * -movementModifier;
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

		if (waitingForSpeedChange) {
			if (movedContraption != null) {
				if (level.isClientSide) {
					float syncSpeed = clientOffsetDiff / 2f;
					offset += syncSpeed;
					movedContraption.setContraptionMotion(toMotionVector(syncSpeed));
					return;
				}
				movedContraption.setContraptionMotion(Vec3.ZERO);
			}
			return;
		}

		if (!level.isClientSide && assembleNextTick) {
			assembleNextTick = false;
			if (running) {
				if (getSpeed() == 0) {
					tryDisassemble();
					return;
				} else
					sendData();
			} else {
				if (getSpeed() != 0)
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
				offset = getGridOffset(offset);
				resetContraptionToOffset();
				collided();
				return;
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
				tryDisassemble();
				if (waitingForSpeedChange) {
					forceMove = true;
					sendData();
				}
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
	public void onSpeedChanged(float prevSpeed) {
		super.onSpeedChanged(prevSpeed);
		sequencedOffsetLimit = -1;
		
		if (isPassive())
			return;
		
		assembleNextTick = true;
		waitingForSpeedChange = false;

		if (movedContraption != null && Math.signum(prevSpeed) != Math.signum(getSpeed()) && prevSpeed != 0) {
			if (!movedContraption.isStalled()) {
				offset = Math.round(offset * 16) / 16;
				resetContraptionToOffset();
			}
			movedContraption.getContraption()
				.stop(level);
		}

		//if (sequenceContext != null && sequenceContext.instruction() == SequencerInstructions.TURN_DISTANCE)
		//	sequencedOffsetLimit = sequenceContext.getEffectiveValue(getTheoreticalSpeed());
	}
	

	@Override
	public void remove() {
		this.remove = true;
		if (!level.isClientSide)
			disassemble();
		super.remove();
	}

	@Override
	protected void write(CompoundTag compound, boolean clientPacket) {
		compound.putBoolean("Running", running);
		compound.putBoolean("Waiting", waitingForSpeedChange);
		compound.putFloat("Offset", offset);
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
		waitingForSpeedChange = compound.getBoolean("Waiting");
		offset = compound.getFloat("Offset");
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

	protected abstract ValueBoxTransform getMovementModeSlot();

	protected abstract Vec3 toMotionVector(float speed);

	protected abstract Vec3 toPosition(float offset);

	protected void visitNewPosition() {}

	protected boolean tryDisassemble() {
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
		disassemble();
		if(hasNetwork()) getOrCreateNetwork().updateEffectiveInertia();
		return true;
	}

	protected MovementMode getMovementMode() {
		return movementMode.get();
	}

	protected boolean moveAndCollideContraption() {
		if (movedContraption == null)
			return false;
		if (movedContraption.isStalled()) {
			movedContraption.setContraptionMotion(Vec3.ZERO);
			return false;
		}
		if(!level.isClientSide && wasStalled) {
			wasStalled = false;
			if(hasNetwork()) {
				KineticNetwork net = getOrCreateNetwork();
				net.stickEffectiveInertia(getContraptionInertia() * speedMultiplier * speedMultiplier);
				net.updateEffectiveInertia();
				net.setSpeed( (float) (Math.signum(net.getSpeed()) * Math.sqrt(net.getSpeed() * net.getSpeed() + 2 * storedEnergy / net.getEffectiveInertia())));
				storedEnergy = 0;
			}
		}
		Vec3 motion = getMotionVector();
		movedContraption.setContraptionMotion(getMotionVector());
		movedContraption.move(motion.x, motion.y, motion.z);
		return ContraptionCollider.collideBlocks(movedContraption);
	}

	protected void collided() {
		if (level.isClientSide) {
			waitingForSpeedChange = true;
			return;
		}
		offset = getGridOffset(offset - getMovementSpeed());
		resetContraptionToOffset();
		tryDisassemble();
	}

	protected void resetContraptionToOffset() {
		if (movedContraption == null)
			return;
		if (!movedContraption.isAlive())
			return;
		Vec3 vec = toPosition(offset);
		movedContraption.setPos(vec.x, vec.y, vec.z);
		if (getSpeed() == 0 || waitingForSpeedChange)
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
			sendData();
			wasStalled = true;
			if(hasNetwork()) getOrCreateNetwork().updateEffectiveInertia();
			storedEnergy += 0.5f * getContraptionInertia() * getSpeed() * getSpeed();
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
		return super.getInertia() + (running && movedContraption != null && !wasStalled && !wasMaximallyExtended && !wasMinimallyExtended ? getContraptionInertia() : 0);
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
		if(!hasNetwork() || !pullFromNetwork) {
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
}
