# GPU geometry emulation development

The renderer's `sdlgpu.geometry` package is an experimental compute path. It is not connected to GL program
linking or drawing yet. `ShaderFrontend` continues rejecting geometry stages, and the backend continues reporting
geometry shaders as unsupported. Do not advertise support just because the source compiler accepts a fixture.

Implemented components:

- `GeometryCompute` converts a preprocessed geometry-stage body to compute. One job handles one input primitive
  and one geometry invocation. Output records retain position, user values, viewport, layer, primitive ID and strip ID.
- `GeometryLayout` and `GeometryField` describe std430 transport and bound all dispatch/allocation arithmetic.
- `GeometryAssembly` expands emitted points, line strips or triangle strips into fixed per-job corner streams.
  It preserves strip winding, uses last-vertex routing, marks unused corners invalid and leaves overflow flagged.
- `GeometryRaster` generates a vertex stage reading those corner streams. Routing selects one logical viewport/layer
  per raster pass. Primitive IDs are exposed through a flat user varying for a later fragment-stage adaptation.

Initial limits are deliberate: scalar/vector/float-matrix interfaces, unsized primitive input arrays, at most 32
invocations and 1024 emitted vertices per invocation, and one output stream. Interface blocks, explicit interface
layouts, precision/invariance qualifiers, clip/cull distances and point size need additional transport support and
are rejected. Internal transport bindings must not alias shader resources.

CPU checks compile generated compute stages to SPIR-V, cross-compile to Metal, compile raster stages, and validate
layouts, overflow arithmetic and rejection paths. They create no device or window.

Julia's development probe has a focused `--geometry <output-directory>` command. It opens an offscreen backend and
checks actual GPU emission and assembly, including two input triangles, six invocations, multiple strip boundaries,
routing and bounded output overflow. It requires an explicit user request, just like every other native probe.
It does not launch PZ. Until it passes, generated kernels have compilation evidence only, not GPU execution evidence.

Remaining before activation:

1. Validate emission, assembly and raster replay on the actual backend; fail on GPU overflow, never silently discard it.
2. Produce primitive input records from GPU vertex fetching, including indices, base vertices and instancing.
3. Manage internal programs, uniforms/resources, transient buffers and original GL state across all affected draw paths.
4. Implement logical viewport/layer state and raster passes, including scissor/depth state and provoking-vertex policy.
5. Preserve fragment-stage interfaces/primitive IDs and introspection, and validate lifecycle/relink/delete behavior.
6. Run offscreen rendering regressions, then manually test the game. Advertise only the validated subset.
