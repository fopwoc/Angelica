package com.gtnewhorizons.angelica.sdlgpu.geometry;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStreamRewriter;

/**
 * Lowers a preprocessed, bounded geometry stage to compute. Vertex input records are produced separately;
 * emitted strips and routing metadata stay on the GPU for a subsequent primitive assembly pass.
 */
public final class GeometryCompute {
    public record Bindings(int input, int output, int counts, int control) {
        public Bindings {
            if (input < 0
                    || output < 0
                    || counts < 0
                    || control < 0
                    || input == output
                    || input == counts
                    || output == counts)
                throw new IllegalArgumentException("Geometry storage bindings must be nonnegative and distinct");
        }
    }

    public record Kernel(GeometryLayout layout, Bindings bindings, String source) {}

    private GeometryCompute() {}

    public static Kernel lower(String preprocessed, Bindings bindings) {
        var parsed = GeometrySyntax.parse(preprocessed);
        var tokens = parsed.tokens();
        var rewrite = new TokenStreamRewriter(parsed.stream());
        var removed = new BitSet();
        var layouts = new LinkedHashMap<String, String>();
        var inputs = new ArrayList<GeometryField>();
        var outputs = new ArrayList<GeometryField>();
        for (var declaration : parsed.declarations()) {
            var words = declaration.tokens().stream().map(Token::getText).toList();
            int storage = GeometrySyntax.storage(words);
            if (storage < 0) {
                for (int i = 0; i + 2 < words.size(); i++) {
                    if (!words.get(i).equals("binding") || !words.get(i + 1).equals("=")) continue;
                    int binding = Integer.parseInt(words.get(i + 2));
                    if ((words.contains("buffer")
                                    && List.of(bindings.input(), bindings.output(), bindings.counts())
                                            .contains(binding))
                            || (words.contains("uniform") && words.contains("{") && binding == bindings.control()))
                        throw new IllegalArgumentException(
                                "Geometry transport binding collides with a shader resource: " + binding);
                }
                continue;
            }
            String qualifier = words.get(storage);
            if (storage == words.size() - 2) {
                if (!words.getFirst().equals("layout")) throw new IllegalArgumentException("Missing geometry layout");
                var parts = String.join("", words.subList(2, storage - 1)).split(",");
                for (String part : parts) {
                    var pair = part.split("=", 2);
                    String key = pair.length == 2 ? pair[0] : qualifier + "_topology";
                    String value = pair.length == 2 ? pair[1] : pair[0];
                    if (layouts.putIfAbsent(key, value) != null)
                        throw new IllegalArgumentException("Duplicate geometry layout: " + key);
                }
            } else {
                if (words.contains("centroid")
                        || words.contains("sample")
                        || words.contains("patch")
                        || words.contains("invariant")
                        || words.contains("precise")
                        || words.contains("layout"))
                    throw new UnsupportedOperationException(
                            "Geometry interface qualifier requires rasterization transport support");
                var fieldWords = words.subList(storage + 1, words.size() - 1);
                if (fieldWords.size() < 2) throw new IllegalArgumentException("Incomplete geometry interface");
                var field = new GeometryField(
                        fieldWords.get(0),
                        fieldWords.get(1),
                        words.contains("flat")
                                ? GeometryField.Interpolation.FLAT
                                : words.contains("noperspective")
                                        ? GeometryField.Interpolation.NOPERSPECTIVE
                                        : GeometryField.Interpolation.SMOOTH);
                var suffix = fieldWords.subList(2, fieldWords.size());
                if (qualifier.equals("in")) {
                    if (!suffix.equals(List.of("[", "]")))
                        throw new UnsupportedOperationException("Geometry inputs must use an unsized primitive array");
                    inputs.add(field);
                } else {
                    if (!suffix.isEmpty())
                        throw new UnsupportedOperationException(
                                "Array or aggregate geometry outputs require a transport layout");
                    outputs.add(field);
                }
            }
            rewrite.delete(declaration.start(), declaration.end());
            removed.set(declaration.start(), declaration.end() + 1);
        }
        for (String name : layouts.keySet())
            if (!List.of("in_topology", "out_topology", "invocations", "max_vertices")
                    .contains(name)) throw new UnsupportedOperationException("Geometry layout qualifier: " + name);
        GeometryLayout layout;
        try {
            layout = new GeometryLayout(
                    GeometryLayout.Input.valueOf(layouts.get("in_topology").toUpperCase(Locale.ROOT)),
                    GeometryLayout.Output.valueOf(layouts.get("out_topology").toUpperCase(Locale.ROOT)),
                    Integer.parseInt(layouts.get("max_vertices")),
                    Integer.parseInt(layouts.getOrDefault("invocations", "1")),
                    inputs,
                    outputs);
        } catch (NullPointerException | NumberFormatException failure) {
            throw new IllegalArgumentException("Incomplete or nonliteral geometry layout", failure);
        }
        Map<String, String> names = Map.ofEntries(
                Map.entry("gl_in", "_geometry_in"), Map.entry("gl_Position", "_geometry_position"),
                Map.entry("gl_InvocationID", "_geometry_invocation"),
                        Map.entry("gl_PrimitiveIDIn", "_geometry_primitive_in"),
                Map.entry("gl_PrimitiveID", "_geometry_primitive_out"), Map.entry("gl_Layer", "_geometry_layer"),
                Map.entry("gl_ViewportIndex", "_geometry_viewport"), Map.entry("EmitVertex", "_geometry_emit"),
                Map.entry("EndPrimitive", "_geometry_end"), Map.entry("main", "_geometry_stage"));
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (removed.get(token.getTokenIndex())) continue;
            String text = token.getText();
            boolean member = i > 0 && tokens.get(i - 1).getText().equals(".");
            if (member) {
                if (text.startsWith("gl_") && !text.equals("gl_Position"))
                    throw new UnsupportedOperationException("Geometry builtin member: " + text);
                continue;
            }
            if (text.startsWith("gl_") && !names.containsKey(text))
                throw new UnsupportedOperationException("Geometry builtin: " + text);
            if (text.equals("EmitStreamVertex") || text.equals("EndStreamPrimitive"))
                throw new UnsupportedOperationException("Geometry streams are not implemented");
            String replacement = names.get(text);
            if (replacement != null) rewrite.replace(token.getTokenIndex(), replacement);
        }
        return new Kernel(layout, bindings, generate(layout, bindings, rewrite.getText()));
    }

    private static String generate(GeometryLayout layout, Bindings bindings, String body) {
        int vertices = layout.input().vertices();
        var code = new StringBuilder("#version 460 core\nlayout(local_size_x=64) in;\n");
        code.append("struct _GeometryInput { vec4 _geometry_position[")
                .append(vertices)
                .append("];\n");
        for (var field : layout.inputs())
            code.append(field.type())
                    .append(' ')
                    .append(field.name())
                    .append('[')
                    .append(vertices)
                    .append("];\n");
        code.append("};\n").append(GeometryTransport.output(layout));
        code.append("layout(std430,binding=")
                .append(bindings.input())
                .append(") readonly buffer _GeometryInputs { _GeometryInput _geometry_inputs[]; };\n")
                .append("layout(std430,binding=")
                .append(bindings.output())
                .append(") writeonly buffer _GeometryOutputs { _GeometryOutput _geometry_outputs[]; };\n")
                .append("layout(std430,binding=")
                .append(bindings.counts())
                .append(") writeonly buffer _GeometryCounts { uvec4 _geometry_counts[]; };\n")
                .append("layout(std140,binding=")
                .append(bindings.control())
                .append(
                        ") uniform _GeometryControl { uint _geometry_primitives; uint _geometry_input_first; uint _geometry_output_first; int _geometry_primitive_base; };\n")
                .append("struct _GeometryPerVertex { vec4 gl_Position; };\n_GeometryPerVertex _geometry_in[")
                .append(vertices)
                .append("];\n")
                .append(
                        "vec4 _geometry_position; int _geometry_invocation, _geometry_primitive_in, _geometry_primitive_out, _geometry_layer, _geometry_viewport;\n")
                .append("uint _geometry_job, _geometry_emitted, _geometry_strip, _geometry_overflow;\n");
        for (var field : layout.inputs())
            code.append(field.type())
                    .append(' ')
                    .append(field.name())
                    .append('[')
                    .append(vertices)
                    .append("];\n");
        for (var field : layout.outputs())
            code.append(field.type()).append(' ').append(field.name()).append(";\n");
        code.append("void _geometry_emit() {\nif (_geometry_emitted>=")
                .append(layout.maxVertices())
                .append("u) { _geometry_overflow=1u; return; }\n")
                .append("uint target=_geometry_output_first+_geometry_job*")
                .append(layout.maxVertices())
                .append("u+_geometry_emitted;\n")
                .append("_geometry_outputs[target]._geometry_position=_geometry_position;\n")
                .append(
                        "_geometry_outputs[target]._geometry_routing=ivec4(_geometry_viewport,_geometry_layer,_geometry_primitive_out,int(_geometry_strip));\n");
        for (var field : layout.outputs())
            code.append("_geometry_outputs[target].")
                    .append(field.name())
                    .append('=')
                    .append(field.name())
                    .append(";\n");
        code.append("_geometry_emitted++;\n}\nvoid _geometry_end() { _geometry_strip++; }\n")
                .append(body)
                .append("\nvoid main() {\n_geometry_job=gl_GlobalInvocationID.x;\nuint primitive=_geometry_job/")
                .append(layout.invocations())
                .append("u;\n")
                .append("if (primitive>=_geometry_primitives) return;\n_geometry_invocation=int(_geometry_job%")
                .append(layout.invocations())
                .append("u);\n")
                .append(
                        "_geometry_primitive_in=int(primitive)+_geometry_primitive_base;\n_geometry_primitive_out=_geometry_primitive_in;\n_geometry_layer=0; _geometry_viewport=0; _geometry_emitted=0u; _geometry_strip=0u; _geometry_overflow=0u;\n")
                .append("uint source=_geometry_input_first+primitive;\nfor (int i=0;i<")
                .append(vertices)
                .append(";i++) {\n_geometry_in[i].gl_Position=_geometry_inputs[source]._geometry_position[i];\n");
        for (var field : layout.inputs())
            code.append(field.name())
                    .append("[i]=_geometry_inputs[source].")
                    .append(field.name())
                    .append("[i];\n");
        return code.append(
                        "}\n_geometry_stage();\n_geometry_counts[_geometry_job]=uvec4(_geometry_emitted,_geometry_strip,_geometry_overflow,0);\n}\n")
                .toString();
    }
}
