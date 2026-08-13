plugins {
    application
}

dependencies {
    implementation(project(":effects-core"))
    implementation(project(":effects-libgdx"))
    implementation(libs.gdx.backend.lwjgl3)
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:${libs.versions.gdx.get()}:natives-desktop")
}

application {
    mainClass.set("io.github.teemuki8.libgdx.agent.effects.showcase.DesktopLauncher")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
