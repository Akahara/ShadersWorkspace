package wonder.shaderdisplay.scene;

import wonder.shaderdisplay.display.ShaderCompiler;
import wonder.shaderdisplay.display.ShaderType;

import java.io.File;

public class ShaderSet {
    public int[] shaderIds = new int[ShaderType.COUNT];
    public String[] resolvedSources = new String[ShaderType.COUNT];
    public File[][] shaderSourceFiles = new File[ShaderType.COUNT][];
    public int program;

    public void disposeAll() {
        ShaderCompiler.deletePrograms(program);
        ShaderCompiler.deleteShaders(shaderIds);
    }

}
