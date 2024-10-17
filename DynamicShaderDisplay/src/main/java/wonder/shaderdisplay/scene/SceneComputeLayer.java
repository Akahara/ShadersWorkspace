package wonder.shaderdisplay.scene;

import wonder.shaderdisplay.FileWatcher;
import wonder.shaderdisplay.display.ShaderFileSet;
import wonder.shaderdisplay.display.ShaderType;
import wonder.shaderdisplay.uniforms.UniformsContext;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class SceneComputeLayer extends SceneLayer implements CompilableLayer, RenderableLayer {

    public final ShaderFileSet fileSet;
    public final Macro[] macros;
    public final UniformDefaultValue[] uniformDefaultValues;
    public final SceneSSBOBinding[] storageBuffers;
    public final ComputeDispatchCount computeDispatch;

    public final UniformsContext shaderUniforms = new UniformsContext(this);
    public ShaderSet compiledShaders = new ShaderSet();

    public SceneComputeLayer(ShaderFileSet fileSet, Macro[] macros, UniformDefaultValue[] uniforms, SceneSSBOBinding[] storageBuffers, ComputeDispatchCount computeDispatch) {
        this.fileSet = fileSet;
        this.macros = macros;
        this.uniformDefaultValues = uniforms;
        this.storageBuffers = storageBuffers;
        this.computeDispatch = computeDispatch;
    }

    @Override
    public String getDisplayName() {
        return fileSet.getPrimaryFileName();
    }

    @Override
    public void renderControls() {
        shaderUniforms.renderControls();
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
