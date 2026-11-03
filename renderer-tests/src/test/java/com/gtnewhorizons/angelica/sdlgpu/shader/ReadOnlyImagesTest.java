package com.gtnewhorizons.angelica.sdlgpu.shader;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.util.spvc.Spv.*;

import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess;
import java.nio.ByteOrder;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class ReadOnlyImagesTest {
    @Test
    void readonlyImageLoadsPreserveTheirScalarTypeInSpirv() {
        for (String prefix : new String[] {"", "i", "u"})
            for (int dimension = 1; dimension <= 3; dimension++) {
                String coordinate = dimension == 1 ? "0" : "ivec" + dimension + "(0)";
                String format = prefix.isEmpty() ? "rgba32f" : prefix.equals("i") ? "rgba32i" : "rgba32ui";
                var result = GlslVulkanPreprocess.run(
                        "#version 460 core\nlayout(" + format + ",binding=4) uniform readonly " + prefix + "image"
                                + dimension + "D data;\n"
                                + "layout(location=0) out vec4 color;void main(){color=vec4(imageLoad(data,"
                                + coordinate + "));}",
                        GL_FRAGMENT_SHADER,
                        "readonly-image",
                        true);
                assertNotNull(result);
                var words = NonUniformSamplerSpirvTest.compile(result.rewrittenSource())
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asIntBuffer();
                var scalars = new HashMap<Integer, String>();
                int sampledType = 0;
                for (int at = 5; at < words.limit(); ) {
                    int instruction = words.get(at), opcode = instruction & 65535;
                    if (opcode == SpvOpTypeFloat) scalars.put(words.get(at + 1), "float");
                    if (opcode == SpvOpTypeInt) scalars.put(words.get(at + 1), words.get(at + 3) == 0 ? "uint" : "int");
                    if (opcode == SpvOpTypeImage) sampledType = words.get(at + 2);
                    at += instruction >>> 16;
                }
                assertEquals(
                        prefix.isEmpty() ? "float" : prefix.equals("i") ? "int" : "uint",
                        scalars.get(sampledType),
                        result.rewrittenSource());
            }
    }

    @Test
    void rewritesOnlyReadonlyLoadsAndSizesWhileRetainingImageBindings() {
        var result = GlslVulkanPreprocess.run("""
                #version 460 core
                layout(rgba32f,binding=4) uniform readonly image2D inputData;
                layout(rgba32f,binding=0) uniform image2D outputData;
                layout(location=0) out vec4 color;
                void main(){
                    ivec2 size=imageSize(inputData);
                    vec4 data=imageLoad(inputData,size-1);
                    imageStore(outputData,ivec2(0),data);
                    color=data+imageLoad(outputData,ivec2(0));
                }
                """, GL_FRAGMENT_SHADER, "mixed-images", true);
        assertNotNull(result);
        String source = result.rewrittenSource();
        assertTrue(source.contains("layout(binding = 4) uniform texture2D inputData"), source);
        assertTrue(source.contains("textureSize(inputData, 0)"), source);
        assertTrue(source.contains("texelFetch(inputData,size-1, 0)"), source);
        assertTrue(source.contains("imageLoad(outputData,ivec2(0))"), source);
        assertTrue(source.contains("imageStore(outputData,ivec2(0),data)"), source);
        NonUniformSamplerSpirvTest.compile(source);
    }
}
