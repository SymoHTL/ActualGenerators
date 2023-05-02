package dev.symo.actualgenerators.block.entity.pipe.config;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum EConnectionType implements StringRepresentable {
    CABLE,
    INPUT,
    OUTPUT,
    BOTH;

    @Override
    public @NotNull String getSerializedName() {
        return this.name().toLowerCase();
    }
}
