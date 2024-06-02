package wonder.shaderdisplay.entry;

import fr.wonder.commons.loggers.Logger;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.Time;
import wonder.shaderdisplay.display.*;
import wonder.shaderdisplay.renderers.*;
import wonder.shaderdisplay.scene.Macro;
import wonder.shaderdisplay.scene.Scene;
import wonder.shaderdisplay.scene.SceneLayer;
import wonder.shaderdisplay.scene.SceneParser;
import wonder.shaderdisplay.uniforms.ResolutionUniform;

import java.io.File;
import java.io.IOException;

public class SetupUtils {

    protected static class Display {

        public Renderer renderer;

    }

    protected static void loadCommonOptions(Main.DisplayOptions options) throws BadInitException {
        Main.logger.setLogLevel(options.verbose ? Logger.LEVEL_DEBUG : Logger.LEVEL_INFO);
    }

    protected static void loadCommonOptions(Main.RunOptions options) throws BadInitException {
        loadCommonOptions(options.displayOptions);

        if(options.targetFPS <= 0) {
            throw new BadInitException("Invalid fps: " + options.targetFPS);
        }

        Texture.setUseCache(!options.noTextureCache);
        Time.setFps(options.targetFPS);
    }

    protected static void loadCommonOptions(Main.ImagePassOptions options) throws BadInitException {
        loadCommonOptions(options.displayOptions);

        Main.isImagePass = true;
    }

    protected static void loadCommonOptions(Main.VideoOptions options) throws BadInitException {
        loadCommonOptions(options.displayOptions);

        if (options.framerate <= 0)
            throw new BadInitException("The framerate must be >0");
        if (options.lastFrame <= 0 && options.videoDuration <= 0)
            throw new BadInitException("Video duration not specified, run with -l <last frame> or -d <duration in seconds>");
        if (options.lastFrame != 0 && options.videoDuration != 0)
            throw new BadInitException("Both 'last frame' and 'duration' cannot be specified at the same time");
        options.lastFrame = options.lastFrame <= 0 ? (int) (options.videoDuration * options.framerate) : options.lastFrame;
        if (options.lastFrame <= options.firstFrame)
            throw new BadInitException("Last frame cannot be less than or equal to the first frame");
    }

    protected static Display createDisplay(Main.DisplayOptions options, boolean windowVisible, boolean useVSync) {
        Display display = new Display();

        Main.logger.info("Creating window");
        GLWindow.createWindow(options.winWidth, options.winHeight, windowVisible, options.forcedGLVersion, options.verbose);
        GLWindow.setVSync(useVSync);
        GLWindow.setTaskBarIcon("/icon.png");
        GLWindow.addResizeListener(ResolutionUniform::updateViewportSize);
        ResolutionUniform.updateViewportSize(options.winWidth, options.winHeight);

        display.renderer = new Renderer();

        return display;
    }

    protected static Scene createScene(Main.DisplayOptions options, File sceneFile) throws BadInitException {
        Scene scene;
        if (sceneFile.getName().endsWith(".fs"))
            scene = createSimpleScene(options, sceneFile);
        else if (sceneFile.getName().endsWith(".json") || sceneFile.getName().endsWith(".scene"))
            scene = SceneParser.loadScene(sceneFile);
        else
            throw new BadInitException("Invalid scene file, either pass in a fragment shader or a scene description file (.json or .scene)");

        for (SceneLayer layer : scene.layers) {
            if (!Renderer.compileShaders(layer))
                throw new BadInitException("Could not load shaders");
        }

        return scene;
    }

    private static Scene createSimpleScene(Main.DisplayOptions options, File fragmentFile) throws BadInitException {
        try {
            Scene scene = new Scene();
            scene.layers.add(new SceneLayer(
                new ShaderFileSet()
                    .setFile(ShaderType.VERTEX, options.vertexShaderFile)
                    .setFile(ShaderType.GEOMETRY, options.geometryShaderFile)
                    .setFile(ShaderType.COMPUTE, options.computeShaderFile)
                    .setFile(ShaderType.FRAGMENT, fragmentFile)
                    .readSources(),
                Mesh.fullscreenTriangle(),
                new Macro[0])
            );
            return scene;
        } catch (IOException e) {
            throw new BadInitException(e.getMessage());
        }
    }

}
