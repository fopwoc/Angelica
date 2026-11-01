# Angelica renderer fork

This fork supplies the Java OpenGL state manager (`glsm/`) and the SDL GPU renderer (`sdl-gpu/`) used by
[Julia for Project Zomboid](https://github.com/fopwoc/julia-pz). It is not a Minecraft mod anymore and does not
build or run as one.

Julia pins this repository's `pz` branch under `third_party/angelica/`, provides the Gradle build descriptors, and
builds from its own root:

```sh
git submodule update --init --recursive
./java/gradlew -p java check :agent:jar
```

What lives here:

- `glsm/` and `sdl-gpu/`: the renderer, LWJGL3 only. Julia's GL backend, its bridges, and the GL features SDL GPU
  lacks are part of these modules, not bytecode patches applied from outside.
- `sdl-gpu/native/`: the patched SDL 3 build with the renderer's native GPU extensions.
- `third_party/gtnhlib/`: a [GTNHLib fork](https://github.com/fopwoc/GTNHLib) of which only the vertex, buffer and
  tessellator classes `glsm` uses are compiled.

The original root Minecraft build and the other modules are not maintained here. The host owns the window, its
thread and its event loop, and hands the renderer what it needs through the renderer's APIs.

The `pz` branch merges [GTNewHorizons/Angelica](https://github.com/GTNewHorizons/Angelica) periodically. Original
source notices and the [LGPL v3 license](LICENSE) remain in force.
