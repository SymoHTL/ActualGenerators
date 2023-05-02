package dev.symo.actualgenerators.block.entity.pipe.config;

public enum EMode {
    EXTRACT,
    INSERT,
    EXTRACT_INSERT,
    DISABLED;

    public EMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public EConnectionType toConnection() {
        return switch (this) {
            case EXTRACT -> EConnectionType.OUTPUT;
            case INSERT -> EConnectionType.INPUT;
            case EXTRACT_INSERT -> EConnectionType.BOTH;
            default -> EConnectionType.CABLE;
        };
    }
}
