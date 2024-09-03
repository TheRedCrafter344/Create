package com.simibubi.create.content.contraptions;

public interface IProvideActorEnergy {

	public float getStoredEnergy();
	
	public void setStoredEnergy(float energy);
	
	//returns the energy actually pulled
	public float pullStoredEnergy(float pullEnergy, boolean pullFromNetwork);
}
