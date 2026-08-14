import io.github.teemuki8.libgdx.agent.effects.core.*;
import io.github.teemuki8.libgdx.agent.effects.libgdx.*;
import io.github.teemuki8.libgdx.agent.effects.runtime.*;
import java.util.List;

// Composition example only: Wings continues to own its ship, simulation, loop, and textures.
final class WingsShipTrailExample implements AutoCloseable {
    private final TrailDefinition definition;
    private final TrailInstance trail;
    private final TrailRenderer renderer;
    private final RegisteredAssetResolver assets;

    // Construct this on Wings' libGDX render thread. Resolve only application-registered assets.
    WingsShipTrailExample(RegisteredAssetResolver assets) {
        this.assets = assets;
        Material2dDefinition material = new Material2dDefinition(
                "wings-engine-trail",
                new ShaderSource(
                        "attribute vec2 a_position; attribute vec4 a_color; "
                                + "attribute vec2 a_texCoord0; varying vec4 v_color; "
                                + "void main(){v_color=a_color;gl_Position=vec4(a_position,0,1);}",
                        "varying vec4 v_color; void main(){gl_FragColor=v_color;}"),
                BlendMode.ADDITIVE, List.of(), List.of());
        definition = new TrailDefinition(
                "wings-player-engine-trail", "ship_engine", material,
                new FloatCurve(List.of(
                        new FloatCurve.Stop(0f, 0.22f),
                        new FloatCurve.Stop(1f, 0f))),
                new ColorGradient(List.of(
                        new ColorGradient.Stop(0f, 0.35f, 0.9f, 1f, 0.95f),
                        new ColorGradient.Stop(1f, 0.05f, 0.15f, 0.8f, 0f))),
                1f / 60f, 0.02f, 96, 0.8f,
                TrailJoin.MITER, TrailCap.BUTT, TrailUvMode.STRETCH, 2f);
        trail = new TrailInstance(definition, RuntimeLimits.developmentDefaults());
        renderer = new TrailRenderer(definition, EffectsLimits.developmentDefaults());
    }

    // Call from Wings' update/render orchestration with its authoritative interpolated transform.
    void update(float deltaSeconds, float engineX, float engineY, float engineZ) {
        trail.setAnchor(new EffectAnchor("ship_engine", engineX, engineY, engineZ));
        trail.advance(deltaSeconds);
    }

    // Call at the desired world/screen render phase on the same render thread.
    void render() {
        renderer.render(trail.snapshot(), assets);
    }

    @Override public void close() {
        renderer.close();
        trail.close();
    }
}
