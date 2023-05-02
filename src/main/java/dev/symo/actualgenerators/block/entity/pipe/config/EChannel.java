package dev.symo.actualgenerators.block.entity.pipe.config;

public enum EChannel {
    Green,
    Red,
    Blue,
    Yellow,
    White,
    Black,
    Orange,
    Purple,
    Cyan,
    Pink,
    ;

    public EChannel next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
