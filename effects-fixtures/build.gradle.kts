dependencies {
    implementation(project(":effects-libgdx"))
    implementation(project(":effects-import"))
    implementation(project(":effects-mcp"))
    implementation(project(":effects-protocol"))
    implementation(libs.gdx.backend.lwjgl3)
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:${libs.versions.gdx.get()}:natives-desktop")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
