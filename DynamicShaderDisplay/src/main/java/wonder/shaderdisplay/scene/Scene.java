package wonder.shaderdisplay.scene;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;
import wonder.shaderdisplay.Time;
import wonder.shaderdisplay.UserControls;
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

    private String[] renderTargetNames;
    private ImInt selectedRenderTarget;

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

    public void renderControls(UserControls generalControls) {
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0, 0, 0, 180);
        ImGui.pushStyleColor(ImGuiCol.Header, 46, 144, 144, 255);
        if (!ImGui.begin("Controls")) {
            ImGui.end();
            ImGui.popStyleColor(2);
            return;
        }

        if (ImGui.collapsingHeader("General")) {
            generalControls.renderControls();
            if (renderTargets.size() > 1) {
                if (selectedRenderTarget == null)
                    selectedRenderTarget = new ImInt(0);
                ImGui.combo("Render Target", selectedRenderTarget, renderTargetNames);
            }
            ImGui.newLine();
        }

        if (ImGui.collapsingHeader("Uniforms", ImGuiTreeNodeFlags.DefaultOpen)) {
            Time.renderControls();
            ImGui.separator();
            for (int i = 0; i < layers.size(); i++) {
                ImGui.pushID(i);
                SceneLayer layer = layers.get(i);
                ImGui.textColored(0xffaaff00, layer.fileSet.getPrimaryFileName());
                layer.shaderUniforms.renderControls();
                ImGui.separator();
                ImGui.popID();
            }
            ImGui.newLine();
        }

        ImGui.popStyleColor(2);
        ImGui.end();
    }

    public String getPrimaryRenderTargetName() {
        return selectedRenderTarget == null ? SceneRenderTarget.DEFAULT_RT.name : renderTargetNames[selectedRenderTarget.get()];
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
