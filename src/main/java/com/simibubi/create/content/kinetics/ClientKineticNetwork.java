package com.simibubi.create.content.kinetics;

import java.util.HashSet;
import java.util.Set;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.world.level.LevelAccessor;

public class ClientKineticNetwork {

	private Long id;
	private LevelAccessor level;
	
	private Set<KineticBlockEntity> loadedMembers;
	
	private float speed;
	private float effectiveInertia;
	private int size;
	
	public float renderAngle;
	
	public ClientKineticNetwork(Long id, LevelAccessor level) {
		this.id = id;
		this.level = level;
		loadedMembers = new HashSet<>();
	}

	public void tick() {
		renderAngle += speed * 0.3f;
		if(Math.abs(renderAngle) > 1e10) renderAngle = 0;
	}
	
	public float getSpeed() {
		return speed;
	}
	
	public float getEffectiveInertia() {
		return effectiveInertia;
	}
	
	public int getSize() {
		return size;
	}
	
	public float getRenderAngle(float partialTicks) {
		return renderAngle + speed * 0.3f * partialTicks;
	}
	
	public void readPacket(float speed, float effectiveInertia, int size) {
		float oldSpeed = this.speed;
		this.speed = speed;
		this.effectiveInertia = effectiveInertia;
		this.size = size;
		if(oldSpeed == speed) return;
		for(KineticBlockEntity be : loadedMembers) {
			be.updateRenderInstance();
		}
	}
	
	
}
