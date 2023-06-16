package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.glUniform1f;

import wonder.shaderdisplay.Time;

class TimeUniform extends Uniform {
	
	private final int location;
	
	TimeUniform(String name, int program) {
		super(name);
		this.location = ValueLocationCache.getLocation(program, name);
	}
	
	@Override
	public void apply() {
		glUniform1f(location, Time.getTime());
	}
	
	@Override
	public void renderControl() {
		Time.renderTimeControls();
	}

	@Override
	public String toUniformString() {
		return "uniform float " + name + ";";
	}
}