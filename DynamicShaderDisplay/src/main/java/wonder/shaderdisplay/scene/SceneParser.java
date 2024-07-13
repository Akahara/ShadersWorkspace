package wonder.shaderdisplay.scene;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import fr.wonder.commons.utils.ArrayOperator;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.Resources;
import wonder.shaderdisplay.display.Mesh;
import wonder.shaderdisplay.display.Renderer;
import wonder.shaderdisplay.display.ShaderFileSet;
import wonder.shaderdisplay.display.ShaderType;
import wonder.shaderdisplay.entry.BadInitException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class SceneParser {

    private static final ObjectMapper mapper = new ObjectMapper(new JsonFactoryBuilder()
            .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS)
            .enable(JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build())
            .addHandler(new DeserializationProblemHandler() {
                @Override
                public boolean handleUnknownProperty(DeserializationContext c, JsonParser p, JsonDeserializer<?> d, Object beanOrClass, String propertyName) throws IOException {
                    if(propertyName.startsWith("_")) {
                        p.skipChildren();
                        return true;
                    }
                    return false;
                }
            });

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

        StringBuilder errors = new StringBuilder();

        Scene scene = new Scene(file);
        scene.macros.addAll(Arrays.asList(serialized.macros));
        scene.renderTargets.add(SceneRenderTarget.DEFAULT_RT);
        scene.renderTargets.addAll(Arrays.asList(serialized.renderTargets));
        boolean tryToReloadPreviousScene = previousScene != null && previousScene.layers.size() == serialized.layers.length;

        validateSceneRenderTargets(errors, serialized.renderTargets);

        for (int i = 0; i < serialized.layers.length; i++) {
            JsonSceneLayer serializedLayerBase = serialized.layers[i];
            try {
                if (serializedLayerBase instanceof JsonSceneStandardLayer serializedLayer) {
                    scene.layers.add(parseStandardLayer(file, scene, serializedLayer));
                } else if (serializedLayerBase instanceof JsonClearPass pass) {
                    scene.layers.add(makeClearLayer(pass.targets, pass.clearColor));
                } else {
                    throw new IOException(serializedLayerBase.getClass().getName());
                }
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

    private static SceneLayer parseStandardLayer(File rootFile, Scene scene, JsonSceneStandardLayer serializedLayer) throws IOException {
        SceneLayer layer = new SceneLayer(
            new ShaderFileSet()
                .setFiles(ShaderType.VERTEX, asOptionalPaths(rootFile, serializedLayer.root, serializedLayer.vertex))
                .setFiles(ShaderType.GEOMETRY, asOptionalPaths(rootFile, serializedLayer.root, serializedLayer.geometry))
                .setFiles(ShaderType.FRAGMENT, asOptionalPaths(rootFile, serializedLayer.root, serializedLayer.fragment))
                .setFile(ShaderType.COMPUTE, asOptionalPath(rootFile, serializedLayer.root, serializedLayer.compute))
                .readSources(),
            loadMesh(rootFile, serializedLayer.root, serializedLayer.model),
            serializedLayer.macros,
            serializedLayer.uniforms,
            serializedLayer.makeRenderState(),
            serializedLayer.targets
        );

        validateLayerRenderTargets(scene, serializedLayer.targets);

        if (!Renderer.compileShaders(scene, layer))
            throw new IOException("Could not build a shader");
        return layer;
    }

    public static SceneLayer makeClearLayer(String[] outputTargets) throws IOException {
        return makeClearLayer(outputTargets, "vec4(0,0,0,1)");
    }

    public static SceneLayer makeClearLayer(String[] outputTargets, String clearColor) throws IOException {
        Stream<Macro> macros = Stream.empty();
        macros = Stream.concat(macros, IntStream.range(0, outputTargets.length).mapToObj(i -> new Macro("CLEAR_TARGET_"+i)));
        macros = Stream.concat(macros, Stream.of(new Macro("CLEAR_COLOR", clearColor)));

        SceneLayer layer = new SceneLayer(
            SceneLayer.BuiltinSceneLayerAddon.CLEAR_PASS,
            new ShaderFileSet()
                .setFixedPrimarySourceName("clear_pass")
                .setSource(ShaderType.FRAGMENT, Resources.readResource("/passes/clear.fs"))
                .setSource(ShaderType.VERTEX, Resources.readResource("/passes/passthrough.vs")),
            Mesh.fullscreenTriangle(),
            macros.toArray(Macro[]::new),
            new SceneUniform[0],
            new SceneLayer.RenderState()
                .setBlending(false)
                .setCulling(SceneLayer.RenderState.Culling.NONE)
                .setDepthTest(false)
                .setDepthWrite(true),
            outputTargets
        );

        if (!Renderer.compileShaders(null, layer))
            throw new IOException("Could not build a shader");

        return layer;
    }

    private static File asOptionalPath(File sceneFile, String optRoot, String optPath) {
        if (optPath == null)
            return null;
        File finalFile = sceneFile.getParentFile();
        if (optRoot != null) finalFile = new File(finalFile, optRoot);
        return new File(finalFile, optPath);
    }

    private static File[] asOptionalPaths(File sceneFile, String optRoot, String[] optPaths) {
        if (optPaths == null) return null;
        return ArrayOperator.map(optPaths, File[]::new, p -> asOptionalPath(sceneFile, optRoot, p));
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

    private static void validateSceneRenderTargets(StringBuilder errors, SceneRenderTarget[] renderTargets) {
        for (int i = 0; i < renderTargets.length; i++) {
            SceneRenderTarget rt = renderTargets[i];
            for (int j = 0; j < i; j++) {
                if (renderTargets[j].name.equals(rt.name))
                    errors.append("Render target '").append(rt.name).append("' specified twice\n");
            }
            if (rt.width < 0 || rt.height < 0)
                errors.append("Invalid size for render target '").append(rt.name).append("'");
            if (!rt.screenRelative && (rt.width < 1.f || rt.height < 1.f || (int)rt.width != rt.width || (int)rt.height != rt.height))
                errors.append("Invalid size for render target '").append(rt.name).append("', for non-screen relative RTs only absolute dimensions are valid\n");
            if (rt.screenRelative && (rt.width > 20 || rt.height > 20))
                errors.append("Very large render target '").append(rt.name).append("', did you forget 'screenRelative':false ?\n");
        }
    }

    private static void validateLayerRenderTargets(Scene scene, String[] layerRenderTargets) throws IOException {
        if (layerRenderTargets.length == 0) {
            throw new IOException("No output render target specified");
        } else {
            SceneRenderTarget baseRenderTarget = scene.getRenderTarget(layerRenderTargets[0]);
            for (int j = 1; j < layerRenderTargets.length; j++) {
                String rtName = layerRenderTargets[j];
                SceneRenderTarget rt = scene.getRenderTarget(rtName);
                for (int k = 0; k < j; k++)
                    if (rtName.equals(layerRenderTargets[k]))
                        throw new IOException("Target '" + rtName + "' is written to twice");
                if (rt == null) {
                    throw new IOException("Target '" + rtName + "' was not declared");
                } else {
                    if (rt.screenRelative != baseRenderTarget.screenRelative || rt.width != baseRenderTarget.width || rt.height != baseRenderTarget.height)
                        throw new IOException("Target '" + rtName + "' and '" + baseRenderTarget.name + "' are both written to but have different dimensions");
                }
            }
        }
    }

}

@JsonIgnoreProperties({ "$schema" })
@JsonFilter("ignores")
class JsonScene {
    @JsonProperty(required = true)
    public String version;
    @JsonProperty(required = true)
    public JsonSceneLayer[] layers;
    public Macro[] macros = new Macro[0];
    public JsonSceneAudio[] audio = new JsonSceneAudio[0];
    public SceneRenderTarget[] renderTargets = new SceneRenderTarget[0];
}


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, defaultImpl = JsonSceneStandardLayer.class, property = "pass")
@JsonSubTypes({
        @JsonSubTypes.Type(value = JsonSceneStandardLayer.class, name = "standard"),
        @JsonSubTypes.Type(value = JsonClearPass.class, name = "clear")
})
class JsonSceneLayer {
    public boolean depthTest = false;
    public boolean depthWrite = false;
    public boolean blending = true;
    public SceneLayer.RenderState.Culling culling = SceneLayer.RenderState.Culling.NONE;
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public String[] targets = new String[] { SceneRenderTarget.DEFAULT_RT.name };

    public SceneLayer.RenderState makeRenderState() {
        SceneLayer.RenderState renderState = new SceneLayer.RenderState();
        renderState.isDepthWriteEnabled = depthWrite;
        renderState.isDepthTestEnabled = depthTest;
        renderState.isBlendingEnabled = blending;
        renderState.culling = culling;
        return renderState;
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
class JsonSceneStandardLayer extends JsonSceneLayer {
    public String root = null;
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public String[] vertex = null;
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public String[] geometry = null;
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public String[] fragment = null;
    public String compute = null;
    public String model = null;
    public Macro[] macros = new Macro[0];
    public SceneUniform[] uniforms = new SceneUniform[0];
}

class JsonClearPass extends JsonSceneLayer {
    public String clearColor = "vec4(0,0,0,1)";
}

class JsonSceneAudio {
    @JsonProperty(required = true)
    public String path;
}