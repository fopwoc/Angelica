# Renderer regression tests

These are the fork-owned renderer tests originally written for Julia. They live in their own module, separate from
upstream's `glsm/src/test` and `sdl-gpu/src/test`. Keep tests in the package of the renderer behavior they exercise.
No test in this module depends on Julia classes, Project Zomboid, a GPU device, or a window.

Julia's Gradle build includes this project and keeps its outputs in `java/modules/renderer-tests/build`.
From Julia's repository root:

```sh
./java/gradlew -p java :renderer-tests:test
```

The suite includes CPU shader compilation and SPIR-V transformations, so it loads the host's LWJGL shader-tool and
memory natives. It never creates a graphics device. Future GPU tests need a separate explicit task and user request.
Upstream's test directories are not part of this module.

Julia retains tests of its host interception, isolated loader, window borrowing, lifecycle, logging integration,
and packaging, plus the full integration probes. This verification-only project is not bundled in the agent distribution.
