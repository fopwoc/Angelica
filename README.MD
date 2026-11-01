# Angelica renderer fork

This fork supplies the Java OpenGL state manager (`glsm/`) and SDL-GPU renderer (`sdl-gpu/`) used by
[Julia for Project Zomboid](https://github.com/fopwoc/julia-pz). The `pz` branch is kept rebased on GTNewHorizons Angelica `master`.
It is no longer intended to build or run as a Minecraft mod.

The consuming Julia repository provides the Gradle build descriptors and pins this repository under
`third_party/angelica/`. Build from Julia's root:

```sh
./java/gradlew -p java check :agent:jar
```

The original root Minecraft build is not maintained for this fork. Renderer changes belong in these source modules,
not in host-specific bytecode patches. The host owns device/window startup and can supply its window-thread executor;
the renderer loads shader resources from its own classpath without Minecraft's resource manager or launcher.

Some reusable GTNHLib vertex/buffer helpers and LWJGL3ify compatibility APIs remain dependencies. This is an ongoing
extraction, not a claim that all Minecraft-origin types have been removed. Julia's build packages these dependencies
and the two renderer modules without publishing them to Maven.

Upstream: [GTNewHorizons/Angelica](https://github.com/GTNewHorizons/Angelica).
Original source notices and the [LGPL v3 license](LICENSE) remain in force.
