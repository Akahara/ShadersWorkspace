package wonder.shaderdisplay.uniforms;

import static org.lwjgl.opengl.GL20.glUniform1i;

import wonder.shaderdisplay.TexturesSwapChain;

public class InputTextureUniform extends Uniform {
	
	public static final int INPUT_TEXTURE_SLOT = TexturesSwapChain.RENDER_TARGET_COUNT;

	private final int location;
	
	public InputTextureUniform(int program, String name) {
		super(name);
		this.location = new ValueLocationCache(program, name).getLocation(0);
	}
	
	@Override
	public void apply() {
		glUniform1i(location, INPUT_TEXTURE_SLOT);
	}

	@Override
	public void renderControl() {
		
	}

}
