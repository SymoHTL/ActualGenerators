package dev.symo.actualgenerators.registry;

import dev.symo.actualgenerators.ActualGenerators;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Corium: the one fluid of the mod, and the heat the Annihilation Furnace runs on.
 *
 * <p>It is what the Geothermal Fissure Tap leaves behind as it works, a millibucket at a time,
 * and it is placed on the floor of a furnace where it stays. It behaves like lava that never
 * makes more of itself: no source conversion, so a pool cannot be farmed, and it crawls rather
 * than runs. Nothing in the mod burns it, drinks it or pipes it anywhere but into a furnace
 * floor; that is the whole of its job.
 */
public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, ActualGenerators.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, ActualGenerators.MODID);

    public static final DeferredHolder<FluidType, FluidType> CORIUM_TYPE = FLUID_TYPES.register("corium",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid_type." + ActualGenerators.MODID + ".corium")
                    .temperature(1600)
                    .viscosity(6000)
                    .density(3500)
                    .lightLevel(12)
                    .canSwim(false)
                    .canDrown(false)
                    .canConvertToSource(false)
                    .pathType(PathType.LAVA)
                    .adjacentPathType(null)));

    public static final DeferredHolder<Fluid, FlowingFluid> CORIUM =
            FLUIDS.register("corium", () -> new BaseFlowingFluid.Source(properties()));
    public static final DeferredHolder<Fluid, FlowingFluid> CORIUM_FLOWING =
            FLUIDS.register("corium_flowing", () -> new BaseFlowingFluid.Flowing(properties()));

    private ModFluids() {
    }

    /** Lava's pace: a slow tick and a short flow, so a poured floor stays where it was poured. */
    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(CORIUM_TYPE, CORIUM, CORIUM_FLOWING)
                .bucket(ModItems.CORIUM_BUCKET)
                .block(ModBlocks.CORIUM)
                .tickRate(30)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .explosionResistance(100.0F);
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }
}
