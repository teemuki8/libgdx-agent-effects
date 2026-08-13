package io.github.teemuki8.libgdx.agent.effects.core;

/** Immutable packed RGBA8888 image; JDK-only. */
public final class RgbaImage {
    private final int width;
    private final int height;
    private final int[] pixels; // packed 0xAARRGGBB, length width*height

    public RgbaImage(int width, int height, int[] pixels) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        java.util.Objects.requireNonNull(pixels, "pixels");
        if (pixels.length != width * height) {
            throw new IllegalArgumentException("pixels length must equal width*height");
        }
        this.width = width;
        this.height = height;
        this.pixels = pixels.clone();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int getPixel(int x, int y) {
        return pixels[y * width + x];
    }

    public int[] pixels() {
        return pixels.clone();
    }

    public RgbaImage copy() {
        return new RgbaImage(width, height, pixels);
    }

    public static RgbaImage solid(int width, int height, int rgba8888) {
        int[] px = new int[width * height];
        java.util.Arrays.fill(px, rgba8888);
        return new RgbaImage(width, height, px);
    }
}
