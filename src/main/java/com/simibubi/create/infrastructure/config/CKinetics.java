package com.simibubi.create.infrastructure.config;

import static com.simibubi.create.AllBlocks.*;

import com.simibubi.create.content.contraptions.ContraptionData;
import com.simibubi.create.content.contraptions.ContraptionMovementSetting;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.config.ConfigBase;

public class CKinetics extends ConfigBase {

	
	public final ConfigInt maxRotationSpeed = i(256, 64, "maxRotationSpeed", Comments.rpm, Comments.maxRotationSpeed);
	
	public final ConfigInt minimumWindmillSails = i(8, 0, "minimumWindmillSails", Comments.minimumWindmillSails); //TODO move to windmill config
	public final ConfigInt windmillSailsPerRPM = i(8, 1, "windmillSailsPerRPM", Comments.windmillSailsPerRPM);
	public final ConfigInt maxEjectorDistance = i(32, 0, "maxEjectorDistance", Comments.maxEjectorDistance); //TODO move to ejector config
	public final ConfigInt ejectorScanInterval = i(120, 10, "ejectorScanInterval", Comments.ejectorScanInterval);

	public float getInertia(KineticBlock block) {
		if(block == SHAFT.get() || block == ANDESITE_ENCASED_SHAFT.get() || block == BRASS_ENCASED_SHAFT.get() || block == METAL_GIRDER_ENCASED_SHAFT.get()) 
			return shaftInertia.getF();
		if(block == COGWHEEL.get() || block == ANDESITE_ENCASED_COGWHEEL.get() || block == BRASS_ENCASED_COGWHEEL.get())
			return smallCogInertia.getF();
		if(block == LARGE_COGWHEEL.get() || block == ANDESITE_ENCASED_LARGE_COGWHEEL.get() || block == BRASS_ENCASED_LARGE_COGWHEEL.get())
			return largeCogInertia.getF();
		if(block == BELT.get())
			return beltInertia.getF();
		if(block == ENCASED_CHAIN_DRIVE.get())
			return chainDriveInertia.getF();
		if(block == MECHANICAL_CRAFTER.get())
			return crafterInertia.getF();
		if(block == CRUSHING_WHEEL.get())
			return crusherInertia.getF();
		if(block == HAND_CRANK.get())
			return handcrankInertia.getF();
		if(block == DEPLOYER.get())
			return deployerInertia.getF();
		if(block == MECHANICAL_DRILL.get())
			return drillInertia.getF();
		if(block == ENCASED_FAN.get())
			return fanInertia.getF();
		if(block == FLYWHEEL.get())
			return flywheelInertia.getF();
		if(block == GEARBOX.get())
			return gearboxInertia.getF();
		if(block == MECHANICAL_ARM.get())
			return mechanicalArmInertia.getF();
		if(block == MILLSTONE.get())
			return millstoneInertia.getF();
		if(block == MECHANICAL_MIXER.get())
			return mixerInertia.getF();
		if(block == MECHANICAL_PRESS.get())
			return pressInertia.getF();
		if(block == MECHANICAL_SAW.get())
			return sawInertia.getF();
		if(block == POWERED_SHAFT.get())
			return steamEngineInertia.getF();
		if(block == WATER_WHEEL.get() || block == LARGE_WATER_WHEEL.get())
			return waterwheelInertia.getF();
		return 10;
	}
	
	public float getFriction(KineticBlock block) {
		if(block == SHAFT.get() || block == ANDESITE_ENCASED_SHAFT.get() || block == BRASS_ENCASED_SHAFT.get() || block == METAL_GIRDER_ENCASED_SHAFT.get()) 
			return shaftFriction.getF();
		if(block == COGWHEEL.get() || block == ANDESITE_ENCASED_COGWHEEL.get() || block == BRASS_ENCASED_COGWHEEL.get())
			return smallCogFriction.getF();
		if(block == LARGE_COGWHEEL.get() || block == ANDESITE_ENCASED_LARGE_COGWHEEL.get() || block == BRASS_ENCASED_LARGE_COGWHEEL.get())
			return largeCogFriction.getF();
		if(block == BELT.get())
			return beltFriction.getF();
		if(block == ENCASED_CHAIN_DRIVE.get())
			return chainDriveFriction.getF();
		if(block == MECHANICAL_CRAFTER.get())
			return crafterFriction.getF();
		if(block == CRUSHING_WHEEL.get())
			return crusherFriction.getF();
		if(block == HAND_CRANK.get())
			return handcrankFriction.getF();
		if(block == DEPLOYER.get())
			return deployerFriction.getF();
		if(block == MECHANICAL_DRILL.get())
			return drillFriction.getF();
		if(block == ENCASED_FAN.get())
			return fanFriction.getF();
		if(block == FLYWHEEL.get())
			return flywheelFriction.getF();
		if(block == GEARBOX.get())
			return gearboxFriction.getF();
		if(block == MECHANICAL_ARM.get())
			return mechanicalArmFriction.getF();
		if(block == MILLSTONE.get())
			return millstoneFriction.getF();
		if(block == MECHANICAL_MIXER.get())
			return mixerFriction.getF();
		if(block == MECHANICAL_PRESS.get())
			return pressFriction.getF();
		if(block == MECHANICAL_SAW.get())
			return sawFriction.getF();
		if(block == POWERED_SHAFT.get())
			return steamEngineFriction.getF();
		if(block == WATER_WHEEL.get() || block == LARGE_WATER_WHEEL.get())
			return waterwheelFriction.getF();
		return 0;
	}
	
	public float getResistance(KineticBlock block) {
		if(block == SHAFT.get() || block == ANDESITE_ENCASED_SHAFT.get() || block == BRASS_ENCASED_SHAFT.get() || block == METAL_GIRDER_ENCASED_SHAFT.get()) 
			return shaftResistance.getF();
		if(block == COGWHEEL.get() || block == ANDESITE_ENCASED_COGWHEEL.get() || block == BRASS_ENCASED_COGWHEEL.get())
			return smallCogResistance.getF();
		if(block == LARGE_COGWHEEL.get() || block == ANDESITE_ENCASED_LARGE_COGWHEEL.get() || block == BRASS_ENCASED_LARGE_COGWHEEL.get())
			return largeCogResistance.getF();
		if(block == BELT.get())
			return beltResistance.getF();
		if(block == ENCASED_CHAIN_DRIVE.get())
			return chainDriveResistance.getF();
		if(block == MECHANICAL_CRAFTER.get())
			return crafterResistance.getF();
		if(block == CRUSHING_WHEEL.get())
			return crusherResistance.getF();
		if(block == HAND_CRANK.get())
			return handcrankResistance.getF();
		if(block == DEPLOYER.get())
			return deployerResistance.getF();
		if(block == MECHANICAL_DRILL.get())
			return drillResistance.getF();
		if(block == ENCASED_FAN.get())
			return fanResistance.getF();
		if(block == FLYWHEEL.get())
			return flywheelResistance.getF();
		if(block == GEARBOX.get())
			return gearboxResistance.getF();
		if(block == MECHANICAL_ARM.get())
			return mechanicalArmResistance.getF();
		if(block == MILLSTONE.get())
			return millstoneResistance.getF();
		if(block == MECHANICAL_MIXER.get())
			return mixerResistance.getF();
		if(block == MECHANICAL_PRESS.get())
			return pressResistance.getF();
		if(block == MECHANICAL_SAW.get())
			return sawResistance.getF();
		if(block == POWERED_SHAFT.get())
			return steamEngineResistance.getF();
		if(block == WATER_WHEEL.get() || block == LARGE_WATER_WHEEL.get())
			return 0;
		return 0;
	}

	public final ConfigGroup shaft = group(1, "shaft", "Shaft");
	public final ConfigFloat shaftInertia = f(5, 1, "shaftInertia", "Rotational Inertia");
	public final ConfigFloat shaftFriction = f(0.1f, 0, "shaftFriction", "Kinetic Friction, in TU");
	public final ConfigFloat shaftResistance = f(0.005f, 0, "shaftResistance", "Wind Resistance, in TU/rpm");
	
	public final ConfigGroup smallCog = group(1, "smallCog", "Small Cogwheel");
	public final ConfigFloat smallCogInertia = f(10, 1, "smallCogInertia", "Rotational Inertia");
	public final ConfigFloat smallCogFriction = f(0.3f, 0, "smallCogFriction", "Kinetic Friction, in TU");
	public final ConfigFloat smallCogResistance = f(0.015f, 0, "smallCogResistance", "Wind Resistance, in TU/rpm");
	
	public final ConfigGroup largeCog = group(1, "largeCog", "Large Cogwheel");
	public final ConfigFloat largeCogInertia = f(20, 1, "largeCogInertia", "Rotational Inertia");
	public final ConfigFloat largeCogFriction = f(0.3f, 0, "largeCogFriction", "Kinetic Friction, in TU");
	public final ConfigFloat largeCogResistance = f(0.03f, 0, "largeCogResistance", "Wind Resistance, in TU/rpm");
	
	public final ConfigGroup belt = group(1, "belt", "Mechanical Belt");
	public final ConfigFloat beltInertia = f(1, 1, "beltInertia", "Rotational Inertia");
	public final ConfigFloat beltFriction = f(0.6f, 0, "beltFriction", "Kinetic Friction, in TU");
	public final ConfigFloat beltResistance = f(0.005f, 0, "beltResistance", "Wind Resistance, in TU/rpm");
	public final ConfigInt maxBeltLength = i(20, 5, "maxBeltLength", Comments.maxBeltLength);
	
	public final ConfigGroup chainDrive = group(1, "chainDrive", "Encased Chain Drive");
	public final ConfigFloat chainDriveInertia = f(8, 1, "chainDriveInertia", "Rotational Inertia");
	public final ConfigFloat chainDriveFriction = f(0.2f, 0, "chainDriveFriction", "Kinetic Friction, in TU");
	public final ConfigFloat chainDriveResistance = f(0.005f, 0, "chainDriveResistance", "Wind Resistance, in TU/rpm");
	
	public final ConfigGroup crafter = group(1, "crafter", "Mechanical Crafter");
	public final ConfigFloat crafterInertia = f(10, 1, "crafterInertia", "Rotational Inertia");
	public final ConfigFloat crafterFriction = f(0.3f, 0, "crafterFriction", "Kinetic Friction, in TU");
	public final ConfigFloat crafterResistance = f(0.015f, 0, "crafterResistance", "Wind Resistance, in TU/rpm");
	public final ConfigFloat crafterTorque = f(4, 0, "crafterTorque", "Torque when processing, in TU");
	
	public final ConfigGroup handcrank = group(1, "handcrank", "Handcrank");
	public final ConfigFloat handcrankInertia = f(10, 1, "handcrankInertia", "Rotational Inertia");
	public final ConfigFloat handcrankFriction = f(0.3f, 0, "handcrankFriction", "Kinetic Friction, in TU");
	public final ConfigFloat handcrankResistance = f(0.015f, 0, "handcrankResistance", "Wind Resistance, in TU/rpm");
	public final ConfigFloat handcrankPower = f(512, 0, "handcrankPower", Comments.crankPower);
	public final ConfigFloat handcrankHungerMultiplier = f(.01f, 0, 1, "crankHungerMultiplier", Comments.crankHungerMultiplier);
	
	public final ConfigGroup crusher = group(1, "crusher", "Crushing Wheels");
	public final ConfigFloat crusherInertia = f(100, 1, "crusherInertia", "Rotational Inertia");
	public final ConfigFloat crusherFriction = f(4, 0, "crusherFriction", "Kinetic Friction, in TU");
	public final ConfigFloat crusherResistance = f(0.03f, 0, "crusherResistance", "Wind Resistance, in TU/rpm");
	public final ConfigFloat crusherTorque = f(16, 0, "crusherTorque", "Torque when processing, in TU");
	public final ConfigInt crushingDamage = i(4, 0, "crushingDamage", Comments.crushingDamage);
	
	public final ConfigGroup deployer = group(1, "deployer", "Deployer");
	public final ConfigFloat deployerInertia = f(30, 1, "deployerInertia", "Rotational Inertia");
	public final ConfigFloat deployerFriction = f(0.3f, 0, "deployerFriction", "Kinetic Friction, in TU");
	public final ConfigFloat deployerResistance = f(0.005f, 0, "deployerResistance", "Wind Resistance, in TU/rpm");
	public final ConfigFloat deployerTorque = f(8, 0, "deployerTorque", "Torque when operating, in TU");
	public final ConfigEnum<DeployerAggroSetting> ignoreDeployerAttacks =
			e(DeployerAggroSetting.CREEPERS, "ignoreDeployerAttacks", Comments.ignoreDeployerAttacks);
	
	public final ConfigGroup drill = group(1, "drill", "Mechanical Drill");
	public final ConfigFloat drillInertia = f(60, 1, "drillInertia", "Rotational Inertia");
	public final ConfigFloat drillFriction = f(0.6f, 0, "drillFriction", "Kinetic Friction, in TU");
	public final ConfigFloat drillResistance = f(0.015f, 0, "drillResistance", "Wind Resistance, in TU/rpm");
	public final ConfigFloat drillTorque = f(8, 0, "drillTorque", "Torque when operating, in TU");
	
	public final ConfigGroup fan = group(1, "encasedFan", "Encased Fan");
	public final ConfigFloat fanInertia = f(60, 1, "fanInertia", "Rotational Inertia");
	public final ConfigFloat fanFriction = f(1f, 0, "fanFriction", "Kinetic Friction, in TU");
	public final ConfigFloat fanResistance = f(0.1f, 0, "fanResistance", "Wind Resistance, in TU/rpm");
	public final ConfigInt fanPushDistance = i(20, 5, "fanPushDistance", Comments.fanPushDistance);
	public final ConfigInt fanPullDistance = i(20, 5, "fanPullDistance", Comments.fanPullDistance);
	public final ConfigInt fanBlockCheckRate = i(30, 10, "fanBlockCheckRate", Comments.fanBlockCheckRate);
	public final ConfigInt fanRotationArgmax = i(256, 64, "fanRotationArgmax", Comments.rpm, Comments.fanRotationArgmax);
	public final ConfigInt fanProcessingTime = i(150, 0, "fanProcessingTime", Comments.fanProcessingTime);
	
	public final ConfigGroup flywheel = group(1, "flywheel", "Flywheel");
	public final ConfigFloat flywheelInertia = f(200, 1, "flywheelInertia", "Rotational Inertia");
	public final ConfigFloat flywheelFriction = f(0.1f, 0, "flywheelFriction", "Kinetic Friction, in TU");
	public final ConfigFloat flywheelResistance = f(0.03f, 0, "flywheelResistance", "Wind Resistance, in TU/rpm");
	
	public final ConfigGroup gearbox = group(1, "gearbox", "Gearbox");
	public final ConfigFloat gearboxInertia = f(40, 1, "gearboxInertia", "Rotational Inertia");
	public final ConfigFloat gearboxFriction = f(1.2f, 0, "gearboxFriction", "Kinetic Friction, in TU");
	public final ConfigFloat gearboxResistance = f(0.06f, 0, "gearboxResistance", "Wind Resistance, in TU/rpm");
	
	public final ConfigGroup mechanicalArm = group(1, "mechanicalArm", "Mechanical Arm");
	public final ConfigFloat mechanicalArmInertia = f(10, 1, "mechanicalArmInertia", "Rotational Inertia");
	public final ConfigFloat mechanicalArmFriction = f(0.3f, 0, "mechanicalArmFriction", "Kinetic Friction, in TU");
	public final ConfigFloat mechanicalArmResistance = f(0.015f, 0, "mechanicalArmResistance", "Wind Resistance, in TU/rpm");
	public final ConfigFloat mechanicalArmTorque = f(4, 0, "mechanicalArmTorque", "Torque when operating, in TU");
	
	public final ConfigGroup millstone = group(1, "millstone", "Millstone");
	public final ConfigFloat millstoneInertia = f(20, 1, "millstoneInertia", "Rotational Inertia");
	public final ConfigFloat millstoneFriction = f(0.6f, 0, "millstoneFriction", "Kinetic Friction, in TU");
	public final ConfigFloat millstoneResistance = f(0.015f, 0, "millstoneResistance", "Wind Resistance, in TU/rpm");
	public final ConfigFloat millstoneTorque = f(8, 0, "millstoneTorque", "Torque when operating, in TU");
	
	public final ConfigGroup mixer = group(1, "mixer", "Mechanical Mixer");
	public final ConfigFloat mixerInertia = f(20, 1, "mixerInertia", "Rotational Inertia");
	public final ConfigFloat mixerFriction = f(0.3f, 0, "mixerFriction", "Kinetic Friction, in TU");
	public final ConfigFloat mixerResistance = f(0.015f, 0, "mixerResistance", "Wind Resistance, in TU/rpm");
	public final ConfigFloat mixerTorque = f(8, 0, "mixerTorque", "Torque when operating, in TU");
	
	public final ConfigGroup press = group(1, "press", "Mechanical Press");
	public final ConfigFloat pressInertia = f(10, 1, "pressInertia", "Rotational Inertia");
	public final ConfigFloat pressFriction = f(0.3f, 0, "pressFriction", "Kinetic Friction, in TU");
	public final ConfigFloat pressResistance = f(0.005f, 0, "pressResistance", "Wind Resistance, in TU/rpm");
	public final ConfigFloat pressTorque = f(16, 0, "pressTorque", "Torque when operating, in TU");
	
	public final ConfigGroup saw = group(1, "saw", "Mechanical Saw");
	public final ConfigFloat sawInertia = f(10, 1, "sawInertia", "Rotational Inertia");
	public final ConfigFloat sawFriction = f(0.3f, 0, "sawFriction", "Kinetic Friction, in TU");
	public final ConfigFloat sawResistance = f(0.015f, 0, "sawResistance", "Wind Resistance, in TU/rpm");
	public final ConfigFloat sawTorque = f(4, 0, "sawTorque", "Torque when breaking blocks, in TU");
	public final ConfigFloat sawProcessingTorque = f(4, 0, "sawProcessingTorque", "Torque when processing a recipe, in TU");
	
	public final ConfigGroup steamEngine = group(1, "steamEngine", "Steam Engine");
	public final ConfigFloat steamEngineInertia = f(20, 1, "steamEngineInertia", "Rotational Inertia");
	public final ConfigFloat steamEngineFriction = f(0.3f, 0, "steamEngineFriction", "Kinetic Friction, in TU");
	public final ConfigFloat steamEngineResistance = f(0.015f, 0, "steamEngineResistance", "Wind Resistance, in TU/rpm");
	public final ConfigFloat steamEnginePowerPassive = f(4096, 0, "steamEnginePowerPassive", "Power generated by a passive steam engine");
	public final ConfigFloat steamEnginePower = f(32768, 0, "steamEnginePower", "Power generated per boiler level");
	
	public final ConfigGroup waterwheel = group(1, "waterwheel", "Waterwheel (Note: large wheels produce twice the power, have 4 times the inertia, and have half the target speed");
	public final ConfigFloat waterwheelInertia = f(20, 1, "waterwheelInertia", "Rotational Inertia");
	public final ConfigFloat waterwheelFriction = f(0.3f, 0, "waterwheelFriction", "Kinetic Friction, in TU");
	public final ConfigFloat waterwheelPower = f(512, 0, "waterwheelPower", "Power generated per flow level");
	public final ConfigFloat waterwheelTargetRPM = f(8, 4, "waterwheelTargetRPM", "The RPM at which the wheel and the water are moving at the same speed; No torque is applied there.");
	public final ConfigFloat waterwheelRemovedPower = f(256, 0, "waterwheelRemovedPower", "If waterwheels are opposing the water's rotation or moving faster than it, power will be removed instead.");
	
	public final ConfigGroup contraptions = group(1, "contraptions", "Moving Contraptions");
	public final ConfigInt maxBlocksMoved = i(2048, 1, "maxBlocksMoved", Comments.maxBlocksMoved);
	public final ConfigInt maxDataSize =
		i(ContraptionData.DEFAULT_LIMIT, 0, "maxDataSize", Comments.bytes, Comments.maxDataDisable, Comments.maxDataSize, Comments.maxDataSize2);
	public final ConfigInt maxChassisRange = i(16, 1, "maxChassisRange", Comments.maxChassisRange);
	public final ConfigInt maxPistonPoles = i(64, 1, "maxPistonPoles", Comments.maxPistonPoles);
	public final ConfigInt maxRopeLength = i(256, 1, "maxRopeLength", Comments.maxRopeLength);
	public final ConfigInt maxCartCouplingLength = i(32, 1, "maxCartCouplingLength", Comments.maxCartCouplingLength);
	public final ConfigInt rollerFillDepth = i(12, 1, "rollerFillDepth", Comments.rollerFillDepth);
	public final ConfigBool survivalContraptionPickup = b(true, "survivalContraptionPickup", Comments.survivalContraptionPickup);
	public final ConfigEnum<ContraptionMovementSetting> spawnerMovement =
		e(ContraptionMovementSetting.NO_PICKUP, "movableSpawners", Comments.spawnerMovement);
	public final ConfigEnum<ContraptionMovementSetting> amethystMovement =
		e(ContraptionMovementSetting.NO_PICKUP, "amethystMovement", Comments.amethystMovement);
	public final ConfigEnum<ContraptionMovementSetting> obsidianMovement =
		e(ContraptionMovementSetting.UNMOVABLE, "movableObsidian", Comments.obsidianMovement);
	public final ConfigEnum<ContraptionMovementSetting> reinforcedDeepslateMovement =
		e(ContraptionMovementSetting.UNMOVABLE, "movableReinforcedDeepslate", Comments.reinforcedDeepslateMovement);
	public final ConfigBool moveItemsToStorage = b(true, "moveItemsToStorage", Comments.moveItemsToStorage);
	public final ConfigBool harvestPartiallyGrown = b(false, "harvestPartiallyGrown", Comments.harvestPartiallyGrown);
	public final ConfigBool harvesterReplants = b(true, "harvesterReplants", Comments.harvesterReplants);
	public final ConfigBool minecartContraptionInContainers =
		b(false, "minecartContraptionInContainers", Comments.minecartContraptionInContainers);
	public final ConfigBool stabiliseStableContraptions = b(false, "stabiliseStableContraptions", Comments.stabiliseStableContraptions, "[Technical]");

	public final ConfigGroup stats = group(1, "stats", Comments.stats);
	public final ConfigFloat mediumSpeed = f(30, 0, 4096, "mediumSpeed", Comments.rpm, Comments.mediumSpeed);
	public final ConfigFloat fastSpeed = f(100, 0, 65535, "fastSpeed", Comments.rpm, Comments.fastSpeed);
	public final ConfigFloat mediumStressImpact =
		f(4, 0, 4096, "mediumStressImpact", Comments.su, Comments.mediumStressImpact);
	public final ConfigFloat highStressImpact = f(8, 0, 65535, "highStressImpact", Comments.su, Comments.highStressImpact);
	public final ConfigFloat mediumCapacity = f(256, 0, 4096, "mediumCapacity", Comments.su, Comments.mediumCapacity);
	public final ConfigFloat highCapacity = f(1024, 0, 65535, "highCapacity", Comments.su, Comments.highCapacity);

	//public final CStress stressValues = nested(1, CStress::new, Comments.stress);

	@Override
	public String getName() {
		return "kinetics";
	}

	private static class Comments {
		static String maxBeltLength = "Maximum length in blocks of mechanical belts.";
		static String crushingDamage = "Damage dealt by active Crushing Wheels.";
		static String maxRotationSpeed = "Maximum allowed rotation speed for any Kinetic Block.";
		static String fanPushDistance = "Maximum distance in blocks Fans can push entities.";
		static String fanPullDistance = "Maximum distance in blocks from where Fans can pull entities.";
		static String fanBlockCheckRate = "Game ticks between Fans checking for anything blocking their air flow.";
		static String fanRotationArgmax = "Rotation speed at which the maximum stats of fans are reached.";
		static String fanProcessingTime = "Game ticks required for a Fan-based processing recipe to take effect.";
		static String crankHungerMultiplier =
			"multiplier used for calculating exhaustion from speed when a crank is turned.";
		static String maxBlocksMoved =
			"Maximum amount of blocks in a structure movable by Pistons, Bearings or other means.";
		static String maxDataSize = "Maximum amount of data a contraption can have before it can't be synced with players.";
		static String maxDataSize2 = "Un-synced contraptions will not be visible and will not have collision.";
		static String maxDataDisable = "[0 to disable this limit]";
		static String maxChassisRange = "Maximum value of a chassis attachment range.";
		static String maxPistonPoles = "Maximum amount of extension poles behind a Mechanical Piston.";
		static String maxRopeLength = "Max length of rope available off a Rope Pulley.";
		static String maxCartCouplingLength = "Maximum allowed distance of two coupled minecarts.";
		static String rollerFillDepth = "Maximum depth of blocks filled in using a Mechanical Roller.";
		static String moveItemsToStorage =
			"Whether items mined or harvested by contraptions should be placed in their mounted storage.";
		static String harvestPartiallyGrown = "Whether harvesters should break crops that aren't fully grown.";
		static String harvesterReplants = "Whether harvesters should replant crops after harvesting.";
		static String stats = "Configure speed/capacity levels for requirements and indicators.";
		static String rpm = "[in Revolutions per Minute]";
		static String su = "[in Stress Units]";
		static String bytes = "[in Bytes]";
		static String mediumSpeed = "Minimum speed of rotation to be considered 'medium'";
		static String fastSpeed = "Minimum speed of rotation to be considered 'fast'";
		static String mediumStressImpact = "Minimum stress impact to be considered 'medium'";
		static String highStressImpact = "Minimum stress impact to be considered 'high'";
		static String mediumCapacity = "Minimum added Capacity by sources to be considered 'medium'";
		static String highCapacity = "Minimum added Capacity by sources to be considered 'high'";
		static String stress = "Fine tune the kinetic stats of individual components";
		static String ignoreDeployerAttacks = "Select what mobs should ignore Deployers when attacked by them.";
		static String disableStress = "Disable the Stress mechanic altogether.";
		static String kineticValidationFrequency =
			"Game ticks between Kinetic Blocks checking whether their source is still valid.";
		static String minimumWindmillSails =
			"Amount of sail-type blocks required for a windmill to assemble successfully.";
		static String windmillSailsPerRPM = "Number of sail-type blocks required to increase windmill speed by 1RPM.";
		static String maxEjectorDistance = "Max Distance in blocks a Weighted Ejector can throw";
		static String ejectorScanInterval =
			"Time in ticks until the next item launched by an ejector scans blocks for potential collisions";
		static String survivalContraptionPickup = "Whether minecart contraptions can be picked up in survival mode.";
		static String spawnerMovement = "Configure how Spawner blocks can be moved by contraptions.";
		static String amethystMovement = "Configure how Budding Amethyst can be moved by contraptions.";
		static String obsidianMovement = "Configure how Obsidian blocks can be moved by contraptions.";
		static String reinforcedDeepslateMovement = "Configure how Reinforced Deepslate blocks can be moved by contraptions.";
		static String minecartContraptionInContainers = "Whether minecart contraptions can be placed into container items.";
		static String stabiliseStableContraptions = "Whether stabilised bearings create a separated entity even on non-rotating contraptions.";
		static String crankPower = "Maximum power, in EU/s, a handcrank applies to a network. That much power is removed instead if the rotation direction is opposite to the crank's";
	}

	public enum DeployerAggroSetting {
		ALL, CREEPERS, NONE
	}

}
