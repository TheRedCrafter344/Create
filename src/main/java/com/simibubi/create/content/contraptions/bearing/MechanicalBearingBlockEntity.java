package com.simibubi.create.content.contraptions.bearing;

import java.util.List;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;

public class MechanicalBearingBlockEntity extends KineticBlockEntity
	implements IBearingBlockEntity, IDisplayAssemblyExceptions {

	protected ControlledContraptionEntity movedContraption;
	protected float angle;
	protected boolean running;
	protected boolean assembleNextTick;
	protected float clientAngleDiff;
	protected AssemblyException lastException;
	protected double sequencedAngleLimit;

	private float prevAngle;

	private boolean wasStalled = false;
	
	public float storedEnergy = 0;
	
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
			}
			movedContraption.getContraption()
				.stop(level);
		}

		//if (!isWindmill() && sequenceContext != null
		//	&& sequenceContext.instruction() == SequencerInstructions.TURN_ANGLE)
		//	sequencedAngleLimit = sequenceContext.getEffectiveValue(getTheoreticalSpeed());
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
		angle = 0;
		sequencedAngleLimit = -1;
		if (isWindmill())
			applyRotation();
		if (movedContraption != null) {
			movedContraption.disassemble();
			AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(level, worldPosition);
		}

		movedContraption = null;
		running = false;
		if(hasNetwork()) getOrCreateNetwork().updateEffectiveInertia();
		assembleNextTick = false;
		sendData();
	}

	@Override
	public void tick() {
		super.tick();

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
					net.speed = (float) (Math.signum(net.speed) * Math.sqrt(net.speed * net.speed + 2 * storedEnergy / net.getEffectiveInertia()));
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
	
	public static final float GRAVITY = 10; //TODO
	
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
		Vec3 torqueVec = com.cross(new Vec3(0, -GRAVITY * totalMass, 0));
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
}
