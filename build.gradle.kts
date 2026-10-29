plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "one.util.ideaplugin"
version = "2.0.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    compileOnly("org.jspecify:jspecify:1.0.0")
    intellijPlatform {
        intellijIdea("2026.2.3")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

// Keep bytecode at Java 21 so the plugin still runs on 2025.1 (JBR 21),
// while compiling against 2026.x platform classes, which are Java 25 bytecode.
tasks.withType<JavaCompile> {
    options.release = 21
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
