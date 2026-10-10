package com.nexusclient.core.module;

import com.nexusclient.core.platform.GameInfo;
import com.nexusclient.core.settings.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Module – one Nexus feature that can be switched on and off in the mod menu, like "FPS", "Zoom" or
 * "Toggle Sprint". Each module has its own settings.
 *
 * mod-core describes WHAT a module is (name, settings, on/off). Anything that needs to reach deep
 * into Minecraft (e.g. changing the camera for Zoom) is done by the loader module, which reads the
 * module's settings.
 */
public abstract class Module {

    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private final boolean serverBlockable;
    private final List<Setting<?>> settings = new ArrayList<Setting<?>>();

    private boolean enabled;
    private boolean favourite;
    private boolean blockedByServer;
    private boolean supported = true;

    /**
     * @param serverBlockable true for features some servers don't allow (minimap, freelook, fullbright,
     *                        auto text…). They switch off automatically on those servers (build step 11).
     */
    protected Module(String id, String name, String description, Category category, boolean enabledByDefault,
                     boolean serverBlockable) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = enabledByDefault;
        this.serverBlockable = serverBlockable;
    }

    /** Adds a setting (call from the module's constructor). Returns it, so it can be kept in a field. */
    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public Category category() { return category; }
    public boolean serverBlockable() { return serverBlockable; }
    public List<Setting<?>> settings() { return Collections.unmodifiableList(settings); }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
                onEnable();
            } else {
                onDisable();
            }
        }
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    /** Switched on, allowed by the server, and working in this version of the mod. */
    public boolean isActive() {
        return enabled && !blockedByServer && supported
                && (category != Category.DEVELOPER || com.nexusclient.core.online.DeveloperMode.active());
    }

    public boolean isFavourite() { return favourite; }
    public void setFavourite(boolean favourite) { this.favourite = favourite; }

    public boolean isBlockedByServer() { return blockedByServer; }
    public void setBlockedByServer(boolean blocked) { this.blockedByServer = blocked; }

    /** False if this version of the mod (e.g. 1.8.9) hasn't got this feature yet – shown as "Coming soon". */
    public boolean isSupported() { return supported; }
    public void setSupported(boolean supported) { this.supported = supported; }

    /** Called when switched on. */
    protected void onEnable() {}

    /** Called when switched off. */
    protected void onDisable() {}

    /** Called 20 times a second while the game runs (if active). */
    public void onTick(GameInfo game) {}
}
