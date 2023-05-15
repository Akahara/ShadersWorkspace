package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.glUniform1f;

import wonder.shaderdisplay.Time;

class TimeUniform extends Uniform {
	
	public static final String NAME = "iTime";
	private final int location;
	
	TimeUniform(int program) {
		super(NAME);
		this.location = new ValueLocationCache(program, NAME).getLocation(0);
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