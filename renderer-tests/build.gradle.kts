// Fork-owned renderer regressions, kept apart from upstream tests and Julia's host tests.
plugins { id("julia.java-conventions") }

// Julia consumes this project but keeps all generated files outside the source fork.
layout.buildDirectory = rootProject.layout.projectDirectory.dir("modules/renderer-tests/build")

dependencies {
    testImplementation(project(":sdl-gpu"))
    testImplementation(libs.asm.tree)
    for (module in listOf("lwjgl", "lwjgl-opengl", "lwjgl-sdl", "lwjgl-shaderc", "lwjgl-spvc")) {
        val coordinates = "org.lwjgl:$module:${libs.versions.lwjgl.get()}"
        testImplementation(coordinates)
        // CPU memory and shader-tool libraries only. No device/window creation belongs in this suite.
        if (module != "lwjgl-sdl") testRuntimeOnly("$coordinates:${NativeTarget.host.classifier}")
    }
}
