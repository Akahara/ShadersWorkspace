package wonder.shaderdisplay.scene;

import com.fasterxml.jackson.annotation.JsonValue;
import wonder.shaderdisplay.display.*;
import wonder.shaderdisplay.uniforms.UniformsContext;

import java.io.File;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;

public class SceneLayer {

    public final SceneType sceneType;
    public final ShaderFileSet fileSet;
    public final Macro[] macros;
    public final SceneUniform[] uniforms;
    public final RenderState renderState;
    public final String[] outRenderTargets;
    public final SceneSSBOBinding[] storageBuffers;
    public Mesh mesh;
    public ComputeDispatchCount computeDispatch;
    // only valid if sceneType is CLEAR
    public float[] clearColor;
    public float clearDepth;

    public boolean enabled = true;

    public final UniformsContext shaderUniforms = new UniformsContext(this);
    public ShaderSet compiledShaders = new ShaderSet();

    public SceneLayer(
            SceneType type,
            ShaderFileSet fileSet,
            Mesh mesh,
            Macro[] macros,
            SceneUniform[] uniforms,
            RenderState renderState,
            String[] outRenderTargets,
            SceneSSBOBinding[] storageBuffers) {
        this.sceneType = Objects.requireNonNull(type);
        this.fileSet = Objects.requireNonNull(fileSet);
        this.mesh = Objects.requireNonNull(mesh);
        this.macros = Objects.requireNonNull(macros);
        this.uniforms = Objects.requireNonNull(uniforms);
        this.renderState = Objects.requireNonNull(renderState);
        this.outRenderTargets = Objects.requireNonNull(outRenderTargets);
        this.storageBuffers = Objects.requireNonNull(storageBuffers);
    }

    public SceneLayer(
            SceneType type,
            ShaderFileSet fileSet,
            ComputeDispatchCount computeDispatch,
            Macro[] macros,
            SceneUniform[] uniforms,
            RenderState renderState,
            String[] outRenderTargets,
            SceneSSBOBinding[] storageBuffers) {
        this.sceneType = Objects.requireNonNull(type);
        this.fileSet = Objects.requireNonNull(fileSet);
        this.computeDispatch = Objects.requireNonNull(computeDispatch);
        this.macros = Objects.requireNonNull(macros);
        this.uniforms = Objects.requireNonNull(uniforms);
        this.renderState = Objects.requireNonNull(renderState);
        this.outRenderTargets = Objects.requireNonNull(outRenderTargets);
        this.storageBuffers = Objects.requireNonNull(storageBuffers);
    }

    public void dispose() {
        if (mesh != null)
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

        public BlendMode blendSrcRGB, blendSrcA, blendDstRGB, blendDstA;
        public boolean isDepthTestEnabled = true;
        public boolean isDepthWriteEnabled = true;
        public Culling culling = Culling.BACK;

        public enum Culling {
            BACK, FRONT, NONE;

            @JsonValue
            public String serialName() { return name().toLowerCase(); }
        }

        @SuppressWarnings("unused")
        public enum BlendMode {
            ZERO(GL_ZERO),
            ONE(GL_ONE),
            SRC_COLOR(GL_SRC_COLOR),
            ONE_MINUS_SRC_COLOR(GL_ONE_MINUS_SRC_COLOR),
            SRC_ALPHA(GL_SRC_ALPHA),
            ONE_MINUS_SRC_ALPHA(GL_ONE_MINUS_SRC_ALPHA),
            DST_ALPHA(GL_DST_ALPHA),
            ONE_MINUS_DST_ALPHA(GL_ONE_MINUS_DST_ALPHA);

            public final int glBlendMode;

            BlendMode(int glBlendMode) {
                this.glBlendMode = glBlendMode;
            }

            @JsonValue
            public String serialName() { return name().toLowerCase(); }
        }
    }

    public enum SceneType {
        STANDARD_PASS,
        COMPUTE_PASS,
        CLEAR_PASS,
    }

}
