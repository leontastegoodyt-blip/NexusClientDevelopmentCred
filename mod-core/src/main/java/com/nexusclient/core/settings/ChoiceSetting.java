package com.nexusclient.core.settings;

import java.util.Arrays;
import java.util.List;

/** A choice between a few options (clicking cycles through them), e.g. "Style: Cross / Dot / Circle". */
public class ChoiceSetting extends Setting<String> {

    private final List<String> options;

    public ChoiceSetting(String id, String name, String defaultValue, String... options) {
        super(id, name, defaultValue);
        this.options = Arrays.asList(options);
    }

    public List<String> options() {
        return options;
    }

    public boolean is(String option) {
        return value.equals(option);
    }

    /** Moves to the next option (after the last one, back to the first). */
    public void next() {
        int index = options.indexOf(value);
        value = options.get((index + 1) % options.size());
    }

    @Override
    public String save() {
        return value;
    }

    @Override
    public void load(String saved) {
        if (options.contains(saved)) {
            value = saved;
        }
    }
}
