package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.sdlgpu.compat.ReportedState;
import com.gtnewhorizons.angelica.sdlgpu.diagnostic.DrawTrace;
import com.gtnewhorizons.angelica.sdlgpu.extension.SdlBufferTextures;
import com.gtnewhorizons.angelica.sdlgpu.extension.SdlOcclusionQueries;
import com.gtnewhorizons.angelica.sdlgpu.extension.SdlTimestamps;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.framebuffer.BlendEquations;
import com.gtnewhorizons.angelica.sdlgpu.framebuffer.ColorMasks;
import com.gtnewhorizons.angelica.sdlgpu.framebuffer.Framebuffers;
import com.gtnewhorizons.angelica.sdlgpu.geometry.GeometryPrograms;
import com.gtnewhorizons.angelica.sdlgpu.geometry.GeometryViewports;
import com.gtnewhorizons.angelica.sdlgpu.query.Queries;
import com.gtnewhorizons.angelica.sdlgpu.shader.LinkedPrograms;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderPrograms;
import com.gtnewhorizons.angelica.sdlgpu.sync.GpuSyncs;
import com.gtnewhorizons.angelica.sdlgpu.texture.SamplerFallbacks;
import com.gtnewhorizons.angelica.sdlgpu.texture.TextureBindings;
import com.gtnewhorizons.angelica.sdlgpu.texture.TextureObjects;
import com.gtnewhorizons.angelica.sdlgpu.texture.TextureTable;
import com.gtnewhorizons.angelica.sdlgpu.texture.TextureViews;
import com.gtnewhorizons.angelica.sdlgpu.texture.VolumeUnpack;
import com.gtnewhorizons.angelica.sdlgpu.texture.buffer.BufferTextures;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.WeakHashMap;

/**
 * Julia's own GL state and services for one hosted backend. The backend layers hold it directly; bytecode hooks and
 * other static entry points reach it through {@link #current()}, which also enforces the context-owning thread.
 */
public final class Subsystems implements AutoCloseable {
    private final GeometryPrograms geometryPrograms = new GeometryPrograms();
    private final WeakHashMap<ContextState, GeometryViewports> geometryViewports = new WeakHashMap<>();

    public GeometryPrograms geometryPrograms() {
        return geometryPrograms;
    }

    public GeometryViewports geometryViewports() {
        return geometryViewports.computeIfAbsent(Upstream.context(), ignored -> new GeometryViewports());
    }

    private final ShaderPrograms shaderPrograms = new ShaderPrograms();
    private final LinkedPrograms linkedPrograms = new LinkedPrograms();
    private final Framebuffers framebuffers = new Framebuffers();
    private final ColorMasks colorMasks = new ColorMasks();
    private final BlendEquations blendEquations = new BlendEquations();
    private final DrawTrace drawTrace = new DrawTrace();
    private final TextureTable textures = new TextureTable();
    private final SamplerFallbacks samplerFallbacks = new SamplerFallbacks();
    private final TextureBindings textureBindings = new TextureBindings();
    private final TextureObjects textureObjects = new TextureObjects(textures);
    private final TextureViews textureViews = new TextureViews(textures);
    private final BufferTextures bufferTextures =
            new BufferTextures(SdlBufferTextures::new, textureBindings, textures, linkedPrograms);
    private final WeakHashMap<ContextState, VolumeUnpack> volumeUnpack = new WeakHashMap<>();
    private final IntOpenHashSet bufferObjects = new IntOpenHashSet();
    private final Queries queries = new Queries(SdlTimestamps::new, SdlOcclusionQueries::new);
    private final GpuSyncs syncs = new GpuSyncs();
    private final ReportedState reported = new ReportedState();

    public static Subsystems current() {
        return Upstream.ownedBackend().subsystems();
    }

    public ShaderPrograms shaderPrograms() {
        return shaderPrograms;
    }

    /** Julia's reflection of each linked upstream program. */
    public LinkedPrograms linkedPrograms() {
        return linkedPrograms;
    }

    public DrawTrace drawTrace() {
        return drawTrace;
    }

    public ColorMasks colorMasks() {
        return colorMasks;
    }

    public BlendEquations blendEquations() {
        return blendEquations;
    }

    public Framebuffers framebuffers() {
        return framebuffers;
    }

    /** Per-name texture records shared by the texture subsystems. */
    public TextureTable textures() {
        return textures;
    }

    public SamplerFallbacks samplerFallbacks() {
        return samplerFallbacks;
    }

    public TextureBindings textureBindings() {
        return textureBindings;
    }

    public TextureObjects textureObjects() {
        return textureObjects;
    }

    public TextureViews textureViews() {
        return textureViews;
    }

    public BufferTextures bufferTextures() {
        return bufferTextures;
    }

    /** Pixel-unpack state GL keeps per context that Angelica does not track. */
    public VolumeUnpack volumeUnpack() {
        return volumeUnpack.computeIfAbsent(Upstream.context(), ignored -> new VolumeUnpack());
    }

    /** Buffer names that were bound or created, so {@code glIsBuffer} matches GL before storage exists. */
    public IntSet bufferObjects() {
        return bufferObjects;
    }

    public Queries queries() {
        return queries;
    }

    public ReportedState reported() {
        return reported;
    }

    public GpuSyncs syncs() {
        return syncs;
    }

    /** Native resources first, then shader bookkeeping; views last because they may still retain storage. */
    @Override
    public void close() {
        geometryPrograms.close();
        drawTrace.close();
        queries.close();
        bufferTextures.close();
        samplerFallbacks.close();
        linkedPrograms.clear();
        shaderPrograms.close();
        textureViews.close();
        textures.clear();
    }
}
