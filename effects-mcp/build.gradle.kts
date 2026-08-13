plugins {
    application
}

dependencies {
    api(project(":effects-protocol"))
    implementation(libs.jackson.databind)
    api(libs.mcp)
}

application {
    mainClass.set("io.github.teemuki8.libgdx.agent.effects.mcp.Main")
}
