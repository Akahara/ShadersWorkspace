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
import fr.wonder.commons.exceptions.ErrorWrapper;
import fr.wonder.commons.utils.ArrayOperator;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.Resources;
import wonder.shaderdisplay.display.*;
import wonder.shaderdisplay.entry.BadInitException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
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

        ErrorWrapper errors = new ErrorWrapper("Could not regenerate the scene");

        Scene scene = new Scene(file);
        scene.macros.addAll(Arrays.asList(serialized.macros));
        scene.renderTargets.add(SceneRenderTarget.DEFAULT_RT);
        scene.renderTargets.addAll(Arrays.asList(serialized.renderTargets));
        ShaderCompiler compiler = new ShaderCompiler(scene);
        boolean tryToReloadPreviousScene = previousScene != null && previousScene.layers.size() == serialized.layers.length; // TODO keep uniform states after a reload

        validateSceneRenderTargets(errors, serialized.renderTargets);

        for (int i = 0; i < serialized.layers.length; i++) {
            JsonSceneLayer serializedLayerBase = serialized.layers[i];
            ErrorWrapper layerErrors = errors.subErrors("Layer " + i);
            try {
                if (serializedLayerBase instanceof JsonSceneStandardLayer serializedLayer) {
                    scene.layers.add(parseStandardLayer(layerErrors, compiler, file, scene, serializedLayer));
                } else if (serializedLayerBase instanceof JsonClearPass pass) {
                    scene.layers.addAll(makeClearLayers(layerErrors, scene.renderTargets, pass));
                } else if (serializedLayerBase instanceof JsonBlitPass pass) {
                    scene.layers.addAll(makeBlitLayers(layerErrors, scene.renderTargets, pass));
                } else {
                    errors.add("Pass doesn't have a layer implementation? : " + serializedLayerBase.getClass().getName());
                }
                errors.assertNoErrors();
            } catch (ErrorWrapper.WrappedException x) {
            } catch (IOException | IllegalArgumentException | IllegalStateException e) {
                errors.add(String.format("Layer %d: %s", i, e.getMessage()));
            }
        }

        if (!errors.noErrors()) {
            scene.dispose();
            errors.dump(Main.logger);
            return previousScene;
        }

        return scene;
    }

    private static SceneLayer parseStandardLayer(ErrorWrapper errors, ShaderCompiler compiler, File rootFile, Scene scene, JsonSceneStandardLayer serializedLayer) throws IOException, ErrorWrapper.WrappedException {
        SceneLayer layer = new SceneLayer(
            new ShaderFileSet()
                .setFile(ShaderType.VERTEX, asOptionalPath(rootFile, serializedLayer.root, serializedLayer.vertex))
                .setFile(ShaderType.GEOMETRY, asOptionalPath(rootFile, serializedLayer.root, serializedLayer.geometry))
                .setFile(ShaderType.FRAGMENT, asOptionalPath(rootFile, serializedLayer.root, serializedLayer.fragment))
                .setFile(ShaderType.COMPUTE, asOptionalPath(rootFile, serializedLayer.root, serializedLayer.compute))
                .completeWithDefaultSources(),
            loadMesh(rootFile, serializedLayer.root, serializedLayer.model),
            serializedLayer.macros,
            serializedLayer.uniforms,
            serializedLayer.makeRenderState(),
            serializedLayer.targets
        );

        validateLayerRenderTargets(scene, serializedLayer.targets);

        ShaderCompiler.ShaderCompilationResult result = compiler.compileShaders(errors, layer);
        result.errors.assertNoErrors();
        return layer;
    }

    private static List<List<SceneRenderTarget>> groupRenderTargets(List<SceneRenderTarget> targets, String[] pickedTargets) throws IllegalArgumentException {
        List<List<SceneRenderTarget>> renderTargetSets = new ArrayList<>();

        for (String pickedTarget : pickedTargets) {
            SceneRenderTarget target = targets.stream()
                    .filter(t -> t.name.equals(pickedTarget))
                    .findFirst()
                    .orElse(null);
            if (target == null)
                throw new IllegalArgumentException("Cannot clear undefined render target '" + pickedTarget + "'");

            List<SceneRenderTarget> matchingSet = renderTargetSets.stream().filter(s -> s.get(0).sizeMatch(target)).findFirst().orElse(null);
            if (matchingSet != null)
                matchingSet.add(target);
            else
                renderTargetSets.add(new ArrayList<>(List.of(target)));
        }

        return renderTargetSets;
    }

    private static List<SceneLayer> makeClearLayers(ErrorWrapper errors, List<SceneRenderTarget> existingRenderTargets, JsonClearPass pass) {
        return groupRenderTargets(existingRenderTargets, pass.targets).stream()
            .map(set -> makeClearLayer(errors, set.stream().map(rt -> rt.name).toArray(String[]::new), pass.clearColor))
            .collect(Collectors.toList());
    }

    public static SceneLayer makeClearLayer(ErrorWrapper errors, String[] outputTargets, String clearColor) {
        Stream<Macro> macros = Stream.empty();
        macros = Stream.concat(macros, IntStream.range(0, outputTargets.length).mapToObj(i -> new Macro("CLEAR_TARGET_"+i)));
        macros = Stream.concat(macros, Stream.of(new Macro("CLEAR_COLOR", clearColor)));

        SceneLayer layer = new SceneLayer(
            SceneLayer.BuiltinSceneLayerAddon.CLEAR_PASS,
            new ShaderFileSet()
                .setFixedPrimarySourceName("clear_pass")
                .setRawSource(ShaderType.FRAGMENT, Resources.readResource("/passes/clear.fs"))
                .setRawSource(ShaderType.VERTEX, Resources.readResource("/passes/passthrough.vs")),
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

        new ShaderCompiler(null).compileShaders(errors, layer);

        return layer;
    }

    private static List<SceneLayer> makeBlitLayers(ErrorWrapper errors, List<SceneRenderTarget> renderTargets, JsonBlitPass pass) {
        return groupRenderTargets(renderTargets, pass.targets).stream()
                .map(set -> makeBlitLayer(errors, set.stream().map(rt -> rt.name).toArray(String[]::new), pass.source))
                .collect(Collectors.toList());
    }

    private static SceneLayer makeBlitLayer(ErrorWrapper errors, String[] renderTargets, String source) {
        Stream<Macro> macros = IntStream.range(0, renderTargets.length).mapToObj(i -> new Macro("BLIT_TARGET_"+i));

        SceneLayer layer = new SceneLayer(
            SceneLayer.BuiltinSceneLayerAddon.CLEAR_PASS,
            new ShaderFileSet()
                .setFixedPrimarySourceName("blit_pass")
                .setRawSource(ShaderType.FRAGMENT, Resources.readResource("/passes/blit.fs"))
                .setRawSource(ShaderType.VERTEX, Resources.readResource("/passes/passthrough.vs")),
            Mesh.fullscreenTriangle(),
            macros.toArray(Macro[]::new),
            new SceneUniform[] { new SceneUniform("u_source", source) },
            new SceneLayer.RenderState()
                .setBlending(false)
                .setCulling(SceneLayer.RenderState.Culling.NONE)
                .setDepthTest(false)
                .setDepthWrite(false),
            renderTargets
        );

        new ShaderCompiler(null).compileShaders(errors, layer);

        return layer;
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

    private static void validateSceneRenderTargets(ErrorWrapper errors, SceneRenderTarget[] renderTargets) {
        for (int i = 0; i < renderTargets.length; i++) {
            SceneRenderTarget rt = renderTargets[i];
            for (int j = 0; j < i; j++) {
                if (renderTargets[j].name.equals(rt.name))
                    errors.add("Render target '" + rt.name + "' specified twice");
            }
            if (rt.width < 0 || rt.height < 0)
                errors.add("Invalid size for render target '" + rt.name + "'");
            if (!rt.screenRelative && (rt.width < 1.f || rt.height < 1.f || (int)rt.width != rt.width || (int)rt.height != rt.height))
                errors.add("Invalid size for render target '" + rt.name + "', for non-screen relative RTs only absolute dimensions are valid");
            if (rt.screenRelative && (rt.width > 20 || rt.height > 20))
                errors.add("Very large render target '" + rt.name + "', did you forget 'screenRelative':false ?");
        }
    }

    private static void validateLayerRenderTargets(Scene scene, String[] layerRenderTargets) {
        if (layerRenderTargets.length == 0) {
            throw new IllegalArgumentException("No output render target specified");
        } else {
            SceneRenderTarget baseRenderTarget = scene.getRenderTarget(layerRenderTargets[0]);
            for (int j = 1; j < layerRenderTargets.length; j++) {
                String rtName = layerRenderTargets[j];
                SceneRenderTarget rt = scene.getRenderTarget(rtName);
                for (int k = 0; k < j; k++)
                    if (rtName.equals(layerRenderTargets[k]))
                        throw new IllegalArgumentException("Target '" + rtName + "' is written to twice");
                if (rt == null) {
                    throw new IllegalArgumentException("Target '" + rtName + "' was not declared");
                } else {
                    if (rt.screenRelative != baseRenderTarget.screenRelative || rt.width != baseRenderTarget.width || rt.height != baseRenderTarget.height)
                        throw new IllegalArgumentException("Target '" + rtName + "' and '" + baseRenderTarget.name + "' are both written to but have different dimensions");
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
    public SceneRenderTarget[] renderTargets = new SceneRenderTarget[0];
}


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, defaultImpl = JsonSceneStandardLayer.class, property = "pass")
@JsonSubTypes({
        @JsonSubTypes.Type(value = JsonSceneStandardLayer.class, name = "standard"),
        @JsonSubTypes.Type(value = JsonClearPass.class, name = "clear"),
        @JsonSubTypes.Type(value = JsonBlitPass.class, name = "blit"),
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
    public String vertex = null;
    public String geometry = null;
    public String fragment = null;
    public String compute = null;
    public String model = null;
    public Macro[] macros = new Macro[0];
    public SceneUniform[] uniforms = new SceneUniform[0];
}

class JsonClearPass extends JsonSceneLayer {
    public String clearColor = "vec4(0,0,0,1)";
}

class JsonBlitPass extends JsonSceneLayer {
    @JsonProperty(required = true)
    public String source;
}
