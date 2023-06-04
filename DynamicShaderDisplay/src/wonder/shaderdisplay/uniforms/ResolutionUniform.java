package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.glUniform2f;

import imgui.ImGui;

public class ResolutionUniform extends Uniform {
	
	private static int viewportWidth, viewportHeight;
	
	private final int location;
	private int w, h;
	
	public ResolutionUniform(String name, int program) {
		super(name);
		this.location = new ValueLocationCache(program, name).getLocation(0);
	}
	
	@Override
	public void apply() {
		if(w != viewportWidth || h != viewportHeight) {
			glUniform2f(location, viewportWidth, viewportHeight);
			w = viewportWidth;
			h = viewportHeight;
		}
	}
	
	@Override
	public void renderControl() {
		ImGui.beginDisabled();
		ImGui.dragFloat2(name, new float[] { viewportWidth, viewportHeight });
		ImGui.endDisabled();
	}
	
	public static void updateViewportSize(int width, int height) {
		viewportWidth = width;
		viewportHeight = height;
	}

	@Override
	public String toUniformString() {
		return "uniform vec2 " + name + ";";
	}
	
}