package wonder.shaderdisplay.scene;

public class SceneUniform {

    // FIXME Currently only texture uniforms that bind to render targets are supported

    public String name;
    public String value;

    public SceneUniform() {}
    public SceneUniform(String name, String value) {
        this.name = name;
        this.value = value;
    }

}
