import org.gradle.api.initialization.resolve.RepositoriesMode

rootProject.name = "libgdx-agent-effects"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

include(
    "effects-core",
    "effects-protocol",
    "effects-libgdx",
    "effects-mcp",
    "effects-fixtures",
)
