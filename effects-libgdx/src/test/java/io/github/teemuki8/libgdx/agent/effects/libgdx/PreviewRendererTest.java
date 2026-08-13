package io.github.teemuki8.libgdx.agent.effects.libgdx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.BufferUtils;
import io.github.teemuki8.libgdx.agent.effects.core.EffectDescription;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.RgbaImage;
import io.github.teemuki8.libgdx.agent.effects.core.ShaderSource;
import io.github.teemuki8.libgdx.agent.effects.core.UniformBinding;
import io.github.teemuki8.libgdx.agent.effects.core.UniformValue;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.IntBuffer;
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
    void multipleSamplersRestoreTheHostsActiveTextureUnit() throws Exception {
        GdxTestHost.run(() -> {
            PreviewRenderer renderer = new PreviewRenderer(EffectsLimits.developmentDefaults());
            try {
                RgbaImage red = RgbaImage.solid(1, 1, 0xffff0000);
                RgbaImage blue = RgbaImage.solid(1, 1, 0xff0000ff);
                ShaderSource src = new ShaderSource(DefaultVertexShader.SOURCE,
                    "uniform sampler2D u_a; uniform sampler2D u_b;"
                    + "void main(){gl_FragColor=(texture2D(u_a,vec2(.5))"
                    + "+texture2D(u_b,vec2(.5)))*.5;}");
                EffectDescription effect = new EffectDescription("two-samplers", src,
                    List.of(
                        new UniformBinding("u_a", new UniformValue.Sampler2d(red)),
                        new UniformBinding("u_b", new UniformValue.Sampler2d(blue))),
                    4, 4, 0f);
                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE3);
                renderer.render(effect);
                IntBuffer active = BufferUtils.newIntBuffer(1);
                Gdx.gl.glGetIntegerv(GL20.GL_ACTIVE_TEXTURE, active);
                assertEquals(GL20.GL_TEXTURE3, active.get(0),
                    "preview must restore the host's active texture unit");
            } finally {
                renderer.close();
            }
        });
    }

    @Test
    void samplerFailureRestoresTheHostsActiveTextureUnit() throws Exception {
        GdxTestHost.run(() -> {
            EffectsLimits limits = new EffectsLimits(64 * 1024, 64, 16, 1,
                16 * 1024, 2048, 2048, 32);
            PreviewRenderer renderer = new PreviewRenderer(limits);
            try {
                RgbaImage allowed = RgbaImage.solid(1, 1, 0xffff0000);
                RgbaImage oversized = RgbaImage.solid(2, 1, 0xff0000ff);
                ShaderSource src = new ShaderSource(DefaultVertexShader.SOURCE,
                    "uniform sampler2D u_a; uniform sampler2D u_b;"
                    + "void main(){gl_FragColor=texture2D(u_a,vec2(.5))"
                    + "+texture2D(u_b,vec2(.5));}");
                EffectDescription effect = new EffectDescription("oversized-sampler", src,
                    List.of(
                        new UniformBinding("u_a", new UniformValue.Sampler2d(allowed)),
                        new UniformBinding("u_b", new UniformValue.Sampler2d(oversized))),
                    4, 4, 0f);
                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE3);

                EffectsException failure = assertThrows(EffectsException.class,
                    () -> renderer.render(effect));

                assertEquals(EffectsException.Kind.LIMIT_EXCEEDED, failure.kind());
                IntBuffer active = BufferUtils.newIntBuffer(1);
                Gdx.gl.glGetIntegerv(GL20.GL_ACTIVE_TEXTURE, active);
                assertEquals(GL20.GL_TEXTURE3, active.get(0),
                    "failed preview must restore the host's active texture unit");
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
