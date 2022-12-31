package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.glUniform2f;

import imgui.ImGui;
import wonder.shaderdisplay.GLWindow;

class ResolutionUniform extends Uniform {

	public static final String NAME = "iResolution";
	
	private final int location;
	private int w, h;
	
	ResolutionUniform(int program) {
		super(NAME);
		this.location = new ValueLocationCache(program, NAME).getLocation(0);
	}
	
	@Override
	public void apply() {
		if(w != GLWindow.winWidth || h != GLWindow.winHeight) {
			glUniform2f(location, GLWindow.winWidth, GLWindow.winHeight);
			w = GLWindow.winWidth;
			h = GLWindow.winHeight;
		}
	}
	
	@Override
	public void renderControl() {
		ImGui.beginDisabled();
		ImGui.dragFloat2(name, new float[] { GLWindow.winWidth, GLWindow.winHeight });
		ImGui.endDisabled();
	}
	
}