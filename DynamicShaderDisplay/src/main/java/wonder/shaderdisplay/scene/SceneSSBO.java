package wonder.shaderdisplay.scene;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SceneSSBO {

    @JsonProperty(required = true)
    public String name;
    @JsonProperty(required = true)
    public int size;

}
