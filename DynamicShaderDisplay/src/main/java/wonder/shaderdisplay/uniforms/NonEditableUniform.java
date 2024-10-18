package wonder.shaderdisplay.uniforms;

public abstract class NonEditableUniform extends Uniform {

    public NonEditableUniform(String name) {
        super(name);
    }

    @Override
    public final String toUniformString() {
        throw new IllegalStateException("toUniformString doesn't make sense on non editable uniforms");
    }

    @Override
    public final boolean isUserEditable() {
        return false;
    }
}
