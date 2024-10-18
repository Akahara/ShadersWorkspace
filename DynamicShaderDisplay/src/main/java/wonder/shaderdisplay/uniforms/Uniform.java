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

