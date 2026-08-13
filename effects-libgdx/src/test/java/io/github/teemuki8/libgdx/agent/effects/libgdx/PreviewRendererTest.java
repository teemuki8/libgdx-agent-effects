package io.github.teemuki8.libgdx.agent.effects.libgdx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.RgbaImage;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import io.github.teemuki8.libgdx.agent.effects.core.UniformBinding;
import io.github.teemuki8.libgdx.agent.effects.core.UniformValue;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PreviewRendererTest {
    @Test
    void previewIsDeterministicAndSolidColor() throws Exception {
        GdxTestHost.run(() -> {
            PreviewRenderer renderer = new PreviewRenderer(EffectsLimits.developmentDefaults());
            try {
                ShaderSource src = new ShaderSource(DefaultVertexShader.SOURCE,
                    "void main(){gl_FragColor=vec4(1.0,0.0,0.0,1.0);}");
                EffectDescription e = new EffectDescription("red", src, List.of(), 64, 64, 0f);
                RgbaImage a = renderer.render(e);
                RgbaImage b = renderer.render(e);
                assertEquals(0xffff0000, a.getPixel(0, 0));
                assertArrayEquals(a.pixels(), b.pixels());
            } finally {
                renderer.close();
            }
        });
    }

    @Test
    void sampler2dUniformUploadsTextureAndSamplesKnownColor() throws Exception {
        GdxTestHost.run(() -> {
            PreviewRenderer renderer = new PreviewRenderer(EffectsLimits.developmentDefaults());
            try {
                RgbaImage texture = RgbaImage.solid(2, 2, 0xff00ff00);
                UniformBinding binding = new UniformBinding("u_tex",
                    new UniformValue.Sampler2d(texture));
                ShaderSource src = new ShaderSource(DefaultVertexShader.SOURCE,
                    "uniform sampler2D u_tex;\n"
                    + "void main(){gl_FragColor=texture2D(u_tex, vec2(0.5, 0.5));}");
                EffectDescription e = new EffectDescription("sampled", src, List.of(binding),
                    16, 16, 0f);
                RgbaImage out = renderer.render(e);
                assertEquals(0xff00ff00, out.getPixel(0, 0));
                assertEquals(0xff00ff00, out.getPixel(15, 15));
            } finally {
                renderer.close();
            }
        });
    }

    @Test
    void pngWriterProducesDecodablePngWithMatchingPixels() throws Exception {
        GdxTestHost.run(() -> {
            int[] px = {
                0xffff0000, 0xff00ff00,
                0xff0000ff, 0xffffffff,
            };
            RgbaImage image = new RgbaImage(2, 2, px);
            byte[] png = new PreviewPngWriter().write(image);
            assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G'},
                Arrays.copyOfRange(png, 0, 4));
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(png));
            assertEquals(image.width(), decoded.getWidth());
            assertEquals(image.height(), decoded.getHeight());
            for (int y = 0; y < image.height(); y++) {
                for (int x = 0; x < image.width(); x++) {
                    assertEquals(image.getPixel(x, y), decoded.getRGB(x, y),
                        "pixel mismatch at (" + x + "," + y + ")");
                }
            }
        });
    }
}
