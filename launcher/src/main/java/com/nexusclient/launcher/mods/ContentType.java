package com.nexusclient.launcher.mods;

/**
 * ContentType – the kinds of things Nexus can add to an instance, and where each one goes.
 *
 * @param label        shown to players, e.g. "Resource Packs"
 * @param modrinthType Modrinth's name for it
 * @param folder       the folder inside the instance where the files go
 */
public enum ContentType {
    MOD("Mods", "mod", "mods"),
    RESOURCE_PACK("Resource Packs", "resourcepack", "resourcepacks"),
    SHADER("Shaders", "shader", "shaderpacks"),
    MODPACK("Modpacks", "modpack", null);

    private final String label;
    private final String modrinthType;
    private final String folder;

    ContentType(String label, String modrinthType, String folder) {
        this.label = label;
        this.modrinthType = modrinthType;
        this.folder = folder;
    }

    public String label() { return label; }
    public String modrinthType() { return modrinthType; }
    public String folder() { return folder; }
}
