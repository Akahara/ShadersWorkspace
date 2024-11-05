package wonder.shaderdisplay.scene;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SSBOBinding {

    @JsonProperty(required = true)
    public String name;
    public int offset = -1;

    public SSBOBinding() {}
    public SSBOBinding(String name, int offset) {
        this.name = name;
        this.offset = offset;
    }

}
