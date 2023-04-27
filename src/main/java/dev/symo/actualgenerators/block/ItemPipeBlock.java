package dev.symo.actualgenerators.block;

import dev.symo.actualgenerators.block.entity.pipe.ItemPipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import javax.annotation.Nullable;

public class ItemPipeBlock extends Block {

    /*public ItemPipeBlock(Properties properties) {
        super(properties);
    }

    public boolean canConnectTo(LevelAccessor world, BlockPos pos, Direction facing) {
        BlockEntity te = world.getBlockEntity(pos.relative(facing));
        return (te != null && te.getCapability(ForgeCapabilities.ITEM_HANDLER, facing.getOpposite()).isPresent());
    }

    public boolean isPipe(LevelAccessor world, BlockPos pos, Direction facing) {
        BlockState state = world.getBlockState(pos.relative(facing));
        return state.getBlock().equals(this);
    }

    BlockEntity createTileEntity(BlockPos pos, BlockState state) {
        return new ItemPipeBlockEntity(pos, state);
    }

    public boolean isConnected(LevelAccessor world, BlockPos pos, Direction facing) {
        ItemPipeBlockEntity pipe = getTileEntity(world, pos);
        ItemPipeBlockEntity other = getTileEntity(world, pos.relative(facing));

        if (!isAbleToConnect(world, pos, facing)) {
            return false;
        }
        boolean canSelfConnect = pipe == null || !pipe.isDisconnected(facing);
        if (!canSelfConnect) {
            return false;
        }
        boolean canSideConnect = other == null || !other.isDisconnected(facing.getOpposite());
        return canSideConnect;
    }

    public boolean isDisconnected(LevelAccessor world, BlockPos pos, Direction side) {
        ItemPipeBlockEntity pipe = getTileEntity(world, pos);
        if (pipe == null) {
            return false;
        }
        return pipe.isDisconnected(side);
    }

    public boolean isAbleToConnect(LevelAccessor world, BlockPos pos, Direction facing) {
        return isPipe(world, pos, facing) || canConnectTo(world, pos, facing);
    }

    @Nullable
    public ItemPipeBlockEntity getTileEntity(LevelAccessor world, BlockPos pos) {
        BlockEntity te = world.getBlockEntity(pos);
        if (te instanceof ItemPipeBlockEntity) {
            return (ItemPipeBlockEntity) te;
        }
        return null;
    }*/

}

