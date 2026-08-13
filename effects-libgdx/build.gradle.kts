dependencies {
    api(project(":effects-core"))
    api(libs.gdx)
    testImplementation(libs.gdx.backend.lwjgl3)
    testRuntimeOnly("com.badlogicgames.gdx:gdx-platform:${libs.versions.gdx.get()}:natives-desktop")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
