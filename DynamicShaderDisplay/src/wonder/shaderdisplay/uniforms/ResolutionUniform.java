package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.glUniform2f;
import static org.lwjgl.opengl.GL20.glUniform2i;

import imgui.ImGui;

public class ResolutionUniform extends Uniform {
	
	private static int viewportWidth, viewportHeight;
	
	private final int location;
	private final boolean isFloat;
	private int w, h;
	
	public ResolutionUniform(String name, int program, boolean isFloat) {
		super(name);
		this.location = ValueLocationCache.getLocation(program, name);
		this.isFloat = isFloat;
	}
	
	@Override
	public void apply() {
		if(w != viewportWidth || h != viewportHeight) {
			if(isFloat)
				glUniform2f(location, viewportWidth, viewportHeight);
			else
				glUniform2i(location, viewportWidth, viewportHeight);
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
		return "uniform " + (isFloat?"vec2 ":"ivec2 ") + name + ";";
	}
	
}