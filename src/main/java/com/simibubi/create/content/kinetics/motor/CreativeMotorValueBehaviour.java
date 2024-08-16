package com.simibubi.create.content.kinetics.motor;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.utility.Components;
import com.simibubi.create.foundation.utility.Lang;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

public class CreativeMotorValueBehaviour extends ScrollValueBehaviour {

	public CreativeMotorValueBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
		super(label, be, slot);
		between(0, 768);
		withFormatter(v -> formatter(v));
	}

	@Override
	public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
		ImmutableList<Component> rows = ImmutableList.of(
				Lang.translateDirect("kinetics.creative_motor.constant_power"),
				Lang.translateDirect("kinetics.creative_motor.constant_torque"),
				Lang.translateDirect("kinetics.creative_motor.match_speed"));
		ValueSettingsFormatter formatter = new ValueSettingsFormatter(this::formatSettings);
		return new ValueSettingsBoard(label, 256, 32, rows, formatter);
	}

	@Override
	public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
		if (!valueSetting.equals(getValueSettings()))
			playFeedbackSound(this);
		setValue(valueSetting.row() * 256 + valueSetting.value());
	}

	@Override
	public ValueSettings getValueSettings() {
		return new ValueSettings(value / 256, value % 256);
	}

	public MutableComponent formatSettings(ValueSettings settings) {
		switch(settings.row()) {
		case 0:
			return Lang.number(0.2 * (settings.value() - 128)).add(Lang.text(" kFE/s")).component();
		case 1:
			return Lang.number(10 * (settings.value() - 128)).add(Lang.text(" TU")).component();
		case 2:
			return Lang.number(2 * (settings.value() - 128)).add(Lang.text(" rpm")).component();
		}
		return Component.literal("nword");
	}
	
	private static String formatter(int value) {
		if(0 <= value && value < 256) {
			return 0.2 * (value - 128) + "kFE/s";
		} else if(256 <= value && value < 512) {
			return 10 * (value - 384) + "TU";
		} else if(512 <= value && value < 768) {
			return 2 * (value - 640) + "rpm";
		}
		return "?";
	}
	
	@Override
	public String getClipboardKey() {
		return "idk man";
	}

}