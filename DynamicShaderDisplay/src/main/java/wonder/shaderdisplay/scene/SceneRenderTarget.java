package wonder.shaderdisplay.scene;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SceneRenderTarget {

    @JsonProperty(required = true)
    public String name;
    public float width = 1.f;
    public float height = 1.f;
    public boolean screenRelative = true; // If false, width&height are absolute dimensions

    public static SceneRenderTarget DEFAULT_RT = new SceneRenderTarget();

    public boolean sizeMatch(SceneRenderTarget other) {
        return width == other.width && height == other.height && screenRelative == other.screenRelative;
    }

    static {
        DEFAULT_RT.name = "screen";
    }
}
