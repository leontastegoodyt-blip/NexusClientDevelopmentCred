package com.nexusclient.core.settings;

/** A piece of text the player types, e.g. the message for Auto GG or a fake name for Nick Hider. */
public class TextSetting extends Setting<String> {

    private final int maxLength;

    public TextSetting(String id, String name, String defaultValue, int maxLength) {
        super(id, name, defaultValue);
        this.maxLength = maxLength;
    }

    public int maxLength() {
        return maxLength;
    }

    @Override
    public void set(String newValue) {
        value = newValue.length() > maxLength ? newValue.substring(0, maxLength) : newValue;
    }

    @Override
    public String save() {
        return value;
    }

    @Override
    public void load(String saved) {
        set(saved);
    }
}
