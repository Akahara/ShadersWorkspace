package wonder.shaderdisplay.scene;

public class UniformDefaultValue {

    // FIXME Currently only texture uniforms that bind to render targets are supported

    public String name;
    public String value;

    public UniformDefaultValue() {}
    public UniformDefaultValue(String name, String value) {
        this.name = name;
        this.value = value;
    }

}
