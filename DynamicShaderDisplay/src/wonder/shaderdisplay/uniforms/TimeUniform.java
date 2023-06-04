package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.glUniform1f;

import wonder.shaderdisplay.Time;

class TimeUniform extends Uniform {
	
	private final int location;
	
	TimeUniform(String name, int program) {
		super(name);
		this.location = new ValueLocationCache(program, name).getLocation(0);
	}
	
	@Override
	public void apply() {
		glUniform1f(location, Time.getTime());
	}
	
	@Override
	public void renderControl() {
		Time.renderTimeControls();
	}
}