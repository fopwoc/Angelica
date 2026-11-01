# Angelica, `pz` branch

This branch is the renderer for [Julia](https://github.com/fopwoc/julia-pz), a Project Zomboid mod. Only `glsm`,
`sdl-gpu`, `renderer-tests`, the patched SDL build in `sdl-gpu/native`, and the GTNHLib subset in `third_party/gtnhlib` matter here.
Julia compiles them with its own Gradle build and keeps the build trees outside the fork. Every other module is left
untouched and unbuilt, and may stop compiling. Minecraft compatibility is not preserved, and the renderer targets
LWJGL3 only: no LWJGL2 API, lwjgl3ify, `org.lwjglx`, or Minecraft classes.

## Submodule chain

- Julia pins this fork at `third_party/angelica`; this fork pins `fopwoc/GTNHLib` (`pz`) at `third_party/gtnhlib`,
  which has its own `AGENTS.md`. Clone and update with `--recursive`.
- Commit innermost first: GTNHLib `pz`, then the pin bump here, then the pin bump in Julia.
- `third_party/gtnhlib` follows the GTNHLib version in `gradle/libs.versions.toml`. When an upstream merge bumps it,
  merge that GTNHLib tag into its `pz` and bump the pin here.

## Upstream

- `upstream` is GTNewHorizons/Angelica. Merge `upstream/master` into `pz` periodically, with `rerere` on. Never rebase
  or force-push `pz`: Julia's history pins its commits.
- After a merge, read the upstream diff around the hooks listed below for fixes worth carrying into owned code.
- We do not open upstream PRs, so upstream's contribution and AI policies do not bind this branch. Findings worth
  sharing go upstream as issues the user writes.

## Owned code

- `sdlgpu.HostedBackend` is the registered backend; `sdlgpu.layer` holds its overrides, one abstract layer per GL
  domain. `Subsystems` holds their state, and `Upstream` reaches the widened upstream internals.
- `sdlgpu.bridge` holds static GL entry points that Julia's rewritten game code calls by name. Keep entry points
  (bridges) apart from execution changes (layers and hooks).
- `sdlgpu.extension` binds the native SDL extensions built from `sdl-gpu/native`.

## Zones

- **Tracked**: files that also exist upstream. Change them only with small hooks: a call into owned code, a setter, a
  wider visibility. Match upstream style, and never reformat, reorder, or rename in them.
- **Owned**: files added on `pz` (`git diff --diff-filter=A upstream/master...pz --name-only`). Free to change. Use
  upstream's packages when package-private access helps, or a new package under `sdlgpu` for a new domain.
- **Deleted**: Minecraft and launcher glue removed on `pz`. When upstream edits one, keep it deleted.
- Owned code must not reference `dev.fopwoc.julia`. Julia configures the renderer through fork APIs before `init()`.

## Style

- Tracked files keep upstream's formatting and log4j loggers. Owned files came from Julia and keep its formatting and
  `System.Logger`.
- Use comments for non-obvious reasoning only. Javadoc a class when its role isn't evident from its name.
- Fork-owned renderer regressions live in `renderer-tests/src/test`, separate from upstream's `glsm/src/test` and
  `sdl-gpu/src/test`. Julia's `:renderer-tests` module builds them; upstream test trees remain untouched and unwired.
  Keep CPU tests independent of Julia and PZ. Device/window tests require a separate explicit task and user request.

## Hooks in tracked files

Keep this list current. One line per hook: file, what it calls, and why.

- `FrameManager`: `setCommandBufferReacquiredHook`; the backend resets uniform-push caches when a command buffer is
  replaced mid-frame.
- `SDLGPURenderBackend`: `configureWindowThread`; presentation runs on the application's window thread.
- `SDLGPURenderBackend.unknownGetInteger`/`unknownGetFloat`: throw instead of answering 0, so unsupported queries
  surface.
- `AttachmentClear`, `DepthStencilReadback`, `PipelineApplier`: `StencilReference.set`; preserves unsigned stencil
  references on macOS ARM64.
- `ResourceManager.shouldDeferTextureUpload`: always `false`; bound uploads and inline writes share one command stream.
- `QuadConverter`: `bufferData` instead of `bufferSubData`; earlier deferred draws keep their indices.
- `FragmentShaderGenerator`, `FullscreenPass`: `ShaderSources` instead of Embeddium's `ShaderLoader`.
- `Device`: reads the application's window through `WindowSource` (`setWindowSource`) instead of LWJGL2's `Display`.
  The window itself, its borrowing and its event loop belong to Julia.
- `SDLGPURenderBackend.init`: the loading-checkpoint listener only flushes. Startup-complete state still controls
  synchronization; Minecraft's separate splash renderer is removed. `OffscreenTarget` remains the final frame target.
- `GLStateManager.capabilities`: LWJGL3 `GLCapabilities` from `GL.getCapabilities()`, installed by the backend's
  capability shim in `onPostWindowCreate` (including offscreen windows), before GLSM pre-initialization reads it.
- `GLSMMatrices`: local GLU error codes; LWJGL3 has no GLU.
- `TessellatorEntityIds`, `TessellatorStreamingDrawer`: import GTNHLib's owned `Tessellator` instead of Minecraft's;
  its global `instance` is always `null`.
- Widened for `Upstream` (owned, `sdlgpu`): `SDLGPURenderBackend`'s manager fields, `s()`, `flushAndSubmitMidFrame`,
  and `midFrameFenceFlush` (package-private); `FenceTracker.fenceMap`, `ShaderManager.releaseShaderAttachment`, and
  `ResourceManager.textureHandles`/`textureMetas`/`wLock`/`beforeTextureReplaced` (public).
- `sdl-gpu`'s `RenderBackend` service file registers only `HostedBackend`, the owned subclass chain
  (`sdlgpu.layer`) over `SDLGPURenderBackend`.
- `ResourceManager`: `TextureStorage.changed()` before taking and after releasing the write lock in the four texture
  map writers, so per-draw caches of derived texture state know to refresh.
- `PipelineApplier.ensureDrawRenderPass`: `DrawTrace.beforeDraw` at the common draw boundary, including indirect and
  instanced draws.
- `RenderBackend.compatShaderSource` (new, defaults to `CompatShaderTransformer.transform`): `GLSMPrograms.glShaderSource`
  calls it; `ShaderLayer` overrides it to resolve conditional GLSL first (`CompatShaderInput`).
- `RenderBackend.selectTextureTarget`/`answersTextureBindingQuery`/`onTextureDeleted` (new, no-op defaults): called at
  the head of GLSM's 30 target-taking texture operations, `GLSMQueries.glGetInteger`, and
  `GLSMTextures.onDeleteTexture`; `TextureLayer` keeps per-target logical bindings through them.
- `RenderBackend.cachesColorMask`/`cachesBlendEquation` (new, default `true`): `GLSMBlend.glColorMask`,
  `glBlendEquation`, and `glBlendEquationSeparate` bypass their cache when false; `FramebufferLayer` returns false
  while outputs differ per index.
- `PipelineCache`: `computeOutputHash` mixes in `ColorMaskHooks`/`BlendEquationHooks`, and `createPipelineInner` reads
  each color target's write mask and blend ops through them.
- Framebuffer slices through `FramebufferHooks` (owned): `PipelineCache` gates its depth/stencil flags by attached
  aspects and keeps an empty color-format array as a depth-only framebuffer; `FrameManager.beginRenderPass` begins
  sparse targets natively; `PipelineApplier.ensureRenderPass` validates the draw, skips the dummy color target for
  absent outputs, and begins through the slice-aware path; `FboState.recomputeTargets` records the slice layout;
  `FBOClearTracker` compares slices and always rebuilds the pipeline color formats.
- Vertex inputs through `VertexInputs` (owned): `PipelineCache` mixes full-width divisors into `computeInputHash`,
  creates pipelines natively, and types disabled attributes from the generic value; `VertexWriteReplayer` clamps
  replay divisors through it. `RenderBackend.preservesVertexLayout` (new, default `false`) stops
  `GLSMVertexInput.pointAttrib` from repairing layouts `VertexLayer` owns.
- Texture views through `TextureViewHooks` (owned): `FBOClearTracker` marks shared access before clearing or writing
  a texture; `ResourceManager` keeps handles still referenced by views and notifies views before reallocating a name;
  `PipelineApplier.ensureDrawRenderPass` prepares views and buffer textures; `SamplerBinder` samples through views,
  buffer textures and fallbacks. `RenderBackend.deleteTextureNow` (new, default `false`) lets `TextureLayer` delete
  at once instead of `GLSMTextureLifetime.deferDeleteTexture`'s deferral.
- Buffer textures through `BufferTextureHooks` (owned): `ContextState.getBoundBuffer(GL_TEXTURE_BUFFER)`,
  `ResourceManager.returnBufferToPool` retiring storage that buffer textures still hold, typed sampler lookups in
  `SamplerBinder.bindStageSamplers` and `ComputeBinder.bindComputeSamplers`, and native MSL texture buffers in
  `MslCrossCompile` (whose cache keys are renamed so older translations are not reused).
- Sampler bindings: `FBOClearTracker`, `StorageTextureBinder`, `MovedSamplerCopies`, and
  `VertexWriteReplayer.capture` read sampled textures through `BufferTextureHooks.samplerBinding`;
  `ComputeBinder.setSamplerOverrides` tells `TextureBindingHooks` (owned) while overrides are active.
- Sampler tables: `ContextState.MAX_SAMPLERS` is `SamplerResources.LIMIT` (32) and its logical unit arrays, like
  `VertexWriteReplayer.TEXTURE_UNITS`, use `TextureBindings.UNIT_COUNT` (256); `GLStateManager.MAX_TEXTURE_UNITS`
  asks for the combined unit count. `ShaderManager` creates shaders and compute pipelines through `SamplerShaderBridge`
  (owned), delegates sampler reflection to `SamplerResources`, checks compute layouts, and renames its disk-cache
  keys; `MslCrossCompile.compile` rewrites instance indices and compiles wide sampler tables through argument buffers.

- `RenderBackend.namedFramebufferTextureLayer` (new, fails if unsupported): `GLSMFramebuffer` calls it directly;
  `FramebufferLayer` attaches the requested slice without temporary binding changes, including when validation fails.

## Deleted on `pz`

- LWJGL2 `Display`/`Drawable` context model: `GLStateManager.makeCurrent`/`releaseContext`/`swapBuffers`/
  `updateDisplay`, the matching `RenderBackend` hooks and redirector rewrites, `SDLDrawable`, `SDLGPUDisplayBridge`.
- GLU quadrics (`compat/lwjgl/Angelica*`), `Lwjgl2GLRenderBackend` and glsm's service file that registered it,
  `MainThreadPump.pollInput`, glsm's Minecraft stubs (`glsm/src/stubs`), and the `KHRDebugCallback` overload of
  `glDebugMessageCallback`.
- `README_uk.MD`, upstream's Minecraft mod README; `README.MD` describes this fork.
- Minecraft splash presentation: `SplashDispatcher`, its backend target/dispatch/teardown branches,
  `FrameManager.presentSplash`, and their splash-only tests. Normal offscreen presentation remains.
- LWJGL2-only `GLU`, `Project`, and `Util.checkGLError` redirector entries; LWJGL3 does not expose those classes.
