package wonder.shaderdisplay.scene;

import imgui.ImGui;
import wonder.shaderdisplay.Time;
import wonder.shaderdisplay.display.GLWindow;
import wonder.shaderdisplay.display.TexturesSwapChain;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Scene {

    public final List<SceneLayer> layers = new ArrayList<>();
    public final List<Macro> macros = new ArrayList<>();
    public final List<SceneRenderTarget> renderTargets = new ArrayList<>();
    public final File sourceFile;

    public TexturesSwapChain swapChain;
    private GLWindow.ListenerHandle resizeHandle;

    public Scene(File sourceFile) {
        this.sourceFile = sourceFile;
    }

    public Scene() {
        this(null);
    }

    public void prepareSwapChain(int winWidth, int winHeight) {
        if (swapChain != null) {
            resizeHandle.remove();
            swapChain.dispose();
        }
        this.swapChain = new TexturesSwapChain(renderTargets, winWidth, winHeight);
        this.resizeHandle = GLWindow.addResizeListener(swapChain::resizeTextures);
    }

    public void dispose() {
        for (SceneLayer layer : layers) {
            layer.dispose();
        }
    }

    public void renderControls() {
        if (!ImGui.begin("Uniforms")) {
            ImGui.end();
            return;
        }
        Time.renderControls();
        for (int i = 0; i < layers.size(); i++) {
            ImGui.pushID(i);
            SceneLayer layer = layers.get(i);
            ImGui.separator();
            ImGui.textColored(0xffaaff00, layer.fileSet.getPrimaryFileName());
            layer.shaderUniforms.renderControls();
            ImGui.popID();
        }
        ImGui.end();
    }

    public SceneRenderTarget getRenderTarget(String name) {
        return renderTargets.stream().filter(r -> r.name.equals(name)).findFirst().orElse(null);
    }

    public void clearSwapChainTextures() {
        swapChain.clearTextures();
    }

    public void presentToScreen(String renderTargetName, boolean drawBackground) {
        swapChain.blitToScreen(renderTargetName, drawBackground);
    }
}
