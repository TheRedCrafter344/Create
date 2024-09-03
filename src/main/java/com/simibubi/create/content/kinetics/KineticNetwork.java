package com.simibubi.create.content.kinetics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.simibubi.create.AllPackets;
import com.simibubi.create.Create;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.network.PacketDistributor;

public class KineticNetwork {

	private Long id;
	private LevelAccessor level;
	
	//maps entities to their speed multipliers
	public Map<KineticBlockEntity, Float> loadedMembers;
	
	//if one is loaded, all must be loaded.
	public List<LevelChunk> chunksToForceload;
	public int forceloadedChunks = 0;
	
	private static final int TICKS_PER_CLIENT_UPDATE = 5;
	private int ticksTilSendToClient = TICKS_PER_CLIENT_UPDATE;
	
	private float speed; //rpm = 0.10472 rad/s
	
	private float loadedEffectiveInertia; //IU = ()
	//TU (1TU = 1rpm / s / IU)
	//Note of course that effective values are not the real physical values.
	
	//declare:: 1 FE = energy of 1 IU moving at 1 rpm = IU * rpm^2
	
	private float unloadedEffectiveInertia;
	private int unloadedMembers;
	
	public KineticNetwork(Long id, LevelAccessor level) {
		this.id = id;
		this.level = level;
		loadedMembers = new HashMap<KineticBlockEntity, Float>();
		chunksToForceload = new ArrayList<>();
	}
	
	public void tick() {
		if(Math.abs(getEffectiveInertia()) < 0.01f) return;
		if(getSize() == 0) return;
		float timeStep = 0.05f;
		float oldSpeed = speed;
		float inertia = getEffectiveInertia();
		/*
		float k1 = getEffectiveTorque(speed) / inertia;
		float k2 = getEffectiveTorque(speed + timeStep*k1/2) / inertia;
		float k3 = getEffectiveTorque(speed + timeStep*k2/2) / inertia;
		float k4 = getEffectiveTorque(speed + timeStep*k3) / inertia;
		
		speed += timeStep * (1/6f*k1 + 1/3f*k2 + 1/3f*k3 + 1/6f*k4);
		*/
		
		speed += timeStep * getEffectiveTorque(speed) / inertia;
		
		//if(speed > MAX_SPEED) speed = MAX_SPEED;
		if(oldSpeed != 0 && Math.signum(speed) != Math.signum(oldSpeed)) speed = 0;
		if(speed != oldSpeed) {
			for(KineticBlockEntity be : loadedMembers.keySet()) be.onSpeedChanged(oldSpeed * loadedMembers.get(be));
		}
		ticksTilSendToClient--;
		if(ticksTilSendToClient <= 0) {
			ticksTilSendToClient = TICKS_PER_CLIENT_UPDATE;
			sendToClient();
		}
	}
	
	//updates and syncs network id and speed mutlipliers.
	public void updateAll() {
		for(KineticBlockEntity be : loadedMembers.keySet()) be.updateSpeedMultiplier();
	}
	
	public void sendToClient() {
		if(!(level instanceof Level)) return;
		Level level = (Level) this.level;
		AllPackets.getChannel().send(PacketDistributor.DIMENSION.with(() -> level.dimension()), new KineticNetworkPacket(id, speed, getEffectiveInertia(), getSize()));
	}
	
	public void initialize(float speed, float effectiveInertia, int members) {
		this.speed = speed;
		this.unloadedEffectiveInertia = effectiveInertia;
		this.unloadedMembers = members;
	}
	
	public void addBlockEntity(KineticBlockEntity be, float speedMultiplier, boolean conserveMomentum) {
		if(loadedMembers.containsKey(be)) return;
		float addedInertia = be.getInertia() * speedMultiplier * speedMultiplier;
		if(conserveMomentum) stickEffectiveInertia(addedInertia);
		loadedMembers.put(be, speedMultiplier);
		loadedEffectiveInertia += addedInertia;
	}
	
	public void remove(KineticBlockEntity be) {
		if(!loadedMembers.containsKey(be)) return;
		float speedMultiplier = loadedMembers.get(be);
		loadedMembers.remove(be);
		if(loadedMembers.isEmpty()) {
			Create.KINETICS_MANAGER.removeNetwork(level, id);
		}
		loadedEffectiveInertia -= be.getInertia() * speedMultiplier * speedMultiplier;
	}
	
	public void loadBlockEntity(KineticBlockEntity be, float speedMultiplier) {
		if(loadedMembers.containsKey(be)) return;
		for(KineticBlockEntity pbe : loadedMembers.keySet()) {
			if(pbe.getBlockPos().equals(be.getBlockPos())) {
				loadedMembers.put(be, speedMultiplier);
				remove(pbe);
				return;
			}
		}
		loadedMembers.put(be, speedMultiplier);
		unloadedMembers--;
		unloadedEffectiveInertia -= be.getInertia() * speedMultiplier * speedMultiplier;
		loadedEffectiveInertia += be.getInertia() * speedMultiplier * speedMultiplier;
		if(unloadedMembers < 0) unloadedMembers = 0;
		if(unloadedEffectiveInertia < 0) unloadedEffectiveInertia = 0;
	}
	
	//changes the speed as if inertia was added, conserving momentum.
	public void stickEffectiveInertia(float modifiedInertia) {
		speed *= getEffectiveInertia() / (getEffectiveInertia() + modifiedInertia);
	}
	
	public float getEffectiveInertia() {
		return loadedEffectiveInertia + unloadedEffectiveInertia;
	}
	
	public void updateEffectiveInertia() {
		float inertia = 0;
		for(Entry<KineticBlockEntity, Float> member : loadedMembers.entrySet()) {
			inertia += member.getKey().getInertia() * member.getValue() * member.getValue();
		}
		loadedEffectiveInertia = inertia;
	}
	
	public float getMultiplierFor(KineticBlockEntity be) {
		if(!loadedMembers.containsKey(be)) return 0;
		return loadedMembers.get(be);
	}
	
	public float getSpeed() {
		return speed;
	}
	
	public float getEffectiveTorque(float speed) {
		float torque = 0;
		for(Entry<KineticBlockEntity, Float> member : loadedMembers.entrySet()) {
			torque += member.getKey().getTorque(speed * member.getValue()) * member.getValue();
		}
		return torque;
	}
	
	public int getSize() {
		return loadedMembers.size() + unloadedMembers;
	}
	
	public Long getId() {
		return id;
	}
	
	public void setSpeed(float speed) {
		float oldSpeed = this.speed;
		this.speed = speed;
		if(speed != oldSpeed) {
			for(KineticBlockEntity be : loadedMembers.keySet()) be.onSpeedChanged(oldSpeed * loadedMembers.get(be));
		}
	}
}
