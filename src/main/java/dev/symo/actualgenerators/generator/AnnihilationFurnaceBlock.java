package dev.symo.actualgenerators.generator;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.symo.actualgenerators.machine.MachineBlock;
import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlock;
import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The controller block. Besides opening the window, it is where the floor is poured: a bucket of
 * a heating fluid used on it goes on to the first empty floor block inside, and an empty bucket
 * takes one back, so a sealed box never has to be opened to heat it.
 */
public class AnnihilationFurnaceBlock extends MultiblockControllerBlock {
    public static final MapCodec<AnnihilationFurnaceBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(propertiesCodec()).apply(instance, AnnihilationFurnaceBlock::new));

    public AnnihilationFurnaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MachineBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnnihilationFurnaceBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack,
                                              BlockState state,
                                              Level level,
                                              BlockPos pos,
                                              Player player,
                                              InteractionHand hand,
                                              BlockHitResult hit) {
        if (stack.getCapability(Capabilities.FluidHandler.ITEM) == null
                || !(level.getBlockEntity(pos) instanceof AnnihilationFurnaceBlockEntity furnace)
                || furnace.structure() == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        FluidStack held = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
        boolean pouring = !held.isEmpty();
        if (pouring && AnnihilationFurnaceBlockEntity.heatOf(held.getFluid().defaultFluidState()) <= 0) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        Floor floor = new Floor(level, furnace.structure());
        FluidActionResult result = pouring
                ? FluidUtil.tryEmptyContainerAndStow(stack, floor, null, FluidType.BUCKET_VOLUME, player, true)
                : FluidUtil.tryFillContainerAndStow(stack, floor, null, FluidType.BUCKET_VOLUME, player, true);
        if (result.isSuccess()) {
            player.setItemInHand(hand, result.getResult());
        } else {
            player.displayClientMessage(Component.translatable(pouring
                    ? "block.actualgenerators.annihilation_furnace.floor_full"
                    : "block.actualgenerators.annihilation_furnace.floor_empty"), true);
        }
        return ItemInteractionResult.CONSUME;
    }

    /**
     * The floor as a tank: a fill puts one source block on the first empty floor block, a drain
     * takes the first source off it. Whole buckets only; the world has no half blocks of lava.
     */
    private record Floor(Level level, MultiblockControllerBlockEntity.Structure box) implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return FluidType.BUCKET_VOLUME;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return AnnihilationFurnaceBlockEntity.heatOf(stack.getFluid().defaultFluidState()) > 0;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.getAmount() < FluidType.BUCKET_VOLUME || !isFluidValid(0, resource)) {
                return 0;
            }
            for (BlockPos pos : box.floor()) {
                if (level.getBlockState(pos).isAir()) {
                    if (action.execute()) {
                        level.setBlock(pos, resource.getFluid().defaultFluidState().createLegacyBlock(), Block.UPDATE_ALL);
                    }
                    return FluidType.BUCKET_VOLUME;
                }
            }
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            FluidStack drained = drain(resource.getAmount(), FluidAction.SIMULATE);
            if (drained.isEmpty() || !FluidStack.isSameFluid(drained, resource)) {
                return FluidStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain < FluidType.BUCKET_VOLUME) {
                return FluidStack.EMPTY;
            }
            for (BlockPos pos : box.floor()) {
                FluidState fluid = level.getFluidState(pos);
                if (fluid.isSource() && AnnihilationFurnaceBlockEntity.heatOf(fluid) > 0) {
                    if (action.execute()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                    return new FluidStack(fluid.getType(), FluidType.BUCKET_VOLUME);
                }
            }
            return FluidStack.EMPTY;
        }
    }
}
