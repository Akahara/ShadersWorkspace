package wonder.shaderdisplay.scene;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public class SceneRenderTarget {

    @JsonProperty(required = true)
    public String name;
    public float width = 1.f;
    public float height = 1.f;
    @JsonProperty(value = "screen_relative")
    public boolean screenRelative = true; // If false, width&height are absolute dimensions
    public RenderTargetType type = RenderTargetType.TEXTURE;

    public static SceneRenderTarget DEFAULT_RT = new SceneRenderTarget();

    public boolean sizeMatch(SceneRenderTarget other) {
        return width == other.width && height == other.height && screenRelative == other.screenRelative;
    }

    static {
        DEFAULT_RT.name = "screen";
    }

    public enum RenderTargetType {
        TEXTURE,
        DEPTH;

        @JsonValue
        public String serialName() { return name().toLowerCase(); }
    }
}
