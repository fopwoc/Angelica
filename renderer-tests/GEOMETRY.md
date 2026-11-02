# GPU geometry emulation development

The renderer's `sdlgpu.geometry` package connects GL geometry programs to compute emission, strip assembly and
raster replay. `ShaderFrontend` validates the original vertex/geometry/fragment link before lowering it. Unsupported
translation or draw paths fail explicitly; geometry is never silently removed. The backend still does not advertise
complete geometry-shader support.

## Execution

- `GeometryVertex` lowers a pure vertex stage to GPU buffer fetching and namespaces its globals, helpers and uniforms.
  `GeometryBuffers` uploads VAO descriptors; vertex and index payload stays in the application's GPU buffers.
- `GeometryCompute` evaluates one input primitive and geometry invocation per compute job. Records retain position,
  user values, viewport, layer, primitive ID and strip ID. The first executor recomputes the pure vertex stage per
  geometry invocation; stages with side effects or pre-raster texture resources are rejected.
- `GeometryAssembly` expands strips into fixed per-job corner streams, preserving winding and last-vertex routing.
- `GeometryRaster` reads emitted records and corners. Flat user values come from the last emitted vertex for every
  corner of a primitive, independent of the native raster API's provoking-vertex convention. Internal raster controls
  use a GPU storage buffer, separate from fragment default uniforms.
- `GeometryUniforms` merges public default uniforms from the original stages. Internal program names, buffers and
  transport uniforms remain private. Relinks reset uniform values and query caches; deleting a current program keeps
  its executable alive until it is unbound.
- `GeometryDrawState` restores the program, VAO, indexed storage/uniform bindings and physical viewport/scissor/depth
  state after internal passes, including on a GPU validation failure.

The executor reads completion/error flags synchronously before rasterization and throws on output overflow or an
out-of-range vertex fetch. Indirect array batches also read back command records through the backend's buffer
readback path, including GPU-written records; vertex/index payload stays on the GPU. It reallocates transport buffers
and replays logical viewports separately. This is an initial correctness path, not a performance claim.

## Wired subset

- Vertex + geometry + fragment programs, `GL_TRIANGLES` input and triangle-strip output.
- `glDrawArrays`, `glDrawElements`, base-vertex indexed draws, and their routed instanced entry points.
- `glMultiDrawArraysIndirect`, including padded records, zero-sized commands, GPU-written parameters and per-command
  draw IDs. Direct array draws with a base instance use the same instanced fetch path. Base instance biases attribute
  indices after divisor division; `gl_InstanceID` stays zero-based and primitive IDs restart for each instance.
- Up to four word-sized GPU vertex/index streams; scalar/vector/float-matrix attributes, disabled generic attributes,
  byte/short/int/float/half formats, normalization and byte/short/int indices. Client-memory arrays are rejected.
- Default uniforms including matrix arrays and values shared across stages, plus ordinary fragment samplers.
- Sixteen logical integer-pixel viewports, with indexed scissor enable/rectangle and depth-range state. Global setters
  broadcast to every viewport as defined by GL. Geometry layer writes are rejected until layered output is wired.
- Scalar/vector/float-matrix varyings with smooth, noperspective or flat interpolation, and fragment primitive IDs.
- Bounds of 32 geometry invocations, 1024 emitted vertices per invocation, 64 MiB per transport/readback buffer and
  65,536 indirect commands per batch.

Application uniform/storage blocks, pre-raster samplers/images/atomics, primitive restart, indexed indirect
and other multi-draw entry points, geometry program-interface queries, multiple output streams, interface blocks, explicit
varying layouts, precision/invariance qualifiers, clip/cull distances and point size are not wired. They fail rather
than using a partial rendering fallback. The component generators handle more topologies than the GL draw executor;
that does not imply those topologies can be used through GL yet.

## Validation

CPU tests compile generated compute/raster stages, cross-compile to Metal, and check transport arithmetic, parser
rejections, attribute locations, uniform merging, default-block reflection after binding remapping, and indexed state.
They create no device or window.

Julia's explicitly requested `--geometry <output-directory>` probe checks GPU emission, six invocations, multiple
strips, routing, bounded overflow and real raster pixels. Its GL integration case links an original three-stage
program, fetches indexed/normalized/disabled vertex attributes on the GPU, uploads shared uniforms and a matrix array,
checks six viewports and flat varyings, then checks state restoration, out-of-range fetch rejection, relinking and
current-program deletion. Its indirect case checks GPU-written instance counts, padded/empty command records,
first-vertex offsets, divisor-two attributes, base instances, draw/instance IDs, primitive-ID resets and binding
restoration, plus direct base-instance drawing. It opens no game or native window. Metal validation does not establish Vulkan behavior or
PZ/pz3d gameplay readiness; those require separate tests.
