package io.github.teemuki8.libgdx.agent.effects.showcase;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsException;
import io.github.teemuki8.libgdx.agent.effects.core.EffectsLimits;
import io.github.teemuki8.libgdx.agent.effects.core.RgbaImage;
import io.github.teemuki8.libgdx.agent.effects.libgdx.PreviewPngWriter;
import io.github.teemuki8.libgdx.agent.effects.libgdx.PreviewRenderer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Interactive application-owned before/after showcase for the public effects API. */
public final class EffectsShowcaseApplication extends ApplicationAdapter {

    private static final float SIDEBAR_WIDTH = 220f;
    private static final float CONTENT_LEFT = 246f;
    private static final float PANE_GAP = 18f;
    private static final float SLIDER_WIDTH = 190f;
    private static final float[] PRESET_TOP = {112f, 158f, 204f, 250f, 338f, 384f};

    private final int smokeFrames;
    private final Path outputDirectory;
    private final List<ShowcasePreset> presets = ShowcasePresets.all();
    private final ShowcaseState state = new ShowcaseState(presets);
    private final RgbaImage sourceImage = BuiltInScene.create();

    private PreviewRenderer renderer;
    private PreviewPngWriter pngWriter;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private Texture sourceTexture;
    private Texture processedTexture;
    private RgbaImage processedImage;
    private String status = "Ready";
    private boolean dirty = true;
    private int renderedFrames;

    public EffectsShowcaseApplication(int smokeFrames, Path outputDirectory) {
        if (smokeFrames < 0) {
            throw new IllegalArgumentException("smokeFrames must be non-negative");
        }
        this.smokeFrames = smokeFrames;
        this.outputDirectory = outputDirectory;
    }

    @Override public void create() {
        renderer = new PreviewRenderer(EffectsLimits.developmentDefaults());
        pngWriter = new PreviewPngWriter();
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        font.getData().setScale(1.05f);
        sourceTexture = textureOf(sourceImage);
        Gdx.input.setInputProcessor(new ShowcaseInput());
        updateProcessed();
    }

    @Override public void render() {
        state.advance(Math.min(Gdx.graphics.getDeltaTime(), 0.1f));
        if (state.selectedPreset().animated() && !state.paused()) {
            dirty = true;
        }
        if (dirty) {
            updateProcessed();
        }

        Gdx.gl.glClearColor(0.035f, 0.047f, 0.075f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        configureProjection();
        drawPanels();
        drawImagesAndText();

        renderedFrames++;
        if (smokeFrames > 0 && renderedFrames >= smokeFrames) {
            Gdx.app.exit();
        }
    }

    private void updateProcessed() {
        dirty = false;
        try {
            ShowcasePreset preset = state.selectedPreset();
            RgbaImage next = renderer.render(
                preset.effect(sourceImage, state.timeSeconds(), state.intensity()));
            Texture nextTexture = textureOf(next);
            if (processedTexture != null) {
                processedTexture.dispose();
            }
            processedImage = next;
            processedTexture = nextTexture;
            status = "GLSL compiled - " + preset.name();
        } catch (RuntimeException failure) {
            if (processedTexture != null) {
                processedTexture.dispose();
                processedTexture = null;
            }
            processedImage = null;
            status = failureStatus(failure);
            if (smokeFrames > 0) {
                throw failure;
            }
        }
    }

    private static String failureStatus(RuntimeException failure) {
        if (failure instanceof EffectsException effectsFailure) {
            return effectsFailure.kind() + ": " + effectsFailure.getMessage();
        }
        return failure.getClass().getSimpleName() + ": " + failure.getMessage();
    }

    private void configureProjection() {
        Matrix4 projection = new Matrix4().setToOrtho2D(0f, 0f,
            Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.setProjectionMatrix(projection);
        shapes.setProjectionMatrix(projection);
    }

    private void drawPanels() {
        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();
        PaneLayout panes = paneLayout(width, height);
        ShowcaseControlLayout controls = ShowcaseControlLayout.at(panes.leftX);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.075f, 0.094f, 0.14f, 1f);
        shapes.rect(0f, 0f, SIDEBAR_WIDTH, height);
        shapes.setColor(0.11f, 0.135f, 0.20f, 1f);
        shapes.rect(0f, height - 42f, width, 42f);
        for (int index = 0; index < presets.size(); index++) {
            if (index == state.selectedIndex()) {
                shapes.setColor(0.19f, 0.25f, 0.39f, 1f);
                shapes.rect(12f, height - PRESET_TOP[index] - 36f, SIDEBAR_WIDTH - 24f, 38f);
            }
            drawSwatch(index, 22f, height - PRESET_TOP[index] - 25f);
        }
        shapes.setColor(0.025f, 0.033f, 0.055f, 1f);
        shapes.rect(panes.leftX, panes.paneY, panes.paneWidth, panes.paneHeight);
        shapes.rect(panes.rightX, panes.paneY, panes.paneWidth, panes.paneHeight);
        drawSlider(controls.timeSliderX(), panes.controlsY, state.timeSeconds()
            / ShowcaseState.TIME_WRAP_SECONDS);
        drawSlider(controls.intensitySliderX(), panes.controlsY, state.intensity());
        shapes.end();
    }

    private void drawSwatch(int index, float x, float y) {
        Color[] colors = {
            new Color(0.94f, 0.28f, 0.30f, 1f), new Color(0.08f, 0.72f, 0.84f, 1f),
            new Color(0.55f, 0.40f, 0.94f, 1f), new Color(0.20f, 0.82f, 0.40f, 1f),
            new Color(0.06f, 0.88f, 0.96f, 1f), new Color(0.94f, 0.32f, 0.76f, 1f),
        };
        shapes.setColor(colors[index]);
        shapes.rect(x, y, 20f, 20f);
    }

    private void drawSlider(float x, float y, float fraction) {
        shapes.setColor(0.21f, 0.25f, 0.34f, 1f);
        shapes.rect(x, y, SLIDER_WIDTH, 5f);
        shapes.setColor(0.55f, 0.39f, 0.94f, 1f);
        shapes.rect(x, y, SLIDER_WIDTH * fraction, 5f);
        shapes.circle(x + SLIDER_WIDTH * fraction, y + 2.5f, 7f);
    }

    private void drawImagesAndText() {
        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();
        PaneLayout panes = paneLayout(width, height);
        ShowcaseControlLayout controls = ShowcaseControlLayout.at(panes.leftX);
        batch.begin();
        drawTexture(sourceTexture, panes.leftX, panes.paneY, panes.paneWidth, panes.paneHeight);
        if (processedTexture != null) {
            drawTexture(processedTexture, panes.rightX, panes.paneY,
                panes.paneWidth, panes.paneHeight);
        }
        font.setColor(0.86f, 0.90f, 1f, 1f);
        font.draw(batch, "libGDX Agent Effects Showcase", 18f, height - 14f);
        font.setColor(0.58f, 0.64f, 0.76f, 1f);
        font.draw(batch, "PRACTICAL", 20f, height - 82f);
        font.draw(batch, "FLASHY", 20f, height - 306f);
        font.setColor(Color.WHITE);
        for (int index = 0; index < presets.size(); index++) {
            font.draw(batch, presets.get(index).name(), 54f, height - PRESET_TOP[index] - 8f);
        }
        font.setColor(0.67f, 0.72f, 0.84f, 1f);
        font.draw(batch, "SOURCE  -  320 x 240", panes.leftX, panes.paneY + panes.paneHeight + 23f);
        font.draw(batch, state.selectedPreset().name().toUpperCase(Locale.ROOT) + "  -  GLSL",
            panes.rightX, panes.paneY + panes.paneHeight + 23f);
        font.draw(batch, "TIME", controls.timeLabelX(), panes.controlsY + 8f);
        font.draw(batch, String.format(Locale.ROOT, "%.2fs", state.timeSeconds()),
            controls.timeValueX(), panes.controlsY + 8f);
        font.draw(batch, "INTENSITY", controls.intensityLabelX(), panes.controlsY + 8f);
        font.draw(batch, String.format(Locale.ROOT, "%.2f", state.intensity()),
            controls.intensityValueX(), panes.controlsY + 8f);
        font.draw(batch, state.paused() ? "[Space] Resume" : "[Space] Pause",
            panes.leftX, panes.controlsY - 38f);
        font.draw(batch, "[R] Reset     [S] Save PNG", panes.leftX + 155f,
            panes.controlsY - 38f);
        font.draw(batch, status, panes.leftX, 25f);
        font.draw(batch, "u_source - u_time - u_resolution - u_intensity",
            Math.max(panes.leftX + 430f, width - 430f), 25f);
        batch.end();
    }

    private void drawTexture(Texture texture, float x, float y, float width, float height) {
        batch.draw(texture, x, y, width, height, 0, 0,
            texture.getWidth(), texture.getHeight(), false, true);
    }

    private static PaneLayout paneLayout(int width, int height) {
        float available = Math.max(500f, width - CONTENT_LEFT - 24f);
        float paneWidth = (available - PANE_GAP) / 2f;
        float paneHeight = Math.min(paneWidth * 0.75f, height - 245f);
        float paneY = height - 92f - paneHeight;
        return new PaneLayout(CONTENT_LEFT, CONTENT_LEFT + paneWidth + PANE_GAP,
            paneY, paneWidth, paneHeight, paneY - 62f);
    }

    private static Texture textureOf(RgbaImage image) {
        Pixmap pixmap = new Pixmap(image.width(), image.height(), Pixmap.Format.RGBA8888);
        try {
            pixmap.setBlending(Pixmap.Blending.None);
            int[] pixels = image.pixels();
            for (int y = 0; y < image.height(); y++) {
                for (int x = 0; x < image.width(); x++) {
                    int argb = pixels[y * image.width() + x];
                    int rgba = ((argb << 8) & 0xffffff00) | ((argb >>> 24) & 0xff);
                    pixmap.drawPixel(x, y, rgba);
                }
            }
            Texture texture = new Texture(pixmap);
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return texture;
        } finally {
            pixmap.dispose();
        }
    }

    private void selectPreset(int index) {
        state.select(index);
        dirty = true;
    }

    private void saveProcessed() {
        if (processedImage == null) {
            status = "Nothing to save";
            return;
        }
        int timeMillis = Math.round(state.timeSeconds() * 1000f);
        Path output = outputDirectory.resolve(String.format(Locale.ROOT, "%s-%05d.png",
            state.selectedPreset().slug(), timeMillis));
        try {
            Files.createDirectories(outputDirectory);
            Files.write(output, pngWriter.write(processedImage));
            status = "Saved " + output;
        } catch (IOException failure) {
            status = "Save failed: " + failure.getMessage();
        }
    }

    @Override public void dispose() {
        Gdx.input.setInputProcessor(null);
        if (processedTexture != null) {
            processedTexture.dispose();
        }
        if (sourceTexture != null) {
            sourceTexture.dispose();
        }
        if (font != null) {
            font.dispose();
        }
        if (shapes != null) {
            shapes.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
        if (renderer != null) {
            renderer.close();
        }
    }

    private final class ShowcaseInput extends InputAdapter {
        @Override public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.SPACE) {
                state.togglePaused();
                dirty = true;
                return true;
            }
            if (keycode == Input.Keys.R) {
                state.reset();
                dirty = true;
                return true;
            }
            if (keycode == Input.Keys.S) {
                saveProcessed();
                return true;
            }
            return false;
        }

        @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (screenX < SIDEBAR_WIDTH) {
                for (int index = 0; index < PRESET_TOP.length; index++) {
                    if (screenY >= PRESET_TOP[index] - 4f && screenY <= PRESET_TOP[index] + 38f) {
                        selectPreset(index);
                        return true;
                    }
                }
            }
            return adjustSlider(screenX, screenY);
        }

        @Override public boolean touchDragged(int screenX, int screenY, int pointer) {
            return adjustSlider(screenX, screenY);
        }

        private boolean adjustSlider(int screenX, int screenY) {
            PaneLayout panes = paneLayout(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            ShowcaseControlLayout controls = ShowcaseControlLayout.at(panes.leftX);
            float renderY = Gdx.graphics.getHeight() - screenY;
            if (Math.abs(renderY - panes.controlsY) > 18f) {
                return false;
            }
            if (screenX >= controls.timeSliderX()
                    && screenX <= controls.timeSliderX() + SLIDER_WIDTH) {
                float fraction = (screenX - controls.timeSliderX()) / SLIDER_WIDTH;
                state.setTimeSeconds(fraction * ShowcaseState.TIME_WRAP_SECONDS);
                dirty = true;
                return true;
            }
            if (screenX >= controls.intensitySliderX()
                    && screenX <= controls.intensitySliderX() + SLIDER_WIDTH) {
                float fraction = (screenX - controls.intensitySliderX()) / SLIDER_WIDTH;
                state.setIntensity(fraction);
                dirty = true;
                return true;
            }
            return false;
        }
    }

    private record PaneLayout(float leftX, float rightX, float paneY, float paneWidth,
            float paneHeight, float controlsY) {}
}
