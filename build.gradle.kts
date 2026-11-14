plugins {
    java
}

val id = project.property("id") as String
val extensionName = project.property("name") as String
val author = project.property("author") as String
val version = project.version as String
val geyserApiVersion = "2.11.0"

repositories {
    // Repo for the Geyser API artifact
    maven("https://repo.opencollab.dev/main/")

    // Add other repositories here
    mavenCentral()
}

dependencies {
    compileOnly("org.geysermc.geyser:api:$geyserApiVersion-SNAPSHOT")
    compileOnly("org.geysermc.geyser:core:$geyserApiVersion-SNAPSHOT")

    implementation("org.yaml:snakeyaml:2.7")
    implementation("com.google.code.gson:gson:2.14.0")
}

// Java currently requires Java 21 or higher, so extensions should also target it
java {
    targetCompatibility = JavaVersion.VERSION_21
    sourceCompatibility = JavaVersion.VERSION_21
}

afterEvaluate {
    val idRegex = Regex("[a-z][a-z0-9-_]{0,63}")
    if (idRegex.matches(id).not()) {
        throw IllegalArgumentException("Invalid extension id $id! Must only contain lowercase letters, " +
                "and cannot start with a number.")
    }

    val nameRegex = Regex("^[A-Za-z_.-]+$")
    if (nameRegex.matches(extensionName).not()) {
        throw IllegalArgumentException("Invalid extension name $extensionName! Must fit regex: ${nameRegex.pattern})")
    }
}

tasks {
    // This automatically fills in the extension.yml file.
    processResources {
        filesMatching("extension.yml") {
            expand(
                "id" to id,
                "name" to extensionName,
                "api" to geyserApiVersion,
                "version" to version,
                "author" to author
            )
        }
    }
}
