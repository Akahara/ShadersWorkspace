package wonder.shaderdisplay.uniforms.predefined;

import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;

import wonder.shaderdisplay.Time;
import wonder.shaderdisplay.controls.Timeline;
import wonder.shaderdisplay.uniforms.NonEditableUniform;
import wonder.shaderdisplay.uniforms.UniformApplicationContext;
import wonder.shaderdisplay.uniforms.ValueLocationCache;

public class FrameUniform extends NonEditableUniform {
	
	private final int location;
	private final boolean isFloat;

	public FrameUniform(String name, int program, boolean isFloat) {
		super(name);
		this.location = ValueLocationCache.getLocation(program, name);
		this.isFloat = isFloat;
	}
	
	@Override
	public void apply(UniformApplicationContext context) {
		if(isFloat)
			glUniform1f(location, Time.getFrame());
		else
			glUniform1i(location, Time.getFrame());
	}
	
	@Override
	public void renderControl() {
		Timeline.renderFrameControls();
	}

}