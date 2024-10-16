package wonder.shaderdisplay.scene;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import wonder.shaderdisplay.controls.UserControls;
import wonder.shaderdisplay.display.GLWindow;
import wonder.shaderdisplay.display.TexturesSwapChain;
import wonder.shaderdisplay.serial.UserConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Scene {

    public final List<SceneLayer> layers = new ArrayList<>();
    public final List<Macro> macros = new ArrayList<>();
    public final List<SceneRenderTarget> renderTargets = new ArrayList<>();
    public String[] renderTargetNames;
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
        this.renderTargetNames = renderTargets.stream().map(rt -> rt.name).toArray(String[]::new);
    }

    public void dispose() {
        for (SceneLayer layer : layers) {
            layer.dispose();
        }
    }

    public void renderControls() {
        for (int i = 0; i < layers.size(); i++) {
            ImGui.pushID(i);
            SceneLayer layer = layers.get(i);
            int activationColor = layer.enabled ? 0xff0ec029 : 0xffc0220e;
            ImGui.pushStyleColor(ImGuiCol.Button, activationColor);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, activationColor);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, activationColor);
            if (ImGui.button(" ")) {
                if (UserControls.isModPressed(UserControls.KeyMod.SHIFT)) {
                    boolean wasInSameState = true;
                    for (int j = 0; j < layers.size(); j++)
                        wasInSameState &= (layers.get(j).enabled == (j <= i));
                    for (int j = 0; j < layers.size(); j++)
                        layers.get(j).enabled = wasInSameState || j <= i;
                } else {
                    layer.enabled = !layer.enabled;
                }
                if (UserConfig.config != null) {
                    UserConfig.config.layers = new UserConfig.LayerState[layers.size()];
                    for (int j = 0; j < layers.size(); j++) {
                        UserConfig.LayerState s = UserConfig.config.layers[j] = new UserConfig.LayerState();
                        s.enabled = layers.get(j).enabled;
                    }
                }
            }
            ImGui.popStyleColor(3);
            ImGui.sameLine();
            ImGui.textColored(0xffaaff00, layer.fileSet.getPrimaryFileName());
            layer.shaderUniforms.renderControls();
            ImGui.separator();
            ImGui.popID();
        }
    }

    public SceneRenderTarget getRenderTarget(String name) {
        return renderTargets.stream().filter(r -> r.name.equals(name)).findFirst().orElse(null);
    }

    public void clearSwapChainTextures() {
        swapChain.clearTextures();
    }

    public void applyUserConfig() {
        UserConfig.LayerState[] configStates = UserConfig.config.layers;

        if (configStates.length != layers.size())
            return;

        for (int i = 0; i < configStates.length; i++)
            layers.get(i).enabled = configStates[i].enabled;
    }
}
