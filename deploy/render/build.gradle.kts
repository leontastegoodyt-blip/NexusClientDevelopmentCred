// The same shared settings as the project's root build.gradle.kts (Java 21, UTF-8, Maven Central),
// but only for the server – see settings.gradle.kts next to this file.
subprojects {
    apply(plugin = "java")

    group = "com.nexusclient"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
