package com.nexusclient.launcher.rerender;

import com.google.gson.JsonObject;
import com.nexusclient.launcher.instance.Instance;
import com.nexusclient.launcher.instance.InstanceStore;
import com.nexusclient.launcher.minecraft.MinecraftVersion;
import com.nexusclient.launcher.minecraft.VersionManifest;
import com.nexusclient.launcher.mods.ContentType;
import com.nexusclient.launcher.online.NexusOnline;
import com.nexusclient.launcher.settings.Settings;
import com.nexusclient.launcher.settings.SettingsStore;

import java.util.List;
import java.util.Map;

/**
 * WorkspaceSharing – keeps the player's shared workspace (Mod Rerender) up to date on the Nexus server,
 * but only while "Let other players render my workspace" is switched on. Switching it off deletes
 * it from the server.
 *
 * It's sent again when the launcher signs in, after each game session (mods/settings may have changed)
 * and whenever the setting or the shared instance changes. Everything runs in the background.
 */
public final class WorkspaceSharing {

    private static InstanceStore store;
    private static volatile String status = "Not shared";

    private WorkspaceSharing() {}

    public static void init(InstanceStore instances) {
        store = instances;
    }

    /** What happened last, for the "My Workspace" tab ("Shared · 42 mods, 120 files"). */
    public static String status() {
        return status;
    }

    /** Sends (or removes) the shared workspace in the background. */
    public static void syncLater() {
        Thread thread = new Thread(WorkspaceSharing::sync, "nexus-workspace");
        thread.setDaemon(true);
        thread.start();
    }

    private static synchronized void sync() {
        NexusOnline online = NexusOnline.get();
        Settings settings = SettingsStore.get();
        if (!online.signedIn() || store == null) {
            status = "Sign in to Nexus to share your workspace";
            return;
        }
        try {
            if (!settings.shareWorkspace) {
                online.delete("/api/workspace");
                status = "Not shared";
                return;
            }
            Instance instance = sharedInstance();
            if (instance == null) {
                status = "No instance to share yet";
                return;
            }
            JsonObject workspace = Workspace.build(instance, gameVersion(instance));
            online.put("/api/workspace", Map.of("data", workspace.toString()));
            status = "Sharing \"" + instance.name() + "\" · " + Workspace.content(workspace, ContentType.MOD).size()
                    + " mods, " + Workspace.files(workspace).size() + " setting files";
        } catch (Exception e) {
            status = "Couldn't reach the Nexus server (" + e.getMessage() + ")";
        }
    }

    /** The chosen instance, or the most recently played one. */
    public static Instance sharedInstance() {
        if (store == null) {
            return null;
        }
        List<Instance> instances = List.copyOf(store.instances());
        String chosen = SettingsStore.get().sharedInstanceId;
        Instance best = null;
        for (Instance instance : instances) {
            if (instance.id().equals(chosen)) {
                return instance;
            }
            if (best == null || instance.lastPlayed() > best.lastPlayed()) {
                best = instance;
            }
        }
        return best;
    }

    /** The real Minecraft version of an instance ("Latest" → e.g. "26.3"). */
    public static String gameVersion(Instance instance) {
        if (!Instance.LATEST.equals(instance.version()) && instance.version() != null) {
            return instance.version();
        }
        try {
            List<MinecraftVersion> versions = VersionManifest.fetchSupportedVersions();
            return versions.isEmpty() ? instance.version() : versions.get(0).id();
        } catch (Exception e) {
            return instance.version();
        }
    }
}
