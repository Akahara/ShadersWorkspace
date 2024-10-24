package wonder.shaderdisplay.scene;

import wonder.shaderdisplay.FileWatcher;
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
    public final SceneSSBOBinding[] storageBuffers;
    public Mesh mesh;

    public final UniformsContext shaderUniforms = new UniformsContext(this);
    public ShaderSet compiledShaders = new ShaderSet();

    public SceneStandardLayer(ShaderFileSet fileSet, Macro[] macros, UniformDefaultValue[] uniforms, RenderState renderState, String[] outRenderTargets, SceneSSBOBinding[] storageBuffers, Mesh mesh) {
        this.fileSet = fileSet;
        this.macros = macros;
        this.uniformDefaultValues = uniforms;
        this.renderState = renderState;
        this.outRenderTargets = outRenderTargets;
        this.storageBuffers = storageBuffers;
        this.mesh = mesh;
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
        mesh.dispose();
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
        if (mesh.getSourceFile() != null)
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
