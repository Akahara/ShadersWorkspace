package wonder.shaderdisplay.uniforms;

abstract class Uniform {
	
	public final String name;
	
	public Uniform(String name) {
		this.name = name;
	}
	
	public abstract void apply(UniformApplicationContext context);
	public abstract void renderControl();
	public abstract String toUniformString();
	public abstract boolean isUserEditable();
	
}

abstract class NonEditableUniform extends Uniform {

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

abstract class EditableUniform extends Uniform {

	public EditableUniform(String name) {
		super(name);
	}

	public final boolean isUserEditable() {
		return true;
	}

}
