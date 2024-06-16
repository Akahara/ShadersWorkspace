package wonder.shaderdisplay.scene;

import com.fasterxml.jackson.annotation.JsonValue;
import wonder.shaderdisplay.display.Mesh;
import wonder.shaderdisplay.display.ShaderFileSet;
import wonder.shaderdisplay.display.Renderer;
import wonder.shaderdisplay.uniforms.UniformsContext;

import java.util.Objects;

public class SceneLayer {

    public final ShaderFileSet fileSet;
    public final Macro[] macros;
    public final RenderState renderState;
    public Mesh mesh;

    public final UniformsContext shaderUniforms = new UniformsContext();
    public final ShaderSet compiledShaders = new ShaderSet();

    public SceneLayer(ShaderFileSet fileSet, Mesh mesh, Macro[] macros, RenderState renderState) {
        this.fileSet = Objects.requireNonNull(fileSet);
        this.mesh = Objects.requireNonNull(mesh);
        this.macros = Objects.requireNonNull(macros);
        this.renderState = Objects.requireNonNull(renderState);
    }

    public void dispose() {
        mesh.dispose();
        compiledShaders.disposeAll();
    }

    public static class ShaderSet {
        public int[] vertex;
        public int[] geometry;
        public int[] fragment;
        public int compute;
        public int program;

        public void disposeAll() {
            Renderer.deletePrograms(program);
            Renderer.deleteShaders(vertex);
            Renderer.deleteShaders(geometry);
            Renderer.deleteShaders(fragment);
        }
    }

    public static class RenderState {

        public static final RenderState DEFAULT = new RenderState();

        public boolean isBlendingEnabled = true;
        public boolean isDepthTestEnabled = true;
        public boolean isDepthWriteEnabled = true;
        public Culling culling = Culling.BACK;

        public enum Culling {
            BACK, FRONT, NONE;

            @JsonValue
            public String serialName() { return name().toLowerCase(); }
        }
    }

}
