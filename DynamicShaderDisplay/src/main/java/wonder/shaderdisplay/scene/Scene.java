package wonder.shaderdisplay.scene;

import imgui.ImGui;
import wonder.shaderdisplay.Time;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Scene {

    public final List<SceneLayer> layers = new ArrayList<>();
    public final List<Macro> macros = new ArrayList<>();
    public final File sourceFile;

    public Scene(File sourceFile) {
        this.sourceFile = sourceFile;
    }

    public Scene() {
        this(null);
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
        for (SceneLayer layer : layers) {
            ImGui.separator();
            ImGui.textColored(0xffaaff00, layer.fileSet.getPrimaryFileName());
            layer.shaderUniforms.renderControls();
        }
        ImGui.end();
    }
}
