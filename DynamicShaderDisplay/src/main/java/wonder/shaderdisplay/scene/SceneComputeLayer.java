package wonder.shaderdisplay.scene;

import wonder.shaderdisplay.FileWatcher;
import wonder.shaderdisplay.display.ShaderFileSet;
import wonder.shaderdisplay.display.ShaderType;
import wonder.shaderdisplay.uniforms.UniformsContext;

import java.util.Arrays;
import java.util.stream.Stream;

public class SceneComputeLayer extends SceneLayer implements CompilableLayer, RenderableLayer {

    public final ShaderFileSet fileSet;
    public final Macro[] macros;
    public final UniformDefaultValue[] uniformDefaultValues;
    public final SSBOBinding[] storageBuffers;
    public final ComputeDispatchCount computeDispatch;

    public final UniformsContext shaderUniforms = new UniformsContext(this);
    public ShaderSet compiledShaders = new ShaderSet();

    public SceneComputeLayer(String displayName, ExecutionCondition[] executions, ShaderFileSet fileSet, Macro[] macros, UniformDefaultValue[] uniforms, SSBOBinding[] storageBuffers, ComputeDispatchCount computeDispatch) {
        super(displayName, executions);
        this.fileSet = fileSet;
        this.macros = macros;
        this.uniformDefaultValues = uniforms;
        this.storageBuffers = storageBuffers;
        this.computeDispatch = computeDispatch;
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
        compiledShaders.disposeAll();
    }

    @Override
    public Stream<FileWatcher.WatchableResourceAssociation> collectResourceFiles() {
        return Arrays.stream(compiledShaders.shaderSourceFiles[ShaderType.COMPUTE.ordinal()]).map(
                f -> new FileWatcher.WatchableShaderFiles(f, this)
        );
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
        return null;
    }
}
