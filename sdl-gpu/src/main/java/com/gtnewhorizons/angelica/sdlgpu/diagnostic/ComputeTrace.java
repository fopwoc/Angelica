package com.gtnewhorizons.angelica.sdlgpu.diagnostic;

import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import static com.gtnewhorizons.angelica.sdlgpu.diagnostic.TraceUniforms.*;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.Subsystems;
import com.gtnewhorizons.angelica.sdlgpu.Upstream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Bounded CPU snapshots after compute binding and uniform packing; never waits for GPU completion. */
final class ComputeTrace implements AutoCloseable {
    private final TraceBudget budget = new TraceBudget(512, 8, 4 * 1024 * 1024);
    private final TraceShaders shaders;
    private final BufferedWriter writer;

    ComputeTrace(Path output) throws IOException {
        this(output, "compute-trace.txt");
    }

    ComputeTrace(Path output, String filename) throws IOException {
        shaders = new TraceShaders(output.resolve("compute-shaders"));
        writer = Files.newBufferedWriter(output.resolve(filename), StandardCharsets.UTF_8);
    }

    void capture(ContextState state, int x, int y, int z, long indirect) {
        if (!budget.available(state.boundProgram)) return;
        var program = state.boundProgramObj;
        if (program == null || !program.linked || program.computeSpirv == null) return;
        String source = Upstream.shaders().getShaderSource(program.computeShader);
        var snapshot = new StringBuilder("program=")
                .append(state.boundProgram)
                .append(" groups=")
                .append(x)
                .append(',')
                .append(y)
                .append(',')
                .append(z)
                .append(" indirectOffset=")
                .append(indirect)
                .append('\n');
        var staging = state.uniformStaging(program);
        if (staging.uniformDataBySlot != null)
            for (int location = 0; location < staging.uniformDataBySlot.length; location++) {
                String name = program.locationToName.get(location);
                if (name == null) continue;
                var member = program.anyBlockMemberInfo(location);
                if (member == null) member = program.locationToMemberInfo.get(location);
                snapshot.append("uniform ")
                        .append(name)
                        .append('=')
                        .append(values(staging.uniformDataBySlot[location], member))
                        .append(" packed=")
                        .append(packed(staging.fsUniformFb, program.fsInfoBySlot, location))
                        .append('\n');
            }
        var map = program.computeBindingMap;
        if (map != null) {
            images(snapshot, state, program, "read", map.roStorageTextureNames(), map.roStorageTextureGlSlots());
            images(snapshot, state, program, "write", map.rwStorageTextureNames(), map.rwStorageTextureGlSlots());
            int[] slots = map.samplerGlSlots();
            String[] names = map.samplerNames();
            for (int slot = 0; slot < slots.length; slot++) {
                String name = names == null || slot >= names.length ? null : names[slot];
                int unit = name != null && program.samplerTextureUnits.containsKey(name)
                        ? program.samplerTextureUnits.getInt(name)
                        : slots[slot];
                int texture = name == null
                        ? Subsystems.current().bufferTextures().samplerBinding(state, unit)
                        : Subsystems.current().bufferTextures().computeSamplerBinding(state, unit, name);
                snapshot.append("sampler ")
                        .append(slot)
                        .append(' ')
                        .append(name)
                        .append(" unit=")
                        .append(unit)
                        .append(" texture=")
                        .append(texture)
                        .append(" storage=")
                        .append(Upstream.resources().getTextureMeta(texture))
                        .append('\n');
            }
        }
        TraceBuffers.append(snapshot, state);
        String text = snapshot.toString();
        if (!budget.reserve(state.boundProgram, text, text.getBytes(StandardCharsets.UTF_8).length + 256)) return;
        try {
            writer.write("computeSource=" + shaders.write(source) + "\n");
            writer.write(text);
            writer.newLine();
            writer.flush();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot write compute trace", failure);
        }
    }

    private static void images(
            StringBuilder out,
            ContextState state,
            ShaderManager.ProgramObject program,
            String access,
            String[] names,
            int[] slots) {
        for (int slot = 0; slot < slots.length; slot++) {
            String name = names[slot];
            int unit = program.imageTextureUnits.containsKey(name) ? program.imageTextureUnits.getInt(name) : slots[slot];
            out.append("image ").append(access).append(' ').append(slot).append(' ').append(name).append(" unit=").append(unit);
            if (unit < 0 || unit >= state.boundStorageTextureByUnit.length) {
                out.append(" invalid-unit\n");
                continue;
            }
            int texture = state.boundStorageTextureByUnit[unit];
            out.append(" texture=").append(texture)
                    .append(" level=").append(state.boundStorageTextureLevel[unit])
                    .append(" layer=").append(state.boundStorageTextureLayer[unit])
                    .append(" layered=").append(state.boundStorageTextureLayered[unit])
                    .append(" access=").append(state.boundStorageTextureAccess[unit])
                    .append(" format=").append(state.boundStorageTextureFormat[unit])
                    .append(" storage=").append(Upstream.resources().getTextureMeta(texture)).append('\n');
        }
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
