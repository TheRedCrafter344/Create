package com.simibubi.create.content.contraptions.bearing;

import java.util.List;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.contraptions.IProvideActorEnergy;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.AngleHelper;
import com.simibubi.create.foundation.utility.Lang;
import com.simibubi.create.foundation.utility.ServerSpeedProvider;
import com.simibubi.create.foundation.utility.VecHelper;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;

public class MechanicalBearingBlockEntity extends KineticBlockEntity
	implements IBearingBlockEntity, IDisplayAssemblyExceptions, IProvideActorEnergy {

	protected ScrollOptionBehaviour<RotationMode> movementMode;
	
	protected ControlledContraptionEntity movedContraption;
	protected float angle;
	protected boolean running;
	protected boolean assembleNextTick;
	protected float clientAngleDiff;
	protected AssemblyException lastException;
	protected double sequencedAngleLimit;

	private float prevAngle;

	private boolean wasStalled = false;
	
	private float storedEnergy = 0;
	
	private byte ticksAtZeroSpeed = 0;
	
	public MechanicalBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		setLazyTickRate(3);
		sequencedAngleLimit = -1;
	}

	@Override
	public boolean isWoodenTop() {
		return false;
	}

	@Override
	protected boolean syncSequenceContext() {
		return true;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		movementMode = new ScrollOptionBehaviour<>(RotationMode.class, Lang.translateDirect("contraptions.movement_mode"), this, getMovementModeSlot());
		behaviours.add(movementMode);
		registerAwardables(behaviours, AllAdvancements.CONTRAPTION_ACTORS);
	}

	@Override
	public void remove() {
		if (!level.isClientSide)
			disassemble();
		super.remove();
	}

	@Override
	public void write(CompoundTag compound, boolean clientPacket) {
		compound.putBoolean("Running", running);
		compound.putFloat("Angle", angle);
		compound.putFloat("StoredEnergy", storedEnergy);
		if (sequencedAngleLimit >= 0)
			compound.putDouble("SequencedAngleLimit", sequencedAngleLimit);
		AssemblyException.write(compound, lastException);
		super.write(compound, clientPacket);
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		if (wasMoved) {
			super.read(compound, clientPacket);
			return;
		}

		float angleBefore = angle;
		running = compound.getBoolean("Running");
		angle = compound.getFloat("Angle");
		storedEnergy = compound.getFloat("StoredEnergy");
		sequencedAngleLimit = compound.contains("SequencedAngleLimit") ? compound.getDouble("SequencedAngleLimit") : -1;
		lastException = AssemblyException.read(compound);
		super.read(compound, clientPacket);
		if (!clientPacket)
			return;
		if (running) {
			if (movedContraption == null || !movedContraption.isStalled()) {
				clientAngleDiff = AngleHelper.getShortestAngleDiff(angleBefore, angle);
				angle = angleBefore;
			}
		} else
			movedContraption = null;
	}

	@Override
	public float getInterpolatedAngle(float partialTicks) {
		if (isVirtual())
			return Mth.lerp(partialTicks + .5f, prevAngle, angle);
		if (movedContraption == null || movedContraption.isStalled() || !running)
			partialTicks = 0;
		float angularSpeed = getAngularSpeed();
		if (sequencedAngleLimit >= 0)
			angularSpeed = (float) Mth.clamp(angularSpeed, -sequencedAngleLimit, sequencedAngleLimit);
		return Mth.lerp(partialTicks, angle, angle + angularSpeed);
	}

	@Override
	public void onSpeedChanged(float prevSpeed) {
		super.onSpeedChanged(prevSpeed);
		sequencedAngleLimit = -1;

		if (movedContraption != null && Math.signum(prevSpeed) != Math.signum(getSpeed()) && prevSpeed != 0) {
			if (!movedContraption.isStalled()) {
				angle = Math.round(angle);
				applyRotation();
				if(movementMode.get() == RotationMode.ROTATE_PLACE
						|| movementMode.get() == RotationMode.ROTATE_PLACE_RETURNED && isNearInitialAngle()) {
					movedContraption.getContraption().stop(level);
					disassemble();
					return;
				}
			}
			movedContraption.getContraption()
				.stop(level);
		}

		//if (!isWindmill() && sequenceContext != null
		//	&& sequenceContext.instruction() == SequencerInstructions.TURN_ANGLE)
		//	sequencedAngleLimit = sequenceContext.getEffectiveValue(getTheoreticalSpeed());
	}

	public boolean isNearInitialAngle() {
		return Math.abs(angle) < 10 || Math.abs(angle) > 360 - 10; //TODO make config option
	}

	public float getAngularSpeed() {
		float speed = convertToAngular(getSpeed());
		if (getSpeed() == 0)
			speed = 0;
		if (level.isClientSide) {
			speed *= ServerSpeedProvider.get();
			speed += clientAngleDiff / 3f;
		}
		return speed;
	}

	@Override
	public AssemblyException getLastAssemblyException() {
		return lastException;
	}

	protected boolean isWindmill() {
		return false;
	}

	@Override
	public BlockPos getBlockPosition() {
		return worldPosition;
	}

	public void assemble() {
		if (!(level.getBlockState(worldPosition)
			.getBlock() instanceof BearingBlock))
			return;

		Direction direction = getBlockState().getValue(BearingBlock.FACING);
		BearingContraption contraption = new BearingContraption(isWindmill(), direction);
		try {
			if (!contraption.assemble(level, worldPosition))
				return;

			lastException = null;
		} catch (AssemblyException e) {
			lastException = e;
			sendData();
			return;
		}

		if (isWindmill())
			award(AllAdvancements.WINDMILL);
		if (contraption.getSailBlocks() >= 16 * 8)
			award(AllAdvancements.WINDMILL_MAXED);

		contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
		movedContraption = ControlledContraptionEntity.create(level, this, contraption);
		BlockPos anchor = worldPosition.relative(direction);
		movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
		movedContraption.setRotationAxis(direction.getAxis());
		level.addFreshEntity(movedContraption);

		AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, worldPosition);

		if (contraption.containsBlockBreakers())
			award(AllAdvancements.CONTRAPTION_ACTORS);

		running = true;
		if (hasNetwork()) {
			getOrCreateNetwork().stickEffectiveInertia(getContraptionInertia() * speedMultiplier * speedMultiplier);
			getOrCreateNetwork().updateEffectiveInertia();
		}
		angle = 0;
		sendData();
	}

	public void disassemble() {
		if (!running && movedContraption == null)
			return;
		
		if (isWindmill())
			applyRotation();
		
		if (movedContraption != null) {
			
			Axis axis = movedContraption.getRotationAxis();
			
			//dissassembles at the position with lower potential energy to prevent infinite energy hack 100% real
			//only use that logic if gravity is relevant, and if the angle isnt too close to a disassembly position
			while(angle < 0) angle += 360;
			while(angle > 360) angle -= 360;
			if(VecHelper.getAxisVector(axis).cross(smallG()).lengthSqr() > 0.001 &&
					90 - (angle % 90) > 1 && angle % 90 > 1) {
				Vec3 sDiff = null;
				int quadrant = VecHelper.getQuadrant(centerOfMass(), axis);
				switch(quadrant) {
				case 1:
					sDiff =
					axis == Axis.X ? new Vec3(0, -1, 1) :
					axis == Axis.Y ? new Vec3(1, 0, -1) :
									 new Vec3(-1, 1, 0);
					break;
				case 2:
					sDiff =
					axis == Axis.X ? new Vec3(0, -1, -1) :
					axis == Axis.Y ? new Vec3(-1, 0, -1) :
									 new Vec3(-1, -1, 0);
					break;
				case 3:
					sDiff =
					axis == Axis.X ? new Vec3(0, 1, -1) :
					axis == Axis.Y ? new Vec3(-1, 0, 1) :
									 new Vec3(1, -1, 0);
					break;
				case 4:
					sDiff =
					axis == Axis.X ? new Vec3(0, 1, 1) :
					axis == Axis.Y ? new Vec3(1, 0, 1) :
									 new Vec3(1, 1, 0);
					break;
				}
				if(sDiff != null && sDiff.dot(smallG()) != 0) {
					boolean useCeilAngle = sDiff.dot(smallG()) > 0;
					angle = (int)angle / 90 + (useCeilAngle ? 1 : 0);
					Rotation rot =
							angle == 1 ? Rotation.COUNTERCLOCKWISE_90 :
							angle == 2 ? Rotation.CLOCKWISE_180 :
							angle == 3 ? Rotation.CLOCKWISE_90 :
							Rotation.NONE;
					BlockPos offset = BlockPos.containing(movedContraption.getAnchorVec().add(.5, .5, .5));
					StructureTransform transform = new StructureTransform(offset, axis, rot, Mirror.NONE);
					movedContraption.disassemble(transform);
				} else {
					movedContraption.disassemble();
				}
			} else {
				movedContraption.disassemble();
			}
			AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(level, worldPosition);
		}
		
		angle = 0;
		sequencedAngleLimit = -1;
		movedContraption = null;
		running = false;
		if(hasNetwork()) getOrCreateNetwork().updateEffectiveInertia();
		assembleNextTick = false;
		sendData();
	}

	@Override
	public void tick() {
		super.tick();

		if(running && Math.abs(getSpeed()) < 0.01f && !(movedContraption != null && movedContraption.isStalled())) {
			if(movementMode.get() == RotationMode.ROTATE_PLACE_STATIC
					|| movementMode.get() == RotationMode.ROTATE_PLACE_STATIC_RETURNED && isNearInitialAngle()) {
				if(ticksAtZeroSpeed >= 5) { //TODO config
					if(movedContraption != null) movedContraption.getContraption().stop(level);
					disassemble();
					return;
				}
				ticksAtZeroSpeed++;
			}
		} else {
			ticksAtZeroSpeed = 0;
		}
		
		prevAngle = angle;
		if (level.isClientSide)
			clientAngleDiff /= 2;

		
		if (!level.isClientSide && assembleNextTick) {
			assembleNextTick = false;
			if (running) {
				if (movedContraption != null)
					movedContraption.getContraption().stop(level);
				disassemble();
				return;
			} else {
				assemble();
			}
		}

		if (!running)
			return;

		if(movedContraption == null) return;
		
		if (!movedContraption.isStalled()) {
			float angularSpeed = getAngularSpeed();
			if (sequencedAngleLimit >= 0) {
				angularSpeed = (float) Mth.clamp(angularSpeed, -sequencedAngleLimit, sequencedAngleLimit);
				sequencedAngleLimit = Math.max(0, sequencedAngleLimit - Math.abs(angularSpeed));
			}
			float newAngle = angle + angularSpeed;
			angle = (float) (newAngle % 360);
			if(!level.isClientSide() && wasStalled) {
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

		applyRotation(); 
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		if (movedContraption != null && !level.isClientSide)
			sendData();
	}

	protected void applyRotation() {
		if (movedContraption == null)
			return;	
		movedContraption.setAngle(angle);
		BlockState blockState = getBlockState();
		if (blockState.hasProperty(BlockStateProperties.FACING))
			movedContraption.setRotationAxis(blockState.getValue(BlockStateProperties.FACING)
				.getAxis());
	}

	@Override
	public void attach(ControlledContraptionEntity contraption) {
		BlockState blockState = getBlockState();
		if (!(contraption.getContraption() instanceof BearingContraption))
			return;
		if (!blockState.hasProperty(BearingBlock.FACING))
			return;

		this.movedContraption = contraption;
		setChanged();
		BlockPos anchor = worldPosition.relative(blockState.getValue(BearingBlock.FACING));
		movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
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
	public float getInertia() {
		return super.getInertia() + (running && movedContraption != null && !wasStalled ? getContraptionInertia() : 0);
	}
	
	public float getContraptionInertia() {
		float inertia = 0;
		Axis axis = movedContraption.getRotationAxis();
		for(StructureBlockInfo sblock : movedContraption.getContraption().getBlocks().values()) {
			float rSquared = sblock.pos().getX() * sblock.pos().getX() * (axis == Axis.X ? 0 : 1)
					+ sblock.pos().getY() * sblock.pos().getY() * (axis == Axis.Y ? 0 : 1)
					+ sblock.pos().getZ() * sblock.pos().getZ() * (axis == Axis.Z ? 0 : 1);
			float mass = 60; //TODO
			if(sblock.state().getBlock() instanceof KineticBlock) {
				mass = 6 * AllConfigs.server().kinetics.getInertia((KineticBlock) sblock.state().getBlock());
			}
			inertia += (rSquared + 1f/6f) * mass;
		}
		return inertia;
	}
	
	public Vec3 smallG() {
		return new Vec3(0, -10, 0); //TODO rotate this if we are on rotated grid
	}
	
	public Vec3 centerOfMass() {
		if(!running || movedContraption == null) return null;
		float totalMass = 0;
		float comX = 0, comY = 0, comZ = 0;
		Axis axis = movedContraption.getRotationAxis();
		for(StructureBlockInfo sblock : movedContraption.getContraption().getBlocks().values()) {
			float mass = 60; //TODO
			if(sblock.state().getBlock() instanceof KineticBlock) {
				mass = 6 * AllConfigs.server().kinetics.getInertia((KineticBlock) sblock.state().getBlock());
			}
			comX += sblock.pos().getX() * mass;
			comY += sblock.pos().getY() * mass;
			comZ += sblock.pos().getZ() * mass;
			totalMass += mass;
		}
		comX /= totalMass;
		comY /= totalMass;
		comZ /= totalMass;
		return VecHelper.rotate(new Vec3(comX, comY, comZ), angle, axis);
	}
	
	@Override
	public float getTorque(float speed) {
		if(!running || movedContraption == null || movedContraption.isStalled()) return super.getTorque(speed);
		float totalMass = 0;
		float comX = 0, comY = 0, comZ = 0;
		Axis axis = movedContraption.getRotationAxis();
		for(StructureBlockInfo sblock : movedContraption.getContraption().getBlocks().values()) {
			float mass = 60; //TODO
			if(sblock.state().getBlock() instanceof KineticBlock) {
				mass = 6 * AllConfigs.server().kinetics.getInertia((KineticBlock) sblock.state().getBlock());
			}
			comX += sblock.pos().getX() * mass;
			comY += sblock.pos().getY() * mass;
			comZ += sblock.pos().getZ() * mass;
			totalMass += mass;
		}
		comX /= totalMass;
		comY /= totalMass;
		comZ /= totalMass;
		Vec3 com = VecHelper.rotate(new Vec3(comX, comY, comZ), angle, axis);
		Vec3 gravity = smallG().scale(totalMass);
		Vec3 torqueVec = com.cross(gravity);
		float torque = (float) (axis == Axis.X ? torqueVec.x :
								axis == Axis.Y ? torqueVec.y :
								axis == Axis.Z ? torqueVec.z : 0);
		return super.getTorque(speed) + torque;
	}
	
	@Override
	public void onStall() {
		if (!level.isClientSide) {
			sendData();
			wasStalled = true;
			if(hasNetwork()) getOrCreateNetwork().updateEffectiveInertia();
			storedEnergy += 0.5f * getContraptionInertia() * getSpeed() * getSpeed();
		}
	}

	@Override
	public boolean isValid() {
		return !isRemoved();
	}

	@Override
	public boolean isAttachedTo(AbstractContraptionEntity contraption) {
		return movedContraption == contraption;
	}

	public boolean isRunning() {
		return running;
	}

	@Override
	public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		if (super.addToTooltip(tooltip, isPlayerSneaking))
			return true;
		if (isPlayerSneaking)
			return false;
		if (!isWindmill() && getSpeed() == 0)
			return false;
		if (running)
			return false;
		BlockState state = getBlockState();
		if (!(state.getBlock() instanceof BearingBlock))
			return false;

		BlockState attachedState = level.getBlockState(worldPosition.relative(state.getValue(BearingBlock.FACING)));
		if (attachedState.canBeReplaced())
			return false;
		TooltipHelper.addHint(tooltip, "hint.empty_bearing");
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
	
	public void setAngle(float forcedAngle) {
		angle = forcedAngle;
	}

	public ControlledContraptionEntity getMovedContraption() {
		return movedContraption;
	}

	public void neighbourChanged(boolean hasNeighborSignal) {
		if(hasNeighborSignal) {
			assembleNextTick = true;
		}
	}
	
	@Override
	public boolean shouldCreateNetwork() {
		return true;
	}
	
	@Override
	public float getStoredEnergy() {
		return storedEnergy;
	}
	
	@Override
	public void setStoredEnergy(float storedEnergy) {
		this.storedEnergy = storedEnergy;
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
