package wonder.shaderdisplay.entry;

import fr.wonder.commons.exceptions.ErrorWrapper;
import fr.wonder.commons.files.FilesUtils;
import fr.wonder.commons.loggers.Logger;
import wonder.shaderdisplay.ImageInputFiles;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.serial.Resources;
import wonder.shaderdisplay.display.*;
import wonder.shaderdisplay.scene.*;
import wonder.shaderdisplay.serial.UserConfig;
import wonder.shaderdisplay.uniforms.ResolutionUniform;

import java.io.File;
import java.io.IOException;

public class SetupUtils {

    protected static class Display {

        public Renderer renderer;

    }

    protected static void loadCommonOptions(Main.DisplayOptions options, ImageInputFiles inputFiles) throws BadInitException {
        Main.logger.setLogLevel(options.verbose ? Logger.LEVEL_DEBUG : Logger.LEVEL_INFO);
        ShaderCompiler.setDebugResolvedShaders(options.debugResolvedShaders);

        // Window size priority:
        // - if --size-to-input, use the first image/video input size
        // - if --width/--height is specified, use that
        // - use the previous instance config if possible to keep settings between restarts
        // - use the default window size
        if (options.sizeToInput) {
            int[] s = inputFiles == null ? null : inputFiles.getFirstInputFileResolution();
            if (s == null) {
                Main.logger.warn("Cannot set the window size to an input's, there are no inputs");
            } else {
                options.winWidth = s[0];
                options.winHeight = s[1];
            }
        }
        if (UserConfig.config != null && UserConfig.config.windowLocation != null) {
            int[] location = UserConfig.config.windowLocation;
            if (options.winWidth < 0) options.winWidth = location[2];
            if (options.winHeight < 0) options.winHeight = location[3];
        }
        if (options.winWidth < 0) options.winWidth = Main.DisplayOptions.DEFAULT_WIN_WIDTH;
        if (options.winHeight < 0) options.winHeight = Main.DisplayOptions.DEFAULT_WIN_HEIGHT;
    }

    protected static Display createDisplay(Main.DisplayOptions options, boolean windowVisible, boolean useVSync) throws BadInitException {
        Display display = new Display();
        Main.logger.info("Creating window");

        GLWindow.createWindow(options.winWidth, options.winHeight, options.forcedGLVersion, options.verbose);
        GLWindow.setVSync(useVSync);
        GLWindow.setTaskBarIcon("/icon.png");
        if (UserConfig.config != null) {
            int[] location = UserConfig.config.windowLocation;
            if (location != null)
                GLWindow.moveWindow(location[0], location[1]);
            GLWindow.addLocationListener((x, y, w, h) -> UserConfig.config.windowLocation = new int[] { x, y, w, h });
        }
        GLWindow.addResizeListener(ResolutionUniform::updateViewportSize);
        ResolutionUniform.updateViewportSize(options.winWidth, options.winHeight);

        display.renderer = new Renderer();

        if (windowVisible)
            GLWindow.showWindow();

        return display;
    }

    protected static File getMainSceneFile(File userInputFile) {
        if (userInputFile.isDirectory())
            return new File(userInputFile, "scene.json");
        return userInputFile;
    }

    protected static Scene createScene(Main.DisplayOptions options, File sceneFile) throws BadInitException {
        Scene scene;

        if (!sceneFile.isFile()) {
            try {
                if (sceneFile.getName().endsWith(".fs"))
                    FilesUtils.write(sceneFile, Resources.DEFAULT_SHADER_SOURCES[ShaderType.FRAGMENT.ordinal()]);
                else if (sceneFile.getName().endsWith(".json") || sceneFile.getName().endsWith(".scene"))
                    Resources.initializeSceneFiles(sceneFile);
            } catch (IOException e) {
                throw new BadInitException(sceneFile + " does not exist and could not be created");
            }
        }

        if (sceneFile.getName().endsWith(".fs"))
            scene = createSimpleScene(options, sceneFile);
        else if (sceneFile.getName().endsWith(".json") || sceneFile.getName().endsWith(".scene"))
            scene = SceneParser.loadScene(sceneFile);
        else
            throw new BadInitException("Invalid scene file, either pass in a fragment shader or a scene description file (.json or .scene)");

        return scene;
    }

    private static Scene createSimpleScene(Main.DisplayOptions options, File fragmentFile) throws BadInitException {
        SceneRenderTarget renderTarget = SceneRenderTarget.DEFAULT_RT;
        ErrorWrapper errors = new ErrorWrapper("Could not build a simple scene");
        Scene scene = new Scene();
        scene.renderTargets.add(renderTarget);
        scene.layers.add(SceneParser.makeClearLayer(
            errors,
            new String[] { renderTarget.name },
            "vec4(0, 0, 0, 1)",
            1
        ));
        scene.layers.add(new SceneLayer(
            SceneLayer.SceneType.STANDARD_PASS,
            new ShaderFileSet()
                .setFile(ShaderType.VERTEX, options.vertexShaderFile)
                .setFile(ShaderType.GEOMETRY, options.geometryShaderFile)
                .setFile(ShaderType.COMPUTE, options.computeShaderFile)
                .setFile(ShaderType.FRAGMENT, fragmentFile)
                .completeWithDefaultSources(),
            Mesh.makeFullscreenTriangleMesh(),
            new Macro[0],
            new SceneUniform[0],
            new SceneLayer.RenderState(),
            new String[] { renderTarget.name }
        ));

        ShaderCompiler compiler = new ShaderCompiler(scene);
        for (SceneLayer layer : scene.layers)
            compiler.compileShaders(errors, layer);

        if (!errors.noErrors()) {
            errors.dump(Main.logger);
            throw new BadInitException("Could not build a simple scene");
        }

        return scene;
    }

}
