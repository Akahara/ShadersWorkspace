package wonder.shaderdisplay.scene;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.lwjgl.opengl.GL11.*;

public class RenderState {

    public static final RenderState DEFAULT = new RenderState();

    public BlendMode blendSrcRGB, blendSrcA, blendDstRGB, blendDstA;
    public boolean isDepthTestEnabled = true;
    public boolean isDepthWriteEnabled = true;
    public Culling culling = Culling.BACK;
    public Topology topology = Topology.TRIANGLES;
    public int tessellationPatchSize = -1;

    public enum Culling {
        BACK, FRONT, NONE;

        @JsonValue
        public String serialName() {
            return name().toLowerCase();
        }
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
        public String serialName() {
            return name().toLowerCase();
        }
    }

    public enum Topology {
        TRIANGLES,
        LINES;

        @JsonValue
        public String serialName() {
            return name().toLowerCase();
        }
    }
}
