package com.gtnewhorizons.angelica.sdlgpu.geometry;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL32.GL_GEOMETRY_SHADER;
import static org.lwjgl.opengl.GL43.*;

import com.gtnewhorizons.angelica.sdlgpu.Upstream;
import com.gtnewhorizons.angelica.sdlgpu.resource.FormatMap;
import com.gtnewhorizons.angelica.sdlgpu.shader.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.spvc.Spvc;

/** Owns linked geometry executables. Public GL names never expose the internal compute/raster programs. */
public final class GeometryPrograms implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(GeometryPrograms.class.getName());
    private final Map<Integer, GeometryExecutable> programs = new HashMap<>();

    public boolean contains(int program) {
        return programs.containsKey(program);
    }

    public boolean current() {
        return contains(Upstream.context().boundProgram);
    }

    public void link(int id, Map<Integer, String> stages, Map<String, float[]> initializers) {
        if (!stages.keySet().equals(Set.of(GL_VERTEX_SHADER, GL_GEOMETRY_SHADER, GL_FRAGMENT_SHADER)))
            throw new UnsupportedOperationException(
                    "Geometry programs require one vertex, geometry and fragment stage");
        for (int stage : List.of(GL_VERTEX_SHADER, GL_GEOMETRY_SHADER))
            for (var token : GeometrySyntax.parse(stages.get(stage)).tokens())
                if (token.getText().contains("sampler")
                        || token.getText().contains("image")
                        || token.getText().startsWith("atomic"))
                    throw new UnsupportedOperationException(
                            "Geometry pre-raster resources/side effects are not wired: " + token.getText());
        String geometry = stages.get(GL_GEOMETRY_SHADER),
                vertex = stages.get(GL_VERTEX_SHADER),
                fragment = stages.get(GL_FRAGMENT_SHADER);
        if (GeometrySyntax.parse(geometry).tokens().stream()
                .anyMatch(t -> t.getText().equals("gl_Layer")))
            throw new UnsupportedOperationException("Layered geometry framebuffer output is not wired yet");
        for (String source : List.of(geometry, vertex, fragment))
            for (var declaration : GeometrySyntax.parse(source).declarations()) {
                var words = declaration.tokens().stream().map(t -> t.getText()).toList();
                if (words.contains("buffer") || words.contains("uniform") && words.contains("{"))
                    throw new UnsupportedOperationException(
                            "Geometry application storage/uniform blocks are not wired yet");
            }
        var kernel = GeometryCompute.lower(geometry, new GeometryCompute.Bindings(0, 1, 2, 4));
        if (kernel.layout().input() != GeometryLayout.Input.TRIANGLES
                || kernel.layout().output() != GeometryLayout.Output.TRIANGLE_STRIP)
            throw new UnsupportedOperationException(
                    "Geometry draw wiring currently requires triangle input and triangle-strip output");
        var logical = Upstream.shaders().getProgram(id);
        var requested = new HashMap<String, Integer>();
        for (var entry : logical.attribLocationBindings.object2IntEntrySet())
            requested.put(entry.getKey(), entry.getIntValue());
        var fetch = GeometryVertex.lower(vertex, kernel, requested);
        var executable = new GeometryExecutable(kernel.layout(), fetch, fragment);
        try {
            executable.create();
            Upstream.shaders().releaseProgramGpuResources(logical);
            GeometryUniforms.merge(logical, executable.computeObject(), executable.rasterObject());
            logical.vertexSource = vertex;
            logical.fragmentSource = fragment;
            logical.vertexReflection = ShaderManager.StageReflection.EMPTY;
            logical.fragmentSpirv = copy(executable.rasterObject().fragmentSpirv);
            logical.maxFragOutputLocation = executable.rasterObject().maxFragOutputLocation;
            Upstream.context().releaseUniformStaging(logical);
            ProgramLinkState.linked(logical, Upstream.context());
            logical.resolvedAttribLocations.clear();
            logical.activeAttribNames = null;
            Arrays.fill(logical.vertexInputBaseType, 0);
            Arrays.fill(logical.vertexInputVecSize, 0);
            for (var input : fetch.attributes()) {
                logical.resolvedAttribLocations.put(input.field().name(), input.location());
                String type = input.field().type();
                int location = input.location();
                logical.vertexInputBaseType[location] = type.startsWith("i")
                        ? Spvc.SPVC_BASETYPE_INT32
                        : type.startsWith("u") ? Spvc.SPVC_BASETYPE_UINT32 : Spvc.SPVC_BASETYPE_FP32;
                logical.vertexInputVecSize[location] =
                        type.contains("vec") || type.startsWith("mat") ? type.charAt(type.length() - 1) - '0' : 1;
            }
            var previous = programs.put(id, executable);
            if (previous != null) previous.close();
            executable = null;
            var state = Upstream.context();
            var previousObject = state.boundProgramObj;
            try {
                state.boundProgramObj = logical;
                for (var entry : initializers.entrySet()) {
                    int location = Upstream.shaders().getUniformLocation(id, entry.getKey());
                    if (location >= 0) Upstream.pipeline().putUniform(state, location, entry.getValue());
                }
            } finally {
                state.boundProgramObj = previousObject;
            }
            if (state.boundProgram == id) bind(id);
            LOG.log(
                    System.Logger.Level.DEBUG,
                    "Linked GPU geometry program {0}: invocations={1}, maxVertices={2}, vertexInputs={3}",
                    id,
                    kernel.layout().invocations(),
                    kernel.layout().maxVertices(),
                    fetch.attributes().size());
        } finally {
            if (executable != null) executable.close();
        }
    }

    private static ByteBuffer copy(ByteBuffer source) {
        if (source == null) return null;
        return MemoryUtil.memAlloc(source.remaining()).put(source.duplicate()).flip();
    }

    public boolean bind(int program) {
        if (!contains(program)) return false;
        var state = Upstream.context();
        if (state.boundProgram != program) {
            var previous = state.boundProgramObj;
            if (previous != null && previous.deletePending) Upstream.shaders().releaseProgramFinal(state.boundProgram);
        }
        state.boundProgram = program;
        state.boundProgramObj = Upstream.shaders().getProgram(program);
        state.pipeline.vertexShader = 0;
        state.pipeline.fragmentShader = 0;
        state.pipeline.programId = program;
        state.pipeline.markShaderDirty();
        return true;
    }

    public boolean draw(int mode, int first, int count, int type, long indices, int baseVertex, int instances) {
        var executable = programs.get(Upstream.context().boundProgram);
        if (executable == null) return false;
        if (mode != GL_TRIANGLES || instances != 1)
            throw new UnsupportedOperationException("Geometry draw wiring requires non-instanced GL_TRIANGLES");
        if (Upstream.context().primitiveRestartEnabled)
            throw new UnsupportedOperationException("Geometry primitive restart is not wired yet");
        if (count > 0) executable.draw(first, count, type, indices, baseVertex);
        return true;
    }

    public void requireIntercepted() {
        if (current())
            throw new UnsupportedOperationException(
                    "Geometry draw entry point has not been routed through compute emulation");
    }

    public int attributeType(int program, int index) {
        var object = Upstream.shaders().getProgram(program);
        var names = object.getActiveAttribNames();
        if (index < 0 || index >= names.size())
            throw new IllegalArgumentException("Geometry attribute index: " + index);
        var input = programs.get(program).attributes().stream()
                .filter(attribute -> attribute.field().name().equals(names.get(index)))
                .findFirst()
                .orElseThrow();
        int location = input.location();
        return FormatMap.mapMemberInfoToGlType(new ShaderManager.UniformMemberInfo(
                0,
                input.field().size(),
                0,
                false,
                object.vertexInputVecSize[location],
                input.columns(),
                object.vertexInputBaseType[location],
                0));
    }

    public void requireResourceQueries(int program) {
        if (contains(program))
            throw new UnsupportedOperationException("Geometry program interface queries are not wired yet");
    }

    public int parameter(int program, int parameter) {
        var executable = programs.get(program);
        return switch (parameter) {
            case GL32.GL_GEOMETRY_INPUT_TYPE -> GL_TRIANGLES;
            case GL32.GL_GEOMETRY_OUTPUT_TYPE -> GL11.GL_TRIANGLE_STRIP;
            case GL32.GL_GEOMETRY_VERTICES_OUT -> executable.layout().maxVertices();
            case GL40.GL_GEOMETRY_SHADER_INVOCATIONS -> executable.layout().invocations();
            default -> throw new UnsupportedOperationException("Geometry program parameter: " + parameter);
        };
    }

    public void released(int program) {
        var removed = programs.remove(program);
        if (removed != null) removed.close();
    }

    @Override
    public void close() {
        for (var program : programs.values()) program.close();
        programs.clear();
    }
}
