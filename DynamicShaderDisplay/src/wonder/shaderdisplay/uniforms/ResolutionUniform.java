package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.glUniform2f;

import imgui.ImGui;

public class ResolutionUniform extends Uniform {
	
	private static int viewportWidth, viewportHeight;
	public static final String NAME = "iResolution";
	
	private final int location;
	private int w, h;
	
	public ResolutionUniform(int program) {
		super(NAME);
		this.location = new ValueLocationCache(program, NAME).getLocation(0);
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
	
}