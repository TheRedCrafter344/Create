package com.simibubi.create.content.kinetics.crank;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.render.CachedBufferer;
import com.simibubi.create.foundation.render.SuperByteBuffer;
import com.simibubi.create.foundation.render.VirtualRenderHelper;
import com.simibubi.create.foundation.utility.Components;
import com.simibubi.create.foundation.utility.Lang;
import com.simibubi.create.foundation.utility.VecHelper;

import dev.engine_room.flywheel.api.model.Model;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class ValveHandleBlockEntity extends HandCrankBlockEntity {

	public ScrollValueBehaviour angleInput;
	public int cooldown;

	private float isTurning = 0;
	
	protected int startAngle;
	protected int targetAngle;
	protected int totalUseTicks;

	private static final float ROTATION_MOMENTUM = 3200;
	private static final float MAX_INERTIA = 1500;
	
	public ValveHandleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		behaviours.add(angleInput = new ValveHandleScrollValueBehaviour(this).between(-180, 180));
		angleInput.onlyActiveWhen(this::showValue);
		angleInput.setValue(45);
	}

	@Override
	protected boolean clockwise() {
		return angleInput.getValue() < 0 ^ backwards;
	}

	@Override
	public void write(CompoundTag compound, boolean clientPacket) {
		super.write(compound, clientPacket);
		compound.putInt("TotalUseTicks", totalUseTicks);
		compound.putInt("StartAngle", startAngle);
		compound.putInt("TargetAngle", targetAngle);
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);
		totalUseTicks = compound.getInt("TotalUseTicks");
		startAngle = compound.getInt("StartAngle");
		targetAngle = compound.getInt("TargetAngle");
	}

	@Override
	public void tick() {
		super.tick();
		if(inUse == 0 && isTurning != 0 && !level.isClientSide() && network != null) {
			KineticNetwork network = getOrCreateNetwork();
			float speed = network.getSpeed(); //TODO this sucks ass
			float speedReduction = isTurning;
			isTurning = 0;
			if(Math.signum(speed - speedReduction) != Math.signum(speed)) {
				network.setSpeed(0);
			} else {
				network.setSpeed(network.getSpeed() - speedReduction);
			}
			network.sendToClient();
		}
		if (inUse == 0 && cooldown > 0)
			cooldown--;
		independentAngle = level.isClientSide() ? getIndependentAngle(0) : 0;
	}

	@Override
	public float getIndependentAngle(float partialTicks) {
		if (inUse == 0 && /*source != null &&*/ Math.abs(getSpeed()) > 1f)
			return KineticBlockEntityRenderer.getAngleForBe(this, worldPosition,
				KineticBlockEntityRenderer.getRotationAxisOf(this));

		int step = getBlockState().getOptionalValue(ValveHandleBlock.FACING)
			.orElse(Direction.SOUTH)
			.getAxisDirection()
			.getStep();

		return (inUse > 0 && totalUseTicks > 0
			? Mth.lerp(Math.min(totalUseTicks, totalUseTicks - inUse + partialTicks) / (float) totalUseTicks,
				startAngle, targetAngle)
			: targetAngle) * Mth.DEG_TO_RAD * (backwards ? -1 : 1) * step;
	}

	@Override
	public float getTorque(float speed) {
		return 0;
	}
	
	public boolean showValue() {
		return inUse == 0;
	}

	public boolean activate(boolean sneak) {
		if (network == null) return false;
		if (Math.abs(getSpeed()) > 1f)
			return false;
		if (inUse > 0 || cooldown > 0)
			return false;
		if (level.isClientSide)
			return true;

		int value = angleInput.getValue();
		int target = Math.abs(value);
		KineticNetwork network = getOrCreateNetwork();
		float effectiveInertia = network.getEffectiveInertia();
		if(effectiveInertia > MAX_INERTIA) return false;
		float rotationSpeed = ROTATION_MOMENTUM / effectiveInertia;
		double degreesPerTick = KineticBlockEntity.convertToAngular(rotationSpeed);
		inUse = (int) Math.ceil(target / degreesPerTick);

		startAngle = (int) ((independentAngle) % 90 + 360) % 90;
		targetAngle = Math.round((startAngle + (target > 135 ? 180 : 90) * Mth.sign(value)) / 90f) * 90;
		totalUseTicks = inUse;
		backwards = sneak;

		//onSpeedChanged(this.speed);
		
		network.setSpeed(network.getSpeed() + rotationSpeed);
		network.sendToClient();
		isTurning = rotationSpeed;
		cooldown = 4;

		return true;
	}
	
	@Override
	@OnlyIn(Dist.CLIENT)
	public SuperByteBuffer getRenderedHandle() {
		return CachedBufferer.block(getBlockState());
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public Model getRenderedHandleInstance() {
		return VirtualRenderHelper.blockModel(getBlockState());
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public boolean shouldRenderShaft() {
		return false;
	}

	public static class ValveHandleScrollValueBehaviour extends ScrollValueBehaviour {

		public ValveHandleScrollValueBehaviour(SmartBlockEntity be) {
			super(Lang.translateDirect("kinetics.valve_handle.rotated_angle"), be, new ValveHandleValueBox());
			withFormatter(v -> String.valueOf(Math.abs(v)) + Lang.translateDirect("generic.unit.degrees")
				.getString());
		}

		@Override
		public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
			ImmutableList<Component> rows = ImmutableList.of(Components.literal("\u27f3")
				.withStyle(ChatFormatting.BOLD),
				Components.literal("\u27f2")
					.withStyle(ChatFormatting.BOLD));
			return new ValueSettingsBoard(label, 180, 45, rows, new ValueSettingsFormatter(this::formatValue));
		}

		@Override
		public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
			int value = Math.max(1, valueSetting.value());
			if (!valueSetting.equals(getValueSettings()))
				playFeedbackSound(this);
			setValue(valueSetting.row() == 0 ? -value : value);
		}

		@Override
		public ValueSettings getValueSettings() {
			return new ValueSettings(value < 0 ? 0 : 1, Math.abs(value));
		}

		public MutableComponent formatValue(ValueSettings settings) {
			return Lang.number(Math.max(1, Math.abs(settings.value())))
				.add(Lang.translateDirect("generic.unit.degrees"))
				.component();
		}

		@Override
		public void onShortInteract(Player player, InteractionHand hand, Direction side) {
			BlockState blockState = blockEntity.getBlockState();
			if (blockState.getBlock() instanceof ValveHandleBlock vhb)
				vhb.clicked(getWorld(), getPos(), blockState, player, hand);
		}

	}

	public static class ValveHandleValueBox extends ValueBoxTransform.Sided {

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return direction == state.getValue(ValveHandleBlock.FACING);
		}

		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, 8, 4.5);
		}

		@Override
		public boolean testHit(BlockState state, Vec3 localHit) {
			Vec3 offset = getLocalOffset(state);
			if (offset == null)
				return false;
			return localHit.distanceTo(offset) < scale / 1.5f;
		}

	}

}
