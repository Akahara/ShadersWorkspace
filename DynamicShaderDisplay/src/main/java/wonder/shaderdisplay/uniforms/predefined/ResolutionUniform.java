package wonder.shaderdisplay.uniforms.predefined;

import wonder.shaderdisplay.uniforms.NonEditableUniform;
import wonder.shaderdisplay.uniforms.UniformApplicationContext;
import wonder.shaderdisplay.uniforms.ValueLocationCache;

import static org.lwjgl.opengl.GL20.glUniform2f;
import static org.lwjgl.opengl.GL20.glUniform2i;

public class ResolutionUniform extends NonEditableUniform {
	
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
	public void apply(UniformApplicationContext context) {
		if(w != viewportWidth || h != viewportHeight) {
			if (isFloat)
				glUniform2f(location, viewportWidth, viewportHeight);
			else
				glUniform2i(location, viewportWidth, viewportHeight);
			w = viewportWidth;
			h = viewportHeight;
		}
	}
	
	@Override
	public void renderControl() {}
	
	public static void updateViewportSize(int width, int height) {
		viewportWidth = width;
		viewportHeight = height;
	}

}