package dev.symo.actualgenerators.block.entity.pipe;

import dev.symo.actualgenerators.block.ItemPipeBlock;
import dev.symo.actualgenerators.block.entity.pipe.config.Connection;
import dev.symo.actualgenerators.block.entity.pipe.config.DirectionalPosition;
import dev.symo.actualgenerators.block.entity.pipe.config.EChannel;
import dev.symo.actualgenerators.block.entity.pipe.config.ERedstoneMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class ItemPipeBlockEntity extends BlockEntity implements MenuProvider, TickingBlockEntity {
    public ItemPipeBlockEntity(BlockPos pos, BlockState state) {
        super(p_155228_, pos, state);
        extractingSides = new boolean[Direction.values().length];
        disconnectedSides = new boolean[Direction.values().length];
    }

    private EChannel channel = EChannel.White; // Default channel
    private ERedstoneMode redstoneMode = ERedstoneMode.Ignored; // Default redstone mode
    private List<ItemStack> whiteList;
    private List<ItemStack> blackList;
    private int priority = 0; // Default priority

    @Nullable
    protected List<Connection> connectionCache;
    @Nullable
    protected Connection[] extractingConnectionCache;

    protected boolean[] extractingSides;
    protected boolean[] disconnectedSides;


    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
        }
    }

    @Nullable
    public Connection getExtractingConnection(Direction side) {
        if (level == null) {
            return null;
        }
        if (extractingConnectionCache == null) {
            updateExtractingConnectionCache();
            if (extractingConnectionCache == null) {
                return null;
            }
        }
        return extractingConnectionCache[side.get3DDataValue()];
    }

    private void updateExtractingConnectionCache() {
        BlockState blockState = getBlockState();
        if (!(blockState.getBlock() instanceof ItemPipeBlock)) {
            extractingConnectionCache = null;
            return;
        }

        extractingConnectionCache = new Connection[Direction.values().length];

        for (Direction direction : Direction.values()) {
            if (!isExtracting(direction)) {
                extractingConnectionCache[direction.get3DDataValue()] = null;
                continue;
            }
            extractingConnectionCache[direction.get3DDataValue()] = new Connection(getBlockPos().relative(direction), direction.getOpposite(), 1);
        }
    }

    public boolean isExtracting(Direction side) {
        return extractingSides[side.get3DDataValue()];
    }

    public boolean isExtracting() {
        for (boolean extract : extractingSides)
            if (extract)
                return true;
        return false;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.item_pipe");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.putString("channel", channel.name());
        nbt.putString("whiteList", redstoneMode.name());
        nbt.putInt("priority", priority);
        saveItemStackList("whiteList", whiteList, nbt);
        saveItemStackList("blackList", blackList, nbt);
    }

    private void saveItemStackList(String key, List<ItemStack> list, CompoundTag nbt) {
        CompoundTag listNBT = new CompoundTag();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag itemNBT = new CompoundTag();
            list.get(i).save(itemNBT);
            listNBT.put(String.valueOf(i), itemNBT);
        }
        nbt.put(key, listNBT);
    }

    private List<ItemStack> loadItemStackList(String key, CompoundTag nbt) {
        List<ItemStack> list = new ArrayList<>();
        CompoundTag listNBT = nbt.getCompound(key);
        for (String key1 : listNBT.getAllKeys()) {
            CompoundTag itemNBT = listNBT.getCompound(key1);
            list.add(ItemStack.of(itemNBT));
        }
        return list;
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        channel = EChannel.valueOf(nbt.getString("channel"));
        redstoneMode = ERedstoneMode.valueOf(nbt.getString("redstoneMode"));
        priority = nbt.getInt("priority");
        whiteList = loadItemStackList("whiteList", nbt);
        blackList = loadItemStackList("blackList", nbt);
    }


    @Override
    public void tick() {
        for (Direction side : Direction.values()) {
            Connection extractingConnection = getExtractingConnection(side);
            if (extractingConnection == null) {
                continue;
            }
            IItemHandler itemHandler = extractingConnection.getItemHandler(level).orElse(null);
            if (itemHandler == null) {
                continue;
            }

            List<Connection> connections = getSortedConnections(side);

            insertOrdered(side, connections, itemHandler);
        }
    }

    protected void insertOrdered(Direction side, List<Connection> connections, IItemHandler itemHandler) {
        int itemsToTransfer = 100;

        ArrayList<ItemStack> nonFittingItems = new ArrayList<>();

        connectionLoop:
        for (Connection connection : connections) {
            nonFittingItems.clear();
            IItemHandler destination = connection.getItemHandler(level).orElse(null);
            if (destination == null) {
                continue;
            }
            if (isFull(destination)) {
                continue;
            }
            for (int i = 0; i < itemHandler.getSlots(); i++) {
                if (itemsToTransfer <= 0) {
                    break connectionLoop;
                }
                ItemStack simulatedExtract = itemHandler.extractItem(i, itemsToTransfer, true);
                if (simulatedExtract.isEmpty()) {
                    continue;
                }
                if (nonFittingItems.stream().anyMatch(stack -> ItemUtils.isStackable(stack, simulatedExtract))) {
                    continue;
                }
                if (canInsert(connection, simulatedExtract, tileEntity.getFilters(side, this)) == tileEntity.getFilterMode(side, this).equals(UpgradeTileEntity.FilterMode.BLACKLIST)) {
                    continue;
                }
                ItemStack stack = ItemHandlerHelper.insertItem(destination, simulatedExtract, false);
                int insertedAmount = simulatedExtract.getCount() - stack.getCount();
                if (insertedAmount <= 0) {
                    nonFittingItems.add(simulatedExtract);
                }
                itemsToTransfer -= insertedAmount;
                itemHandler.extractItem(i, insertedAmount, false);
            }
        }
    }

    private boolean isFull(IItemHandler itemHandler) {
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stackInSlot = itemHandler.getStackInSlot(i);
            if (stackInSlot.getCount() < itemHandler.getSlotLimit(i)) {
                return false;
            }
        }
        return true;
    }


    public List<Connection> getSortedConnections(Direction side) {
        return getConnections().stream().sorted(Comparator.comparingInt(Connection::getDistance)).collect(Collectors.toList());
    }

    public List<Connection> getConnections() {
        if (level == null) {
            return new ArrayList<>();
        }
        if (connectionCache == null) {
            updateConnectionCache();
            if (connectionCache == null) {
                return new ArrayList<>();
            }
        }
        return connectionCache;
    }

    private void updateConnectionCache() {
        BlockState blockState = getBlockState();
        if (!(blockState.getBlock() instanceof PipeBlock)) {
            connectionCache = null;
            return;
        }
        if (!isExtracting()) {
            connectionCache = null;
            return;
        }

        Map<DirectionalPosition, Connection> connections = new HashMap<>();

        Map<BlockPos, Integer> queue = new HashMap<>();
        List<BlockPos> travelPositions = new ArrayList<>();

        addToQueue(level, worldPosition, queue, travelPositions, connections, 1);

        while (queue.size() > 0) {
            Map.Entry<BlockPos, Integer> blockPosIntegerEntry = queue.entrySet().stream().findAny().get();
            addToQueue(level, blockPosIntegerEntry.getKey(), queue, travelPositions, connections, blockPosIntegerEntry.getValue());
            travelPositions.add(blockPosIntegerEntry.getKey());
            queue.remove(blockPosIntegerEntry.getKey());
        }

        connectionCache = new ArrayList<>(connections.values());
    }

    public boolean isDisconnected(Direction side) {
        return disconnectedSides[side.get3DDataValue()];
    }

    public boolean canInsert(Level level, Connection connection) {
        LazyOptional<?> capability = connection.getCapability(level, ForgeCapabilities.ITEM_HANDLER);
        if (capability.isPresent()) {
            return true;
        }
        return false;
    }

    public void addToQueue(Level world, BlockPos position, Map<BlockPos, Integer> queue, List<BlockPos> travelPositions, Map<DirectionalPosition, Connection> insertPositions, int distance) {
        Block block = world.getBlockState(position).getBlock();
        if (!(block instanceof ItemPipeBlock)) {
            return;
        }
        ItemPipeBlock pipeBlock = (ItemPipeBlock) block;
        for (Direction direction : Direction.values()) {
            if (pipeBlock.isConnected(world, position, direction)) {
                BlockPos p = position.relative(direction);
                DirectionalPosition dp = new DirectionalPosition(p, direction.getOpposite());
                Connection connection = new Connection(dp.getPos(), dp.getDirection(), distance);
                if (!isExtracting(level, position, direction) && canInsert(level, connection)) {
                    if (!insertPositions.containsKey(dp)) {
                        insertPositions.put(dp, connection);
                    } else {
                        if (insertPositions.get(dp).getDistance() > distance) {
                            insertPositions.put(dp, connection);
                        }
                    }
                } else {
                    if (!travelPositions.contains(p) && !queue.containsKey(p)) {
                        queue.put(p, distance + 1);
                    }
                }
            }
        }
    }

    private boolean isExtracting(Level level, BlockPos pos, Direction direction) {
        BlockEntity te = level.getBlockEntity(pos);
        if (te instanceof ItemPipeBlockEntity pipe) {
            if (pipe.isExtracting(direction)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public BlockPos getPos() {
        return null;
    }
}
