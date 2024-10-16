package wonder.shaderdisplay.entry;

import fr.wonder.commons.exceptions.UnreachableException;
import wonder.shaderdisplay.*;
import wonder.shaderdisplay.controls.ImGuiSystem;
import wonder.shaderdisplay.controls.Timeline;
import wonder.shaderdisplay.controls.UserControls;
import wonder.shaderdisplay.display.GLWindow;
import wonder.shaderdisplay.display.ShaderCompiler;
import wonder.shaderdisplay.display.Texture;
import wonder.shaderdisplay.display.WindowBlit;
import wonder.shaderdisplay.scene.Scene;
import wonder.shaderdisplay.scene.SceneParser;
import wonder.shaderdisplay.scene.SceneRenderTarget;
import wonder.shaderdisplay.serial.InputFiles;
import wonder.shaderdisplay.serial.Resources;
import wonder.shaderdisplay.serial.UserConfig;
import wonder.shaderdisplay.uniforms.UniformApplicationContext;
import wonder.shaderdisplay.uniforms.ViewUniform;

import java.io.File;

public class EntryRun extends SetupUtils {

    protected static void loadCommonOptions(Main.RunOptions options, InputFiles inputFiles) throws BadInitException {
        loadCommonOptions(options.displayOptions, inputFiles);

        if(options.targetFPS <= 0) {
            throw new BadInitException("Invalid fps: " + options.targetFPS);
        }

        Resources.setDefaultFragmentTemplate(options.fragmentTemplate);
        Texture.setUseCache(!options.noTextureCache);
        Time.setFps(options.targetFPS);
    }

    public static void run(Main.RunOptions options, File sceneFile, File... rawInputFiles) {
        Main.logger.info("-- Running display --");

        Display display;
        Scene scene;
        sceneFile = getMainSceneFile(sceneFile);

        try {
            UserConfig.loadConfig(sceneFile);
            InputFiles inputFiles = InputFiles.singleton = new InputFiles(rawInputFiles, options.frameExact);
            loadCommonOptions(options, inputFiles);
            display = createDisplay(options.displayOptions, true, options.vsync);
            inputFiles.startReadingFiles();
            if (inputFiles.hasInputVideo() && options.frameExact) {
                options.targetFPS = inputFiles.getCommonVideoFramerate();
                Time.setFps(options.targetFPS);
                Main.logger.info("Using input video framerate: " + options.targetFPS);
            }
            scene = createScene(options.displayOptions, sceneFile);
            scene.prepareSwapChain(options.displayOptions.winWidth, options.displayOptions.winHeight);
            scene.applyUserConfig();
        } catch (BadInitException e) {
            Main.logger.err(e.getMessage());
            Main.exit();
            throw new UnreachableException();
        }

        try {
            FileWatcher fileWatcher = new FileWatcher(scene, options.hardReload);
            ImGuiSystem imgui = options.noGui ? null : new ImGuiSystem(sceneFile);
            UserControls userControls = ViewUniform.userControls = new UserControls();
            Timeline timeline = new Timeline();
            Resources.scanForAndLoadSnippets();
            fileWatcher.startWatching();

            Main.logger.info("Running shader");

            long frameDuration = (long) (1E9 / options.targetFPS);
            long shaderLastNano = System.nanoTime();
            long nextFrame = System.nanoTime();
            long lastSec = System.nanoTime();
            int frames = 0;
            long workTime = 0;
            WindowTitleSupplier windowTitleSupplier = new WindowTitleSupplier(sceneFile.getName());

            while (!GLWindow.shouldDispose()) {
                // reload shaders if necessary
                boolean hasPendingFileChanges = fileWatcher.hasPendingChanges();
                windowTitleSupplier.hasPendingFileChanges = hasPendingFileChanges;
                if (hasPendingFileChanges && !fileWatcher.isDebouncingRecompilation()) {
                    synchronized (fileWatcher) {
                        boolean rewatchFiles = false;
                        if (fileWatcher.requiresSceneRecompilation()) {
                            rewatchFiles = true;
                            Main.logger.info("Regenerating scene");
                            scene = SceneParser.regenerateScene(scene.sourceFile, scene);
                            scene.prepareSwapChain(GLWindow.getWinWidth(), GLWindow.getWinHeight());
                            scene.applyUserConfig();
                        }
                        ShaderCompiler.ShaderCompilationResult compilationResult = fileWatcher.processShaderRecompilation();
                        rewatchFiles |= compilationResult.fileDependenciesUpdated;
                        if (compilationResult.success) {
                            UniformApplicationContext.resetLoggedBindingWarnings();
                            if (options.resetTimeOnUpdate)
                                Time.jumpToFrame(0);
                            if (options.resetRenderTargetsOnUpdate)
                                scene.clearSwapChainTextures();
                        }
                        fileWatcher.processDummyFilesRecompilation();
                        if (rewatchFiles) {
                            fileWatcher.stopWatching();
                            fileWatcher = new FileWatcher(scene, options.hardReload);
                            fileWatcher.startWatching();
                        }
                    }
                }

                // -------- draw frame ---------

                // render the actual frame
                if (!Time.isPaused() || Time.justChanged() || userControls.justMoved())
                    display.renderer.render(scene);
                int primaryRTIndex = userControls.getPrimaryRenderTargetIndex();
                WindowBlit.blitToScreen(
                        scene.swapChain.getAttachment(primaryRTIndex < 0 ? SceneRenderTarget.DEFAULT_RT.name : scene.renderTargets.get(primaryRTIndex).name),
                        userControls.getDrawBackground(),
                        userControls.getDepthPreviewZRangeStart(),
                        userControls.getDepthPreviewZRangeStop());

                // update time *after* having drawn the frame and *before* drawing controls
                // that way time can be set by the controls and not be modified until next frame
                long endNano = System.nanoTime();
                float realDeltaTime = (endNano - shaderLastNano) / (float) 1E9;
                if (options.frameExact)
                    Time.stepFrame(1);
                else
                    Time.step(realDeltaTime);
                Time.applyTimeLoop(UserConfig.config.timeLoop);
                userControls.step(realDeltaTime);

                if (imgui != null)
                    imgui.renderControls(scene, userControls, timeline);

                // ---------/draw frame/---------

                InputFiles.singleton.updateAudio();
                GLWindow.endFrame();

                if (userControls.pollShouldTakeScreenshot())
                    userControls.takeScreenshot(scene, options.displayOptions);
                if (userControls.pollResetRenderTargetsQueried())
                    scene.clearSwapChainTextures();

                long sleepBegin = System.nanoTime();
                if (endNano < nextFrame)
                    Thread.sleep((nextFrame - endNano) / (int) 1E6);
                workTime += endNano - shaderLastNano - (System.nanoTime() - sleepBegin);
                nextFrame += frameDuration;
                long sleepEnd = System.nanoTime();
                if (nextFrame + frameDuration * 4 < sleepEnd)
                    nextFrame = sleepEnd + frameDuration; // Don't try to catch up if we are way too late
                frames++;
                if (endNano > lastSec + 1E9) {
                    windowTitleSupplier.millisPerFrame = workTime / 1E6 / frames;
                    windowTitleSupplier.currentFPS = frames;
                    lastSec = endNano;
                    workTime = 0;
                    frames = 0;
                }
                shaderLastNano = endNano;

                GLWindow.setWindowTitle(windowTitleSupplier.getTitle());
            }

            GLWindow.dispose();
        } catch (Throwable e) {
            if (options.displayOptions.verbose)
                Main.logger.merr(e, "An error occurred");
            else
                Main.logger.err(e, "An error occurred");
        } finally {
            UserConfig.saveConfig(scene.sourceFile);
            Main.exit();
        }
    }
}

class WindowTitleSupplier {

    private final String primaryFileName;
    public int currentFPS;
    public double millisPerFrame;
    public boolean hasPendingFileChanges;

    WindowTitleSupplier(String primaryFileName) {
        this.primaryFileName = primaryFileName;
    }

    String getTitle() {
        return String.format("%s %s- %d fps - %.4f millis per frame - %d expected fps",
                primaryFileName,
                hasPendingFileChanges ? "(*) " : "",
                currentFPS, millisPerFrame, (int) (1000 / millisPerFrame));
    }

}
