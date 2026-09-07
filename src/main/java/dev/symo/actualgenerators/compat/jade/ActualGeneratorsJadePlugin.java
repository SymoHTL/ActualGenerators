package dev.symo.actualgenerators.compat.jade;

import dev.symo.actualgenerators.machine.multiblock.HatchBlockEntity;
import dev.symo.actualgenerators.machine.multiblock.HatchBlock;
import dev.symo.actualgenerators.logistics.LinkPortBlock;
import dev.symo.actualgenerators.logistics.LinkPortBlockEntity;
import dev.symo.actualgenerators.machine.MachineBlock;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Puts what a machine is doing into the Jade tooltip, so a base can be read without opening nine
 * windows.
 *
 * <p>Energy is deliberately not here: machines expose the ordinary Forge Energy capability, and
 * Jade's own universal energy provider already draws a bar for anything that does.
 *
 * <p>Loaded by Jade itself, so nothing here runs when Jade is absent.
 */
@WailaPlugin
public class ActualGeneratorsJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(MachineStatusProvider.INSTANCE, MachineBlockEntity.class);
        registration.registerBlockDataProvider(HatchStatusProvider.INSTANCE, HatchBlockEntity.class);
        registration.registerBlockDataProvider(LinkPortStatusProvider.INSTANCE, LinkPortBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MachineStatusProvider.INSTANCE, MachineBlock.class);
        registration.registerBlockComponent(HatchStatusProvider.INSTANCE, HatchBlock.class);
        registration.registerBlockComponent(LinkPortStatusProvider.INSTANCE, LinkPortBlock.class);
    }
}
