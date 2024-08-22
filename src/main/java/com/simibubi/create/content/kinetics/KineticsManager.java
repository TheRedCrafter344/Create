package com.simibubi.create.content.kinetics;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.kinetics.base.DirectionalShaftHalvesBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock;
import com.simibubi.create.content.kinetics.gearbox.GearboxBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlock;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import com.simibubi.create.foundation.utility.Iterate;
import com.simibubi.create.foundation.utility.Pair;
import com.simibubi.create.foundation.utility.WorldHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class KineticsManager {

	private Map<LevelAccessor, Map<Long, KineticNetwork>> networks = new HashMap<>();
	private Map<LevelAccessor, Map<Long, ClientKineticNetwork>> clientNetworks = new HashMap<>();
	
	private Map<LevelAccessor, KineticNetworkSavedData> savedData = new HashMap<>();
	
	public void onLoadWorld(LevelAccessor world) {
		if(world.isClientSide()) {
			clientNetworks.put(world, new HashMap<>());
		} else {
			if(!(world instanceof ServerLevel)) Create.LOGGER.warn("HOLY FUCKING SIGMA!!!!!");
			KineticNetworkSavedData worldData = KineticNetworkSavedData.load((ServerLevel) world);
			savedData.put(world, worldData);
			networks.put(world, worldData.networks);
			worldData.networks = null; // we dont need this anymore :)
		}
	}

	public void onUnloadWorld(LevelAccessor world) {
		networks.remove(world);
		clientNetworks.remove(world);
	}
	
	public void tickNetworks(LevelAccessor world) {
		if(!networks.containsKey(world)) return;
		for(KineticNetwork network : networks.get(world).values()) {
			network.tick();
		}
		if(!savedData.containsKey(world)) return;
		savedData.get(world).setDirty();
	}
	
	public void tickClientNetworks(LevelAccessor world) {
		if(!clientNetworks.containsKey(world)) return;
		for(ClientKineticNetwork network : clientNetworks.get(world).values()) {
			network.tick();
		}
	}
	
	public Collection<KineticNetwork> getAllNetworks(LevelAccessor world) {
		return networks.get(world).values();
	}
	
	public KineticNetwork getOrCreateNetworkFor(KineticBlockEntity be) {
		return getOrCreateNetwork(be.getLevel(), be.network);
	}
	
	public KineticNetwork getOrCreateNetwork(LevelAccessor level, Long id) {
		if(level.isClientSide()) return null;
		KineticNetwork network;
		Map<Long, KineticNetwork> map = networks.computeIfAbsent(level, $ -> new HashMap<>());
		if (id == null)
			return null;

		if (!map.containsKey(id)) {
			network = new KineticNetwork(id, level);
			map.put(id, network);
			return network;
		}
		network = map.get(id);
		return network;
	}
	
	public void removeNetwork(LevelAccessor level, Long id) {
		if(level.isClientSide() || id == null || !networks.containsKey(level)) return;
		networks.get(level).remove(id);
	}
	
	public ClientKineticNetwork getOrCreateNetworkClientFor(KineticBlockEntity be) {
		return getOrCreateNetworkClient(be.getLevel(), be.network);
	}
	
	public ClientKineticNetwork getOrCreateNetworkClient(LevelAccessor level, Long id) {
		if(!level.isClientSide()) return null;
		ClientKineticNetwork network;
		Map<Long, ClientKineticNetwork> map = clientNetworks.computeIfAbsent(level, $ -> new HashMap<>());
		if(id == null)
			return null;
		if(!map.containsKey(id)) {
			network = new ClientKineticNetwork(id, level);
			map.put(id, network);
			return network;
		}
		network = map.get(id);
		return network;
	}
	
	
	public void handleAdded(Level worldIn, BlockPos pos, KineticBlockEntity addedTE) {
		if(worldIn.isClientSide || !worldIn.isLoaded(pos)) return;
		Long addToNetwork = null;
		float speedMultiplier = 0;
		List<Long> networksToMerge = new ArrayList<>();
		List<Float> mergeMultipliers2 = new ArrayList<>();
		List<Float> gearRatios = new ArrayList<>();
		//first is list with networks, second is list of blocks without
		Pair<List<KineticBlockEntity>, List<KineticBlockEntity>> neighbours = getConnectedNeighbours(addedTE);
		for (KineticBlockEntity neighbourTE : neighbours.getFirst()) {
			if(addToNetwork == null) {
				//add to first network we find
				addToNetwork = neighbourTE.network;
				speedMultiplier = neighbourTE.getOrCreateNetwork().getMultiplierFor(neighbourTE) * getRotationSpeedModifier(neighbourTE, addedTE);
			} else {
				if(neighbourTE.network.equals(addToNetwork)) {
					//if speeds are incompatible, break this
					float newSpeedMultiplier = neighbourTE.getOrCreateNetwork().getMultiplierFor(neighbourTE) * getRotationSpeedModifier(neighbourTE, addedTE);
					if(Math.abs(newSpeedMultiplier - speedMultiplier) > 0.001f) {
						worldIn.destroyBlock(pos, true);
						return;
					}
				} else {
					if(!networksToMerge.contains(neighbourTE.network)) {
						networksToMerge.add(neighbourTE.network);
						mergeMultipliers2.add(neighbourTE.getOrCreateNetwork().getMultiplierFor(neighbourTE));
						gearRatios.add(getRotationSpeedModifier(addedTE, neighbourTE));
					}
				}
			}
		}
		if(addToNetwork != null) {
			addedTE.setNetwork(addToNetwork, speedMultiplier, true);
			for (int i = 0; i < networksToMerge.size(); i++) {
				mergeNetworks(worldIn, addToNetwork, networksToMerge.get(i), true, speedMultiplier, mergeMultipliers2.get(i), gearRatios.get(i));
			}
			for(KineticBlockEntity neighbourTE : neighbours.getSecond()) {
				handleAdded(worldIn, neighbourTE.getBlockPos(), neighbourTE);
			}
		} else if(addedTE.shouldCreateNetwork() && addedTE.network == null) {
			Long newNetworkId = pos.asLong();
			while(networks.get(worldIn).get(newNetworkId) != null) {
				newNetworkId++;
			}
			addedTE.setNetwork(newNetworkId, 1, false);
			for(KineticBlockEntity neighbourTE : neighbours.getSecond()) {
				handleAdded(worldIn, neighbourTE.getBlockPos(), neighbourTE);
			}
		}
	}
	
	public void handleRemoved(Level worldIn, BlockPos pos, KineticBlockEntity removedTE) {
		if(worldIn.isClientSide || !worldIn.isLoaded(pos)) return;
		if(!removedTE.hasNetwork()) return;
		List<KineticBlockEntity> unvisitedNeighbours = getConnectedNeighbours(removedTE).getFirst();
		if(unvisitedNeighbours.size() <= 1) return;
		List<List<KineticBlockEntity>> networkPartitions = new ArrayList<>();
		LinkedList<KineticBlockEntity> currentlySearching = new LinkedList<>();
		final int initialNetworkSize = removedTE.getOrCreateNetwork().getSize();
		int partitionedSize = 0;
		while (!unvisitedNeighbours.isEmpty()) {
			List<KineticBlockEntity> currentNetworkPartition = new ArrayList<>(initialNetworkSize - partitionedSize);
			currentlySearching.add(unvisitedNeighbours.get(0));
			while(!currentlySearching.isEmpty()) {
				KineticBlockEntity ke = currentlySearching.poll();
				currentNetworkPartition.add(ke);
				unvisitedNeighbours.remove(ke);
				for (KineticBlockEntity next : getConnectedNeighbours(ke).getFirst()) {
					if (!currentNetworkPartition.contains(next) && !currentlySearching.contains(next)
							&& next != removedTE)
						currentlySearching.add(next);
				}
			}
			partitionedSize += currentNetworkPartition.size();
			networkPartitions.add(currentNetworkPartition);
		}
		if(networkPartitions.size() <= 1) return;
		KineticNetwork network = removedTE.getOrCreateNetwork();
		removedTE.setNetwork(null, 0, false);
		for(int i = 1; i < networkPartitions.size(); i++) {
			List<KineticBlockEntity> currentNetworkPartition = networkPartitions.get(i);
			Long newNetworkId = pos.asLong();
			while(networks.get(worldIn).get(newNetworkId) != null) newNetworkId++;
			KineticNetwork newNetwork = new KineticNetwork(newNetworkId, worldIn);
			newNetwork.initialize(network.getSpeed(), 0, 0);
			networks.get(worldIn).put(newNetworkId, newNetwork);
			for(KineticBlockEntity ke : currentNetworkPartition) {
				ke.setNetwork(newNetworkId, network.getMultiplierFor(ke), false);
			}
		}
	}
	
	//assume that no network changes have to happen, conserve momentum.
	public void updateSpeedMultipliers(Level worldIn, BlockPos pos, KineticBlockEntity changedTE, boolean conserveMomentum) {
		//TODO
		
	}
	
	//network2 merges into network 1.
	//initialMultiplier1,2 is the speed multiplier of the block which is in contact on each side
	//gearRatio is the rotation modifier from block 1 to block 2.
	public void mergeNetworks(LevelAccessor worldIn, Long network1, Long network2, boolean conserveMomentum, float initialMultiplier1, float initialMultiplier2, float gearRatio) {
		if(network1 == network2) return;
		KineticNetwork net1 = networks.get(worldIn).get(network1);
		KineticNetwork net2 = networks.get(worldIn).get(network2);
		float rs1 = gearRatio * initialMultiplier1;
		if(conserveMomentum) {
			float i1s2s2 = net1.getEffectiveInertia() * initialMultiplier2 * initialMultiplier2;
			float i2rs1 = net2.getEffectiveInertia() * rs1;
			net1.setSpeed((i1s2s2*net1.getSpeed() + i2rs1*initialMultiplier2*net2.getSpeed()) / (i1s2s2 + i2rs1*rs1));
		}
		Iterator<Entry<KineticBlockEntity, Float>> iter = net2.loadedMembers.entrySet().iterator();
		float speedMultiplierMultiplier = rs1 / initialMultiplier2;
		while(iter.hasNext()) {
			Entry<KineticBlockEntity, Float> be = iter.next();
			float thisMultiplier = be.getValue();
			iter.remove();
			be.getKey().setNetwork(network1, thisMultiplier * speedMultiplierMultiplier, false);
		}
		networks.get(worldIn).remove(network2);
		net1.updateAll();
	}
	
	/**
	 * Determines the change in rotation between two attached kinetic entities. For
	 * instance, an axis connection returns 1 while a 1-to-1 gear connection
	 * reverses the rotation and therefore returns -1.
	 *
	 * @param from
	 * @param to
	 * @return
	 */
	public static float getRotationSpeedModifier(KineticBlockEntity from, KineticBlockEntity to) {
		final BlockState stateFrom = from.getBlockState();
		final BlockState stateTo = to.getBlockState();

		Block fromBlock = stateFrom.getBlock();
		Block toBlock = stateTo.getBlock();
		if (!(fromBlock instanceof IRotate && toBlock instanceof IRotate))
			return 0;

		final IRotate definitionFrom = (IRotate) fromBlock;
		final IRotate definitionTo = (IRotate) toBlock;
		final BlockPos diff = to.getBlockPos()
			.subtract(from.getBlockPos());
		final Direction direction = Direction.getNearest(diff.getX(), diff.getY(), diff.getZ());
		final Level world = from.getLevel();

		boolean alignedAxes = true;
		for (Axis axis : Axis.values())
			if (axis != direction.getAxis())
				if (axis.choose(diff.getX(), diff.getY(), diff.getZ()) != 0)
					alignedAxes = false;

		boolean connectedByAxis =
			alignedAxes && definitionFrom.hasShaftTowards(world, from.getBlockPos(), stateFrom, direction)
				&& definitionTo.hasShaftTowards(world, to.getBlockPos(), stateTo, direction.getOpposite());

		boolean connectedByGears = ICogWheel.isSmallCog(stateFrom)
			&& ICogWheel.isSmallCog(stateTo);

		float custom = from.propagateRotationTo(to, stateFrom, stateTo, diff, connectedByAxis, connectedByGears);
		float custom2 = to.propagateRotationTo(from, stateTo, stateFrom, diff.multiply(-1), connectedByAxis, connectedByGears);
		if (custom != 0 && custom2 != 0) {
			return custom / custom2;
		} else if(custom != 0) {
			return custom;
		} else if(custom2 != 0) {
			return 1f / custom2;
		}

		// Axis <-> Axis
		if (connectedByAxis) {
			float axisModifier = getAxisModifier(to, direction.getOpposite());
			if (axisModifier != 0)
				axisModifier = 1 / axisModifier;
			return getAxisModifier(from, direction) * axisModifier;
		}

		// Attached Encased Belts
		if (fromBlock instanceof ChainDriveBlock && toBlock instanceof ChainDriveBlock) {
			boolean connected = ChainDriveBlock.areBlocksConnected(stateFrom, stateTo, direction);
			return connected ? ChainDriveBlock.getRotationSpeedModifier(from, to) : 0;
		}

		// Large Gear <-> Large Gear
		if (isLargeToLargeGear(stateFrom, stateTo, diff)) {
			Axis sourceAxis = stateFrom.getValue(AXIS);
			Axis targetAxis = stateTo.getValue(AXIS);
			int sourceAxisDiff = sourceAxis.choose(diff.getX(), diff.getY(), diff.getZ());
			int targetAxisDiff = targetAxis.choose(diff.getX(), diff.getY(), diff.getZ());

			return sourceAxisDiff > 0 ^ targetAxisDiff > 0 ? -1 : 1;
		}

		// Gear <-> Large Gear
		if (ICogWheel.isLargeCog(stateFrom) && ICogWheel.isSmallCog(stateTo))
			if (isLargeToSmallCog(stateFrom, stateTo, definitionTo, diff))
				return -2f;
		if (ICogWheel.isLargeCog(stateTo) && ICogWheel.isSmallCog(stateFrom))
			if (isLargeToSmallCog(stateTo, stateFrom, definitionFrom, diff))
				return -.5f;

		// Gear <-> Gear
		if (connectedByGears) {
			if (diff.distManhattan(BlockPos.ZERO) != 1)
				return 0;
			if (ICogWheel.isLargeCog(stateTo))
				return 0;
			if (direction.getAxis() == definitionFrom.getRotationAxis(stateFrom))
				return 0;
			if (definitionFrom.getRotationAxis(stateFrom) == definitionTo.getRotationAxis(stateTo))
				return -1;
		}
		return 0;
	}
	
	private static boolean isLargeToLargeGear(BlockState from, BlockState to, BlockPos diff) {
		if (!ICogWheel.isLargeCog(from) || !ICogWheel.isLargeCog(to))
			return false;
		Axis fromAxis = from.getValue(AXIS);
		Axis toAxis = to.getValue(AXIS);
		if (fromAxis == toAxis)
			return false;
		for (Axis axis : Axis.values()) {
			int axisDiff = axis.choose(diff.getX(), diff.getY(), diff.getZ());
			if (axis == fromAxis || axis == toAxis) {
				if (axisDiff == 0)
					return false;

			} else if (axisDiff != 0)
				return false;
		}
		return true;
	}
	
	private static float getAxisModifier(KineticBlockEntity be, Direction direction) {
		if (!(be instanceof DirectionalShaftHalvesBlockEntity))
			return 1;
		return ((DirectionalShaftHalvesBlockEntity) be).getRotationSpeedModifier(direction);
	}
	
	private static boolean isLargeToSmallCog(BlockState from, BlockState to, IRotate defTo, BlockPos diff) {
		Axis axisFrom = from.getValue(AXIS);
		if (axisFrom != defTo.getRotationAxis(to))
			return false;
		if (axisFrom.choose(diff.getX(), diff.getY(), diff.getZ()) != 0)
			return false;
		for (Axis axis : Axis.values()) {
			if (axis == axisFrom)
				continue;
			if (Math.abs(axis.choose(diff.getX(), diff.getY(), diff.getZ())) != 1)
				return false;
		}
		return true;
	}
	
	private static boolean isLargeCogToSpeedController(BlockState from, BlockState to, BlockPos diff) {
		if (!ICogWheel.isLargeCog(from) || !AllBlocks.ROTATION_SPEED_CONTROLLER.has(to))
			return false;
		if (!diff.equals(BlockPos.ZERO.below()))
			return false;
		Axis axis = from.getValue(CogWheelBlock.AXIS);
		if (axis.isVertical())
			return false;
		if (to.getValue(SpeedControllerBlock.HORIZONTAL_AXIS) == axis)
			return false;
		return true;
	}
	private static KineticBlockEntity findConnectedNeighbour(KineticBlockEntity currentTE, BlockPos neighbourPos) {
		BlockState neighbourState = currentTE.getLevel()
			.getBlockState(neighbourPos);
		if (!(neighbourState.getBlock() instanceof IRotate))
			return null;
		if (!neighbourState.hasBlockEntity())
			return null;
		BlockEntity neighbourBE = currentTE.getLevel()
			.getBlockEntity(neighbourPos);
		if (!(neighbourBE instanceof KineticBlockEntity))
			return null;
		KineticBlockEntity neighbourKBE = (KineticBlockEntity) neighbourBE;
		if (!(neighbourKBE.getBlockState()
			.getBlock() instanceof IRotate))
			return null;
		if (!isConnected(currentTE, neighbourKBE) && !isConnected(neighbourKBE, currentTE))
			return null;
		return neighbourKBE;
	}

	public static boolean isConnected(KineticBlockEntity from, KineticBlockEntity to) {
		final BlockState stateFrom = from.getBlockState();
		final BlockState stateTo = to.getBlockState();
		return getRotationSpeedModifier(from, to) != 0
			|| from.isCustomConnection(to, stateFrom, stateTo);
	}

	private static Pair<List<KineticBlockEntity>, List<KineticBlockEntity>> getConnectedNeighbours(KineticBlockEntity be) {
		List<KineticBlockEntity> neighboursWithNetwork = new LinkedList<>();
		List<KineticBlockEntity> neighboursWithoutNetwork = new LinkedList<>();
		for (BlockPos neighbourPos : getPotentialNeighbourLocations(be)) {
			final KineticBlockEntity neighbourBE = findConnectedNeighbour(be, neighbourPos);
			if (neighbourBE == null)
				continue;
			if(neighbourBE.network != null) {
				neighboursWithNetwork.add(neighbourBE);
			} else {
				neighboursWithoutNetwork.add(neighbourBE);
			}
		}
		return Pair.of(neighboursWithNetwork, neighboursWithoutNetwork);
	}

	private static List<BlockPos> getPotentialNeighbourLocations(KineticBlockEntity be) {
		List<BlockPos> neighbours = new LinkedList<>();
		BlockPos blockPos = be.getBlockPos();
		Level level = be.getLevel();

		if (!level.isLoaded(blockPos))
			return neighbours;

		for (Direction facing : Iterate.directions) {
			BlockPos relative = blockPos.relative(facing);
			if (level.isLoaded(relative))
				neighbours.add(relative);
		}

		BlockState blockState = be.getBlockState();
		if (!(blockState.getBlock() instanceof IRotate))
			return neighbours;
		IRotate block = (IRotate) blockState.getBlock();
		return be.addPropagationLocations(block, blockState, neighbours);
	}
	
}
