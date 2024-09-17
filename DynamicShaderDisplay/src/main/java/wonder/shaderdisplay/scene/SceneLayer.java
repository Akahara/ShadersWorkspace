package wonder.shaderdisplay.scene;

import com.fasterxml.jackson.annotation.JsonValue;
import wonder.shaderdisplay.display.*;
import wonder.shaderdisplay.uniforms.UniformsContext;

import java.io.File;
import java.util.Objects;

public class SceneLayer {

    public final SceneType sceneType;
    public final ShaderFileSet fileSet;
    public final Macro[] macros;
    public final SceneUniform[] uniforms;
    public final RenderState renderState;
    public final String[] outRenderTargets;
    public Mesh mesh;
    // only valid if sceneType is CLEAR
    public float[] clearColor;
    public float clearDepth;

    public final UniformsContext shaderUniforms = new UniformsContext(this);
    public ShaderSet compiledShaders = new ShaderSet();

    public SceneLayer(SceneType type, ShaderFileSet fileSet, Mesh mesh, Macro[] macros, SceneUniform[] uniforms, RenderState renderState, String[] outRenderTargets) {
        this.sceneType = Objects.requireNonNull(type);
        this.fileSet = Objects.requireNonNull(fileSet);
        this.mesh = Objects.requireNonNull(mesh);
        this.macros = Objects.requireNonNull(macros);
        this.uniforms = Objects.requireNonNull(uniforms);
        this.renderState = Objects.requireNonNull(renderState);
        this.outRenderTargets = Objects.requireNonNull(outRenderTargets);
    }

    public void dispose() {
        mesh.dispose();
        compiledShaders.disposeAll();
    }

    public static class ShaderSet {
        public int[] shaderIds = new int[ShaderType.COUNT];
        public String[] resolvedSources = new String[ShaderType.COUNT];
        public File[][] shaderSourceFiles = new File[ShaderType.COUNT][];
        public int program;

        public void disposeAll() {
            ShaderCompiler.deletePrograms(program);
            ShaderCompiler.deleteShaders(shaderIds);
        }
    }

    public static class RenderState {

        public static final RenderState DEFAULT = new RenderState();

        public boolean isBlendingEnabled = true;
        public boolean isDepthTestEnabled = true;
        public boolean isDepthWriteEnabled = true;
        public Culling culling = Culling.BACK;

        public RenderState setBlending(boolean enabled) { this.isBlendingEnabled = enabled; return this; }
        public RenderState setDepthTest(boolean enabled) { this.isDepthTestEnabled = enabled; return this; }
        public RenderState setDepthWrite(boolean enabled) { this.isDepthWriteEnabled = enabled; return this; }
        public RenderState setCulling(Culling culling) { this.culling = culling; return this; }

        public enum Culling {
            BACK, FRONT, NONE;

            @JsonValue
            public String serialName() { return name().toLowerCase(); }
        }
    }

    public enum SceneType {
        STANDARD_PASS,
        CLEAR_PASS,
    }

}
