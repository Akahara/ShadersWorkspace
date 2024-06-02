package wonder.shaderdisplay.scene;

import wonder.shaderdisplay.display.Mesh;
import wonder.shaderdisplay.display.ShaderFileSet;
import wonder.shaderdisplay.renderers.Renderer;
import wonder.shaderdisplay.uniforms.UniformsContext;

import java.util.Objects;

public class SceneLayer {

    public final ShaderFileSet fileSet;
    public final Mesh mesh;
    public final Macro[] macros;
    public final UniformsContext shaderUniforms = new UniformsContext();

    public final ShaderSet compiledShaders = new ShaderSet();

    public SceneLayer(ShaderFileSet fileSet, Mesh mesh, Macro[] macros) {
        this.fileSet = Objects.requireNonNull(fileSet);
        this.mesh = Objects.requireNonNull(mesh);
        this.macros = Objects.requireNonNull(macros);
    }

    public void dispose() {
        mesh.dispose();
        Renderer.deletePrograms(compiledShaders.program);
        Renderer.deleteShaders(compiledShaders.vertex, compiledShaders.geometry, compiledShaders.fragment, compiledShaders.compute);
    }

    public static class ShaderSet {
        public int vertex;
        public int geometry;
        public int fragment;
        public int compute;
        public int program;
    }

}
