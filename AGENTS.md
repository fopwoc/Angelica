# Angelica, `pz` branch

This branch is the renderer for Julia, a Project Zomboid mod. Only `glsm`, `sdl-gpu`, and the patched SDL build in
`sdl-gpu/native` matter here; Julia compiles them with its own Gradle build and keeps the build trees outside the
fork. Every other module is left untouched and unbuilt.
Minecraft compatibility is not preserved, and the renderer targets LWJGL3 only: no LWJGL2 API, lwjgl3ify, or
`org.lwjglx`. The one exception is the GTNHLib binary glsm still links; Julia remaps it at load time until its used
classes are vendored here.

## Upstream

- `upstream` is GTNewHorizons/Angelica. Merge `upstream/master` into `pz` periodically, with `rerere` on. Never rebase
  or force-push `pz`: Julia's history pins its commits.
- After a merge, read the upstream diff around the hooks listed below for fixes worth carrying into owned code.
- We do not open upstream PRs, so upstream's contribution and AI policies do not bind this branch. Findings worth
  sharing go upstream as issues the user writes.

## Zones

- **Tracked**: files that also exist upstream. Change them only with small hooks: a call into owned code, a setter, a
  wider visibility. Match upstream style, and never reformat, reorder, or rename in them.
- **Owned**: files added on `pz` (`git diff --diff-filter=A upstream/master...pz --name-only`). Free to change. Put
  them in upstream's domain packages so package-private access works.
- **Deleted**: Minecraft and launcher glue removed on `pz`. When upstream edits one, keep it deleted.
- Owned code must not reference `dev.fopwoc.julia`. Julia configures the renderer through fork APIs before `init()`.

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
- `SDLGPURenderBackend.init`: creates no splash target, and the loading-checkpoint listener only flushes. The rest of
  the Minecraft splash path (`SplashDispatcher`, `OffscreenTarget`, the splash branches) stays in place but never
  runs.
- `GLStateManager.capabilities`: LWJGL3 `GLCapabilities` from `GL.getCapabilities()`, installed by the backend's
  capability shim on the initializing thread.
- `GLSMMatrices`: local GLU error codes; LWJGL3 has no GLU.
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

## Deleted on `pz`

- LWJGL2 `Display`/`Drawable` context model: `GLStateManager.makeCurrent`/`releaseContext`/`swapBuffers`/
  `updateDisplay`, the matching `RenderBackend` hooks and redirector rewrites, `SDLDrawable`, `SDLGPUDisplayBridge`.
- GLU quadrics (`compat/lwjgl/Angelica*`), `Lwjgl2GLRenderBackend` and glsm's service file that registered it,
  `MainThreadPump.pollInput`, the `launchwrapper` stub, and the `KHRDebugCallback` overload of
  `glDebugMessageCallback`.
