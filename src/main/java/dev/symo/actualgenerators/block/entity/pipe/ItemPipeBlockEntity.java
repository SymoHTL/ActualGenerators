package dev.symo.actualgenerators.block.entity.pipe;

import dev.symo.actualgenerators.block.entity.ModBlockEntities;
import dev.symo.actualgenerators.block.entity.pipe.config.*;
import dev.symo.actualgenerators.screen.ItemPipeBlockMenu;
import dev.symo.actualgenerators.util.DirectionUtil;
import dev.symo.actualgenerators.util.ItemUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static dev.symo.actualgenerators.block.ItemPipeBlock.*;

public class ItemPipeBlockEntity extends BlockEntity implements MenuProvider {
    public PipeInput[] inputConnections = new PipeInput[6];
    public PipeOutput[] outputConnections = new PipeOutput[6];
    private CompoundTag nbt;

    public ItemPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_PIPE_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("container.item_pipe_block");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return new ItemPipeBlockMenu(id, inventory, this);
    }

    public void onNeighborChange(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbor, boolean isMoving) {
        // get the direction of the neighbor
        Direction direction = DirectionUtil.getFacingDirection(pos, neighbor);
        // get the block entity of the neighbor
        BlockEntity neighborBlockEntity = level.getBlockEntity(neighbor);
        // check if neighbor has Item capability
        if (neighborBlockEntity != null) {
            var capability = neighborBlockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite()).orElse(null);
            if (capability != null) {
                // default to output
                PipeOutput pipeOutput = new PipeOutput(this, neighborBlockEntity, capability, direction, EMode.DISABLED);
                outputConnections[direction.get3DDataValue()] = pipeOutput;
            }
        }
        updateState(state, level, pos);
    }

    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState otherState, boolean bool) {
        updateState(state, level, pos);
    }

    public void updateState(BlockState state, Level level, BlockPos pos) {
        // clear all state
        state.setValue(NORTH_CONNECTION, false);
        state.setValue(SOUTH_CONNECTION, false);
        state.setValue(EAST_CONNECTION, false);
        state.setValue(WEST_CONNECTION, false);
        state.setValue(TOP_CONNECTION, false);
        state.setValue(BOTTOM_CONNECTION, false);
        state.setValue(UP_TYPE, EConnectionType.CABLE);
        state.setValue(DOWN_TYPE, EConnectionType.CABLE);
        state.setValue(NORTH_TYPE, EConnectionType.CABLE);
        state.setValue(SOUTH_TYPE, EConnectionType.CABLE);
        state.setValue(EAST_TYPE, EConnectionType.CABLE);
        state.setValue(WEST_TYPE, EConnectionType.CABLE);

        for (Direction dir : Direction.values()) {
            var input = inputConnections[dir.get3DDataValue()];
            var output = outputConnections[dir.get3DDataValue()];
            PipeIO io = input;

            if (io == null) io = output;

            if (io != null) {
                switch (dir) {
                    case UP -> {
                        state.setValue(TOP_CONNECTION, true);
                        state.setValue(UP_TYPE, io.Mode.toConnection());
                    }
                    case DOWN -> {
                        state.setValue(BOTTOM_CONNECTION, true);
                        state.setValue(DOWN_TYPE, io.Mode.toConnection());
                    }
                    case NORTH -> {
                        state.setValue(NORTH_CONNECTION, true);
                        state.setValue(NORTH_TYPE, io.Mode.toConnection());
                    }
                    case SOUTH -> {
                        state.setValue(SOUTH_CONNECTION, true);
                        state.setValue(SOUTH_TYPE, io.Mode.toConnection());
                    }
                    case EAST -> {
                        state.setValue(EAST_CONNECTION, true);
                        state.setValue(EAST_TYPE, io.Mode.toConnection());
                    }
                    case WEST -> {
                        state.setValue(WEST_CONNECTION, true);
                        state.setValue(WEST_TYPE, io.Mode.toConnection());
                    }
                }
            }
        }

        level.setBlockAndUpdate(pos, state);
    }

    @Override
    public void saveAdditional(@NotNull CompoundTag nbt) {
        super.saveAdditional(nbt);
        saveConnections(nbt);
    }

    public void saveConnections(@NotNull CompoundTag nbt) {
        var inputConnectionsTag = new CompoundTag();
        for (int i = 0; i < inputConnections.length; i++) {
            if (inputConnections[i] != null) {
                var inputTag = new CompoundTag();
                inputConnections[i].SaveToNBT(inputTag);
                inputConnectionsTag.put(String.valueOf(i), inputTag);
            }
        }
        nbt.put("inputConnections", inputConnectionsTag);

        var outputConnectionsTag = new CompoundTag();
        for (int i = 0; i < outputConnections.length; i++) {
            if (outputConnections[i] != null) {
                var outputTag = new CompoundTag();
                outputConnections[i].SaveToNBT(outputTag);
                outputConnectionsTag.put(String.valueOf(i), outputTag);
            }
        }
        nbt.put("outputConnections", outputConnectionsTag);
    }

    public CompoundTag saveConnections() {
        var nbt = new CompoundTag();
        var inputConnectionsTag = new CompoundTag();
        for (int i = 0; i < inputConnections.length; i++) {
            if (inputConnections[i] != null) {
                var inputTag = new CompoundTag();
                inputConnections[i].SaveToNBT(inputTag);
                inputConnectionsTag.put(String.valueOf(i), inputTag);
            }
        }
        nbt.put("inputConnections", inputConnectionsTag);

        var outputConnectionsTag = new CompoundTag();
        for (int i = 0; i < outputConnections.length; i++) {
            if (outputConnections[i] != null) {
                var outputTag = new CompoundTag();
                outputConnections[i].SaveToNBT(outputTag);
                outputConnectionsTag.put(String.valueOf(i), outputTag);
            }
        }
        nbt.put("outputConnections", outputConnectionsTag);

        return nbt;
    }

    @Override
    public void load(@NotNull CompoundTag nbt) {
        super.load(nbt);
        this.nbt = nbt;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (nbt != null) {
            readConnections(nbt);
            System.out.println("Loaded" + nbt);
        }
    }

    public void readConnections(@NotNull CompoundTag nbt) {
        var inputConnectionsTag = nbt.getCompound("inputConnections");
        for (int i = 0; i < inputConnections.length; i++) {
            if (inputConnectionsTag.contains(String.valueOf(i))) {
                var inputTag = inputConnectionsTag.getCompound(String.valueOf(i));
                inputConnections[i] = PipeInput.LoadFromNBT(inputTag, this);
            }
        }

        var outputConnectionsTag = nbt.getCompound("outputConnections");
        for (int i = 0; i < outputConnections.length; i++) {
            if (outputConnectionsTag.contains(String.valueOf(i))) {
                var outputTag = outputConnectionsTag.getCompound(String.valueOf(i));
                outputConnections[i] = PipeOutput.LoadFromNBT(outputTag, this);
            }
        }
    }

    public void onRemove() {
    }

    public void tick(Level level, BlockPos pos, ItemPipeBlockEntity pipe) {
        if (level.isClientSide()) return;

        for (PipeInput input : pipe.inputConnections) {
            if (input == null) continue;

            if (input.RedstoneMode != ERedstoneMode.Ignored && input.RedstoneMode != ERedstoneMode.AlwaysOn) {
                int power = level.getBestNeighborSignal(pos);
                if (input.RedstoneMode == ERedstoneMode.High && power == 0)
                    continue;
                else if (input.RedstoneMode == ERedstoneMode.Low && power != 0)
                    continue;
            }

            var endPoints = getEndpoints(level, input, pos);
            if (endPoints.isEmpty()) continue;


            switch (input.PlacementStrategy) {
                case CLOSEST -> {
                    // sort endpoints by priority then distance
                    endPoints.sort((a, b) -> {
                        if (a.Priority == b.Priority) return (int) (a.distance - b.distance);
                        return b.Priority - a.Priority;
                    });
                    moveItems(input, endPoints);
                }
                case FURTHEST -> {
                    // sort endpoints by priority then distance
                    endPoints.sort((a, b) -> {
                        if (a.Priority == b.Priority) return (int) (b.distance - a.distance);
                        return b.Priority - a.Priority;
                    });
                    moveItems(input, endPoints);
                }
                case ROUND_ROBIN -> {
                    // sort endpoints by priority
                    endPoints.sort((a, b) -> b.Priority - a.Priority);
                    // split moveAmount evenly between endpoints
                    int moveAmount = input.moveAmount / endPoints.size();
                    int remainder = input.moveAmount % endPoints.size();
                    for (PipeOutput output : endPoints) {
                        ItemUtil.moveItemsAmount(input.fromHandler, output.toHandler, moveAmount);
                    }
                    // move remainder to first endpoint
                    if (remainder > 0) {
                        ItemUtil.moveItemsAmount(input.fromHandler, endPoints.get(0).toHandler, remainder);
                    }
                }
                case RANDOM -> {
                    // get random endpoint
                    int remaining = input.moveAmount;
                    int safety = 0;
                    while (remaining > 0) {
                        int index = level.random.nextInt(endPoints.size());
                        remaining = ItemUtil.moveItemsAmount(input.fromHandler, endPoints.get(index).toHandler, remaining);
                        if (safety++ > 10) break;
                    }
                }
            }
        }
    }

    private void moveItems(PipeInput input, List<PipeOutput> endPoints) {
        int remaining = input.moveAmount;
        for (PipeOutput endPoint : endPoints) {
            if (remaining <= 0)
                return;
            remaining = ItemUtil.moveItemsAmount(input.fromHandler, endPoint.toHandler, remaining);
        }
    }

    private List<PipeOutput> getEndpoints(Level level, PipeInput input, BlockPos pos) {
        List<PipeOutput> endPoints = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(pos);
        visited.add(pos);

        while (!queue.isEmpty()) {
            BlockPos currentPos = queue.pop();
            BlockEntity currentEntity = level.getBlockEntity(currentPos);
            if (currentEntity instanceof ItemPipeBlockEntity currentPipe) {
                for (PipeOutput output : currentPipe.outputConnections) {
                    if (output == null) continue;
                    if (input.Channel != output.Channel) continue;
                    if (output.Mode != EMode.INSERT && output.Mode != EMode.EXTRACT_INSERT) continue;
                    //TODO : REDSTONE
                    endPoints.add(output);
                }
                for (Direction direction : Direction.values()) {
                    BlockPos nextPos = currentPos.relative(direction);
                    BlockEntity nextEntity = level.getBlockEntity(nextPos);
                    if (nextEntity instanceof ItemPipeBlockEntity && !visited.contains(nextPos)) {
                        queue.add(nextPos);
                        visited.add(nextPos);
                    }
                }
            }
        }
        return endPoints;
    }

    public PipeOutput getOutput(Direction direction) {
        return outputConnections[direction.get3DDataValue()];
    }

    public PipeInput getInput(Direction direction) {
        return inputConnections[direction.get3DDataValue()];
    }


    private void setInput(Direction direction, @Nullable PipeInput input, EMode mode) {
        var blockEntity = Objects.requireNonNull(this.getLevel()).getBlockEntity(this.getBlockPos().relative(direction));
        if (blockEntity == null) return;
        var handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite())
                .orElse(null);
        if (handler == null) return;
        if (input == null)
            input = new PipeInput(this, blockEntity, handler, direction, mode);
        else
            input = new PipeInput(this, blockEntity, handler, direction, input);

        inputConnections[direction.get3DDataValue()] = input;
    }

    private void setOutput(Direction direction, @Nullable PipeOutput output, EMode mode) {
        var blockEntity = Objects.requireNonNull(this.getLevel()).getBlockEntity(this.getBlockPos().relative(direction));
        if (blockEntity == null) return;
        var handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite()).orElse(null);
        if (handler == null) return;
        if (output == null)
            output = new PipeOutput(this, blockEntity, handler, direction, mode);
        else
            output = new PipeOutput(this, blockEntity, handler, direction, output);
        outputConnections[direction.get3DDataValue()] = output;
    }

    public void setMode(Direction direction, EMode mode) {
        switch (mode) {
            case EXTRACT -> {
                var previous = inputConnections[direction.get3DDataValue()];
                if (previous != null) {
                    previous.Mode = EMode.EXTRACT;
                    setInput(direction, previous, null);
                } else
                    setInput(direction, null, EMode.EXTRACT);
                outputConnections[direction.get3DDataValue()] = null;
            }
            case INSERT -> {
                var previous = outputConnections[direction.get3DDataValue()];
                if (previous != null) {
                    previous.Mode = EMode.INSERT;
                    setOutput(direction, previous, null);
                } else
                    setOutput(direction, null, EMode.INSERT);
                inputConnections[direction.get3DDataValue()] = null;
            }
            case EXTRACT_INSERT -> {
                var prevInput = inputConnections[direction.get3DDataValue()];
                var prevOutput = outputConnections[direction.get3DDataValue()];
                if (prevInput != null) {
                    prevInput.Mode = EMode.EXTRACT_INSERT;
                    setInput(direction, prevInput, null);
                } else
                    setInput(direction, null, EMode.EXTRACT_INSERT);
                if (prevOutput != null) {
                    prevOutput.Mode = EMode.EXTRACT_INSERT;
                    setOutput(direction, prevOutput, null);
                } else
                    setOutput(direction, null, EMode.EXTRACT_INSERT);
            }
            case DISABLED -> {
                inputConnections[direction.get3DDataValue()] = null;
                outputConnections[direction.get3DDataValue()] = null;
            }
        }
    }

    public void setPriority(Direction direction, boolean isInput, int priority) {
        if (isInput) {
            var input = inputConnections[direction.get3DDataValue()];
            if (input != null)
                input.Priority = priority;
        } else {
            var output = outputConnections[direction.get3DDataValue()];

            if (output != null)
                output.Priority = priority;
        }
    }

    public void setChannel(Direction direction, boolean isInput, EChannel channel) {
        if (isInput) {
            var input = inputConnections[direction.get3DDataValue()];
            if (input != null)
                input.Channel = channel;
        } else {
            var output = outputConnections[direction.get3DDataValue()];
            if (output != null)
                output.Channel = channel;
        }
    }

    public void setRedstoneMode(Direction direction, boolean isInput, ERedstoneMode mode) {
        if (isInput) {
            var input = inputConnections[direction.get3DDataValue()];
            if (input != null)
                input.RedstoneMode = mode;
        } else {
            var output = outputConnections[direction.get3DDataValue()];
            if (output != null)
                output.RedstoneMode = mode;
        }
    }

    public void clearIO() {
        for (int i = 0; i < 6; i++) {
            inputConnections[i] = null;
            outputConnections[i] = null;
        }
    }


}
