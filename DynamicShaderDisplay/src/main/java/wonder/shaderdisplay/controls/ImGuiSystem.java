package wonder.shaderdisplay.controls;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiDockNodeFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import wonder.shaderdisplay.display.GLWindow;
import wonder.shaderdisplay.scene.Scene;
import wonder.shaderdisplay.serial.UserConfig;

import java.io.File;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ImGuiSystem {

    private final ImGuiImplGlfw glfw;
    private final ImGuiImplGl3 gl3;

    private final Window controlsWindow = new Window("Controls");
    private final Window uniformsWindow = new Window("Uniforms");
    private final Window timelineWindow = new Window("Timeline");
    private final Window[] windows = {
        controlsWindow,
        uniformsWindow,
        timelineWindow,
    };

    public ImGuiSystem(File projectRootFile) {
        ImGui.createContext();
        File iniFile = new File(UserConfig.getProjectConfigDir(projectRootFile), "imgui.ini");
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(iniFile.getAbsolutePath());
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable);
        io.addConfigFlags(ImGuiConfigFlags.ViewportsEnable);
        glfw = new ImGuiImplGlfw();
        gl3 = new ImGuiImplGl3();
        glfw.init(GLWindow.getWindow(), true);
        gl3.init();
    }

    public void renderControls(Scene scene, UserControls userControls, Timeline timeline) {
        glfw.newFrame();
        ImGui.newFrame();
        ImGui.dockSpaceOverViewport(ImGui.getMainViewport(), ImGuiDockNodeFlags.PassthruCentralNode);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0, 0, 0, 180);
        ImGui.pushStyleColor(ImGuiCol.Header, 46, 144, 144, 255);

        renderMainMenuBar();
        controlsWindow.render(() -> userControls.renderControls(scene));
        uniformsWindow.render(scene::renderControls);
        timelineWindow.render(timeline::render);

        ImGui.popStyleColor(2);
        ImGui.render();
        gl3.renderDrawData(ImGui.getDrawData());
        ImGui.updatePlatformWindows();
        ImGui.renderPlatformWindowsDefault();
        GLWindow.restoreGLFWContext();
    }

    private void renderMainMenuBar() {
        if (!ImGui.beginMainMenuBar())
            return;
        boolean anyToggled = false;
        if (ImGui.beginMenu("Windows")) {
            for (Window w : windows) {
                boolean toggled = ImGui.menuItem(w.title, null, w.visible);
                w.visible ^= toggled;
                anyToggled |= toggled;
            }
            ImGui.endMenu();
        }
        if (anyToggled)
            UserConfig.config.visibleImGuiWindows = Stream.of(windows).filter(w -> w.visible).map(w -> w.title).toList();
        ImGui.endMainMenuBar();
    }

    public static void copyToClipboardBtn(String name, Supplier<String> copiedText) {
        if(tooltipButton("C##" + name, "Copy to clipboard"))
            ImGui.setClipboardText(copiedText.get());
    }

    public static boolean tooltipButton(String name, String tooltip) {
        boolean pressed = ImGui.button(name);
        showTooltipOnHover(tooltip);
        return pressed;
    }

    public static void showTooltipOnHover(String tooltip) {
        if(ImGui.isItemHovered())
            ImGui.setTooltip(tooltip);
    }
}

class Window {
    public final String title;
    public boolean visible;

    public Window(String title) {
        this.title = title;
        this.visible = UserConfig.config.visibleImGuiWindows.contains(title);
    }

    public void render(Runnable renderFunc) {
        if (!visible) return;
        if (ImGui.begin(title))
            renderFunc.run();
        ImGui.end();
    }
}