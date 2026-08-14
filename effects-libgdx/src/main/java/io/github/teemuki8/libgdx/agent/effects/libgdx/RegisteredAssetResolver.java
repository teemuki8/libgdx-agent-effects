package io.github.teemuki8.libgdx.agent.effects.libgdx;

import com.badlogic.gdx.graphics.Texture;
import io.github.teemuki8.libgdx.agent.effects.core.AssetKey;

/** Resolves only application-registered texture keys; ownership remains with the application. */
@FunctionalInterface
public interface RegisteredAssetResolver {

    /** Returns the registered texture, or {@code null} when the key is unavailable. */
    Texture resolve(AssetKey key);
}
