package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.glUniform1f;

import wonder.shaderdisplay.Time;

class FrameUniform extends Uniform {
	
	public static final String NAME = "iFrame";
	private final int location;
	
	FrameUniform(int program) {
		super(NAME);
		this.location = new ValueLocationCache(program, NAME).getLocation(0);
	}
	
	@Override
	public void apply() {
		glUniform1f(location, Time.getFrame());
	}
	
	@Override
	public void renderControl() {
		Time.renderTimeControls();
	}
}