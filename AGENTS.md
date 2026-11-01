# Angelica, `pz` branch

This branch is the renderer for Julia, a Project Zomboid mod. Only `glsm`, `sdl-gpu`, and the patched SDL build
matter here; Julia compiles them with its own Gradle build. Every other module is left untouched and unbuilt.
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
- `Device`: reads the application's window through `WindowSource` (`setWindowSource`) instead of LWJGL2's `Display`;
  `SDLGPURenderBackend` sizes the splash target from it.
- `GLStateManager.capabilities`: LWJGL3 `GLCapabilities` from `GL.getCapabilities()`, installed by the backend's
  capability shim on the initializing thread.
- `GLSMMatrices`: local GLU error codes; LWJGL3 has no GLU.

## Deleted on `pz`

- LWJGL2 `Display`/`Drawable` context model: `GLStateManager.makeCurrent`/`releaseContext`/`swapBuffers`/
  `updateDisplay`, the matching `RenderBackend` hooks and redirector rewrites, `SDLDrawable`, `SDLGPUDisplayBridge`.
- GLU quadrics (`compat/lwjgl/Angelica*`), `Lwjgl2GLRenderBackend`, `MainThreadPump.pollInput`, the `launchwrapper`
  stub, and the `KHRDebugCallback` overload of `glDebugMessageCallback`.
