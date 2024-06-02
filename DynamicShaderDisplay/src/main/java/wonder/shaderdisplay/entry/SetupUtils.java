package wonder.shaderdisplay.entry;

import fr.wonder.commons.files.FilesUtils;
import fr.wonder.commons.loggers.Logger;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.Resources;
import wonder.shaderdisplay.Time;
import wonder.shaderdisplay.display.*;
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

        if (sceneFile.isDirectory())
            sceneFile = new File(sceneFile, "scene.json");
        if (!sceneFile.isFile()) {
            try {
                if (sceneFile.getName().endsWith(".fs"))
                    FilesUtils.write(sceneFile, Resources.getDefaultShaderSources()[ShaderType.FRAGMENT.ordinal()]);
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
