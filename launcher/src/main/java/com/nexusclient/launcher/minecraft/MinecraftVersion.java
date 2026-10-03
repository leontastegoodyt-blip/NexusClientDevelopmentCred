package com.nexusclient.launcher.minecraft;

/**
 * MinecraftVersion – one Minecraft version from Mojang's version list (for example "1.8.9").
 *
 * This is a "record": a simple Java class that only holds data. Java writes the constructor
 * and the getters for us, so version.id() gives back "1.8.9", version.url() the link, etc.
 *
 * The names here match the names in Mojang's JSON exactly, which is how Gson knows
 * which JSON value goes into which field.
 *
 * @param id          the version name, e.g. "1.8.9" or "26.3"
 * @param type        "release" for normal versions, "snapshot" for test versions
 * @param url         link to this version's own JSON file (which lists its game files)
 * @param sha1        a "fingerprint" of that JSON file, used to check the download isn't damaged
 * @param releaseTime when Mojang released it
 */
public record MinecraftVersion(String id, String type, String url, String sha1, String releaseTime) {

    /** Dropdowns and lists show a version using this text, so just show the name, e.g. "1.8.9". */
    @Override
    public String toString() {
        return id;
    }
}
