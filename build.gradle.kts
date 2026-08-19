import xyz.jpenilla.resourcefactory.bukkit.BukkitPluginYaml
import xyz.jpenilla.resourcefactory.bukkit.Permission

plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.6" // Shades + relocates bStats
    id("xyz.jpenilla.run-paper") version "2.3.1" // Adds runServer for testing
    id("xyz.jpenilla.resource-factory-bukkit-convention") version "1.2.0" // Generates plugin.yml based on the Gradle config
}

group = "net.player005.vanillablocking"
version = "1.5.0"
description = "Allows blocking your sword to reduce taken damage like in older Minecraft versions"

// The oldest server the plugin supports. Everything in the main source set is
// compiled against this API, so the compiler guarantees we never call anything
// a 1.21.4 server is missing.
val baseApi = "1.21.4-R0.1-SNAPSHOT"

// 1.21.5 added the native minecraft:blocks_attacks item component. Code using
// it lives in its own source set compiled against the newer API and is only
// class-loaded when the server actually provides that component - see
// BlockingStrategies.
val modernApi = "1.21.5-R0.1-SNAPSHOT"

java {
    // Configure the java toolchain. This allows gradle to auto-provision JDK 21 on systems that only have JDK 11 installed for example.
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

val modern: SourceSet by sourceSets.creating

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/") // WorldGuard
    maven("https://repo.extendedclip.com/releases/") // PlaceholderAPI
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$baseApi")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.13")
    compileOnly("me.clip:placeholderapi:2.11.6")

    // Shaded, so servers do not need to install it
    implementation("org.bstats:bstats-bukkit:3.2.1")

    // The 1.21.5+ source set sees the newer API plus the main classes
    "modernCompileOnly"("io.papermc.paper:paper-api:$modernApi")
    "modernCompileOnly"(sourceSets.main.get().output)

    testImplementation("io.papermc.paper:paper-api:$baseApi")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    compileJava {
        // Set the release flag. This configures what version bytecode the compiler will emit, as well as what JDK APIs are usable.
        // See https://openjdk.java.net/jeps/247 for more information.
        options.release = 21
    }
    named<JavaCompile>("compileModernJava") {
        options.release = 21
    }
    javadoc {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
    }
    test {
        useJUnitPlatform()
    }
    shadowJar {
        archiveClassifier = ""
        from(modern.output)
        relocate("org.bstats", "net.player005.vanillablocking.bstats")
    }
    build {
        dependsOn(shadowJar)
    }
    runServer {
        minecraftVersion("1.21.4")
    }
}

// Configure plugin.yml generation
// - name, version, and description are inherited from the Gradle project.
bukkitPluginYaml {
    main = "net.player005.vanillablocking.VanillaBlockingPaper"
    load = BukkitPluginYaml.PluginLoadOrder.STARTUP
    authors.add("Player005")
    apiVersion = "1.21"
    description = properties["description"] as String
    foliaSupported = true

    // All optional. OldCombatMechanics must load first so its config can be
    // read on enable instead of only on the first /vsb reload.
    softDepend = listOf("OldCombatMechanics", "WorldGuard", "PlaceholderAPI", "floodgate")

    commands.register("vanillablocking") {
        description = "Admin command for VanillaSwordBlocking"
        aliases = listOf("vsb")
    }

    permissions.register("vanillablocking.admin") {
        description = "Access to /vsb reload, cleanup, debug and refresh"
        default = Permission.Default.OP
    }
    permissions.register("vanillablocking.block") {
        description = "Allows blocking with a sword"
        default = Permission.Default.TRUE
    }
    permissions.register("vanillablocking.toggle") {
        description = "Allows toggling your own sword blocking with /vsb toggle"
        default = Permission.Default.TRUE
    }
}
