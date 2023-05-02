package dev.symo.actualgenerators.event;

import java.util.ArrayList;
import java.util.List;

public class EventHandler {
    private final List<Runnable> listeners = new ArrayList<>();

    public void add(Runnable listener) {
        listeners.add(listener);
    }

    public void remove(Runnable listener) {
        listeners.remove(listener);
    }

    public void fire() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
