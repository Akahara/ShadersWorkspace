package wonder.shaderdisplay.uniforms;

abstract class Uniform {
	
	public final String name;
	
	public Uniform(String name) {
		this.name = name;
	}
	
	public abstract void apply();
	public abstract void renderControl();
	public void step(float delta) {}
	
}