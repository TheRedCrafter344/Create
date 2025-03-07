package com.simibubi.create.content.kinetics.waterwheel;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.simibubi.create.foundation.utility.Iterate;
import com.simibubi.create.foundation.utility.Lang;
import com.simibubi.create.foundation.utility.VecHelper;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WaterWheelBlockEntity extends KineticBlockEntity {

	public static final Map<Axis, Set<BlockPos>> SMALL_OFFSETS = new EnumMap<>(Axis.class);
	public static final Map<Axis, Set<BlockPos>> LARGE_OFFSETS = new EnumMap<>(Axis.class);

	static {
		for (Axis axis : Iterate.axes) {
			HashSet<BlockPos> offsets = new HashSet<>();
			for (Direction d : Iterate.directions)
				if (d.getAxis() != axis)
					offsets.add(BlockPos.ZERO.relative(d));
			SMALL_OFFSETS.put(axis, offsets);

			offsets = new HashSet<>();
			for (Direction d : Iterate.directions) {
				if (d.getAxis() == axis)
					continue;
				BlockPos centralOffset = BlockPos.ZERO.relative(d, 2);
				offsets.add(centralOffset);
				for (Direction d2 : Iterate.directions) {
					if (d2.getAxis() == axis)
						continue;
					if (d2.getAxis() == d.getAxis())
						continue;
					offsets.add(centralOffset.relative(d2));
				}
			}
			LARGE_OFFSETS.put(axis, offsets);
		}
	}

	public int flowScore;
	public BlockState material;

	public WaterWheelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		material = Blocks.SPRUCE_PLANKS.defaultBlockState();
		setLazyTickRate(60);
	}

	protected int getSize() {
		return 1;
	}

	protected Set<BlockPos> getOffsetsToCheck() {
		return (getSize() == 1 ? SMALL_OFFSETS : LARGE_OFFSETS).get(getAxis());
	}

	public InteractionResult applyMaterialIfValid(ItemStack stack) {
		if (!(stack.getItem()instanceof BlockItem blockItem))
			return InteractionResult.PASS;
		BlockState material = blockItem.getBlock()
			.defaultBlockState();
		if (material == this.material)
			return InteractionResult.PASS;
		if (!material.is(BlockTags.PLANKS))
			return InteractionResult.PASS;
		if (level.isClientSide() && !isVirtual())
			return InteractionResult.SUCCESS;
		this.material = material;
		notifyUpdate();
		level.levelEvent(2001, worldPosition, Block.getId(material));
		return InteractionResult.SUCCESS;
	}

	protected Axis getAxis() {
		Axis axis = Axis.X;
		BlockState blockState = getBlockState();
		if (blockState.getBlock()instanceof IRotate irotate)
			axis = irotate.getRotationAxis(blockState);
		return axis;
	}

	@Override
	public boolean shouldCreateNetwork() {
		return true;
	}
	
	@Override
	public void lazyTick() {
		super.lazyTick();

		// Water can change flow direction without notifying neighbours
		determineAndApplyFlowScore();
	}

	public void determineAndApplyFlowScore() {
		Vec3 wheelPlane =
			Vec3.atLowerCornerOf(new Vec3i(1, 1, 1).subtract(Direction.get(AxisDirection.POSITIVE, getAxis())
				.getNormal()));

		int flowScore = 0;
		boolean lava = false;
		for (BlockPos blockPos : getOffsetsToCheck()) {
			BlockPos targetPos = blockPos.offset(worldPosition);
			Vec3 flowAtPos = getFlowVectorAtPosition(targetPos).multiply(wheelPlane);
			lava |= FluidHelper.isLava(level.getFluidState(targetPos)
				.getType());

			if (flowAtPos.lengthSqr() == 0)
				continue;

			flowAtPos = flowAtPos.normalize();
			Vec3 normal = Vec3.atLowerCornerOf(blockPos)
				.normalize();

			Vec3 positiveMotion = VecHelper.rotate(normal, 90, getAxis());
			double dot = flowAtPos.dot(positiveMotion);
			if (Math.abs(dot) > .5)
				flowScore += Math.signum(dot);
		}

		if (flowScore != 0 && !level.isClientSide())
			award(lava ? AllAdvancements.LAVA_WHEEL : AllAdvancements.WATER_WHEEL);

		setFlowScoreAndUpdate(flowScore);
	}

	public Vec3 getFlowVectorAtPosition(BlockPos pos) {
		FluidState fluid = level.getFluidState(pos);
		Vec3 vec = fluid.getFlow(level, pos);
		BlockState blockState = level.getBlockState(pos);
		if (blockState.getBlock() == Blocks.BUBBLE_COLUMN)
			vec = new Vec3(0, blockState.getValue(BubbleColumnBlock.DRAG_DOWN) ? -1 : 1, 0);
		return vec;
	}

	public void setFlowScoreAndUpdate(int score) {
		if (flowScore == score)
			return;
		flowScore = score;
		setChanged();
	}

	private void redraw() {
		if (!isVirtual())
			requestModelDataUpdate();
		if (hasLevel()) {
			level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 16);
			level.getChunkSource()
				.getLightEngine()
				.checkBlock(worldPosition);
		}
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		registerAwardables(behaviours, AllAdvancements.LAVA_WHEEL, AllAdvancements.WATER_WHEEL);
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);
		flowScore = compound.getInt("FlowScore");

		BlockState prevMaterial = material;
		if (!compound.contains("Material"))
			return;

		material = NbtUtils.readBlockState(blockHolderGetter(), compound.getCompound("Material"));
		if (material.isAir())
			material = Blocks.SPRUCE_PLANKS.defaultBlockState();

		if (clientPacket && prevMaterial != material)
			redraw();
	}

	@Override
	public void write(CompoundTag compound, boolean clientPacket) {
		super.write(compound, clientPacket);
		compound.putInt("FlowScore", flowScore);
		compound.put("Material", NbtUtils.writeBlockState(material));
	}

	@Override
	protected AABB createRenderBoundingBox() {
		return new AABB(worldPosition).inflate(getSize());
	}

	//@Override
	//public float getGeneratedSpeed() {
	//	return Mth.clamp(flowScore, -1, 1) * 8 / getSize();
	//}

	private static final float POWER_LOSS_AT = 0.8f; // wheel rpm / water rpm point where power loss starts. power generated will be 0 when that ratio is 1.
	
	@Override
	public float getTorque(float speed) {
		if(flowScore == 0) return super.getTorque(speed);
		float targetRPM = Mth.clamp(flowScore, -1, 1) * AllConfigs.server().kinetics.waterwheelTargetRPM.getF() / getSize();
		float generatedPower = AllConfigs.server().kinetics.waterwheelPower.getF() * Math.abs(flowScore) * getSize();
		float powerLossAt = targetRPM * Mth.clamp(POWER_LOSS_AT, 0, 0.9f);
		float power;
		speed = speed > 0 ? Math.max(speed, 1)
				  		  : Math.min(speed, -1);
		if(speed * targetRPM < 0) {
			power = -AllConfigs.server().kinetics.waterwheelRemovedPower.getF() * getSize();
		} else if(Math.abs(speed) < Math.abs(powerLossAt)) {
			power = generatedPower;
		} else {
			float slope = generatedPower / (targetRPM - powerLossAt);
			power = Math.max(generatedPower - slope * (speed - powerLossAt), -AllConfigs.server().kinetics.waterwheelRemovedPower.getF() * getSize());
		}
		return power / speed + super.getTorque(speed);
	}
	
	@Override
	public float getInertia() {
		return super.getInertia() * getSize() * getSize();
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
		}
		Lang.text("Flow level: " + flowScore).forGoggles(tooltip);
		return true;
	}
}
	