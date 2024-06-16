package wonder.shaderdisplay.uniforms;

import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.display.Texture;
import wonder.shaderdisplay.scene.Scene;
import wonder.shaderdisplay.scene.SceneLayer;
import wonder.shaderdisplay.scene.SceneUniform;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class UniformApplicationContext {

    private static Set<String> failedRenderTargetBindings = new HashSet<>();

    public static void resetLoggedBindingWarnings() {
        failedRenderTargetBindings.clear();
    }

    private final Scene scene;
    private final SceneLayer layer;

    private int nextTextureSlot = 0;

    public UniformApplicationContext(Scene scene, SceneLayer layer) {
        this.scene = scene;
        this.layer = layer;
    }

    public int getNextTextureSlot() {
        return nextTextureSlot++;
    }

    public SceneUniform getDefaultUniform(String name) {
        return Stream.of(layer.uniforms).filter(u -> u.name.equals(name)).findFirst().orElse(null);
    }

    public Texture getRenderTargetReadableTexture(String accessingUniform, String renderTargetName) {
        Texture texture = scene.swapChain.getRenderTargetReadableTexture(layer, renderTargetName);
        if (texture == null && failedRenderTargetBindings.add(renderTargetName)) {
            Main.logger.warn("Could not bind sampler " + accessingUniform + ": '" + renderTargetName + "' is not a render target");
        }
        return texture;
    }

}
