package com.simibubi.create.content.kinetics.base;

import java.util.List;

import javax.annotation.Nullable;

import com.jozufozu.flywheel.backend.instancing.InstancedRenderDispatcher;
import com.simibubi.create.Create;
import com.simibubi.create.content.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.equipment.goggles.IHaveHoveringInformation;
import com.simibubi.create.content.kinetics.ClientKineticNetwork;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.gearbox.GearboxBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.sound.SoundScapes;
import com.simibubi.create.foundation.sound.SoundScapes.AmbienceGroup;
import com.simibubi.create.foundation.utility.Lang;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;

public class KineticBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, IHaveHoveringInformation {

	public @Nullable Long network;
	
	protected KineticEffectHandler effects;
	
	public float inertia = 10;
	
	//protected float speed;
	
	protected boolean wasMoved;
	
	protected float speedMultiplier;
	
	public float prevOffset;
	public float prevSpeed;
	
	public boolean updateKinetics;
	
	public KineticBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		effects = new KineticEffectHandler(this);
		updateKinetics = true;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

	@Override
	public void initialize() {
		if(!level.isClientSide && network != null) {
			KineticNetwork network = getOrCreateNetwork();
			//if(!network.initialized) network.initialize(speed / speedMultiplier, networkInertia, networkSize);
			
			network.loadBlockEntity(this, speedMultiplier);
			
			updateKinetics = false;
		}
		super.initialize();
	}
	
	@Override
	public void tick() {
		super.tick();
		if (!level.isClientSide && updateKinetics)
			attachKinetics();
		
		
		effects.tick();
		
		if (level.isClientSide) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> this.tickAudio());
			//renderAngle += speed * 0.3f;
			//if(renderAngle > 360) renderAngle -= 360;
			//return;
		}
		/*
		if(!level.isClientSide) {
			ticksTilSpeedUpdate--;
			if(ticksTilSpeedUpdate <= 0) {
				ticksTilSpeedUpdate = 5;
				if(network != null) updateFromNetwork();
			}
		}
		*/ //network handles this now
	}
	
	public float getRenderAngle(float partialTicks) {
		ClientKineticNetwork network = getOrCreateClientNetwork();
		if(network == null) return 0;
		return (network.getRenderAngle(partialTicks) * speedMultiplier) % 360;
	}
	
	public float getRenderAngle(float partialTicks, float modulo) {
		ClientKineticNetwork network = getOrCreateClientNetwork();
		if(network == null) return 0;
		return (network.getRenderAngle(partialTicks) * speedMultiplier) % modulo;
	}
	
	public void updateSpeedMultiplier() {
		if(network == null) return;
		KineticNetwork network = getOrCreateNetwork();
		float oldMultiplier = speedMultiplier;
		speedMultiplier = network.getMultiplierFor(this);
		if(oldMultiplier != speedMultiplier) {
			setChanged();
			sendData();
		}
	}
	
	public void onSpeedChanged(float oldSpeed) {
		
	}

	@Override
	public void remove() {
		if (!level.isClientSide) {
			if (network != null)
				getOrCreateNetwork().remove(this);
			detachKinetics();
		}
		super.remove();
	}
	
	@Override
	protected void write(CompoundTag compound, boolean clientPacket) {
		if (updateKinetics)
			compound.putBoolean("NeedsKineticsUpdate", true);

		if (network != null) {
			CompoundTag networkTag = new CompoundTag();
			networkTag.putLong("Id", this.network);
			networkTag.putFloat("SpeedMultiplier", speedMultiplier);
			compound.put("Network", networkTag);
		}

		super.write(compound, clientPacket);
	}
	
	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		clearKineticInformation();

		// DO NOT READ kinetic information when placed after movement
		if (wasMoved) {
			super.read(compound, clientPacket);
			return;
		}

		if (compound.contains("Network")) {
			CompoundTag networkTag = compound.getCompound("Network");
			network = networkTag.getLong("Id");
			speedMultiplier = networkTag.getFloat("SpeedMultiplier");
		}

		super.read(compound, clientPacket);

		if (clientPacket) {
			
			updateRenderInstance();
		}
	}
	
	public void updateRenderInstance() {
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> InstancedRenderDispatcher.enqueueUpdate(this));
	}
	
	public void clearKineticInformation() {
		network = null;
		speedMultiplier = 0;
	}
	
	public void warnOfMovement() {
		wasMoved = true;
	}
	
	public void attachKinetics() {
		updateKinetics = false;
		Create.KINETICS_MANAGER.handleAdded(level, worldPosition, this);
	}

	public void detachKinetics() {
		Create.KINETICS_MANAGER.handleRemoved(level, worldPosition, this);
	}

	public float getInertia() {
		if(!(getBlockState().getBlock() instanceof KineticBlock)) return 0;
		KineticBlock block = (KineticBlock) getBlockState().getBlock();
		return AllConfigs.server().kinetics.getInertia(block);
	}
	
	public float getTorque(float speed) {
		if(!(getBlockState().getBlock() instanceof KineticBlock)) return 0;
		KineticBlock block = (KineticBlock) getBlockState().getBlock();
		return -AllConfigs.server().kinetics.getFriction(block) * Math.signum(speed) - AllConfigs.server().kinetics.getResistance(block) * speed;
	}
	
	public KineticNetwork getOrCreateNetwork() {
		return Create.KINETICS_MANAGER.getOrCreateNetworkFor(this);
	}
	
	public ClientKineticNetwork getOrCreateClientNetwork() {
		return Create.KINETICS_MANAGER.getOrCreateNetworkClientFor(this);
	}
	
	//override this for anything that can **cause** rotation
	public boolean shouldCreateNetwork() {
		return false;
	}
	
	//override this if needed
	protected boolean syncSequenceContext() {
		return false;
	}
	
	public static void switchToBlockState(Level world, BlockPos pos, BlockState state) {
		if (world.isClientSide)
			return;

		BlockEntity blockEntity = world.getBlockEntity(pos);
		BlockState currentState = world.getBlockState(pos);
		boolean isKinetic = blockEntity instanceof KineticBlockEntity;

		if (currentState == state)
			return;
		if (blockEntity == null || !isKinetic) {
			world.setBlock(pos, state, 3);
			return;
		}

		KineticBlockEntity kineticBlockEntity = (KineticBlockEntity) blockEntity;
		if (state.getBlock() instanceof KineticBlock && !((KineticBlock) state.getBlock()).areStatesKineticallyEquivalent(currentState, state)) {
			if (kineticBlockEntity.hasNetwork())
				kineticBlockEntity.getOrCreateNetwork().remove(kineticBlockEntity);
			kineticBlockEntity.detachKinetics();
			//kineticBlockEntity.removeSource();
		}

		world.setBlock(pos, state, 3);
	}
	
	public boolean hasNetwork() {
		return network != null;
	}
	
	public void setNetwork(@Nullable Long networkIn, float speedMultiplier, boolean conserveNetworkMomentum) {
		
		if (network == networkIn && network != null) {
			this.speedMultiplier = speedMultiplier;
			getOrCreateNetwork().loadedMembers.put(this, speedMultiplier); //TODO conserve momentum
			setChanged();
			sendData();
			return;
		}
		if (network != null)
			getOrCreateNetwork().remove(this);

		network = networkIn;
		
		
		if (networkIn == null) {
			setChanged();
			sendData();
			return;
		}

		network = networkIn;
		KineticNetwork network = getOrCreateNetwork();
		network.addBlockEntity(this, speedMultiplier, conserveNetworkMomentum);
		this.speedMultiplier = speedMultiplier;
		setChanged();
		sendData();
	}
	
	//helper functions, no clue what these do tbf
	public static float convertToDirection(float axisSpeed, Direction d) {
		return d.getAxisDirection() == AxisDirection.POSITIVE ? axisSpeed : -axisSpeed;
	}

	public static float convertToLinear(float speed) {
		return speed / 512f;
	}

	public static float convertToAngular(float speed) {
		return speed * 3 / 10f;
	}
	
	@Override
	public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		return false;
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
		return true;
	}
	
	// Custom Propagation

	/**
	 * Specify ratio of transferred rotation from this kinetic component to a
	 * specific other.
	 *
	 * @param target           other Kinetic BE to transfer to
	 * @param stateFrom        this BE's blockstate
	 * @param stateTo          other BE's blockstate
	 * @param diff             difference in position (to.pos - from.pos)
	 * @param connectedViaAxes whether these kinetic blocks are connected via mutual
	 *                         IRotate.hasShaftTowards()
	 * @param connectedViaCogs whether these kinetic blocks are connected via mutual
		 *                         IRotate.hasIntegratedCogwheel()
		 * @return factor of rotation speed from this BE to other. 0 if no rotation is
		 *         transferred, or the standard rules apply (integrated shafts/cogs)
		 */
	public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs) {
		return 0;
	}

		/**
		 * Specify additional locations the rotation propagator should look for
		 * potentially connected components. Neighbour list contains offset positions in
		 * all 6 directions by default.
		 *
		 * @param block
		 * @param state
		 * @param neighbours
		 * @return
		 */
	public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
		if (!canPropagateDiagonally(block, state))
			return neighbours;

		Axis axis = block.getRotationAxis(state);
		BlockPos.betweenClosedStream(new BlockPos(-1, -1, -1), new BlockPos(1, 1, 1))
			.forEach(offset -> {
				if (axis.choose(offset.getX(), offset.getY(), offset.getZ()) != 0)
					return;
				if (offset.distSqr(BlockPos.ZERO) != 2)
					return;
				neighbours.add(worldPosition.offset(offset));
			});
		return neighbours;
	}

		/**
		 * Specify whether this component can propagate speed to the other in any
		 * circumstance. Shaft and cogwheel connections are already handled by internal
		 * logic. Does not have to be specified on both ends, it is assumed that this
		 * relation is symmetrical.
		 *
		 * @param other
		 * @param state
		 * @param otherState
		 * @return true if this and the other component should check their propagation
		 *         factor and are not already connected via integrated cogs or shafts
		 */
	public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) {
			return false;
	}

	protected boolean canPropagateDiagonally(IRotate block, BlockState state) {
		return ICogWheel.isSmallCog(state) || ICogWheel.isLargeCog(state);
	}

	//also copied
	@Override
	public void requestModelDataUpdate() {
		super.requestModelDataUpdate();
		if (!this.remove)
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> InstancedRenderDispatcher.enqueueUpdate(this));
	}

	@OnlyIn(Dist.CLIENT)
	public void tickAudio() {
		float componentSpeed = Math.abs(getSpeed());
		if (componentSpeed < 5f)
			return;
		float pitch = Mth.clamp((componentSpeed / 256f) + .45f, .85f, 1f);
		if (isNoisy())
			SoundScapes.play(AmbienceGroup.KINETIC, worldPosition, pitch);
		Block block = getBlockState().getBlock();
		if (ICogWheel.isSmallCog(block) || ICogWheel.isLargeCog(block) || block instanceof GearboxBlock)
			SoundScapes.play(AmbienceGroup.COG, worldPosition, pitch);
	}
	
	protected boolean isNoisy() {
		return true;
	}

	public int getRotationAngleOffset(Axis axis) {
		return 0;
	}

	public float getSpeed() {
		if(level.isClientSide) {
			ClientKineticNetwork network = getOrCreateClientNetwork();
			if(network == null) return 0;
			return network.getSpeed() * speedMultiplier;
		} else {
			KineticNetwork network = getOrCreateNetwork();
			if(network == null) return 0;
			return network.getSpeed() * speedMultiplier;
		}
	}
	
	public float getNetworkInertia() {
		if(level.isClientSide) {
			ClientKineticNetwork network = getOrCreateClientNetwork();
			if(network == null) return 0;
			return network.getEffectiveInertia();
		} else {
			KineticNetwork network = getOrCreateNetwork();
			if(network == null) return 0;
			return network.getEffectiveInertia();
		}
	}
}
