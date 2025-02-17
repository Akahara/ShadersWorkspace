package wonder.shaderdisplay.scene;

import wonder.shaderdisplay.FileWatcher;
import wonder.shaderdisplay.display.IndirectDrawDescription;
import wonder.shaderdisplay.display.Mesh;
import wonder.shaderdisplay.display.ShaderFileSet;
import wonder.shaderdisplay.display.ShaderType;
import wonder.shaderdisplay.uniforms.UniformsContext;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class SceneStandardLayer extends SceneLayer implements CompilableLayer, RenderableLayer {

    public final ShaderFileSet fileSet;
    public final Macro[] macros;
    public final UniformDefaultValue[] uniformDefaultValues;
    public final RenderState renderState;
    public final String[] outRenderTargets;
    public final SSBOBinding[] storageBuffers;
    public final VertexLayout vertexLayout;

    // One of the two is not null
    public Mesh mesh;
    public final IndirectDrawDescription indirectDraw;

    public final UniformsContext shaderUniforms = new UniformsContext(this);
    public ShaderSet compiledShaders = new ShaderSet();

    public SceneStandardLayer(String displayName, ExecutionCondition[] executions, ShaderFileSet fileSet, Macro[] macros, UniformDefaultValue[] uniforms, RenderState renderState, String[] outRenderTargets, SSBOBinding[] storageBuffers, VertexLayout vertexLayout, Mesh mesh) {
        super(displayName, executions);
        this.fileSet = fileSet;
        this.macros = macros;
        this.uniformDefaultValues = uniforms;
        this.renderState = renderState;
        this.outRenderTargets = outRenderTargets;
        this.storageBuffers = storageBuffers;
        this.vertexLayout = vertexLayout;
        this.mesh = mesh;
        this.indirectDraw = null;
    }

    public SceneStandardLayer(String displayName, ExecutionCondition[] executions, ShaderFileSet fileSet, Macro[] macros, UniformDefaultValue[] uniforms, RenderState renderState, String[] outRenderTargets, SSBOBinding[] storageBuffers, VertexLayout vertexLayout, IndirectDrawDescription indirectDraw) {
        super(displayName, executions);
        this.fileSet = fileSet;
        this.macros = macros;
        this.uniformDefaultValues = uniforms;
        this.renderState = renderState;
        this.outRenderTargets = outRenderTargets;
        this.storageBuffers = storageBuffers;
        this.vertexLayout = vertexLayout;
        this.indirectDraw = indirectDraw;
    }

    @Override
    public String getDisplayName() {
        return displayName == null ? fileSet.getPrimaryFileName() : displayName;
    }

    @Override
    public void renderControls(Scene scene) {
        shaderUniforms.renderControls(scene);
    }

    @Override
    public void dispose() {
        if (mesh != null) mesh.dispose();
        compiledShaders.disposeAll();
    }

    @Override
    public Stream<FileWatcher.WatchableResourceAssociation> collectResourceFiles() {
        List<FileWatcher.WatchableResourceAssociation> watches = new ArrayList<>();
        for (ShaderType type : ShaderType.TYPES) {
            if (!fileSet.hasCustomShader(type))
                continue;
            for (File file : compiledShaders.shaderSourceFiles[type.ordinal()])
                watches.add(new FileWatcher.WatchableShaderFiles(file, this));
        }
        if (mesh != null && mesh.getSourceFile() != null)
            watches.add(new FileWatcher.WatchableMeshFile(mesh.getSourceFile(), this));
        return watches.stream();
    }

    @Override
    public Macro[] getCompilationMacros() {
        return macros;
    }

    @Override
    public ShaderFileSet getCompilationFileset() {
        return fileSet;
    }

    @Override
    public ShaderSet getCompiledShaders() {
        return compiledShaders;
    }

    @Override
    public void replaceCompiledShaders(ShaderSet shaderSet) {
        compiledShaders.disposeAll();
        compiledShaders = shaderSet;
    }

    @Override
    public UniformsContext getUniformControls() {
        return shaderUniforms;
    }

    @Override
    public UniformDefaultValue[] getDefaultUniformValues() {
        return uniformDefaultValues;
    }

    @Override
    public String[] getOutputRenderTargets() {
        return outRenderTargets;
    }
}
