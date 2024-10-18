package wonder.shaderdisplay.uniforms;

public abstract class EditableUniform extends Uniform {

    public EditableUniform(String name) {
        super(name);
    }

    public final boolean isUserEditable() {
        return true;
    }

}
