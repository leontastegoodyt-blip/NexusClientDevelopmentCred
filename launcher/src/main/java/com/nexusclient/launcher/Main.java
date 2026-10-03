package com.nexusclient.launcher;

/**
 * Main – where the installed Nexus app starts. It just starts NexusLauncher.
 *
 * Why a separate class: when JavaFX is packed into the installer as normal library files, Java refuses
 * to start a class that "extends Application" directly ("JavaFX runtime components are missing").
 * Starting from a plain class like this one avoids that.
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        NexusLauncher.main(args);
    }
}
