package wonder.shaderdisplay.uniforms.predefined;

import static org.lwjgl.opengl.GL20.glUniform1f;

import wonder.shaderdisplay.Time;
import wonder.shaderdisplay.controls.Timeline;
import wonder.shaderdisplay.uniforms.NonEditableUniform;
import wonder.shaderdisplay.uniforms.UniformApplicationContext;
import wonder.shaderdisplay.uniforms.ValueLocationCache;

public class TimeUniform extends NonEditableUniform {
	
	private final int location;

	public TimeUniform(String name, int program) {
		super(name);
		this.location = ValueLocationCache.getLocation(program, name);
	}
	
	@Override
	public void apply(UniformApplicationContext context) {
		glUniform1f(location, Time.getTime());
	}
	
	@Override
	public void renderControl() {
		Timeline.renderTimeControls();
	}

}