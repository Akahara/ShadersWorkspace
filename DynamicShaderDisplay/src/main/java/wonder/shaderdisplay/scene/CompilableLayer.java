package wonder.shaderdisplay.scene;

import wonder.shaderdisplay.display.ShaderFileSet;
import wonder.shaderdisplay.uniforms.UniformsContext;

public interface CompilableLayer {

    Macro[] getCompilationMacros();
    ShaderFileSet getCompilationFileset();
    ShaderSet getCompiledShaders();
    void replaceCompiledShaders(ShaderSet shaderSet);
    UniformsContext getUniformControls();

}
