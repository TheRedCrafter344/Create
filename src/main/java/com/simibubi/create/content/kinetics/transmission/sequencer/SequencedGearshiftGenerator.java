package com.simibubi.create.content.kinetics.transmission.sequencer;

import com.simibubi.create.foundation.data.SpecialBlockStateGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.generators.ModelFile;

public class SequencedGearshiftGenerator extends SpecialBlockStateGen {

	@Override
	protected int getXRotation(BlockState state) {
		switch(state.getValue(SequencedGearshiftBlock.FACING)) {
		case DOWN:
			return 270;
		case UP:
			return 90;
		default:
			return 0;
		}
	}

	@Override
	protected int getYRotation(BlockState state) {
		switch(state.getValue(SequencedGearshiftBlock.FACING)) {
		case NORTH:
			return 0;
		case EAST:
			return 270;
		case SOUTH:
			return 180;
		case WEST:
			return 90;
		default:
			return 0;
		}
	}

	@Override
	public <T extends Block> ModelFile getModel(DataGenContext<Block, T> ctx, RegistrateBlockstateProvider prov,
		BlockState state) {
		String variant = "idle";
		int seq = state.getValue(SequencedGearshiftBlock.STATE);
		if (seq > 0)
			variant = "seq_" + seq;
		return prov.models()
			.getExistingFile(prov.modLoc("block/" + ctx.getName() + "/" + variant));
	}

}
