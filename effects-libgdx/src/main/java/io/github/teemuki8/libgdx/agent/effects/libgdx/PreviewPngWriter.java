package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import io.github.teemuki8.libgdx.agent.effects.core.RgbaImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Writes an RgbaImage as PNG bytes. */
public final class PreviewPngWriter {

    public byte[] write(RgbaImage image) {
        Pixmap pix = new Pixmap(image.width(), image.height(), Pixmap.Format.RGBA8888);
        try {
            ByteBuffer buffer = ByteBuffer.allocateDirect(image.width() * image.height() * 4);
            for (int pixel : image.pixels()) {
                buffer.put((byte) ((pixel >> 16) & 0xff));
                buffer.put((byte) ((pixel >> 8) & 0xff));
                buffer.put((byte) (pixel & 0xff));
                buffer.put((byte) ((pixel >> 24) & 0xff));
            }
            buffer.flip();
            pix.getPixels().put(buffer);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PixmapIO.PNG encoder = new PixmapIO.PNG();
            try {
                encoder.setFlipY(false);
                encoder.write(out, pix);
            } catch (IOException e) {
                throw new IllegalStateException("failed to write PNG", e);
            } finally {
                encoder.dispose();
            }
            return out.toByteArray();
        } finally {
            pix.dispose();
        }
    }
}
