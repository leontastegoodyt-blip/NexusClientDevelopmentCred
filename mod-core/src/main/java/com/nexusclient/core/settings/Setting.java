package com.nexusclient.core.settings;

/**
 * Setting – one option of a mod that the player can change in the mod menu, e.g. "Show background"
 * (on/off), "Scale" (a slider) or "Text colour".
 *
 * Each kind of setting is its own small class (BooleanSetting, NumberSetting, ...). They all have an
 * id (used when saving) and a name (shown in the menu).
 *
 * @param <T> the kind of value, e.g. Boolean or Double
 */
public abstract class Setting<T> {

    private final String id;
    private final String name;
    private final T defaultValue;
    protected T value;

    protected Setting(String id, String name, T defaultValue) {
        this.id = id;
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public T get() {
        return value;
    }

    public void set(T newValue) {
        this.value = newValue;
    }

    /** The value it starts with (and goes back to on "Reset"). */
    public T defaultValue() {
        return defaultValue;
    }

    public void reset() {
        this.value = defaultValue;
    }

    /** The value as text, for saving. */
    public abstract String save();

    /** Reads a value saved by save(). Bad values are ignored (the setting keeps its current value). */
    public abstract void load(String saved);
}
