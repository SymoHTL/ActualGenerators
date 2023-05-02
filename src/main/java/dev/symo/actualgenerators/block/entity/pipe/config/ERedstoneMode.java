package dev.symo.actualgenerators.block.entity.pipe.config;

public enum ERedstoneMode {
    Ignored,
    AlwaysOn,
    High,
    Low,
    ;

    public ERedstoneMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    @Override
    public String toString() {
        return switch (this) {
            case Ignored -> "Ignored";
            case AlwaysOn -> "Always On";
            case High -> "High";
            case Low -> "Low";
        };
    }
}
