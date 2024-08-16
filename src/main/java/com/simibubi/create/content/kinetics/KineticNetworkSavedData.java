package com.simibubi.create.content.kinetics;

import java.util.HashMap;
import java.util.Map;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.NBTHelper;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

public class KineticNetworkSavedData extends SavedData {

	public Map<Long, KineticNetwork> networks = new HashMap<>();
	
	private LevelAccessor level;
	
	private KineticNetworkSavedData(LevelAccessor level) {
		this.level = level;
	}
	
	@Override
	public CompoundTag save(CompoundTag nbt) {
		nbt.put("networks", NBTHelper.writeCompoundList(Create.KINETICS_MANAGER.getAllNetworks(level), network -> {
			CompoundTag tag = new CompoundTag();
			tag.putLong("id", network.getId());
			tag.putFloat("speed", network.getSpeed());
			tag.putFloat("inertia", network.getEffectiveInertia());
			tag.putInt("size", network.getSize());
			return tag;
		}));
		return nbt;
	}
	
	private static KineticNetworkSavedData load(CompoundTag nbt, LevelAccessor level) {
		KineticNetworkSavedData data = new KineticNetworkSavedData(level);
		NBTHelper.iterateCompoundList(nbt.getList("networks", Tag.TAG_COMPOUND),
				tag -> {
					long id = tag.getLong("id");
					float speed = tag.getFloat("speed");
					float inertia = tag.getFloat("inertia");
					int size = tag.getInt("size");
					KineticNetwork network = new KineticNetwork(id, level);
					network.initialize(speed, inertia, size);
					data.networks.put(id, network);
				});
		return data;
		
	}
	
	public static KineticNetworkSavedData load(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(nbt -> KineticNetworkSavedData.load(nbt, level), () -> new KineticNetworkSavedData(level), "kinetic_networks");
	}
}
