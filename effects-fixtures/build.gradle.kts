dependencies {
    implementation(project(":effects-libgdx"))
    implementation(project(":effects-mcp"))
    implementation(libs.gdx.backend.lwjgl3)
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:${libs.versions.gdx.get()}:natives-desktop")
}
