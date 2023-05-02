package dev.symo.actualgenerators.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ParamEventHandler<T> {
    private final List<Consumer<T>> listeners = new ArrayList<>();

    public void addEventListener(Consumer<T> listener) {
        listeners.add(listener);
    }

    public void removeEventListener(Consumer<T> listener) {
        listeners.remove(listener);
    }

    public void fireEvent(T eventData) {
        for (Consumer<T> listener : listeners) {
            listener.accept(eventData);
        }
    }
}
