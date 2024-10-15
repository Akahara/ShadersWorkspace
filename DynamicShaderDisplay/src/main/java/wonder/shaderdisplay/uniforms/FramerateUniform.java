package wonder.shaderdisplay.uniforms;

import wonder.shaderdisplay.Time;

import static org.lwjgl.opengl.GL20.glUniform1f;

public class FramerateUniform extends NonEditableUniform {

    private final int location;

    public FramerateUniform(String name, int program) {
        super(name);
        this.location = ValueLocationCache.getLocation(program, name);
    }

    @Override
    public void apply(UniformApplicationContext context) {
        glUniform1f(location, Time.getFramerate());
    }

    @Override
    public void renderControl() {}
}
