package com.nexusclient.core.settings;

/** A number between a minimum and maximum (shown as a slider), e.g. "Zoom strength: 4x". */
public class NumberSetting extends Setting<Double> {

    private final double min;
    private final double max;
    private final double step;
    private final String unit;

    /**
     * @param step the slider jumps in steps of this size (e.g. 0.1)
     * @param unit shown after the number, e.g. "x" or "%" (can be "")
     */
    public NumberSetting(String id, String name, double defaultValue, double min, double max, double step, String unit) {
        super(id, name, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
        this.unit = unit;
    }

    public double min() { return min; }
    public double max() { return max; }
    public double step() { return step; }

    @Override
    public void set(Double newValue) {
        double clamped = Math.max(min, Math.min(max, newValue));
        value = Math.round(clamped / step) * step; // snap to the step size
    }

    /** Where the value sits between min and max, from 0.0 to 1.0 (for drawing the slider). */
    public double fraction() {
        return (value - min) / (max - min);
    }

    public void setFraction(double fraction) {
        set(min + (max - min) * Math.max(0, Math.min(1, fraction)));
    }

    /** The value as text, e.g. "1.5x" or "60%". */
    public String display() {
        boolean whole = step >= 1;
        return (whole ? String.valueOf(Math.round(value)) : String.format(java.util.Locale.ROOT, "%.1f", value)) + unit;
    }

    public int intValue() {
        return (int) Math.round(value);
    }

    @Override
    public String save() {
        return String.valueOf(value);
    }

    @Override
    public void load(String saved) {
        try {
            set(Double.parseDouble(saved));
        } catch (NumberFormatException ignored) {
            // keep the current value
        }
    }
}
