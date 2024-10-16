package wonder.shaderdisplay.scene;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SceneSSBOBinding {

    @JsonProperty(required = true)
    public String name;
    public int offset = -1;
    public int size = -1;

}
