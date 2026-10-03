package com.nexusclient.core.settings;

/** An on/off setting (shown as a switch). */
public class BooleanSetting extends Setting<Boolean> {

    public BooleanSetting(String id, String name, boolean defaultValue) {
        super(id, name, defaultValue);
    }

    public boolean isOn() {
        return value;
    }

    public void toggle() {
        value = !value;
    }

    @Override
    public String save() {
        return String.valueOf(value);
    }

    @Override
    public void load(String saved) {
        value = Boolean.parseBoolean(saved);
    }
}
