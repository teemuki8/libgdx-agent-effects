package io.github.teemuki8.libgdx.agent.effects.showcase;

import io.github.teemuki8.libgdx.agent.effects.core.RgbaImage;

/** Generates the deterministic source image used by the desktop showcase. */
public final class BuiltInScene {

    public static final int WIDTH = 320;
    public static final int HEIGHT = 240;

    /** Creates a stylized night landscape with gradients, silhouettes, and bright edge detail. */
    public static RgbaImage create() {
        int[] pixels = new int[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                pixels[y * WIDTH + x] = pixel(x, y);
            }
        }
        return new RgbaImage(WIDTH, HEIGHT, pixels);
    }

    private static int pixel(int x, int y) {
        int red = 10 + y * 20 / HEIGHT;
        int green = 16 + y * 18 / HEIGHT;
        int blue = 38 + y * 30 / HEIGHT;

        int moonDistance = square(x - 250) + square(y - 54);
        if (moonDistance < square(28)) {
            int glow = Math.max(0, 255 - moonDistance / 5);
            red = Math.max(red, 218 + glow / 8);
            green = Math.max(green, 220 + glow / 10);
            blue = Math.max(blue, 205 + glow / 12);
        } else if (moonDistance < square(39)) {
            blue += 26;
            red += 12;
        }

        if (isStar(x, y)) {
            red = 185;
            green = 220;
            blue = 255;
        }

        int ridge = 150 - Math.abs(x - 95) * 3 / 5;
        int secondRidge = 174 - Math.abs(x - 220) * 2 / 5;
        if (y > Math.max(ridge, secondRidge)) {
            red = 25;
            green = 34;
            blue = 54;
        }
        if (y > 190) {
            red = 21 + (y - 190) / 4;
            green = 17 + (x / 37) % 3;
            blue = 26 + (y - 190) / 3;
        }

        if (x >= 44 && x <= 90 && y >= 166 && y <= 206) {
            red = 34;
            green = 42;
            blue = 56;
            if (x == 44 || x == 90 || y == 166 || y == 206) {
                red = 30;
                green = 220;
                blue = 201;
            }
        }
        if (isHero(x, y)) {
            red = 211;
            green = 224;
            blue = 239;
        }
        return argb(red, green, blue);
    }

    private static boolean isStar(int x, int y) {
        return y < 125 && ((x * 37 + y * 61 + 17) % 389 == 0
            || (x * 19 + y * 43 + 11) % 521 == 0);
    }

    private static boolean isHero(int x, int y) {
        if (y < 151 || y > 211 || x < 145 || x > 175) {
            return false;
        }
        if (y < 163) {
            return square(x - 160) + square(y - 157) < square(7);
        }
        int halfWidth = y < 185 ? 7 + (y - 163) / 4 : 11;
        return Math.abs(x - 160) <= halfWidth;
    }

    private static int square(int value) {
        return value * value;
    }

    private static int argb(int red, int green, int blue) {
        return 0xff000000 | (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private BuiltInScene() {}
}
