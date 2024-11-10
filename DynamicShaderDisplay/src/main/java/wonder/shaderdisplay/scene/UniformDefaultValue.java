package wonder.shaderdisplay.scene;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UniformDefaultValue {

    // FIXME Currently only texture uniforms that bind to render targets are supported

    @JsonProperty(required = true)
    public String name;
    @JsonProperty(required = true)
    public String value;

    public static UniformDefaultValue withValue(String name, String value) {
        UniformDefaultValue v = new UniformDefaultValue();
        v.name = name;
        v.value = value;
        return v;
    }

}
