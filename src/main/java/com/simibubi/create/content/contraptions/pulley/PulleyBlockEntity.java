package com.simibubi.create.content.contraptions.pulley;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.BlockMovementChecks;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption.MovementMode;
import com.simibubi.create.content.contraptions.piston.LinearActuatorBlockEntity;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchObservable;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.utility.Iterate;
import com.simibubi.create.foundation.utility.Lang;
import com.simibubi.create.foundation.utility.NBTHelper;
import com.simibubi.create.foundation.utility.VecHelper;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PulleyBlockEntity extends LinearActuatorBlockEntity implements ThresholdSwitchObservable {

	protected int initialOffset;
	private float prevAnimatedOffset;

	protected BlockPos mirrorParent;
	protected List<BlockPos> mirrorChildren;
	public WeakReference<AbstractContraptionEntity> sharedMirrorContraption;

	protected ScrollOptionBehaviour<MovementMode> movementMode;
	
	public PulleyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		movementMode = new ScrollOptionBehaviour<>(MovementMode.class, Lang.translateDirect("contraptions.movement_mode"),
				this, getMovementModeSlot());
		behaviours.add(movementMode);
		registerAwardables(behaviours, AllAdvancements.CONTRAPTION_ACTORS);
		registerAwardables(behaviours, AllAdvancements.PULLEY_MAXED);
	}
	
	@Override
	protected AABB createRenderBoundingBox() {
		double expandY = -offset;
		if (sharedMirrorContraption != null) {
			AbstractContraptionEntity ace = sharedMirrorContraption.get();
			if (ace != null)
				expandY = ace.getY() - worldPosition.getY();
		}
		return super.createRenderBoundingBox().expandTowards(0, expandY, 0);
	}

	@Override
	public void tick() {
		float prevOffset = offset;
		super.tick();

		if (level.isClientSide() && mirrorParent != null)
			if (sharedMirrorContraption == null || sharedMirrorContraption.get() == null
				|| !sharedMirrorContraption.get()
					.isAlive()) {
				sharedMirrorContraption = null;
				if (level.getBlockEntity(mirrorParent)instanceof PulleyBlockEntity pte && pte.movedContraption != null)
					sharedMirrorContraption = new WeakReference<>(pte.movedContraption);
			}

		if (isVirtual())
			prevAnimatedOffset = offset;
		invalidateRenderBoundingBox();

		if (prevOffset < 200 && offset >= 200)
			award(AllAdvancements.PULLEY_MAXED);
	}

	@Override
	protected boolean isPassive() {
		return mirrorParent != null;
	}

	@Nullable
	public AbstractContraptionEntity getAttachedContraption() {
		return mirrorParent != null && sharedMirrorContraption != null ? sharedMirrorContraption.get()
			: movedContraption;
	}

	@Override
	protected boolean assemble() throws AssemblyException {
		if (!(level.getBlockState(worldPosition)
			.getBlock() instanceof PulleyBlock))
			return false;
		//if (getSpeed() == 0 && mirrorParent == null)
		//	return false;
		int maxLength = AllConfigs.server().kinetics.maxRopeLength.get();
		int i = 1;
		while (i <= maxLength) {
			BlockPos ropePos = worldPosition.below(i);
			BlockState ropeState = level.getBlockState(ropePos);
			if (!AllBlocks.ROPE.has(ropeState) && !AllBlocks.PULLEY_MAGNET.has(ropeState)) {
				break;
			}
			++i;
		}
		offset = i - 1;
		if (offset >= getExtensionRange() && getSpeed() > 0)
			return false;
		if (offset <= 0 && getSpeed() < 0)
			return false;

		// Collect Construct
		if (!level.isClientSide && mirrorParent == null) {
			needsContraption = false;
			BlockPos anchor = worldPosition.below(Mth.floor(offset + 1));
			initialOffset = Mth.floor(offset);
			PulleyContraption contraption = new PulleyContraption(initialOffset);
			boolean canAssembleStructure = contraption.assemble(level, anchor);

			if (canAssembleStructure) {
				Direction movementDirection = getSpeed() > 0 ? Direction.DOWN : Direction.UP;
				if (ContraptionCollider.isCollidingWithWorld(level, contraption, anchor.relative(movementDirection),
					movementDirection))
					canAssembleStructure = false;
			}

			if (!canAssembleStructure && getSpeed() > 0)
				return false;

			removeRopes();

			if (!contraption.getBlocks()
				.isEmpty()) {
				contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
				movedContraption = ControlledContraptionEntity.create(level, this, contraption);
				movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
				level.addFreshEntity(movedContraption);
				forceMove = true;
				needsContraption = true;

				if (contraption.containsBlockBreakers())
					award(AllAdvancements.CONTRAPTION_ACTORS);

				for (BlockPos pos : contraption.createColliders(level, Direction.UP)) {
					if (pos.getY() != 0)
						continue;
					pos = pos.offset(anchor);
					if (level.getBlockEntity(
						new BlockPos(pos.getX(), worldPosition.getY(), pos.getZ())) instanceof PulleyBlockEntity pbe)
						pbe.startMirroringOther(worldPosition);
				}
			}
		}

		if (mirrorParent != null)
			removeRopes();

		clientOffsetDiff = 0;
		running = true;
		sendData();
		return true;
	}

	private void removeRopes() {
		for (int i = ((int) offset); i > 0; i--) {
			BlockPos offset = worldPosition.below(i);
			BlockState oldState = level.getBlockState(offset);
			level.setBlock(offset, oldState.getFluidState()
				.createLegacyBlock(), 66);
		}
	}

	@Override
	public void disassemble() {
		if (!running && movedContraption == null && mirrorParent == null)
			return;
		offset = getGridOffset(offset);
		if (movedContraption != null)
			resetContraptionToOffset();

		if (!level.isClientSide) {
			if (shouldCreateRopes()) {
				if (offset > 0) {
					BlockPos magnetPos = worldPosition.below((int) offset);
					FluidState ifluidstate = level.getFluidState(magnetPos);
					if (level.getBlockState(magnetPos)
						.getDestroySpeed(level, magnetPos) != -1) {

						level.destroyBlock(magnetPos, level.getBlockState(magnetPos)
							.getCollisionShape(level, magnetPos)
							.isEmpty());
						level.setBlock(magnetPos, AllBlocks.PULLEY_MAGNET.getDefaultState()
							.setValue(BlockStateProperties.WATERLOGGED,
								Boolean.valueOf(ifluidstate.getType() == Fluids.WATER)),
							66);
					}
				}

				boolean[] waterlog = new boolean[(int) offset];

				for (boolean destroyPass : Iterate.trueAndFalse) {
					for (int i = 1; i <= ((int) offset) - 1; i++) {
						BlockPos ropePos = worldPosition.below(i);
						if (level.getBlockState(ropePos)
							.getDestroySpeed(level, ropePos) == -1)
							continue;

						if (destroyPass) {
							FluidState ifluidstate = level.getFluidState(ropePos);
							waterlog[i] = ifluidstate.getType() == Fluids.WATER;
							level.destroyBlock(ropePos, level.getBlockState(ropePos)
								.getCollisionShape(level, ropePos)
								.isEmpty());
							continue;
						}

						level.setBlock(worldPosition.below(i), AllBlocks.ROPE.getDefaultState()
							.setValue(BlockStateProperties.WATERLOGGED, waterlog[i]), 66);
					}
				}
				
			}

			if (movedContraption != null && mirrorParent == null)
				movedContraption.disassemble();
			notifyMirrorsOfDisassembly();
		}

		if (movedContraption != null)
			movedContraption.discard();

		movedContraption = null;
		initialOffset = 0;
		running = false;
		sendData();
	}

	protected boolean shouldCreateRopes() {
		return !remove;
	}

	@Override
	protected Vec3 toPosition(float offset) {
		if (movedContraption.getContraption() instanceof PulleyContraption) {
			PulleyContraption contraption = (PulleyContraption) movedContraption.getContraption();
			return Vec3.atLowerCornerOf(contraption.anchor)
				.add(0, contraption.getInitialOffset() - offset, 0);

		}
		return Vec3.ZERO;
	}

	@Override
	protected void visitNewPosition() {
		super.visitNewPosition();
		if (level.isClientSide)
			return;
		if (movedContraption != null)
			return;
		if (getSpeed() <= 0)
			return;

		BlockPos posBelow = worldPosition.below((int) (offset + getMovementSpeed()) + 1);
		BlockState state = level.getBlockState(posBelow);
		if (!BlockMovementChecks.isMovementNecessary(state, level, posBelow))
			return;
		if (BlockMovementChecks.isBrittle(state))
			return;

		disassemble();
		assembleNextTick = true;
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		initialOffset = compound.getInt("InitialOffset");
		needsContraption = compound.getBoolean("NeedsContraption");
		super.read(compound, clientPacket);

		BlockPos prevMirrorParent = mirrorParent;
		mirrorParent = null;
		mirrorChildren = null;

		if (compound.contains("MirrorParent")) {
			mirrorParent = NbtUtils.readBlockPos(compound.getCompound("MirrorParent"));
			offset = 0;
			if (prevMirrorParent == null || !prevMirrorParent.equals(mirrorParent))
				sharedMirrorContraption = null;
		}

		if (compound.contains("MirrorChildren"))
			mirrorChildren = NBTHelper.readCompoundList(compound.getList("MirrorChildren", Tag.TAG_COMPOUND),
				NbtUtils::readBlockPos);

		if (mirrorParent == null)
			sharedMirrorContraption = null;
	}

	@Override
	public void write(CompoundTag compound, boolean clientPacket) {
		compound.putInt("InitialOffset", initialOffset);
		super.write(compound, clientPacket);

		if (mirrorParent != null)
			compound.put("MirrorParent", NbtUtils.writeBlockPos(mirrorParent));
		if (mirrorChildren != null)
			compound.put("MirrorChildren", NBTHelper.writeCompoundList(mirrorChildren, NbtUtils::writeBlockPos));
	}

	public void startMirroringOther(BlockPos parent) {
		if (parent.equals(worldPosition))
			return;
		if (!(level.getBlockEntity(parent) instanceof PulleyBlockEntity pbe))
			return;
		if (pbe.getType() != getType())
			return;
		if (pbe.mirrorChildren == null)
			pbe.mirrorChildren = new ArrayList<>();
		pbe.mirrorChildren.add(worldPosition);
		pbe.notifyUpdate();

		mirrorParent = parent;
		try {
			assemble();
		} catch (AssemblyException e) {
		}
		notifyUpdate();
	}

	public void notifyMirrorsOfDisassembly() {
		if (mirrorChildren == null)
			return;
		for (BlockPos blockPos : mirrorChildren) {
			if (!(level.getBlockEntity(blockPos) instanceof PulleyBlockEntity pbe))
				continue;
			pbe.offset = offset;
			pbe.disassemble();
			pbe.mirrorParent = null;
			pbe.notifyUpdate();
		}
		mirrorChildren.clear();
		notifyUpdate();
	}

	@Override
	protected int getExtensionRange() {
		return Math.max(0, Math.min(AllConfigs.server().kinetics.maxRopeLength.get(),
			(worldPosition.getY() - 1) - level.getMinBuildHeight()));
	}

	@Override
	protected int getInitialOffset() {
		return initialOffset;
	}

	@Override
	protected Vec3 toMotionVector(float speed) {
		return new Vec3(0, -speed, 0);
	}

	protected ValueBoxTransform getMovementModeSlot() {
		return new CenteredSideValueBoxTransform((state, d) -> d == Direction.UP);
	}

	@Override
	public float getInterpolatedOffset(float partialTicks) {
		if (isVirtual())
			return Mth.lerp(partialTicks, prevAnimatedOffset, offset);
		boolean moving = running && (movedContraption == null || !movedContraption.isStalled());
		return super.getInterpolatedOffset(moving ? partialTicks : 0.5f);
	}

	public void animateOffset(float forcedOffset) {
		offset = forcedOffset;
	}

	@Override
	public float getPercent() {
		int distance = worldPosition.getY() - level.getMinBuildHeight();
		if (distance <= 0)
			return 100;
		return 100 * getInterpolatedOffset(.5f) / distance;
	}

	public BlockPos getMirrorParent() {
		return mirrorParent;
	}

	@Override
	public float getContraptionInertia() {
		float totalMass = 0;
		for(StructureBlockInfo sblock : movedContraption.getContraption().getBlocks().values()) {
			float mass = 60; //TODO
			if(sblock.state().getBlock() instanceof KineticBlock) {
				mass = 6 * AllConfigs.server().kinetics.getInertia((KineticBlock) sblock.state().getBlock());
			}
			totalMass += mass;
		}
		return totalMass * 0.373f * 0.373f; //radius of piston attachment squared
	}

	@Override
	public float getTorque(float speed) {
		if(!running || movedContraption == null || movedContraption.isStalled() || wasCollided
				|| ((wasMaximallyExtended || wasMinimallyExtended) && getSpeed() != 0 && !isPassive())) 
			return super.getTorque(speed);
		
		float totalMass = 0;
		
		Direction pistonDir = Direction.DOWN;
		Axis shaftAxis = level.getBlockState(worldPosition).getValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS);
		
		for(StructureBlockInfo sblock : movedContraption.getContraption().getBlocks().values()) {
			float mass = 60; //TODO
			if(sblock.state().getBlock() instanceof KineticBlock) {
				mass = 6 * AllConfigs.server().kinetics.getInertia((KineticBlock) sblock.state().getBlock());
			}
			totalMass += mass;
		}
		
		Vec3 attachmentPoint = VecHelper.getAxisVector(pistonDir.getAxis()).cross(VecHelper.getAxisVector(shaftAxis)).scale(-0.373);
		Vec3 torqueVec = attachmentPoint.cross(smallG().scale(totalMass));
		
		float torque = (float) (shaftAxis == Axis.X ? torqueVec.x :
								shaftAxis == Axis.Y ? torqueVec.y :
								shaftAxis == Axis.Z ? torqueVec.z : 0);
		return super.getTorque(speed) + torque;
	}
	
	@Override
	protected MovementMode getMovementMode() {
		return movementMode.get();
	}
	
	@Override
	public boolean pullsEnergyFromNetwork() {
		return getMovementSpeed() < 0;
	}
}
