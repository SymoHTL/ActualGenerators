package dev.symo.actualgenerators.machine.multiblock;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A hatch in the hand: used on a casing or on a hatch of another kind, it takes that block's
 * place and hands the old block back, so a wall is built plain and given its doors afterwards
 * without breaking anything. Anywhere else it is a block like any other.
 *
 * <p>Before the block, not after it: a hatch opens the controller's window on a plain click, so
 * a click on one would never reach an item's {@code useOn}.
 */
public class HatchBlockItem extends BlockItem {
    public HatchBlockItem(HatchBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState there = level.getBlockState(pos);
        boolean swappable = there.getBlock() instanceof MultiblockCasingBlock || there.getBlock() instanceof HatchBlock;
        if (!swappable || there.is(getBlock())) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (player != null && !level.mayInteract(player, pos)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockState placed = getBlock().defaultBlockState()
                .setValue(HatchBlock.FACING, player == null ? Direction.NORTH : player.getDirection().getOpposite());
        level.setBlock(pos, placed, Block.UPDATE_ALL);
        SoundType sound = placed.getSoundType(level, pos, player);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        ItemStack old = new ItemStack(there.getBlock());
        if (player == null) {
            Block.popResource(level, pos, old);
        } else if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            if (!player.getInventory().add(old)) {
                player.drop(old, false);
            }
        }
        return InteractionResult.CONSUME;
    }
}
