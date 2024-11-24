package wonder.shaderdisplay.scene;

import com.fasterxml.jackson.annotation.*;
import fr.wonder.commons.exceptions.ErrorWrapper;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.serial.JsonUtils;
import wonder.shaderdisplay.serial.Resources;
import wonder.shaderdisplay.display.*;
import wonder.shaderdisplay.entry.BadInitException;
import wonder.shaderdisplay.scene.RenderState.BlendMode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class SceneParser {

    public static Scene loadScene(File file) throws BadInitException {
        Scene scene = regenerateScene(file, null);
        if (scene == null)
            throw new BadInitException("Could not load the scene");
        return scene;
    }

    public static Scene regenerateScene(File file, Scene previousScene) {
        JsonScene serialized;
        try {
            serialized = JsonUtils.JSON_MAPPER.readValue(file, JsonScene.class);
        } catch (IOException e) {
            Main.logger.err("Could not parse the scene file: " + e.getMessage());
            return previousScene;
        }

        ErrorWrapper errors = new ErrorWrapper("Could not regenerate the scene");

        boolean tryToReloadPreviousScene = previousScene != null && previousScene.layers.size() == serialized.layers.length; // TODO keep uniform states & buffers after a reload

        Scene scene = new Scene(file);
        scene.macros.addAll(Arrays.asList(serialized.macros));
        scene.renderTargets.add(SceneRenderTarget.DEFAULT_RT);
        scene.renderTargets.addAll(Arrays.asList(serialized.renderTargets));
        scene.sharedUniforms = new SharedUniforms(serialized.sharedUniforms);
        for (SceneSSBO ssbo : serialized.storageBuffers)
            scene.storageBuffers.put(ssbo.name, new StorageBuffer(ssbo.size));

        ShaderCompiler compiler = new ShaderCompiler(scene);

        validateSceneRenderTargets(errors, serialized.renderTargets);

        for (int i = 0; i < serialized.layers.length; i++) {
            JsonSceneLayer serializedLayerBase = serialized.layers[i];
            ErrorWrapper layerErrors = errors.subErrors("Layer " + i);
            try {
                if (serializedLayerBase instanceof JsonSceneStandardLayer serializedLayer) {
                    scene.layers.add(parseStandardLayer(layerErrors, compiler, file, scene, serializedLayer));
                } else if (serializedLayerBase instanceof JsonComputePass pass) {
                    scene.layers.add(parseComputeLayer(layerErrors, compiler, file, scene, pass));
                } else if (serializedLayerBase instanceof JsonClearPass pass) {
                    scene.layers.add(makeClearLayer(layerErrors, pass.displayName, pass.executions, pass.targets, pass.clearColor, pass.clearDepth));
                } else if (serializedLayerBase instanceof JsonBlitPass pass) {
                    scene.layers.addAll(makeBlitLayers(layerErrors, pass.displayName, pass.executions, scene, pass)); // TODO make a blit layer primitive
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

        if (previousScene != null)
            previousScene.dispose();
        return scene;
    }

    private static SceneLayer parseStandardLayer(ErrorWrapper errors, ShaderCompiler compiler, File rootFile, Scene scene, JsonSceneStandardLayer serializedLayer) throws IOException, ErrorWrapper.WrappedException {
        ShaderFileSet fileSet = new ShaderFileSet()
            .setFile(ShaderType.VERTEX, asOptionalPath(rootFile, serializedLayer.root, serializedLayer.vertex))
            .setFile(ShaderType.GEOMETRY, asOptionalPath(rootFile, serializedLayer.root, serializedLayer.geometry))
            .setFile(ShaderType.FRAGMENT, asOptionalPath(rootFile, serializedLayer.root, serializedLayer.fragment))
            .completeWithDefaultSources()
            .createMissingFilesFromTemplate();

        SceneStandardLayer layer;
        if (serializedLayer.indirectDraw != null) {
            if (serializedLayer.model != null)
                errors.add("Cannot have both a model and an indirect draw call");
            JsonIndirectDrawDescription draw = serializedLayer.indirectDraw;
            IndirectDrawDescription indirectDraw = new IndirectDrawDescription(
                new SSBOBinding(draw.indirectArgsBufferName, draw.indirectArgsOffset),
                draw.vertexBufferName,
                draw.indexBufferName,
                draw.indirectCallCount,
                draw.topology
            );
            validateLayerStorageBuffers(errors, scene.storageBuffers,
                new SSBOBinding[] { indirectDraw.indirectArgsBuffer },
                new String[] { draw.indexBufferName, draw.vertexBufferName });
            layer = new SceneStandardLayer(
                serializedLayer.displayName,
                serializedLayer.executions,
                fileSet,
                serializedLayer.macros,
                serializedLayer.uniforms,
                serializedLayer.makeRenderState(errors),
                serializedLayer.targets,
                serializedLayer.storageBuffers,
                indirectDraw
            );
        } else {
            Mesh mesh = loadMesh(rootFile, serializedLayer.root, serializedLayer.model);
            layer = new SceneStandardLayer(
                serializedLayer.displayName,
                serializedLayer.executions,
                fileSet,
                serializedLayer.macros,
                serializedLayer.uniforms,
                serializedLayer.makeRenderState(errors),
                serializedLayer.targets,
                serializedLayer.storageBuffers,
                mesh
            );
        }

        validateLayerRenderTargets(errors, scene, serializedLayer.targets, layer.renderState);
        validateLayerStorageBuffers(errors, scene.storageBuffers, layer.storageBuffers, null);
        compiler.compileShaders(errors, layer);
        return layer;
    }

    private static SceneLayer parseComputeLayer(ErrorWrapper errors, ShaderCompiler compiler, File rootFile, Scene scene, JsonComputePass serializedLayer) throws ErrorWrapper.WrappedException {
        if (serializedLayer.dispatch.length != 3) {
            errors.add("Invalid dispatch count, expected [x,y,z]");
            serializedLayer.dispatch = new int[] { 1,1,1 };
        } else if (Arrays.stream(serializedLayer.dispatch).anyMatch(n -> (n <= 0))) {
            errors.add("Invalid dispatch count, all count must be >=1");
        }

        SceneComputeLayer layer = new SceneComputeLayer(
            serializedLayer.displayName,
            serializedLayer.executions,
            new ShaderFileSet()
                    .setFile(ShaderType.COMPUTE, asOptionalPath(rootFile, serializedLayer.root, serializedLayer.compute))
                    .completeWithDefaultSources(),
            serializedLayer.macros,
            serializedLayer.uniforms,
            serializedLayer.storageBuffers,
            new ComputeDispatchCount(serializedLayer.dispatch)
        );

        layer.fileSet.createMissingFilesFromTemplate();
        validateLayerStorageBuffers(errors, scene.storageBuffers, layer.storageBuffers, null);
        compiler.compileShaders(errors, layer);
        return layer;
    }

    private static List<List<SceneRenderTarget>> groupRenderTargetsBySize(List<SceneRenderTarget> targets, String[] pickedTargets) throws IllegalArgumentException {
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

    public static SceneClearLayer makeClearLayer(ErrorWrapper errors, String displayName, ExecutionCondition[] executions, String[] renderTargets, float[] clearColor, float clearDepth) {
        if (clearColor.length == 3)
            clearColor = new float[] { clearColor[0], clearColor[1], clearColor[2], 1 };
        if (clearColor.length != 4) {
            errors.add("Invalid clear color, expected rgb or rgba");
            clearColor = new float[4];
        }

        return new SceneClearLayer(
            displayName,
            executions,
            renderTargets,
            clearColor,
            clearDepth
        );
    }

    private static List<SceneLayer> makeBlitLayers(ErrorWrapper errors, String displayName, ExecutionCondition[] executions, Scene scene, JsonBlitPass pass) {
        return groupRenderTargetsBySize(scene.renderTargets, pass.targets).stream()
                .map(set -> makeBlitLayer(errors, displayName, executions, scene, set.stream().map(rt -> rt.name).toArray(String[]::new), pass))
                .collect(Collectors.toList());
    }

    private static SceneLayer makeBlitLayer(ErrorWrapper errors, String displayName, ExecutionCondition[] executions, Scene scene, String[] renderTargets, JsonBlitPass pass) {
        Stream<Macro> macros = IntStream.range(0, renderTargets.length).mapToObj(i -> new Macro("BLIT_TARGET_"+i));

        // TODO blit depth textures

        SceneStandardLayer layer = new SceneStandardLayer(
            displayName,
            executions,
            new ShaderFileSet()
                .setFixedPrimarySourceName("blit_pass")
                .setRawSource(ShaderType.FRAGMENT, Resources.readResource("/passes/blit.fs"))
                .setRawSource(ShaderType.VERTEX, Resources.readResource("/passes/passthrough.vs")),
            macros.toArray(Macro[]::new),
            new UniformDefaultValue[] { UniformDefaultValue.withValue("u_source", pass.source) },
            pass.makeRenderState(errors),
            renderTargets,
            new SSBOBinding[0],
            Mesh.makeFullscreenTriangleMesh()
        );

        new ShaderCompiler(null).compileShaders(errors, layer);
        validateLayerRenderTargets(errors, scene, renderTargets, layer.renderState);

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
            return Mesh.makeFullscreenTriangleMesh();
        switch (nameOrPath) {
        case "fullscreen":
            return Mesh.makeFullscreenTriangleMesh();
        case "line":
            return Mesh.makeLineMesh();
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

    private static void validateLayerRenderTargets(ErrorWrapper errors, Scene scene, String[] layerRenderTargets, RenderState rs) {
        String foundDepthTarget = null;
        if (layerRenderTargets.length == 0) {
            errors.add("No output render target specified");
            return;
        }

        SceneRenderTarget baseRenderTarget = scene.getRenderTarget(layerRenderTargets[0]);
        for (int j = 0; j < layerRenderTargets.length; j++) {
            String rtName = layerRenderTargets[j];
            SceneRenderTarget rt = scene.getRenderTarget(rtName);

            for (int k = 0; k < j; k++)
                if (rtName.equals(layerRenderTargets[k]))
                    errors.add("Target '" + rtName + "' is written to twice");
            if (rt == null) {
                errors.add("Target '" + rtName + "' was not declared");
                continue;
            }
            if (rt.type == SceneRenderTarget.RenderTargetType.DEPTH) {
                if (foundDepthTarget != null)
                    errors.add("Cannot have multiple depth targets '" + rtName + "' and '" + foundDepthTarget + "'");
                foundDepthTarget = rtName;
            }
            if (rt.screenRelative != baseRenderTarget.screenRelative || rt.width != baseRenderTarget.width || rt.height != baseRenderTarget.height)
                errors.add("Target '" + rtName + "' and '" + baseRenderTarget.name + "' are both written to but have different dimensions");
        }

        if (foundDepthTarget == null && (rs.isDepthTestEnabled || rs.isDepthWriteEnabled))
            errors.add("Depth test/write enabled without a depth render target");
    }

    private static void validateLayerStorageBuffers(ErrorWrapper errors, Map<String, StorageBuffer> availableStorageBuffers, SSBOBinding[] usedStorageBuffers, String[] usedUnmappedStorageBuffers) {
        if (usedStorageBuffers != null) {
            for (SSBOBinding buf : usedStorageBuffers) {
                if (buf == null)
                    continue;
                StorageBuffer ssbo = availableStorageBuffers.get(buf.name);
                if (ssbo == null)
                    errors.add("Using unknown storage buffer '%s'".formatted(buf.name));
                else if (buf.offset >= 0 && buf.offset > ssbo.getSizeInBytes())
                    errors.add("Storage buffer '%s' is bound from offset %d but has size %d".formatted(buf.name, buf.offset, ssbo.getSizeInBytes()));
            }
        }
        if (usedUnmappedStorageBuffers != null) {
            for (String buf : usedUnmappedStorageBuffers) {
                if (buf == null)
                    continue;
                StorageBuffer ssbo = availableStorageBuffers.get(buf);
                if (ssbo == null)
                    errors.add("Using unknown storage buffer '%s'".formatted(buf));
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
    @JsonProperty(value = "storage_buffers")
    public SceneSSBO[] storageBuffers = new SceneSSBO[0];
    @JsonProperty(value = "render_targets")
    public SceneRenderTarget[] renderTargets = new SceneRenderTarget[0];
    @JsonProperty(value = "shared_uniforms")
    public String[] sharedUniforms = new String[0];
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, defaultImpl = JsonSceneStandardLayer.class, property = "pass")
@JsonSubTypes({
        @JsonSubTypes.Type(value = JsonSceneStandardLayer.class, name = "standard"),
        @JsonSubTypes.Type(value = JsonClearPass.class, name = "clear"),
        @JsonSubTypes.Type(value = JsonBlitPass.class, name = "blit"),
        @JsonSubTypes.Type(value = JsonComputePass.class, name = "compute"),
})
class JsonSceneLayer {
    @JsonProperty(value = "depth_test")
    public boolean depthTest = false;
    @JsonProperty(value = "depth_write")
    public boolean depthWrite = false;
    @JsonProperty(value = "blend_factors")
    public BlendMode[] blendFactors = null;
    public BlendModeTemplate blending = null;
    public RenderState.Culling culling = RenderState.Culling.NONE;
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public String[] targets = new String[] { SceneRenderTarget.DEFAULT_RT.name };
    public ExecutionCondition[] executions = { ExecutionCondition.alwaysPassing() };
    @JsonProperty(value = "display_name")
    public String displayName = null;

    public RenderState makeRenderState(ErrorWrapper errors) {
        RenderState renderState = new RenderState();
        renderState.isDepthWriteEnabled = depthWrite;
        renderState.isDepthTestEnabled = depthTest;
        renderState.culling = culling;

        if (blending != null && blendFactors != null)
            errors.add("The blend state cannot be specified by both 'blending' and 'blendFactors'");
        if (blending != null) {
            switch (blending) {
                case NONE -> {}
                case ADDITIVE -> {
                    renderState.blendSrcRGB = BlendMode.ONE;
                    renderState.blendDstRGB = BlendMode.ONE;
                    renderState.blendSrcA   = BlendMode.ONE;
                    renderState.blendDstA   = BlendMode.ONE;
                }
                case ALPHA -> {
                    renderState.blendSrcRGB = BlendMode.SRC_ALPHA;
                    renderState.blendDstRGB = BlendMode.ONE_MINUS_SRC_ALPHA;
                    renderState.blendSrcA   = BlendMode.ONE;
                    renderState.blendDstA   = BlendMode.ONE_MINUS_SRC_ALPHA;
                }
            }
        } else if (blendFactors != null && blendFactors.length != 0) {
            if (blendFactors.length != 1 && blendFactors.length != 2 && blendFactors.length != 4)
                errors.add("'blendFactors' should be [coef] or [coefRGB, coefA] or [srcRGB, dstRGB, srcA, dstA]");
            renderState.blendSrcRGB = blendFactors[0];
            renderState.blendDstRGB = blendFactors.length >= 2 ? blendFactors[1] : blendFactors[0];
            renderState.blendSrcA = blendFactors.length >= 4 ? blendFactors[2] : blendFactors[0];
            renderState.blendDstA = blendFactors.length >= 4 ? blendFactors[3] : (blendFactors.length >= 2 ? blendFactors[1] : blendFactors[0]);
        }
        return renderState;
    }

    enum BlendModeTemplate {
        NONE, ADDITIVE, ALPHA;

        @JsonValue
        public String serialName() { return name().toLowerCase(); }
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
class JsonSceneStandardLayer extends JsonSceneLayer {
    public String root = null;
    public String vertex = null;
    public String geometry = null;
    @JsonProperty(required = true)
    public String fragment = null;
    public String model = null;
    @JsonProperty(value = "indirect_draw")
    public JsonIndirectDrawDescription indirectDraw = null;
    public Macro[] macros = new Macro[0];
    public UniformDefaultValue[] uniforms = new UniformDefaultValue[0];
    @JsonProperty(value = "storage_buffers")
    public SSBOBinding[] storageBuffers = new SSBOBinding[0];
}

class JsonIndirectDrawDescription {
    @JsonProperty(required = true, value = "indirect_args_buffer")
    public String indirectArgsBufferName;
    @JsonProperty(value = "indirect_args_offset")
    public int indirectArgsOffset = 0;
    @JsonProperty(value = "vertex_buffer")
    public String vertexBufferName = null;
    @JsonProperty(value = "index_buffer")
    public String indexBufferName = null;
    @JsonProperty(required = true, value = "indirect_call_count")
    public int indirectCallCount;
    public Mesh.Topology topology = Mesh.Topology.TRIANGLE_LIST;
}

class JsonComputePass extends JsonSceneLayer {
    public String root = null;
    @JsonProperty(required = true)
    public String compute = null;
    public int[] dispatch = { 1, 1, 1 };
    public Macro[] macros = new Macro[0];
    public UniformDefaultValue[] uniforms = new UniformDefaultValue[0];
    @JsonProperty(value = "storage_buffers")
    public SSBOBinding[] storageBuffers = new SSBOBinding[0];
}

class JsonClearPass extends JsonSceneLayer {
    @JsonProperty(value = "clear_color")
    public float[] clearColor = { 0,0,0,1 };
    @JsonProperty(value = "clear_depth")
    public float clearDepth = 1.f;
}

class JsonBlitPass extends JsonSceneLayer {
    @JsonProperty(required = true)
    public String source;
}
