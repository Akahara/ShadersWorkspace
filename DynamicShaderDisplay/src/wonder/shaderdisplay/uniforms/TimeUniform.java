package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.glUniform1f;

import imgui.ImGui;
import imgui.type.ImBoolean;

class TimeUniform extends Uniform {
	
	public static final String NAME = "iTime";
	private final int location;
	private float currentTime;
	private ImBoolean paused = new ImBoolean();
	
	TimeUniform(int program) {
		super(NAME);
		this.location = new ValueLocationCache(program, NAME).getLocation(0);
	}
	
	@Override
	public void step(float d) {
		if(!paused.get())
			currentTime += d;
	}
	
	@Override
	public void apply() {
		glUniform1f(location, currentTime);
	}
	
	@Override
	public void renderControl() {
		ImGui.checkbox("Pause iTime", paused);
		
		if(ImGui.button("Reset iTime"))
			currentTime = 0;
		
		if(!paused.get()) ImGui.beginDisabled();
		float[] ptr = new float[] { currentTime };
		ImGui.dragFloat("iTime", ptr, .01f);
		currentTime = ptr[0];
		if(!paused.get()) ImGui.endDisabled();
	}
	
	TimeUniform copy(Uniform u) {
		if(u instanceof TimeUniform) {
			currentTime = ((TimeUniform) u).currentTime;
			paused.set(((TimeUniform) u).paused);
		}
		return this;
	}
}