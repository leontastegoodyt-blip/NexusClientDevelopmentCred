// =====================================================================================
// build.gradle.kts (root) – settings that apply to EVERY module at once.
//
// Instead of repeating the same lines in all 6 modules, we put them here.
// Each module also has its own build.gradle.kts for things only that module needs.
// =====================================================================================

subprojects {
    // Every module is a Java project.
    apply(plugin = "java")

    // "group" is like the family name of our code, "version" is the release number.
    group = "com.nexusclient"
    version = "0.1.0"

    // Where Gradle downloads libraries (other people's code we use) from.
    // Maven Central is the main public library store for Java.
    repositories {
        mavenCentral()
    }

    // Build everything with Java 21 unless a module says otherwise.
    // (mod-legacy will switch to Java 8 later, because Minecraft 1.8.9 needs it.)
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    // Read our source files as UTF-8 so special characters (like é or ✓) never break.
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
