package wonder.shaderdisplay.scene;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.display.Mesh;
import wonder.shaderdisplay.display.Renderer;
import wonder.shaderdisplay.display.ShaderFileSet;
import wonder.shaderdisplay.display.ShaderType;
import wonder.shaderdisplay.entry.BadInitException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class SceneParser {

    private static final ObjectMapper mapper = new ObjectMapper(new JsonFactoryBuilder()
            .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS)
            .enable(JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build());

    public static Scene loadScene(File file) throws BadInitException {
        Scene scene = regenerateScene(file, null);
        if (scene == null)
            throw new BadInitException("Could not load the scene");
        return scene;
    }

    public static Scene regenerateScene(File file, Scene previousScene) {
        JsonScene serialized;
        try {
            serialized = mapper.readValue(file, JsonScene.class);
        } catch (IOException e) {
            Main.logger.err("Could not parse the scene file: " + e.getMessage());
            return previousScene;
        }

        Scene scene = new Scene(file);
        scene.macros.addAll(Arrays.asList(serialized.macros));
        StringBuilder errors = new StringBuilder();
        boolean tryToReloadPreviousScene = previousScene != null && previousScene.layers.size() == serialized.layers.length;

        for (int i = 0; i < serialized.layers.length; i++) {
            JsonSceneLayer serializedLayer = serialized.layers[i];
            try {
                SceneLayer.RenderState renderState = new SceneLayer.RenderState();
                renderState.isDepthWriteEnabled = serializedLayer.depthWrite;
                renderState.isDepthTestEnabled = serializedLayer.depthTest;
                renderState.isBlendingEnabled = serializedLayer.blending;
                renderState.culling = serializedLayer.culling;
                SceneLayer layer = new SceneLayer(
                    new ShaderFileSet()
                        .setFile(ShaderType.VERTEX, asOptionalPath(file, serializedLayer.root, serializedLayer.vertex))
                        .setFile(ShaderType.GEOMETRY, asOptionalPath(file, serializedLayer.root, serializedLayer.geometry))
                        .setFile(ShaderType.FRAGMENT, asOptionalPath(file, serializedLayer.root, serializedLayer.fragment))
                        .setFile(ShaderType.COMPUTE, asOptionalPath(file, serializedLayer.root, serializedLayer.compute))
                        .readSources(),
                    loadMesh(file, serializedLayer.root, serializedLayer.model),
                    serializedLayer.macros,
                    renderState
                );
                if (tryToReloadPreviousScene) {
//                    layer.shaderUniforms
                }
                if (!Renderer.compileShaders(scene, layer))
                    throw new IOException("Could not build a shader");
                scene.layers.add(layer);
            } catch (IOException e) {
                errors.append("Layer " + i + ": " + e.getMessage() + "\n");
            }
        }

        if (!errors.isEmpty()) {
            scene.dispose();
            errors.deleteCharAt(errors.length()-1);
            Main.logger.err(errors.toString());
            return previousScene;
        }

        return scene;
    }

    private static File asOptionalPath(File sceneFile, String optRoot, String optPath) {
        if (optPath == null)
            return null;
        File finalFile = sceneFile.getParentFile();
        if (optRoot != null) finalFile = new File(finalFile, optRoot);
        return new File(finalFile, optPath);
    }

    private static Mesh loadMesh(File sceneFile, String optRoot, String nameOrPath) throws IOException {
        if (nameOrPath == null)
            return Mesh.fullscreenTriangle();
        switch (nameOrPath) {
        case "fullscreen":
            return Mesh.fullscreenTriangle();
        default:
            try {
                return Mesh.parseFile(asOptionalPath(sceneFile, optRoot, nameOrPath));
            } catch (IOException e) {
                throw new IOException("Could not parse mesh file '" + nameOrPath + "': " + e.getMessage());
            }
        }
    }

}

class JsonScene {
    @JsonProperty(required = true)
    public String version;
    @JsonProperty(required = true)
    public JsonSceneLayer[] layers;
    public Macro[] macros = new Macro[0];
    public JsonSceneAudio[] audio = new JsonSceneAudio[0];
}

class JsonSceneLayer {
    public String root = null;
    public String vertex = null;
    public String geometry = null;
    public String fragment = null;
    public String compute = null;
    public String model = null;
    public Macro[] macros = new Macro[0];
    public boolean depthTest = true;
    public boolean depthWrite = true;
    public boolean blending = true;
    public SceneLayer.RenderState.Culling culling = SceneLayer.RenderState.Culling.NONE;
}

class JsonSceneAudio {
    @JsonProperty(required = true)
    public String path;
}