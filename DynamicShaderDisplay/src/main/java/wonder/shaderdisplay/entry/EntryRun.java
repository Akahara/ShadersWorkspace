package wonder.shaderdisplay.entry;

import fr.wonder.commons.exceptions.UnreachableException;
import fr.wonder.commons.systems.process.ProcessUtils;
import imgui.ImGui;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import wonder.shaderdisplay.*;
import wonder.shaderdisplay.display.GLWindow;
import wonder.shaderdisplay.display.TexturesSwapChain;
import wonder.shaderdisplay.scene.Scene;
import wonder.shaderdisplay.scene.SceneParser;

import java.io.File;

import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;

public class EntryRun extends SetupUtils {

    public static void run(Main.RunOptions options, File fragment) {
        Main.logger.info("-- Running display --");

        Display display;
        Scene scene;

        try {
            loadCommonOptions(options);
            display = createDisplay(options.displayOptions, true, options.vsync);
            scene = createScene(options.displayOptions, fragment);
        } catch (BadInitException e) {
            Main.logger.err(e.getMessage());
            Main.exit();
            throw new UnreachableException();
        }

        try {
            TexturesSwapChain renderTargetsSwapChain = new TexturesSwapChain(options.displayOptions.winWidth, options.displayOptions.winHeight);
            FileWatcher shaderFiles = new FileWatcher(scene, options.hardReload);
            ImGuiSystem imgui = options.noGui ? null : new ImGuiSystem();
            UserControls userControls = new UserControls();
            Resources.scanForAndLoadSnippets();
            GLWindow.addResizeListener(renderTargetsSwapChain::resizeTextures);
            shaderFiles.startWatching();

            Main.logger.info("Running shader");

            long shaderLastNano = System.nanoTime();
            long nextFrame = System.nanoTime();
            long lastSec = System.nanoTime();
            int frames = 0;
            long workTime = 0;

            while (!GLWindow.shouldDispose()) {
                // reload shaders if necessary
                if (shaderFiles.processShaderRecompilation()) {
                    if (options.resetTimeOnUpdate)
                        Time.setFrame(0);
                    if (options.resetRenderTargetsOnUpdate)
                        renderTargetsSwapChain.clearTextures();
                }
                if (shaderFiles.requiresSceneRecompilation()) {
                    shaderFiles.stopWatching();
                    scene = SceneParser.regenerateScene(fragment, scene);
                    shaderFiles = new FileWatcher(scene, options.hardReload);
                    shaderFiles.startWatching();
                }

                if (System.in.available() > 0)
                    userControls.readStdin();

                // -------- draw frame ---------

                // render the actual frame
                renderTargetsSwapChain.swap();
                renderTargetsSwapChain.bind();
                if (!Time.isPaused() || Time.justChanged())
                    display.renderer.render(scene);
                renderTargetsSwapChain.blitToScreen(userControls.getDrawBackground());

                // update time *after* having drawn the frame and *before* drawing controls
                // that way time can be set by the controls and not be modified until next frame
                long current = System.nanoTime();
                if (options.frameExact)
                    Time.stepFrame(1);
                else
                    Time.step((current - shaderLastNano) / (float) 1E9);
                shaderLastNano = current;

                if (imgui != null)
                    imgui.renderControls(renderTargetsSwapChain, scene, userControls);

                // ---------/draw frame/---------

                glfwSwapBuffers(GLWindow.getWindow());
                glfwPollEvents();

                if (userControls.poolShouldTakeScreenshot())
                    userControls.takeScreenshot(renderTargetsSwapChain, options.displayOptions);

                workTime += System.nanoTime() - current;
                current = System.nanoTime();
                if (current < nextFrame)
                    ProcessUtils.sleep((nextFrame - current) / (int) 1E6);
                nextFrame += 1E9 / options.targetFPS;
                frames++;
                if (current > lastSec + 1E9) {
                    GLWindow.setWindowTitle(String.format("Shader workspace - %d fps - %f millis per frame - %d expected fps",
                            frames, workTime / 1E6 / frames, (int) (1E9 * frames / workTime)));
                    lastSec = current;
                    workTime = 0;
                    frames = 0;
                }
            }

            GLWindow.dispose();
        } catch (Throwable e) {
            if (options.displayOptions.verbose)
                Main.logger.merr(e, "An error occurred");
            else
                Main.logger.err(e, "An error occurred");
        } finally {
            Main.exit();
        }
    }
}

class ImGuiSystem {

    private final ImGuiImplGlfw glfw;
    private final ImGuiImplGl3 gl3;

    ImGuiSystem() {
        ImGui.createContext();
        glfw = new ImGuiImplGlfw();
        gl3 = new ImGuiImplGl3();
        glfw.init(GLWindow.getWindow(), true);
        gl3.init();
    }

    public void renderControls(TexturesSwapChain renderTargetsSwapChain, Scene scene, UserControls userControls) {
        glfw.newFrame();
        ImGui.newFrame();
        userControls.renderControls(renderTargetsSwapChain);
        scene.renderControls();
        ImGui.render();
        gl3.renderDrawData(ImGui.getDrawData());
        ImGui.updatePlatformWindows();
        ImGui.renderPlatformWindowsDefault();
    }
}
