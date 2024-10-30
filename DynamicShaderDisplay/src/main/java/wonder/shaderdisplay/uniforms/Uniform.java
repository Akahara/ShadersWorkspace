package wonder.shaderdisplay.uniforms;

import wonder.shaderdisplay.controls.UserControls;

public abstract class Uniform {

	public static UserControls userControls;

	public final String name;
	
	public Uniform(String name) {
		this.name = name;
	}
	
	public abstract void apply(UniformApplicationContext context);
	public abstract void renderControl();
	public abstract String toUniformString();
	public abstract boolean isUserEditable();
	
}

