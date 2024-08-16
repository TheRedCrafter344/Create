package com.simibubi.create.content.kinetics;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.networking.SimplePacketBase;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent.Context;

public class KineticNetworkPacket extends SimplePacketBase {

	public long networkId;
	public float speed;
	public float inertia;
	public int size;
	
	public KineticNetworkPacket(long networkId, float speed, float inertia, int size) {
		this.networkId = networkId;
		this.speed = speed;
		this.inertia = inertia;
		this.size = size;
	}
	
	public KineticNetworkPacket(FriendlyByteBuf buffer) {
		this.networkId = buffer.readLong();
		this.speed = buffer.readFloat();
		this.inertia = buffer.readFloat();
		this.size = buffer.readInt();
	}
	
	@Override
	public void write(FriendlyByteBuf buffer) {
		buffer.writeLong(networkId);
		buffer.writeFloat(speed);
		buffer.writeFloat(inertia);
		buffer.writeInt(size);
	}

	@Override
	public boolean handle(Context context) {
		context.enqueueWork(() -> {
			ClientKineticNetwork network = Create.KINETICS_MANAGER.getOrCreateNetworkClient(Minecraft.getInstance().level, networkId);
			network.readPacket(speed, inertia, size);
		});
		return true;
	}
}
