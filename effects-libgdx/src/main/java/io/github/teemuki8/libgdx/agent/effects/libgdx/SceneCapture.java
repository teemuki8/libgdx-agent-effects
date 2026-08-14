package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.graphics.Texture;

/** Application-owned color capture supplied as an explicit graph input. */
public interface SceneCapture {
    /** Borrowed color texture; the effects library never disposes it. */
    Texture colorTexture();

    /** Declared capture width. */
    int width();

    /** Declared capture height. */
    int height();
}
