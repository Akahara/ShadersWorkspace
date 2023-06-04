package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.glUniform1i;

import wonder.shaderdisplay.Time;

class FrameUniform extends Uniform {
	
	private final int location;
	
	FrameUniform(String name, int program) {
		super(name);
		this.location = new ValueLocationCache(program, name).getLocation(0);
	}
	
	@Override
	public void apply() {
		glUniform1i(location, Time.getFrame());
	}
	
	@Override
	public void renderControl() {
		Time.renderTimeControls();
	}

	@Override
	public String toUniformString() {
		return "uniform int " + name + ";";
	}
}