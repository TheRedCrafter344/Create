package com.simibubi.create.content.kinetics.crusher;

import java.util.List;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDamageTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.Iterate;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class CrushingWheelBlockEntity extends KineticBlockEntity {
	public CrushingWheelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		setLazyTickRate(20);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		registerAwardables(behaviours, AllAdvancements.CRUSHING_WHEEL, AllAdvancements.CRUSHER_MAXED);
	}

	@Override
	public void onSpeedChanged(float prevSpeed) {
		super.onSpeedChanged(prevSpeed);
		fixControllers();
	}

	@Override
	public float getTorque(float speed) {
		CrushingWheelControllerBlockEntity controller = null;
		for(Direction d : Iterate.directions) {
			if(getBlockState().getValue(CrushingWheelBlock.AXIS) == d.getAxis()) continue;
			BlockPos controllerPos = getBlockPos().relative(d);
			if(AllBlocks.CRUSHING_WHEEL_CONTROLLER.has(getLevel().getBlockState(controllerPos))) {
				controller = (CrushingWheelControllerBlockEntity) getLevel().getBlockEntity(controllerPos);
				break;
			}
		}
		boolean isRunning = controller != null && controller.isOccupied();
		return super.getTorque(speed) + (isRunning ? AllConfigs.server().kinetics.crusherTorque.getF() : 0) * -Math.signum(speed);
	}
	
	public void fixControllers() {
		for (Direction d : Iterate.directions)
			((CrushingWheelBlock) getBlockState().getBlock()).updateControllers(getBlockState(), getLevel(), getBlockPos(),
					d);
	}

	@Override
	protected AABB createRenderBoundingBox() {
		return new AABB(worldPosition).inflate(1);
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		fixControllers();
	}

	@SubscribeEvent
	public static void crushingIsFortunate(LootingLevelEvent event) {
		DamageSource damageSource = event.getDamageSource();
		if (damageSource == null || !damageSource.is(AllDamageTypes.CRUSH))
			return;
		event.setLootingLevel(2);		//This does not currently increase mob drops. It seems like this only works for damage done by an entity.
	}

	@SubscribeEvent
	public static void handleCrushedMobDrops(LivingDropsEvent event) {
		DamageSource damageSource = event.getSource();
		if (damageSource == null || !damageSource.is(AllDamageTypes.CRUSH))
			return;
		Vec3 outSpeed = Vec3.ZERO;
		for (ItemEntity outputItem : event.getDrops()) {
			outputItem.setDeltaMovement(outSpeed);
		}
	}

}
