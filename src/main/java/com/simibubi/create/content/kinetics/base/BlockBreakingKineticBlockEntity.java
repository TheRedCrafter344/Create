package com.simibubi.create.content.kinetics.base;

import java.util.concurrent.atomic.AtomicInteger;

import com.simibubi.create.foundation.utility.BlockHelper;
import com.simibubi.create.foundation.utility.VecHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public abstract class BlockBreakingKineticBlockEntity extends KineticBlockEntity {

	public static final AtomicInteger NEXT_BREAKER_ID = new AtomicInteger();
	protected float destroyProgress;
	protected int breakerId = -NEXT_BREAKER_ID.incrementAndGet();
	protected BlockPos breakingPos;

	public BlockBreakingKineticBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public float getTorque(float speed) {
		if(breakingPos == null) return super.getTorque(speed);
		BlockState stateToBreak = level.getBlockState(breakingPos);
		float blockHardness = stateToBreak.getDestroySpeed(level, breakingPos);
		return super.getTorque(speed) + (canBreak(stateToBreak, blockHardness) ? getBreakingTorque() : 0) * -Math.signum(speed);
	}
	
	public abstract float getBreakingTorque();
	
	protected abstract BlockPos getBreakingPos();

	protected boolean shouldRun() {
		return true;
	}

	@Override
	public void write(CompoundTag compound, boolean clientPacket) {
		compound.putFloat("Progress", destroyProgress);
		if (breakingPos != null)
			compound.put("Breaking", NbtUtils.writeBlockPos(breakingPos));
		super.write(compound, clientPacket);
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		destroyProgress = compound.getFloat("Progress");
		if (compound.contains("Breaking"))
			breakingPos = NbtUtils.readBlockPos(compound.getCompound("Breaking"));
		super.read(compound, clientPacket);
	}

	@Override
	public void invalidate() {
		super.invalidate();
		if (!level.isClientSide && destroyProgress != 0)
			level.destroyBlockProgress(breakerId, breakingPos, -1);
	}

	@Override
	public void tick() {
		super.tick();

		if (level.isClientSide)
			return;
		if (!shouldRun())
			return;
		if (getSpeed() == 0)
			return;

		breakingPos = getBreakingPos();
		BlockState stateToBreak = level.getBlockState(breakingPos);
		float blockHardness = stateToBreak.getDestroySpeed(level, breakingPos);

		if (!canBreak(stateToBreak, blockHardness)) {
			if (destroyProgress != 0) {
				destroyProgress = 0;
				level.destroyBlockProgress(breakerId, breakingPos, -1);
			}
			return;
		}

		float breakSpeed = getBreakSpeed(stateToBreak);
		destroyProgress += breakSpeed;
		level.playSound(null, worldPosition, stateToBreak.getSoundType()
			.getHitSound(), SoundSource.NEUTRAL, .25f, 1);

		if (destroyProgress >= blockHardness * 30) {
			onBlockBroken(stateToBreak);
			destroyProgress = 0;
			level.destroyBlockProgress(breakerId, breakingPos, -1);
			return;
		}

		level.destroyBlockProgress(breakerId, breakingPos, (int)(destroyProgress / (blockHardness * 3)));
	}

	public boolean canBreak(BlockState stateToBreak, float blockHardness) {
		return isBreakable(stateToBreak, blockHardness);
	}

	public static boolean isBreakable(BlockState stateToBreak, float blockHardness) {
		return !(stateToBreak.liquid() || stateToBreak.getBlock() instanceof AirBlock || blockHardness == -1);
	}

	public void onBlockBroken(BlockState stateToBreak) {
		Vec3 vec = VecHelper.offsetRandomly(VecHelper.getCenterOf(breakingPos), level.random, .125f);
		BlockHelper.destroyBlock(level, breakingPos, 1f, (stack) -> {
			if (stack.isEmpty())
				return;
			if (!level.getGameRules()
				.getBoolean(GameRules.RULE_DOBLOCKDROPS))
				return;
			if (level.restoringBlockSnapshots)
				return;
			
			ItemEntity itementity = new ItemEntity(level, vec.x, vec.y, vec.z, stack);
			itementity.setDefaultPickUpDelay();
			itementity.setDeltaMovement(Vec3.ZERO);
			level.addFreshEntity(itementity);
		});
	}

	protected float getBreakSpeed(BlockState stateToBreak) {
		return Math.abs(getSpeed() / 100f);
	}

}
