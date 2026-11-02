package com.gtnewhorizons.angelica.sdlgpu.geometry;

import java.util.*;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStreamRewriter;

/** Pure vertex stages are evaluated on the GPU before each geometry invocation; shader side effects are rejected. */
public final class GeometryVertex {
    public static final int META_BINDING = 3, FIRST_STREAM_BINDING = 4, INDEX_BINDING = 8, MAX_STREAMS = 4;
    public static final int META_WORDS = 36 * 4;

    public record Attribute(GeometryField field, int location) {
        public int columns() {
            return field.type().startsWith("mat") ? field.type().charAt(3) - '0' : 1;
        }
    }

    public record Plan(String source, List<Attribute> attributes, Map<String, String> uniforms) {
        public Plan {
            attributes = List.copyOf(attributes);
            uniforms = Map.copyOf(uniforms);
        }
    }

    private GeometryVertex() {}

    public static Plan lower(String vertex, GeometryCompute.Kernel geometry, Map<String, Integer> requestedLocations) {
        var parsed = GeometrySyntax.parse(vertex);
        var rewrite = new TokenStreamRewriter(parsed.stream());
        var removed = new BitSet();
        var names = new LinkedHashMap<String, String>();
        var uniforms = new LinkedHashMap<String, String>();
        var inputs = new ArrayList<Attribute>();
        var outputs = new LinkedHashMap<String, GeometryField>();
        var assigned = new BitSet();
        var attributeDeclarations = new ArrayList<List<String>>();
        for (var token : parsed.tokens()) {
            if (token.getText().startsWith("_vertex_")
                    || token.getText().equals("struct")
                    || token.getText().equals("buffer")
                    || token.getText().contains("sampler")
                    || token.getText().contains("image")
                    || token.getText().startsWith("atomic"))
                throw new UnsupportedOperationException(
                        "Geometry vertex transport does not support " + token.getText());
        }
        for (var declaration : parsed.declarations()) {
            var words = declaration.tokens().stream().map(Token::getText).toList();
            int storage = GeometrySyntax.storage(words);
            if (storage >= 0) {
                boolean input = words.get(storage).equals("in");
                var fieldWords = words.subList(storage + 1, words.size() - 1);
                if (fieldWords.size() != 2)
                    throw new UnsupportedOperationException(
                            "Geometry vertex arrays/interface blocks are not implemented");
                var field = new GeometryField(fieldWords.get(0), fieldWords.get(1));
                if (input) {
                    attributeDeclarations.add(words);
                    names.put(field.name(), "_vertex_input_" + field.name());
                } else {
                    if (words.contains("layout"))
                        throw new UnsupportedOperationException(
                                "Explicit vertex varying locations need geometry transport mapping");
                    outputs.put(field.name(), field);
                    names.put(field.name(), "_vertex_output_" + field.name());
                }
                rewrite.delete(declaration.start(), declaration.end());
                removed.set(declaration.start(), declaration.end() + 1);
            } else if (words.contains("uniform")) {
                int at = words.indexOf("uniform");
                if (words.contains("{") || at + 2 >= words.size())
                    throw new UnsupportedOperationException("Geometry vertex uniform blocks are not implemented");
                String name = words.get(at + 2);
                names.put(name, "_vertex_uniform_" + name);
                uniforms.put("_vertex_uniform_" + name, name);
            } else if (words.size() >= 3 && (!words.contains("(") || words.contains("="))) {
                int type = words.get(0).equals("const") ? 1 : 0;
                String name = words.get(type + 1);
                names.put(name, "_vertex_global_" + name);
            }
        }
        // Explicit and application-bound attributes reserve their complete matrix location ranges first.
        for (var words : attributeDeclarations) {
            int at = GeometrySyntax.storage(words);
            var field = new GeometryField(words.get(at + 1), words.get(at + 2));
            int location = requestedLocations.getOrDefault(field.name(), -1);
            int explicit = words.indexOf("location");
            if (explicit >= 0) location = Integer.parseInt(words.get(explicit + 2));
            var attribute = new Attribute(field, location);
            if (location >= 0) reserve(assigned, location, attribute.columns());
            inputs.add(attribute);
        }
        for (int i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i);
            if (input.location() >= 0) continue;
            int location = 0;
            while (location + input.columns() <= 16
                    && assigned.nextSetBit(location) >= 0
                    && assigned.nextSetBit(location) < location + input.columns()) location++;
            reserve(assigned, location, input.columns());
            inputs.set(i, new Attribute(input.field(), location));
        }
        for (var input : geometry.layout().inputs()) {
            var output = outputs.get(input.name());
            if (output == null || !output.type().equals(input.type()))
                throw new IllegalArgumentException("Vertex/geometry varying mismatch: " + input.name());
        }
        // Namespace user helper functions as well as globals; member selectors remain untouched.
        var tokens = parsed.tokens();
        int depth = 0;
        for (int i = 0; i < tokens.size(); i++) {
            String text = tokens.get(i).getText();
            if (text.equals("{")
                    && depth++ == 0
                    && i > 0
                    && tokens.get(i - 1).getText().equals(")")) {
                int j = i - 1, parens = 1;
                while (--j >= 0) {
                    if (tokens.get(j).getText().equals(")")) parens++;
                    if (tokens.get(j).getText().equals("(") && --parens == 0) break;
                }
                if (j > 0) {
                    String name = tokens.get(j - 1).getText();
                    names.put(name, name.equals("main") ? "_vertex_stage" : "_vertex_function_" + name);
                }
            } else if (text.equals("}")) depth--;
        }
        if (!names.containsKey("main")) throw new IllegalArgumentException("Vertex stage has no main");
        names.put("gl_Position", "_vertex_position");
        names.put("gl_VertexID", "_vertex_id");
        names.put("gl_InstanceID", "_vertex_instance");
        names.put("gl_InstanceIDARB", "_vertex_instance");
        names.put("gl_DrawID", "_vertex_draw");
        names.put("gl_DrawIDARB", "_vertex_draw");
        names.put("gl_BaseInstance", "_vertex_base_instance");
        names.put("gl_BaseInstanceARB", "_vertex_base_instance");
        names.put("gl_BaseVertex", "_vertex_base_vertex");
        names.put("gl_BaseVertexARB", "_vertex_base_vertex");
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (removed.get(token.getTokenIndex())
                    || i > 0 && tokens.get(i - 1).getText().equals(".")) continue;
            String text = token.getText();
            if (text.startsWith("gl_") && !names.containsKey(text))
                throw new UnsupportedOperationException("Geometry vertex builtin: " + text);
            String replacement = names.get(text);
            if (replacement != null) rewrite.replace(token.getTokenIndex(), replacement);
        }
        var header = new StringBuilder(
                        "vec4 _vertex_position; int _vertex_id, _vertex_instance, _vertex_draw, _vertex_base_instance, _vertex_base_vertex; uint _vertex_bad;\n")
                .append(fetchSource());
        for (var input : inputs)
            header.append(input.field().type())
                    .append(' ')
                    .append(names.get(input.field().name()))
                    .append(";\n");
        for (var output : outputs.values())
            header.append(output.type())
                    .append(' ')
                    .append(names.get(output.name()))
                    .append(";\n");
        header.append(rewrite.getText())
                .append("\nvoid _vertex_prepare(uint primitive,int corner) {\n")
                .append(
                        "_vertex_instance=int(primitive/_vertex_meta[2].x); primitive%=_vertex_meta[2].x;\n_vertex_draw=int(_vertex_meta[2].z); _vertex_base_instance=int(_vertex_meta[2].y); _vertex_base_vertex=int(_vertex_meta[0].z);\nuint element=primitive*3u+uint(corner); uvec4 draw=_vertex_meta[0];\n")
                .append(
                        "uint index=draw.w==0u?draw.x+element:_vertex_read(4u,_vertex_meta[1].x+element*draw.w,draw.w);\n_vertex_id=int(index)+int(draw.z);\n");
        for (var input : inputs) {
            String name = names.get(input.field().name()), type = input.field().type();
            for (int column = 0; column < input.columns(); column++) {
                String value = "_vertex_fetch(" + (input.location() + column) + ",_vertex_id)";
                String cast = type.startsWith("i")
                        ? "ivec4(" + value + ")"
                        : type.startsWith("u") ? value : "uintBitsToFloat(" + value + ")";
                int rows = type.contains("vec")
                        ? type.charAt(type.length() - 1) - '0'
                        : type.startsWith("mat") ? type.charAt(type.length() - 1) - '0' : 1;
                header.append(name);
                if (input.columns() > 1) header.append('[').append(column).append(']');
                header.append('=')
                        .append(cast)
                        .append(rows < 4 ? "." + "xyzw".substring(0, rows) : "")
                        .append(";\n");
            }
        }
        header.append("_vertex_stage();\n_geometry_in[corner].gl_Position=_vertex_position;\n");
        for (var input : geometry.layout().inputs())
            header.append(input.name())
                    .append("[corner]=")
                    .append(names.get(input.name()))
                    .append(";\n");
        header.append("}\n");
        String code = geometry.source();
        int
                a =
                        code.indexOf("layout(std430,binding="
                                + geometry.bindings().input() + ") readonly buffer _GeometryInputs"),
                b = code.indexOf(";", a);
        if (a < 0 || b < 0) throw new IllegalArgumentException("Geometry input transport declaration missing");
        b = code.indexOf(";", b + 1); // first semicolon terminates the array member, second terminates the block
        code = code.substring(0, a) + code.substring(b + 1);
        a = code.indexOf("uint source=_geometry_input_first+primitive;");
        b = code.indexOf("_geometry_stage();", a);
        if (a < 0 || b < 0) throw new IllegalArgumentException("Geometry input transport load missing");
        code = code.substring(0, a)
                + "_geometry_primitive_in=int(primitive%_vertex_meta[2].x)+_geometry_primitive_base; _geometry_primitive_out=_geometry_primitive_in;\n_vertex_bad=0u; for(int corner=0;corner<3;corner++) _vertex_prepare(primitive,corner);\n"
                + "if(_vertex_bad!=0u) { _geometry_counts[_geometry_job]=uvec4(0,0,2,0); return; }\n"
                + code.substring(b);
        int main = code.indexOf("void main()");
        return new Plan(code.substring(0, main) + header + code.substring(main), inputs, uniforms);
    }

    private static void reserve(BitSet assigned, int first, int count) {
        if (first < 0
                || first + count > 16
                || assigned.nextSetBit(first) >= 0 && assigned.nextSetBit(first) < first + count)
            throw new IllegalArgumentException("Overlapping/out-of-range vertex input locations");
        assigned.set(first, first + count);
    }

    private static String fetchSource() {
        var s = new StringBuilder("layout(std430,binding=3) readonly buffer _VertexFetch {uvec4 _vertex_meta[];};\n");
        for (int i = 0; i < MAX_STREAMS; i++)
            s.append("layout(std430,binding=")
                    .append(FIRST_STREAM_BINDING + i)
                    .append(") readonly buffer _VertexRaw")
                    .append(i)
                    .append(" {uint _vertex_raw")
                    .append(i)
                    .append("[];};\n");
        s.append("layout(std430,binding=8) readonly buffer _VertexIndices {uint _vertex_indices[];};\n");
        return s + """
                uint _vertex_word(uint stream,uint word) {
                    switch(stream) {
                        case 0u:return _vertex_raw0[word];case 1u:return _vertex_raw1[word];
                        case 2u:return _vertex_raw2[word];case 3u:return _vertex_raw3[word];
                        default:return _vertex_indices[word];
                    }
                }
                uint _vertex_read(uint stream,uint address,uint bytes) {
                    uint length=stream==4u?_vertex_meta[1].y:_vertex_meta[35][int(stream)];
                    if(address>length || bytes>length-address) {_vertex_bad=1u;return 0u;}
                    uint shift=(address&3u)*8u,word=_vertex_word(stream,address/4u)>>shift;
                    if(shift+bytes*8u>32u) word|=_vertex_word(stream,address/4u+1u)<<(32u-shift);
                    return bytes==4u?word:word&((1u<<(bytes*8u))-1u);
                }
                uvec4 _vertex_fetch(int location,int vertex) {
                    uvec4 info=_vertex_meta[3+location*2],defaults=_vertex_meta[4+location*2];
                    uint format=info.w;
                    if((format&0x80000000u)==0u) return defaults;
                    if(vertex<0) {_vertex_bad=1u;return uvec4(0);}
                    uint components=format&7u,kind=(format>>3u)&15u,stream=(format>>16u)&3u;
                    bool integer=(format&256u)!=0u,normalized=(format&128u)!=0u;
                    uint index=uint(vertex);
                    if(info.z!=0u) {
                        uint instance=uint(_vertex_instance)/info.z,base=_vertex_meta[2].y;
                        if(instance>0xffffffffu-base) {_vertex_bad=1u;return uvec4(0);}
                        index=instance+base;
                    }
                    uint bytes=kind==0u||kind==5u||kind==6u?4u:kind==1u||kind==2u?1u:2u;
                    uvec4 result=integer?uvec4(0,0,0,1):uvec4(0,0,0,floatBitsToUint(1.0));
                    for(uint component=0u;component<components;component++) {
                        uint length=_vertex_meta[35][int(stream)],tail=component*bytes;
                        if(info.x>length || tail>length-info.x || info.y!=0u && index>(length-info.x-tail)/info.y) {_vertex_bad=1u;return uvec4(0);}
                        uint bits=_vertex_read(stream,info.x+index*info.y+tail,bytes);
                        if(kind==0u) result[component]=bits;
                        else if(kind==7u) result[component]=floatBitsToUint(unpackHalf2x16(bits).x);
                        else {
                            bool signedType=kind==1u||kind==3u||kind==5u;
                            int value=int(bits<<(32u-bytes*8u))>>int(32u-bytes*8u);
                            float f=signedType?float(value):float(bits);
                            if(normalized) f=signedType?max(f/(bytes==4u?2147483647.0:float((1u<<(bytes*8u-1u))-1u)),-1.0):f/(bytes==4u?4294967295.0:float((1u<<(bytes*8u))-1u));
                            result[component]=integer?(signedType?uint(value):bits):floatBitsToUint(f);
                        }
                    }
                    return result;
                }
                """;
    }
}
